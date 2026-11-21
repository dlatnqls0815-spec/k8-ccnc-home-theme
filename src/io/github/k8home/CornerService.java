package io.github.k8home;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ccNC keeps two blocks floating in the bottom corners of every detail screen: back / home / menu on
 * the left and the now-playing box on the right. This service draws the same two blocks over stock
 * screens. It hides itself over the theme (which draws its own), launchers, the stock navigation
 * (the navigation row is NavBarService's job) and phone projection or camera screens.
 */
public final class CornerService extends Service {
    private static final String TAG = "K8HomeCorners";
    private static final String PAGE = "file:///android_asset/corners.html";
    private static final long POLL_MS = 400;
    private static final float HEIGHT = 0.125f;
    // Measured on the manual's settings screen: 230 and 234 of 944 px.
    private static final float KEYS_WIDTH = 0.244f;
    private static final float MEDIA_WIDTH = 0.248f;
    private static final String[] HIDDEN_PARTS = {
        "phoneprojection", "carplay", "androidauto", "camera", "rvm", "avm", "pdw"
    };

    private final Handler handler = new Handler();
    private final Set<String> launchers = new HashSet<String>();
    private WindowManager windows;
    private WebView keys, mediaBox;
    private boolean keysReady, mediaReady, attached, running, rearView;
    // Elapsed-time marks; 0 = not shown. A missed close broadcast expires after the limit below.
    private long keyboardAt, voiceAt;
    private static final long INPUT_LIMIT_MS = 120000;
    // Stock Korean keyboard and navi keyboard (split screen listens to the same), voice recognition.
    private static final String[] KEYBOARD_SHOW = {"com.android.inputmethod.Korean.action.showwindow", "com.mobis.intent.action.navi_keyboard_show"};
    private static final String[] KEYBOARD_HIDE = {"com.android.inputmethod.Korean.action.hidewindow", "com.mobis.intent.action.navi_keyboard_hide"};
    private static final String[] VOICE_START = {"com.mobis.intent.action.VR_START", "com.hkmc.intent.action.start_dialog_vr"};
    private static final String[] VOICE_END = {"com.mobis.intent.action.VR_FINISH", "com.hkmc.intent.action.end_dialog_vr"};

    private static boolean recent(long at) {
        return at != 0 && android.os.SystemClock.elapsedRealtime() - at < INPUT_LIMIT_MS;
    }

    private static boolean any(String[] list, String action) {
        for (String each : list) if (each.equals(action)) return true;
        return false;
    }
    private StockMedia media = StockMedia.EMPTY;
    private BluetoothCoverArt coverArt;
    private String coverImage = "";

    /** Starts or stops the service to match the setting. */
    static void sync(Context context) {
        Intent intent = new Intent(context, CornerService.class);
        try {
            if (ThemePrefs.corners(context)) context.startService(intent);
            else context.stopService(intent);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Corner service unavailable", failure);
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (StockNavigation.REARVIEW_ON.equals(action) || StockNavigation.REARVIEW_OFF.equals(action)) {
                rearView = StockNavigation.REARVIEW_ON.equals(action);
                if (rearView) detach();
                return;
            }
            if (any(KEYBOARD_SHOW, action) || any(KEYBOARD_HIDE, action)) {
                keyboardAt = any(KEYBOARD_SHOW, action) ? android.os.SystemClock.elapsedRealtime() : 0;
                if (keyboardAt != 0) detach();
                return;
            }
            if (any(VOICE_START, action) || any(VOICE_END, action)) {
                voiceAt = any(VOICE_START, action) ? android.os.SystemClock.elapsedRealtime() : 0;
                if (voiceAt != 0) detach();
                return;
            }
            StockMedia next = media.update(intent.getAction(), intent.getExtras());
            if (next == media) return;
            media = next;
            coverArt.setTrack("bt".equals(media.source) ? media.title + "\n" + media.artist : "");
            pushMedia();
        }
    };

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            if (!ThemePrefs.corners(CornerService.this)) {
                stopSelf();
                return;
            }
            boolean want = !rearView && !recent(keyboardAt) && !recent(voiceAt) && keysReady && mediaReady && screenOn() && allowed(StockNavigation.foreground(CornerService.this));
            if (want && !attached) attach();
            else if (!want && attached) detach();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        coverArt = new BluetoothCoverArt(new BluetoothCoverArt.Listener() {
            @Override public void changed(String image) {
                coverImage = image;
                pushMedia();
            }
        });
        IntentFilter filter = new IntentFilter();
        StockMedia.addActions(filter);
        filter.addAction(StockNavigation.REARVIEW_ON);
        filter.addAction(StockNavigation.REARVIEW_OFF);
        for (String[] list : new String[][] {KEYBOARD_SHOW, KEYBOARD_HIDE, VOICE_START, VOICE_END}) {
            for (String each : list) filter.addAction(each);
        }
        registerReceiver(receiver, filter);
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        try {
            for (ResolveInfo info : getPackageManager().queryIntentActivities(home, 0)) {
                if (info.activityInfo != null) launchers.add(info.activityInfo.packageName);
            }
        } catch (RuntimeException ignored) { }
        launchers.add(getPackageName());
        keys = createWebView(true);
        mediaBox = createWebView(false);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!ThemePrefs.corners(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            sendBroadcast(new Intent(StockMedia.REQUEST));
            handler.post(poll);
        }
        return START_STICKY;
    }

    private WebView createWebView(final boolean isKeys) {
        WebView view = new WebView(this);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setTextZoom(100);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        view.setVerticalScrollBarEnabled(false);
        view.setHorizontalScrollBarEnabled(false);
        view.setBackgroundColor(0x00000000); // the page draws the block with its rounded inner corner
        view.addJavascriptInterface(new Bridge(), "K8Corners");
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView loaded, String url) {
                return true;
            }

            @Override public void onPageFinished(WebView loaded, String url) {
                if (!PAGE.equals(url)) return;
                loaded.evaluateJavascript("window.K8CornerView.part('" + (isKeys ? "keys" : "media") + "')", null);
                if (isKeys) keysReady = true;
                else {
                    mediaReady = true;
                    pushMedia();
                }
            }
        });
        view.loadUrl(PAGE);
        return view;
    }

    private final class Bridge {
        @JavascriptInterface public void action(final String action) {
            handler.post(new Runnable() {
                @Override public void run() {
                    if (attached) handle(action);
                }
            });
        }
    }

    private void handle(String action) {
        if ("home".equals(action)) openTheme(false, false);
        else if ("menu".equals(action)) openTheme(true, false);
        else if ("back".equals(action)) {
            if (!previousTask()) openTheme(false, false);
        } else if ("media".equals(action)) {
            // ccNC opens the playing source; with nothing playing it opens the media screen.
            if (media.title.isEmpty() || !StockApps.openPlayer(this, media.source)) openTheme(false, true);
        }
    }

    private void openTheme(boolean apps, boolean mediaPage) {
        try {
            startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(HomeActivity.EXTRA_OPEN_APPS, apps).putExtra(HomeActivity.EXTRA_OPEN_MEDIA, mediaPage));
        } catch (RuntimeException failure) {
            Log.w(TAG, "Theme home unavailable", failure);
        }
    }

    /**
     * A normal app cannot send the BACK key to another app, so the corner back key brings the
     * previously used screen to the front instead. Stock screens keep their own back arrow.
     */
    @SuppressWarnings("deprecation")
    private boolean previousTask() {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        if (manager == null) return false;
        try {
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(2);
            if (tasks.size() < 2) return false;
            manager.moveTaskToFront(tasks.get(1).id, 0);
            return true;
        } catch (RuntimeException failure) {
            Log.w(TAG, "Previous screen unavailable", failure);
            return false;
        }
    }

    private boolean allowed(ComponentName top) {
        if (top == null) return false;
        String name = top.getPackageName();
        if (launchers.contains(name) || StockNavigation.PACKAGE.equals(name)) return false;
        String lower = top.flattenToString().toLowerCase(java.util.Locale.US);
        for (String part : HIDDEN_PARTS) if (lower.contains(part)) return false;
        return true;
    }

    private void attach() {
        if (windows == null) return;
        Point size = new Point();
        windows.getDefaultDisplay().getRealSize(size);
        if (size.x <= 0 || size.y <= 0) return;
        int height = Math.round(size.y * HEIGHT);
        try {
            windows.addView(keys, params(Math.round(size.x * KEYS_WIDTH), height, Gravity.LEFT, "K8HomeCornerKeys"));
            try {
                windows.addView(mediaBox, params(Math.round(size.x * MEDIA_WIDTH), height, Gravity.RIGHT, "K8HomeCornerMedia"));
            } catch (RuntimeException failure) {
                windows.removeViewImmediate(keys);
                throw failure;
            }
            attached = true;
            pushMedia();
        } catch (RuntimeException failure) {
            Log.w(TAG, "overlay window was refused", failure);
        }
    }

    private static WindowManager.LayoutParams params(int width, int height, int side, String title) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | side;
        params.setTitle(title);
        return params;
    }

    private void detach() {
        if (!attached) return;
        attached = false;
        for (WebView view : new WebView[] { keys, mediaBox }) {
            try {
                windows.removeViewImmediate(view);
            } catch (RuntimeException ignored) { }
        }
    }

    private void pushMedia() {
        if (!mediaReady || mediaBox == null) return;
        mediaBox.evaluateJavascript("window.K8CornerView && window.K8CornerView.media(" + quote(media.title) + ","
            + quote(media.artist.isEmpty() ? media.settings : media.artist) + "," + Boolean.TRUE.equals(media.playing)
            + "," + quote("bt".equals(media.source) ? coverImage : "") + ")", null);
    }

    @SuppressWarnings("deprecation")
    private boolean screenOn() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        return power != null && power.isScreenOn();
    }

    @Override public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(receiver);
        coverArt.close();
        detach();
        for (WebView view : new WebView[] { keys, mediaBox }) {
            if (view == null) continue;
            view.removeJavascriptInterface("K8Corners");
            view.destroy();
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private static String quote(String value) {
        return JSONObject.quote(value).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
}
