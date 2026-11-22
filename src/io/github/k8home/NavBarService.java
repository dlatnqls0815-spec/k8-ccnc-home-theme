package io.github.k8home;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Parcel;
import android.os.RemoteException;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;


/** Hosts the optional ccNC row for a map session opened from the theme. */
public final class NavBarService extends Service {
    static final String OPENED_MAP = "io.github.k8home.action.OPENED_MAP";
    static final String SEARCHING_MAP = "io.github.k8home.action.SEARCHING_MAP";
    private static final String TAG = "K8HomeNavBar";
    private static final String PAGE = "file:///android_asset/navbar.html";
    private static final String PLAYLIST_PAGE = "file:///android_asset/playback_panel.html";
    private static final String ROUTE_PAGE = "file:///android_asset/route_panel.html";
    private static final String SKIN_PAGE = "file:///android_asset/naviskin.html";
    // Navigation IInterfaceService call 5 = isMapView_State() (vtable order of com.mnsoft.navi.g, 2026-11-22).
    private static final String NAVI_DESCRIPTOR = "com.mnsoft.navi.IInterfaceService";
    private static final int NAVI_IS_MAP_VIEW = 5;
    // Skin window: the stock left button column above the bar, 140×632 of 1920×720.
    private static final int SKIN_WIDTH = 140;
    private static final int SKIN_HEIGHT = 632;
    private static final int REFERENCE_WIDTH = 1920;
    private static final int REFERENCE_HEIGHT = 720;
    // The live 1920×720 stock strip begins at y=627, including its upper edge.
    private static final int ROW_HEIGHT = 94;
    private static final long POLL_MS = 250;
    private static final long ENTRY_TIMEOUT_MS = 10000;

