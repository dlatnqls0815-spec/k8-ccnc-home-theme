package io.github.k8home;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HomeActivity extends Activity {
    private static final String PAGE = "file:///android_asset/index.html";
    private static final ComponentName NAVIGATION = new ComponentName("com.mnsoft.navi", "com.mnsoft.navi.NaviApp");
    private static final String VEHICLE_SETTINGS = "com.hkmc.software.vehiclesetting";
    private static final String BT_WIDGET = "com.hkmc.audio.btaudio.widgetupdate";
    private static final String USB_WIDGET = "com.hkmc.audio.usb.widgetupdate";
    private static final String WIDGET_REQUEST = "com.hkmc.request.widgetupdate";
    private static final int STANDBY_PHOTO = 21;
    private static final int SPLIT_PHOTO = 22;
    private static final int DRIVER_ONE_PHOTO = 23;
    private static final int DRIVER_TWO_PHOTO = 24;
    private static final String[] SHORTCUTS = {"media", "phone", "radio", "projection", "settings",
        "settings-car", "settings-general", "settings-nav", "settings-sound",
        "settings-devices", "settings-profile", "settings-voice", "settings-display",
        "voicememo", "connect", "alerts", "source-bt", "source-usb", "source-fm", "source-am", "source-nature"};
    private static final String[] LABELS = {"미디어", "전화", "라디오", "폰 프로젝션", "순정 설정",
        "차량", "일반", "내비게이션", "사운드", "기기 연결", "사용자 프로필", "음성인식", "디스플레이",
        "음성 메모", "Kia Connect", "알림", "블루투스 오디오", "USB 오디오", "FM 라디오", "AM 라디오", "자연의 소리"};
    private FrameLayout root;
    private FrameLayout mapSlot;
    private WebView web;
    private SharedPreferences shortcuts;
    private boolean pageReady;
    private String mediaTitle = "";
    private String mediaArtist = "";
    private Boolean mediaPlaying;
    private long mediaPosition = -1;
    private long mediaDuration = -1;
    private String mediaSource = "";
    private int brightness = -1;
    private VoiceMemos memos;
    private int unmutedVolume = -1;
    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction()) ||
                BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                syncControls();
                return;
            }
            Bundle extras = intent.getExtras();
            if (extras == null) return;
            mediaTitle = mediaText(extras.get("filename"));
            mediaArtist = mediaText(extras.get("artist_name"));
            String playstate = String.valueOf(extras.get("playstate"));
            mediaPlaying = "1".equals(playstate) ? Boolean.TRUE : "0".equals(playstate) ? Boolean.FALSE : null;
            mediaPosition = timeValue(extras.get("audio_current_time"));
            mediaDuration = timeValue(extras.get("audio_total_time"));
            mediaSource = USB_WIDGET.equals(intent.getAction()) ? "usb" : "bt";
            syncMedia();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        shortcuts = getSharedPreferences("shortcuts", MODE_PRIVATE);
        brightness = shortcuts.getInt("theme-brightness", -1);
        if (shortcuts.contains("theme-brightness")) applyBrightness();
        memos = new VoiceMemos(this, new VoiceMemos.Listener() {
            @Override public void changed(String mode, String active, JSONArray list, long free) {
                if (pageReady && !isDestroyed()) web.evaluateJavascript("window.K8Home.memosChanged(" + javascriptString(mode)
                    + "," + javascriptString(active) + "," + list + "," + free + ")", null);
            }
        });
        if (state != null) memos.restoreExport(state.getString("memo-export-id"));
        root = new FrameLayout(this);
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        web.setBackgroundColor(0xff0c121b);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new HomeBridge(), "K8Host");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (PAGE.equals(url)) {
                    pageReady = true;
                    syncMapContent();
                    syncMedia();
                    syncControls();
                    syncPhoto("standby");
                    syncPhoto("split");
                    syncPhoto("profile0");
                    syncPhoto("profile1");
                }
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        mapSlot = new FrameLayout(this);
        mapSlot.setVisibility(View.GONE);
        root.addView(mapSlot, new FrameLayout.LayoutParams(0, 0));
        setContentView(root);
        web.loadUrl(PAGE);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BT_WIDGET);
        filter.addAction(USB_WIDGET);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mediaReceiver, filter);
        sendBroadcast(new Intent(WIDGET_REQUEST));
        syncControls();
    }

    @Override protected void onPause() {
        memos.stop();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("memo-export-id", memos.exportId());
    }

    @Override protected void onStop() {
        unregisterReceiver(mediaReceiver);
        mediaTitle = "";
        mediaArtist = "";
        mediaPlaying = null;
        mediaPosition = mediaDuration = -1;
        mediaSource = "";
        syncMedia();
        super.onStop();
    }

    private static String mediaText(Object value) {
        if (!(value instanceof CharSequence)) return "";
        String text = value.toString();
        return text.substring(0, Math.min(text.length(), 160));
    }

    private static String javascriptString(String value) {
        return JSONObject.quote(value).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    private void syncMedia() {
        if (pageReady) web.evaluateJavascript("window.K8Home.mediaChanged(" + javascriptString(mediaTitle)
            + "," + javascriptString(mediaArtist) + "," + mediaPlaying + "," + mediaPosition + ","
            + mediaDuration + "," + javascriptString(mediaSource) + ")", null);
    }

    private static long timeValue(Object value) {
        try {
            long time = Long.parseLong(String.valueOf(value));
            return time >= 0 && time <= 86400000 ? time : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void syncControls() {
        if (!pageReady) return;
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        Boolean bluetoothEnabled = null;
        JSONArray names = null;
        try {
            if (bluetooth != null) {
                bluetoothEnabled = bluetooth.isEnabled();
                if (bluetoothEnabled) {
                    List<String> paired = new ArrayList<>();
                    for (BluetoothDevice device : bluetooth.getBondedDevices()) {
                        String name = mediaText(device.getName());
                        paired.add(name.isEmpty() ? "이름 없는 기기" : name);
                    }
                    Collections.sort(paired);
                    names = new JSONArray(paired);
                }
            }
        } catch (SecurityException ignored) { }
        web.evaluateJavascript("window.K8Home.controlsChanged("
            + (audio == null ? "null" : String.valueOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC)))
            + "," + (audio == null ? 0 : audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            + "," + brightness + "," + bluetoothEnabled + ")", null);
        String deviceNames = String.valueOf(names).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
        web.evaluateJavascript("window.K8Home.pairedDevicesChanged(" + deviceNames + ")", null);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) syncControls();
    }

    private void applyBrightness() {
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.screenBrightness = brightness < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : brightness / 100f;
        getWindow().setAttributes(attributes);
    }

    private void changeBrightness(int level) {
        brightness = level < 0 ? -1 : Math.max(25, Math.min(100, level));
        applyBrightness();
        shortcuts.edit().putInt("theme-brightness", brightness).apply();
        syncControls();
    }

    private void adjustVolume(String action) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) { toast("미디어 음량을 사용할 수 없습니다."); return; }
        try {
            if ("volume-mute".equals(action)) {
                int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (current > 0) {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                    unmutedVolume = current;
                } else {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(1, unmutedVolume), 0);
                    unmutedVolume = -1;
                }
            } else {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    "volume-up".equals(action) ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
            }
        } catch (SecurityException ignored) {
            toast("이 기기에서 미디어 음량을 변경할 수 없습니다.");
        }
        syncControls();
    }

    private void syncPhoto(String target) {
        if (pageReady) web.evaluateJavascript("window.K8Home.photoChanged(" + javascriptString(target)
            + "," + javascriptString(shortcuts.getString("photo-" + target, "")) + ")", null);
    }

    private void choosePhoto(int request) {
        try {
            startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
                .addCategory(Intent.CATEGORY_OPENABLE), request);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            toast("이 기기에 사진 선택 앱이 없습니다.");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == VoiceMemos.EXPORT_REQUEST) {
            memos.exportResult(result == RESULT_OK && data != null ? data.getData() : null);
            return;
        }
        final String target;
        switch (request) {
            case STANDBY_PHOTO: target = "standby"; break;
            case SPLIT_PHOTO: target = "split"; break;
            case DRIVER_ONE_PHOTO: target = "profile0"; break;
            case DRIVER_TWO_PHOTO: target = "profile1"; break;
            default: return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    try (InputStream stream = getContentResolver().openInputStream(uri)) {
                        BitmapFactory.decodeStream(stream, null, options);
                    }
                    if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Invalid image");
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = 1;
                    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 1024) {
                        options.inSampleSize *= 2;
                    }
                    Bitmap image;
                    try (InputStream stream = getContentResolver().openInputStream(uri)) {
                        image = BitmapFactory.decodeStream(stream, null, options);
                    }
                    if (image == null) throw new IOException("Invalid image");
                    ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                    boolean encoded = image.compress(Bitmap.CompressFormat.JPEG, 72, jpeg);
                    image.recycle();
                    if (!encoded || jpeg.size() > 450000) throw new IOException("Image could not be compressed");
                    shortcuts.edit().putString("photo-" + target,
                        "data:image/jpeg;base64," + Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP)).apply();
                    runOnUiThread(new Runnable() {
                        @Override public void run() { if (!isDestroyed()) syncPhoto(target); }
                    });
                } catch (IOException | RuntimeException ignored) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { if (!isDestroyed()) toast("사진을 불러올 수 없습니다."); }
                    });
                }
            }
        }, "theme-photo").start();
    }

    /** Runs on the UI thread. Pass a detached map view, or null to restore the empty state. */
    void setMapContent(View content) {
        if (content != null && content.getParent() == mapSlot) return;
        if (content != null && content.getParent() != null) {
            throw new IllegalArgumentException("Map view already has a parent");
        }
        mapSlot.removeAllViews();
        mapSlot.setVisibility(View.GONE);
        if (content != null) mapSlot.addView(content, new FrameLayout.LayoutParams(-1, -1));
        syncMapContent();
    }

    private void syncMapContent() {
        if (pageReady) web.evaluateJavascript(
            "window.K8Home.mapAttached(" + (mapSlot.getChildCount() != 0) + ")", null);
    }

    public final class HomeBridge {
        @JavascriptInterface public void setBrightness(final int level) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (!isFinishing() && !isDestroyed()) changeBrightness(Math.max(25, level));
                }
            });
        }
        @JavascriptInterface public void setMediaVolume(final int level) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed()) return;
                    AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
                    if (audio == null) return;
                    int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    try {
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.max(0, Math.min(max, level)), 0);
                    } catch (SecurityException ignored) {
                        toast("이 기기에서 미디어 음량을 변경할 수 없습니다.");
                    }
                    syncControls();
                }
            });
        }

        @JavascriptInterface public void dial(final String number) {
            if (number == null || number.length() > 24 || !number.matches("\\+?[0-9*#]{2,24}") || !number.matches(".*[0-9].*")) return;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (!isFinishing() && !isDestroyed()
                        && !open(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))) {
                        toast("이 기기에서 전화 걸기 화면을 열 수 없습니다.");
                    }
                }
            });
        }

        @JavascriptInterface public void performAction(final String action) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (!isFinishing() && !isDestroyed()) handleAction(action);
                }
            });
        }

        @JavascriptInterface public void updateBounds(final int viewport, final int left, final int top,
                                                       final int width, final int height) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed() || mapSlot.getChildCount() == 0) return;
                    if (viewport <= 0 || width <= 0 || height <= 0 || left < 0 || top < 0) {
                        mapSlot.setVisibility(View.GONE);
                        return;
                    }
                    float scale = web.getWidth() / (float) viewport;
                    int x = Math.round(left * scale);
                    int y = Math.round(top * scale);
                    int w = Math.min(Math.round(width * scale), web.getWidth() - x);
                    int h = Math.min(Math.round(height * scale), web.getHeight() - y);
                    if (w <= 0 || h <= 0) {
                        mapSlot.setVisibility(View.GONE);
                        return;
                    }
                    FrameLayout.LayoutParams bounds = new FrameLayout.LayoutParams(w, h);
                    bounds.leftMargin = x;
                    bounds.topMargin = y;
                    mapSlot.setLayoutParams(bounds);
                    mapSlot.setVisibility(View.VISIBLE);
                }
            });
        }
    }

    private void handleAction(String action) {
        if (action == null) return;
        if ("exit".equals(action)) {
            finish();
        } else if ("navi".equals(action)) {
            openNavigation();
        } else if ("nav-home".equals(action) || "nav-work".equals(action)) {
            String destination = "nav-home".equals(action) ? "우리 집" : "회사";
            new AlertDialog.Builder(this).setTitle(destination)
                .setMessage("순정 내비를 열고 저장된 " + destination + "을 선택하세요.")
                .setPositiveButton("순정 내비 열기", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int index) { openNavigation(); }
                })
                .setNegativeButton("취소", null).show();
        } else if ("media-prev".equals(action)) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
        } else if ("media-next".equals(action)) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT);
        } else if ("media-toggle".equals(action) || "media-play".equals(action) || "media-pause".equals(action)) {
            int key = "media-play".equals(action) ? KeyEvent.KEYCODE_MEDIA_PLAY
                : "media-pause".equals(action) ? KeyEvent.KEYCODE_MEDIA_PAUSE : KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
            sendMediaKey(key);
        } else if ("volume-up".equals(action) || "volume-down".equals(action) || "volume-mute".equals(action)) {
            adjustVolume(action);
        } else if ("bluetooth-enable".equals(action)) {
            if (!open(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))) {
                openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS, "블루투스");
            }
        } else if ("brightness-up".equals(action) || "brightness-down".equals(action)) {
            changeBrightness((brightness < 0 ? 75 : brightness) + ("brightness-up".equals(action) ? 10 : -10));
        } else if ("brightness-auto".equals(action) || "brightness-reset".equals(action)) {
            changeBrightness("brightness-auto".equals(action) && brightness < 0 ? 75 : -1);
        } else if ("settings-date".equals(action)) {
            openSettingsPanel(Settings.ACTION_DATE_SETTINGS, "날짜/시간");
        } else if ("settings-language".equals(action)) {
            openSettingsPanel(Settings.ACTION_LOCALE_SETTINGS, "언어");
        } else if ("settings-keyboard".equals(action)) {
            openSettingsPanel(Settings.ACTION_INPUT_METHOD_SETTINGS, "키보드");
        } else if (action.startsWith("memo-")) {
            memos.action(action);
        } else if ("photo-standby".equals(action) || "photo-split".equals(action)) {
            choosePhoto("photo-standby".equals(action) ? STANDBY_PHOTO : SPLIT_PHOTO);
        } else if ("photo-profile0".equals(action) || "photo-profile1".equals(action)) {
            choosePhoto("photo-profile0".equals(action) ? DRIVER_ONE_PHOTO : DRIVER_TWO_PHOTO);
        } else if ("configure".equals(action)) {
            new AlertDialog.Builder(this).setTitle("바로가기 설정")
                .setItems(LABELS, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int index) {
                        chooseApp(index, false);
                    }
                })
                .setNegativeButton("닫기", null).show();
        } else {
            for (int i = 0; i < SHORTCUTS.length; i++) {
                if (!SHORTCUTS[i].equals(action)) continue;
                if (!openSaved(action) && !openDefaultShortcut(action, LABELS[i])) chooseApp(i, true);
                return;
            }
        }
    }

    private boolean openSaved(String action) {
        ComponentName saved = ComponentName.unflattenFromString(shortcuts.getString(action, ""));
        return saved != null && open(saved);
    }

    private boolean openDefaultShortcut(String action, String label) {
        switch (action) {
            case "settings":
                return open(new Intent(Settings.ACTION_SETTINGS));
            case "settings-general":
                return openSettingsRoot();
            case "settings-car":
                Intent vehicle = getPackageManager().getLaunchIntentForPackage(VEHICLE_SETTINGS);
                if (vehicle != null && open(vehicle)) return true;
                showSettingsFallback(label);
                return true;
            case "settings-nav":
                new AlertDialog.Builder(this).setTitle(label)
                    .setMessage("순정 내비게이션을 열고 설정 메뉴를 선택하세요.")
                    .setPositiveButton("순정 내비 열기", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int index) { openNavigation(); }
                    })
                    .setNegativeButton("취소", null).show();
                return true;
            case "settings-sound":
                return openSettingsPanel(Settings.ACTION_SOUND_SETTINGS, label);
            case "settings-devices":
                return openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS, label);
            case "projection":
                Intent projection = getPackageManager().getLaunchIntentForPackage("com.daudio.app.dipo");
                return projection != null && open(projection);
            case "settings-display":
                return openSettingsPanel(Settings.ACTION_DISPLAY_SETTINGS, label);
            case "settings-profile":
            case "settings-voice":
                showSettingsFallback(label);
                return true;
            case "source-fm":
            case "source-am":
                return openSaved("radio");
            case "source-bt":
            case "source-usb":
            case "source-nature":
                return openSaved("media");
            default:
                return false;
        }
    }

    private boolean openSettingsRoot() {
        return openSaved("settings") || open(new Intent(Settings.ACTION_SETTINGS));
    }

    private boolean openSettingsPanel(String action, String label) {
        if (!open(new Intent(action))) showSettingsFallback(label);
        return true;
    }

    private void showSettingsFallback(String label) {
        new AlertDialog.Builder(this).setTitle(label)
            .setMessage("순정 설정을 열고 해당 항목을 선택하세요.")
            .setPositiveButton("순정 설정 열기", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int index) {
                    if (!openSettingsRoot()) toast("바로가기 설정에서 순정 설정 앱을 연결하세요.");
                }
            })
            .setNegativeButton("취소", null).show();
    }

    private void openNavigation() {
        if (!open(NAVIGATION)) toast("이 기기에서 순정 내비게이션을 열 수 없습니다.");
    }

    private void sendMediaKey(int key) {
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audio == null) {
            toast("미디어 제어를 사용할 수 없습니다.");
            return;
        }
        long time = SystemClock.uptimeMillis();
        try {
            audio.dispatchMediaKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, key, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, key, 0));
        } catch (SecurityException ignored) {
            toast("이 기기에서 미디어 키를 전달할 수 없습니다.");
        }
    }

    private boolean open(ComponentName component) {
        return open(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private boolean open(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    private void chooseApp(final int index, final boolean launch) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> entries = new ArrayList<>(pm.queryIntentActivities(
            new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        Collections.sort(entries, new ResolveInfo.DisplayNameComparator(pm));
        final List<ComponentName> components = new ArrayList<>();
        List<CharSequence> names = new ArrayList<>();
        int selected = -1;
        String saved = shortcuts.getString(SHORTCUTS[index], "");
        for (ResolveInfo entry : entries) {
            if (entry.activityInfo == null || !entry.activityInfo.exported
                || getPackageName().equals(entry.activityInfo.packageName)) continue;
            ComponentName component = new ComponentName(entry.activityInfo.packageName, entry.activityInfo.name);
            if (components.contains(component)) continue;
            if (saved.equals(component.flattenToString())) selected = components.size();
            components.add(component);
            names.add(entry.loadLabel(pm));
        }
        if (components.isEmpty()) {
            toast("이 기기에 선택할 수 있는 앱이 없습니다.");
            return;
        }
        new AlertDialog.Builder(this).setTitle(LABELS[index] + " 앱 선택")
            .setSingleChoiceItems(names.toArray(new CharSequence[0]), selected, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int choice) {
                    ComponentName component = components.get(choice);
                    shortcuts.edit().putString(SHORTCUTS[index], component.flattenToString()).apply();
                    dialog.dismiss();
                    if (launch && !open(component)) toast("선택한 앱을 열 수 없습니다.");
                }
            })
            .setNeutralButton("연결 해제", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int choice) {
                    shortcuts.edit().remove(SHORTCUTS[index]).apply();
                }
            })
            .setNegativeButton("취소", null).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public void onBackPressed() {
        if (pageReady) web.evaluateJavascript("window.K8Home.back()", null);
        else finish();
    }

    @Override protected void onDestroy() {
        mapSlot.removeAllViews();
        root.removeView(web);
        web.removeJavascriptInterface("K8Host");
        web.destroy();
        super.onDestroy();
    }
}