    private final Handler handler = new Handler();
    private WindowManager windows;
    private WebView web;
    private WebView panel;
    private String panelPage = "";
    private BluetoothCoverArt coverArt;
    private WebView skin;
    private boolean skinReady, skinAttached, naviBound;
    private IBinder navi;
    private String skinState = "";
    private boolean lastMapView;
    private final ServiceConnection naviConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            try {
                if (NAVI_DESCRIPTOR.equals(binder.getInterfaceDescriptor())) navi = binder;
            } catch (RemoteException ignored) { }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            navi = null;
        }
    };
    private String coverImage = "";
    private boolean attached, pageReady, sessionEnded;
    private boolean searchSuspended;
    private boolean cancelPending;
    private StockMedia media = StockMedia.EMPTY;
    private long entryDeadline, resumeAfter;
    private StockRoute route;
    private StockTransport transport;

    private final StockRoute.Listener routeListener = new StockRoute.Listener() {
        @Override public void routeChanged(boolean guidanceStarted) {
            if (sessionEnded) return;
            if (guidanceStarted && navigationInFront()) resumeOverlay();
            StockRoute.Snapshot data = route.snapshot();
            if (data.known && !data.active && cancelPending) {
                cancelPending = false;
                handler.removeCallbacks(cancelFallback);
            }
            pushRoute();
            pushSkin();
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (sessionEnded) return;
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action) || StockNavigation.REARVIEW_ON.equals(action)) {
                endSession(); // never draw over the rear camera; the stock row comes back
                return;
            }
            StockMedia next = media.update(action, intent.getExtras());
            if (next == media) return;
            if (!media.source.equals(next.source)) transport.close();
            media = next;
            coverArt.setTrack("bt".equals(media.source) ? media.title + "\n" + media.artist : "");
            pushMedia();
        }
    };

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (sessionEnded) return;
            long now = SystemClock.elapsedRealtime();
            if (!ThemePrefs.navBar(NavBarService.this) || !screenOn()) {
                endSession();
                return;
            }
            if (mapInFront() && pageReady) {
                if (!searchSuspended && now >= resumeAfter && !attached && !attach()) {
                    endSession();
                    return;
                }
                syncSkin();
            } else if ((!searchSuspended || !navigationInFront())
                    && (attached || now >= entryDeadline)) {
                endSession();
                return;
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final Runnable cancelFallback = new Runnable() {
        @Override public void run() {
            if (!cancelPending) return;
            if (mapInFront()) toast("순정 하단줄에서 경로 상태를 확인하세요.");
            endSession();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        transport = new StockTransport(this);
        coverArt = new BluetoothCoverArt(new BluetoothCoverArt.Listener() {
            @Override public void changed(String image) {
                coverImage = image;
                pushCover();
            }
        });
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(StockNavigation.REARVIEW_ON);
        StockMedia.addActions(filter);
        registerReceiver(receiver, filter);
        route = StockRoute.of(this);
        route.start(routeListener);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        boolean searching = intent != null && SEARCHING_MAP.equals(intent.getAction());
        if (intent == null || (!searching && !OPENED_MAP.equals(intent.getAction())) || !ThemePrefs.navBar(this)) {
            endSession();
            return START_NOT_STICKY;
        }
        if (web == null) buildBar();
        sessionEnded = false;
        searchSuspended = searching;
        if (searching) detach();
        resumeAfter = 0;
        entryDeadline = SystemClock.elapsedRealtime() + ENTRY_TIMEOUT_MS;
        handler.removeCallbacks(poll);
        handler.post(poll);
        return START_NOT_STICKY;
    }

    private void buildBar() {
        web = createWebView();
        web.setBackgroundColor(0xff686765);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (!sessionEnded && PAGE.equals(url)) pageReady = true;
            }
        });
        web.loadUrl(PAGE);
    }

    /** ccNC-shaped covers for the stock left buttons. The window never takes touches, so taps reach the
     *  stock buttons underneath. Shown only while the navigation says its plain map view is up. */
    private void syncSkin() {
        if (!ThemePrefs.naviSkin(this)) {
            detachSkin();
            return;
        }
        boolean map = mapView();
        if (map != lastMapView) {
            lastMapView = map;
            Log.i(TAG, "navi map view " + map + " (call " + NAVI_IS_MAP_VIEW + ")");
        }
        boolean want = attached && map;
        if (want && skin == null) {
            skin = createWebView();
            skin.setBackgroundColor(0);
            skin.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return true;
                }

                @Override public void onPageFinished(WebView view, String url) {
                    if (view == skin && SKIN_PAGE.equals(url)) {
                        skinReady = true;
                        skinState = "";
                        pushSkin();
                    }
                }
            });
            skin.loadUrl(SKIN_PAGE);
        }
        if (want && !skinAttached && skin != null) {
            Point size = new Point();
            windows.getDefaultDisplay().getRealSize(size);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                Math.round(size.x * SKIN_WIDTH / (float) REFERENCE_WIDTH),
                Math.round(size.y * SKIN_HEIGHT / (float) REFERENCE_HEIGHT), WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            params.setTitle("K8HomeNaviSkin");
            try {
                windows.addView(skin, params);
                skinAttached = true;
                pushSkin();
            } catch (RuntimeException failure) {
                Log.w(TAG, "Navigation skin unavailable", failure);
            }
        } else if (!want) {
            detachSkin();
        }
    }

    private void detachSkin() {
        if (!skinAttached) return;
        skinAttached = false;
        try {
            windows.removeViewImmediate(skin);
        } catch (RuntimeException ignored) { }
    }

    private void pushSkin() {
        if (skin == null || !skinReady) return;
        StockRoute.Snapshot data = route.snapshot();
        // DAYNIGHT_CHANGED daynight_status: true = night (uimode 2). Unknown → night, as the stock map at boot.
        boolean night = !"false".equalsIgnoreCase(data.dayNight);
        boolean muted = "true".equalsIgnoreCase(data.mute) || "1".equals(data.mute);
        String next = night + "," + muted;
        if (next.equals(skinState)) return;
        skinState = next;
        Log.i(TAG, "navi skin night,muted=" + next + " (raw " + data.dayNight + "/" + data.mute + ")");
        skin.evaluateJavascript("window.K8NaviSkin && window.K8NaviSkin.state(" + next + ")", null);
    }

    private boolean mapView() {
        if (navi == null) {
            if (!naviBound) {
                try {
                    naviBound = bindService(new Intent().setComponent(new ComponentName(StockNavigation.PACKAGE,
                        StockNavigation.PACKAGE + ".InterfaceService")), naviConnection, BIND_AUTO_CREATE);
                } catch (SecurityException failure) {
                    Log.w(TAG, "Navigation state service refused", failure);
                }
            }
            return false;
        }
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(NAVI_DESCRIPTOR);
            if (!navi.transact(NAVI_IS_MAP_VIEW, data, reply, 0)) return false;
            reply.readException();
            return reply.readInt() != 0;
        } catch (RemoteException | RuntimeException failure) {
            return false;
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    private WebView createWebView() {
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
        view.addJavascriptInterface(new Bridge(), "K8NavBar");
        return view;
    }

    private final class Bridge {
        @JavascriptInterface public void action(final String action) {
            handler.post(new Runnable() {
                @Override public void run() {
                    if (!sessionEnded && attached && mapInFront() && screenOn()) handle(action);
                }
            });
        }
    }

    private void handle(String action) {
        if ("home".equals(action) || "menu".equals(action)) {
            endSession();
            try {
                startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(HomeActivity.EXTRA_OPEN_APPS, "menu".equals(action)));
            } catch (RuntimeException failure) {
                Log.w(TAG, "Theme home unavailable", failure);
            }
        } else if ("back".equals(action)) {
            endSession();
        } else if ("search".equals(action)) {
            searchSuspended = true;
            entryDeadline = SystemClock.elapsedRealtime() + ENTRY_TIMEOUT_MS;
            detach();
            StockNavigation.request(this, StockNavigation.SEARCH);
        } else if ("cancel".equals(action) && !cancelPending && route.snapshot().active) {
            if (StockNavigation.cancelRoute(this)) {
                cancelPending = true;
                handler.postDelayed(cancelFallback, 2200);
            } else {
                endSession();
            }
        } else if ("playlist".equals(action)) {
            togglePanel(PLAYLIST_PAGE, 492, 290);
        } else if ("route-details".equals(action) && route.snapshot().active) {
            togglePanel(ROUTE_PAGE, 432, 166);
        } else if ("playlist-close".equals(action) || "route-close".equals(action)) {
            dismissPanel();
        } else if ("media-player".equals(action)) {
            if (StockApps.openPlayer(this, media.source)) endSession();
            else toast("순정 미디어 화면을 열 수 없습니다.");
        } else if ("play".equals(action) || "previous".equals(action) || "next".equals(action)) {
            int key = "previous".equals(action) ? KeyEvent.KEYCODE_MEDIA_PREVIOUS
                : "next".equals(action) ? KeyEvent.KEYCODE_MEDIA_NEXT : KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            if (!transport.send(media.source, key) && !MediaKeys.dispatch(this, key)) {
                toast("순정 미디어 제어를 사용할 수 없습니다.");
            }
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!sessionEnded) sendBroadcast(new Intent(StockMedia.REQUEST));
                }
            }, 700);
        }
    }

    private void togglePanel(final String page, int width, int height) {
        if (windows == null || !attached) return;
        if (page.equals(panelPage)) {
            dismissPanel();
            return;
        }
        dismissPanel();
        Point size = new Point();
        windows.getDefaultDisplay().getRealSize(size);
        float scale = size.x / (float) REFERENCE_WIDTH;
        final WebView view = createWebView();
        view.setBackgroundColor(0xff202225);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override public void onPageFinished(WebView loaded, String url) {
                if (panel != loaded || !page.equals(url)) return;
                if (PLAYLIST_PAGE.equals(page)) pushPlaylist();
                else pushRoute();
            }
        });
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            Math.round(width * scale), Math.round(height * scale), WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.OPAQUE);
        params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        params.y = Math.round((ROW_HEIGHT + 8) * scale);
        params.x = Math.round(8 * scale);
        params.setTitle(PLAYLIST_PAGE.equals(page) ? "K8HomePlaybackPanel" : "K8HomeRoutePanel");
        try {
            windows.addView(view, params);
            panel = view;
            panelPage = page;
            view.loadUrl(page);
        } catch (RuntimeException failure) {
            if (panel == view) dismissPanel();
            else view.destroy();
            Log.w(TAG, "Navigation panel unavailable", failure);
        }
    }

    private void dismissPanel() {
        if (panel == null) return;
        WebView view = panel;
        panel = null;
        panelPage = "";
        try {
            windows.removeViewImmediate(view);
        } catch (IllegalArgumentException ignored) { }
        view.removeJavascriptInterface("K8NavBar");
        view.destroy();
    }

    private boolean attach() {
        if (windows == null) return false;
        Point size = new Point();
        windows.getDefaultDisplay().getRealSize(size);
        if ((long) size.x * REFERENCE_HEIGHT != (long) size.y * REFERENCE_WIDTH || size.x <= 0) return false;
        int height = Math.round(size.y * ROW_HEIGHT / (float) REFERENCE_HEIGHT);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            size.x, height, WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE);
        params.gravity = Gravity.BOTTOM | Gravity.LEFT;
        params.setTitle("K8HomeNavBar");
        try {
            windows.addView(web, params);
            attached = true;
            pushRoute();
            pushMedia();
            pushCover();
            sendBroadcast(new Intent(StockMedia.REQUEST));
            return true;
        } catch (RuntimeException failure) {
            Log.w(TAG, "Navigation row unavailable", failure);
            return false;
        }
    }

    private boolean mapInFront() {
        return StockNavigation.MAP.equals(StockNavigation.foreground(this));
    }

    private boolean navigationInFront() {
        ComponentName foreground = StockNavigation.foreground(this);
        return foreground != null && StockNavigation.PACKAGE.equals(foreground.getPackageName());
    }

    @SuppressWarnings("deprecation")
    private boolean screenOn() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        return power != null && power.isScreenOn();
    }

    private void resumeOverlay() {
        if (!searchSuspended) return;
        searchSuspended = false;
        long now = SystemClock.elapsedRealtime();
        resumeAfter = now + 850;
        entryDeadline = now + ENTRY_TIMEOUT_MS;
    }

    private void pushRoute() {
        if (!pageReady || !attached) return;
        StockRoute.Snapshot data = route.snapshot();
        web.evaluateJavascript("window.K8NavBarView.route(" + quote(data.distance) + ","
            + quote(data.remaining) + "," + quote(data.destination) + "," + data.active + ","
            + quote(data.arrival) + "," + quote(data.road) + ")", null);
        if (ROUTE_PAGE.equals(panelPage)) {
            if (!data.active) {
                dismissPanel();
            } else {
                panel.evaluateJavascript("window.K8RoutePanel && window.K8RoutePanel.update("
                    + quote(data.destination) + "," + quote(data.distance) + "," + quote(data.remaining) + ","
                    + quote(data.arrival) + ")", null);
            }
        }
    }

    private void pushMedia() {
        if (pageReady && attached) web.evaluateJavascript("window.K8NavBarView.media(" + quote(media.title) + ","
            + quote(media.artist.isEmpty() ? media.settings : media.artist) + "," + Boolean.TRUE.equals(media.playing)
            + ")", null);
        pushPlaylist();
    }

    private void pushPlaylist() {
        if (PLAYLIST_PAGE.equals(panelPage)) panel.evaluateJavascript("window.K8Playback && window.K8Playback.update("
            + quote(media.title) + "," + quote(media.artist) + "," + Boolean.TRUE.equals(media.playing)
            + "," + quote(media.source) + "," + quote(media.settings) + ")", null);
    }

    private void pushCover() {
        if (pageReady && attached) web.evaluateJavascript("window.K8NavBarView.cover(" + quote(coverImage) + ")", null);
    }

    private void endSession() {
        sessionEnded = true;
        transport.close();
        detach();
        handler.removeCallbacksAndMessages(null);
        stopSelf();
    }

    private void detach() {
        detachSkin();
        dismissPanel();
        if (!attached) return;
        attached = false;
        try {
            windows.removeViewImmediate(web);
        } catch (RuntimeException failure) {
            Log.w(TAG, "Navigation row already detached", failure);
        }
    }

    @Override public void onDestroy() {
        sessionEnded = true;
        transport.close();
        coverArt.close();
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(receiver);
        route.stop(routeListener);
        detach();
        if (web != null) {
            web.removeJavascriptInterface("K8NavBar");
            web.destroy();
        }
        if (skin != null) {
            skin.removeJavascriptInterface("K8NavBar");
            skin.destroy();
            skin = null;
        }
        if (naviBound) {
            unbindService(naviConnection);
            naviBound = false;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static String quote(String value) {
        return JSONObject.quote(value).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
}
