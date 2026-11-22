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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HomeActivity extends Activity {
    private static final String PAGE = "file:///android_asset/index.html";
    static final String EXTRA_OPEN_APPS = "io.github.k8home.extra.OPEN_APPS";
    static final String EXTRA_OPEN_MEDIA = "io.github.k8home.extra.OPEN_MEDIA";
    private static final String STOCK_PHONE = "com.hkmc.bluetooth";
    private static final int STANDBY_PHOTO = 21;
    private static final int SPLIT_PHOTO = 22;
    private static final int DRIVER_ONE_PHOTO = 23;
    private static final int DRIVER_TWO_PHOTO = 24;
    private static final String[] SHORTCUTS = {"media", "phone", "radio", "projection", "settings",
        "settings-car", "settings-general", "settings-nav", "settings-sound",
        "settings-devices", "settings-profile", "settings-voice", "settings-display",
        "voicememo", "connect", "alerts", "source-bt", "source-usb", "source-fm", "source-am", "source-nature",
        "nemesis", "nemesis-mydrive", "nemesis-youtube"};
    private static final String[] LABELS = {"미디어", "전화", "라디오", "폰 프로젝션", "순정 설정",
        "차량", "일반", "내비게이션", "사운드", "기기 연결", "사용자 프로필", "음성인식", "디스플레이",
        "음성 메모", "Kia Connect", "알림", "블루투스 오디오", "USB 오디오", "FM 라디오", "AM 라디오", "자연의 소리",
        "네메시스", "K8 마이드라이브", "유튜브"};
    private FrameLayout root;
    private FrameLayout mapSlot;
    private StockMapPreview stockMapPreview;
    private FrameLayout splitSlot;
    private StockMapPreview splitMapPreview;
    private boolean splitFrameReady;
    private boolean activityResumed;
    private boolean mapFrameReady;
    private WebView web;
    private SharedPreferences shortcuts;
    private boolean pageReady;
    private StockMedia media = StockMedia.EMPTY;
    private BluetoothCoverArt coverArt;
    private String coverImage = "";
    private int brightness = -1;
    private VoiceMemos memos;
    private int unmutedVolume = -1;
    private boolean pendingHome;
    private boolean pendingApps, pendingMedia;
    private StockRoute route;
    private StockPhone phone;
    private StockTransport transport;
    private StockClimate climate;
    private StockHybrid hybrid;
    private StockWeather weather;
    private StockVehicle vehicle;
    private JSONObject vehicleSettings = new JSONObject();
    private boolean readingVehicleSettings;

    private final StockRoute.Listener routeListener = new StockRoute.Listener() {
        @Override public void routeChanged(boolean guidanceStarted) {
            syncRoute();
        }
    };
    private final BroadcastReceiver mediaReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                    || BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)
                    || StockPhone.isConnectionAction(action)) {
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)
                        && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF) {
                    phone.clearBluetoothCall();
                }
                syncControls();
                return;
            }
            if (phone.updateCall(action, intent.getExtras())) {
                syncPhone();
                return;
            }
            StockMedia next = media.update(action, intent.getExtras());
            if (next == media) return;
            if (!media.source.equals(next.source)) transport.close();
            media = next;
            coverArt.setTrack("bt".equals(media.source) ? media.title + "\n" + media.artist : "");
            syncMedia();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        transport = new StockTransport(this);
        vehicle = new StockVehicle(this, new Runnable() {
            @Override public void run() { syncVehicle(); }
        });
        weather = new StockWeather(this, new Runnable() {
            @Override public void run() { syncWeather(); }
        });
        hybrid = new StockHybrid(this, new Runnable() {
            @Override public void run() { syncHybrid(); }
        });
        climate = new StockClimate(this, new Runnable() {
            @Override public void run() { syncClimate(); }
        });
        phone = new StockPhone(this, new Runnable() {
            @Override public void run() { syncPhone(); }
        });
        pendingApps = getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_APPS, false);
        pendingMedia = getIntent() != null && getIntent().getBooleanExtra(EXTRA_OPEN_MEDIA, false);
        pendingHome = pendingApps || pendingMedia;
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
        coverArt = new BluetoothCoverArt(new BluetoothCoverArt.Listener() {
            @Override public void changed(String image) {
                coverImage = image;
                syncCover();
            }
        });
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
        // KitKat WebView: a 1 s press starts the native long-press (selection) and cancels the page's touch,
        // so holding a card to move it never finished. The page handles long presses itself.
        web.setLongClickable(false);
        web.setHapticFeedbackEnabled(false);
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View view) {
                return true;
            }
        });
        web.addJavascriptInterface(new HomeBridge(), "K8Host");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (PAGE.equals(url)) {
                    pageReady = true;
                    syncMapContent();
                    syncRoute();
                    syncMedia();
                    syncCover();
                    syncControls();
                    syncSystemSettings();
                    syncClimate();
                    syncHybrid();
                    syncWeather();
                    syncVehicle();
                    syncFlags();
                    syncPhoto("standby");
                    syncPhoto("split");
                    syncPhoto("profile0");
                    syncPhoto("profile1");
                    openPendingHome();
                }
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        mapSlot = new FrameLayout(this);
        mapSlot.setVisibility(View.GONE);
        root.addView(mapSlot, new FrameLayout.LayoutParams(0, 0));
        splitSlot = new FrameLayout(this);
        splitSlot.setVisibility(View.GONE);
        root.addView(splitSlot, new FrameLayout.LayoutParams(0, 0));
        setContentView(root);
        stockMapPreview = new StockMapPreview(this, new StockMapPreview.Listener() {
            @Override public void frameChanged(boolean ready) {
                mapFrameReady = ready;
                syncMapFrame();
            }
        });
        setMapContent(stockMapPreview);
        splitMapPreview = new StockMapPreview(this, new StockMapPreview.Listener() {
            @Override public void frameChanged(boolean ready) {
                splitFrameReady = ready;
                if (pageReady) web.evaluateJavascript("window.K8Home.splitMapFrameChanged(" + ready + ")", null);
            }
        }, true);
        splitSlot.addView(splitMapPreview, new FrameLayout.LayoutParams(-1, -1));
        splitSlot.setOnTouchListener(new SplitGestures());
        web.loadUrl(PAGE);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        StockMedia.addActions(filter);
        StockPhone.addActions(filter);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mediaReceiver, filter);
        route = StockRoute.of(this);
        route.start(routeListener);
        phone.start();
        climate.start();
        hybrid.start();
        weather.start();
        vehicle.start();
        sendBroadcast(new Intent(StockMedia.REQUEST));
        syncControls();
        stopService(new Intent(this, NavBarService.class));
        CornerService.sync(this);
    }

    private void openPendingHome() {
        if (!pageReady || !pendingHome) return;
        pendingHome = false;
        String script = pendingApps ? "window.K8Home.openHome(); window.K8Home.showPage('apps1')"
            : pendingMedia ? "window.K8Home.openHome(); window.K8Home.showPage('media')"
            : "window.K8Home.openHome()";
        pendingApps = false;
        pendingMedia = false;
        web.evaluateJavascript(script, null);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingHome = true;
        pendingApps = intent != null && intent.getBooleanExtra(EXTRA_OPEN_APPS, false);
        pendingMedia = intent != null && intent.getBooleanExtra(EXTRA_OPEN_MEDIA, false);
        openPendingHome();
    }

    @Override protected void onPause() {
        activityResumed = false;
        stockMapPreview.setActive(false);
        splitMapPreview.setActive(false);
        memos.stop();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        syncSystemSettings();
        refreshVehicleSettings();
        stockMapPreview.setActive(hasWindowFocus() && mapSlot.getVisibility() == View.VISIBLE);
        splitMapPreview.setActive(hasWindowFocus() && splitSlot.getVisibility() == View.VISIBLE);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("memo-export-id", memos.exportId());
    }

    @Override protected void onStop() {
        unregisterReceiver(mediaReceiver);
        route.stop(routeListener);
        phone.stop();
        climate.stop();
        hybrid.stop();
        weather.stop();
        vehicle.stop();
        transport.close();
        syncPhone();
        media = StockMedia.EMPTY;
        coverArt.setTrack("");
        syncMedia();
        super.onStop();
    }

    private static String javascriptString(String value) {
        return JSONObject.quote(value).replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    private void syncMedia() {
        if (!pageReady) return;
        web.evaluateJavascript("window.K8Home.mediaChanged(" + javascriptString(media.title) + ","
            + javascriptString(media.artist) + "," + media.playing + "," + media.position + "," + media.duration
            + "," + javascriptString(media.source) + "," + javascriptString(media.settings) + ")", null);
    }

    private void syncRoute() {
        if (!pageReady || route == null) return;
        StockRoute.Snapshot data = route.snapshot();
        web.evaluateJavascript("window.K8Home.routeChanged(" + data.known + "," + data.active + ","
            + javascriptString(data.destination) + "," + javascriptString(data.address) + ","
            + javascriptString(data.distance) + "," + javascriptString(data.remaining) + ","
            + javascriptString(data.arrival) + "," + javascriptString(data.road) + ","
            + javascriptString(naviStatus(data)) + ")", null);
    }

    private void syncVehicle() {
        if (vehicle == null) return;
        // Before any media widget broadcast arrives, start from the stock current audio mode.
        if (media.source.isEmpty()) {
            int mode = vehicle.snapshot().optInt("audioMode", -1);
            if (mode > 0) {
                android.os.Bundle extras = new android.os.Bundle();
                extras.putInt("com.hkmc.mode.EXTRA_MODE_TYPE", mode);
                StockMedia next = media.update("com.hkmc.intent.action.MODE_CHANGED", extras);
                if (next != media) {
                    media = next;
                    syncMedia();
                }
            }
        }
        if (!pageReady) return;
        web.evaluateJavascript("window.K8Home.vehicleChanged && window.K8Home.vehicleChanged("
            + javascriptString(vehicle.snapshot().toString()) + ")", null);
    }

    private void syncWeather() {
        if (!pageReady || weather == null) return;
        web.evaluateJavascript("window.K8Home.weatherChanged && window.K8Home.weatherChanged("
            + javascriptString(weather.snapshot().toString()) + ")", null);
    }

    private void syncHybrid() {
        if (!pageReady || hybrid == null) return;
        web.evaluateJavascript("window.K8Home.hybridChanged && window.K8Home.hybridChanged("
            + javascriptString(hybrid.snapshot().toString()) + ")", null);
    }

    private void syncClimate() {
        if (!pageReady || climate == null) return;
        web.evaluateJavascript("window.K8Home.climateChanged("
            + javascriptString(climate.snapshot().toString()) + ")", null);
    }

    private static String naviStatus(StockRoute.Snapshot data) {
        JSONObject status = new JSONObject();
        try {
            if (!data.dayNight.isEmpty()) status.put("dayNight", data.dayNight);
            if (!data.mute.isEmpty()) status.put("mute", data.mute);
            if (!data.mapVersion.isEmpty()) status.put("mapVersion", data.mapVersion);
            if (!data.appVersion.isEmpty()) status.put("appVersion", data.appVersion);
        } catch (JSONException ignored) { }
        return status.toString();
    }

    private void syncCover() {
        if (pageReady) web.evaluateJavascript("window.K8Home.coverArtChanged("
            + javascriptString(coverImage) + ")", null);
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
                        String name = StockMedia.text(device.getName());
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
        syncPhone();
    }

    private void syncPhone() {
        if (!pageReady) return;
        web.evaluateJavascript("window.K8Home.phoneChanged(" + javascriptString(phone.callDevices().toString())
            + "," + javascriptString(phone.audioDevices().toString()) + "," + javascriptString(phone.callState())
            + "," + javascriptString(phone.caller()) + "," + javascriptString(phone.callSource()) + ")", null);
    }

    private void syncSystemSettings() {
        if (!pageReady) return;
        web.evaluateJavascript("window.K8Home.systemSettingsChanged("
            + javascriptString(HeadUnitInfo.androidSettings(this).toString()) + ","
            + javascriptString(vehicleSettings.toString()) + ")", null);
    }

    private void refreshVehicleSettings() {
        if (readingVehicleSettings) return;
        readingVehicleSettings = true;
        new Thread(new Runnable() {
            @Override public void run() {
                final JSONObject settings = HeadUnitInfo.savedVehicleSettings(HomeActivity.this);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        readingVehicleSettings = false;
                        if (isDestroyed()) return;
                        vehicleSettings = settings;
                        syncSystemSettings();
                    }
                });
            }
        }, "K8HomeVehicleSettings").start();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (stockMapPreview != null) stockMapPreview.setActive(hasFocus && activityResumed
            && mapSlot.getVisibility() == View.VISIBLE);
        if (splitMapPreview != null) splitMapPreview.setActive(hasFocus && activityResumed
            && splitSlot.getVisibility() == View.VISIBLE);
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

    /** Sends the two switches of the general settings screen to the page. */
    private void syncFlags() {
        if (!pageReady) return;
        web.evaluateJavascript("window.K8Home.systemFlagsChanged("
            + ThemePrefs.autoStart(this) + "," + ThemePrefs.navBar(this) + "," + ThemePrefs.corners(this) + ","
            + ThemePrefs.naviSkin(this) + ")", null);
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
        if (pageReady) {
            web.evaluateJavascript("window.K8Home.mapAttached(" + (mapSlot.getChildCount() != 0) + ")", null);
            syncMapFrame();
        }
    }

    private void syncMapFrame() {
        if (pageReady) web.evaluateJavascript("window.K8Home.mapFrameChanged(" + mapFrameReady + ")", null);
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
                    if (isFinishing() || isDestroyed()) return;
                    Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
                    if (!open(new Intent(dial).setPackage(STOCK_PHONE)) && !open(dial) && !open(StockApps.PHONE)) {
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

        /** Bounds of the split widget's map page, or zeros when it is not on screen. */
        @JavascriptInterface public void updateSplitBounds(final int viewport, final int left, final int top,
                                                            final int width, final int height) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed()) return;
                    float scale = viewport > 0 ? web.getWidth() / (float) viewport : 0;
                    int x = Math.round(left * scale), y = Math.round(top * scale);
                    int w = Math.min(Math.round(width * scale), web.getWidth() - x);
                    int h = Math.min(Math.round(height * scale), web.getHeight() - y);
                    if (scale <= 0 || left < 0 || top < 0 || w <= 0 || h <= 0) {
                        splitMapPreview.setActive(false);
                        splitSlot.setVisibility(View.GONE);
                        return;
                    }
                    FrameLayout.LayoutParams bounds = new FrameLayout.LayoutParams(w, h);
                    bounds.leftMargin = x;
                    bounds.topMargin = y;
                    splitSlot.setLayoutParams(bounds);
                    splitSlot.setVisibility(View.VISIBLE);
                    splitMapPreview.setActive(activityResumed && hasWindowFocus());
                }
            });
        }

        @JavascriptInterface public void updateBounds(final int viewport, final int left, final int top,
                                                       final int width, final int height) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed() || mapSlot.getChildCount() == 0) return;
                    if (viewport <= 0 || width <= 0 || height <= 0 || left < 0 || top < 0) {
                        stockMapPreview.setActive(false);
                        mapSlot.setVisibility(View.GONE);
                        return;
                    }
                    float scale = web.getWidth() / (float) viewport;
                    int x = Math.round(left * scale);
                    int y = Math.round(top * scale);
                    int w = Math.min(Math.round(width * scale), web.getWidth() - x);
                    int h = Math.min(Math.round(height * scale), web.getHeight() - y);
                    if (w <= 0 || h <= 0) {
                        stockMapPreview.setActive(false);
                        mapSlot.setVisibility(View.GONE);
                        return;
                    }
                    FrameLayout.LayoutParams bounds = new FrameLayout.LayoutParams(w, h);
                    bounds.leftMargin = x;
                    bounds.topMargin = y;
                    mapSlot.setLayoutParams(bounds);
                    mapSlot.setVisibility(View.VISIBLE);
                    stockMapPreview.setActive(activityResumed && hasWindowFocus());
                }
            });
        }
    }

    /**
     * The native split map sits above the page, so it reads the widget gestures itself: a tap opens the
     * stock navigation, a swipe right closes the split screen, a vertical swipe changes the widget.
     */
    private final class SplitGestures implements View.OnTouchListener {
        private float startX, startY;

        @Override public boolean onTouch(View view, android.view.MotionEvent event) {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                startX = event.getRawX();
                startY = event.getRawY();
            } else if (action == android.view.MotionEvent.ACTION_UP) {
                float dx = event.getRawX() - startX, dy = event.getRawY() - startY;
                float slop = 60 * getResources().getDisplayMetrics().density;
                String gesture = Math.abs(dx) < slop && Math.abs(dy) < slop ? "tap"
                    : Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? "right" : "left") : (dy < 0 ? "up" : "down");
                if ("tap".equals(gesture)) handleAction("navi");
                else if (pageReady) web.evaluateJavascript("window.K8Home.splitGesture('" + gesture + "')", null);
            }
            return true;
        }
    }

    private void handleAction(String action) {
        if (action == null) return;
        if ("exit".equals(action)) {
            finish();
        } else if ("navi".equals(action)) {
            openNavigation(true);
        } else if ("nav-search".equals(action)) {
            openNavigationCommand(StockNavigation.SEARCH);
        } else if (action.startsWith("pkg:") && action.length() > 4) {
            // Stock app tiles in the all-apps screen; only installed packages with a launcher entry open.
            if (!openPackage(action.substring(4))) toast("이 차에서 해당 순정 앱을 열 수 없습니다.");
        } else if ("nav-petrol".equals(action) || "nav-parking".equals(action)) {
            // Nearby list from the navi; if it does not come up, fall back to the navi search screen.
            final String nearby = "nav-petrol".equals(action) ? StockNavigation.NEARBY_PETROL : StockNavigation.NEARBY_PARKING;
            StockNavigation.request(this, nearby);
            web.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!StockNavigation.MAP.equals(StockNavigation.foreground(HomeActivity.this))) {
                        openNavigationCommand(StockNavigation.SEARCH);
                    }
                }
            }, 1500);
        } else if ("nav-current".equals(action)) {
            if (openNavigation(true)) StockNavigation.requestWhenMapOpens(this, StockNavigation.CURRENT_POSITION);
        } else if ("phone-history".equals(action) || "phone-contacts".equals(action)) {
            String phoneAction = "phone-history".equals(action) ? "android.bluetooth.intent.action.RECENT_CALLS"
                : "com.android.contacts.action.LIST_CONTACTS_WITH_PHONES";
            if (!open(new Intent(phoneAction).setPackage(STOCK_PHONE)) && !open(StockApps.PHONE)) {
                toast("이 기기에서 순정 전화 화면을 열 수 없습니다.");
            }
        } else if ("stock-memos".equals(action)) {
            if (!open(new ComponentName("com.daudio.app.voicememo", "com.daudio.app.voicememo.MainActivity"))) {
                toast("순정 음성 메모를 열 수 없습니다.");
            }
        } else if ("media-player".equals(action)) {
            if (!StockApps.openPlayer(this, media.source)) toast("순정 플레이어를 열 수 없습니다.");
        } else if ("nav-home".equals(action) || "nav-work".equals(action)) {
            savedPlace("nav-home".equals(action) ? "home" : "work");
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
        } else if ("auto-start-toggle".equals(action)) {
            boolean on = !ThemePrefs.autoStart(this);
            ThemePrefs.setAutoStart(this, on);
            syncFlags();
            toast(on ? "시동 후 자동 실행을 켰습니다." : "시동 후 자동 실행을 껐습니다.");
        } else if ("nav-bar-toggle".equals(action)) {
            boolean on = !ThemePrefs.navBar(this);
            ThemePrefs.setNavBar(this, on);
            if (!on) stopService(new Intent(this, NavBarService.class));
            syncFlags();
            toast(on ? "테마에서 지도를 열면 하단바 시험이 시작됩니다." : "순정 하단줄을 사용합니다.");
        } else if ("navi-skin-toggle".equals(action)) {
            boolean on = !ThemePrefs.naviSkin(this);
            ThemePrefs.setNaviSkin(this, on);
            syncFlags();
            toast(on ? "하단바 시험 중 순정 내비 왼쪽 버튼을 ccNC 모양으로 덮습니다." : "순정 내비 왼쪽 버튼을 그대로 둡니다.");
        } else if ("corners-toggle".equals(action)) {
            boolean on = !ThemePrefs.corners(this);
            ThemePrefs.setCorners(this, on);
            CornerService.sync(this);
            syncFlags();
            toast(on ? "순정 화면에도 하단 버튼을 띄웁니다." : "순정 화면의 하단 버튼을 껐습니다.");
        } else if ("home-settings".equals(action)) {
            if (!open(new Intent("android.settings.HOME_SETTINGS"))) {
                toast("이 기기에서 홈 화면 설정을 열 수 없습니다.");
            }
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
            case "media":
                return open(StockApps.MEDIA);
            case "phone":
                return open(StockApps.PHONE);
            case "radio":
                return open(StockApps.RADIO);
            case "settings":
                return open(StockApps.SETTINGS) || open(new Intent(Settings.ACTION_SETTINGS));
            case "settings-general":
                return openSettingsRoot();
            case "settings-car":
                if (open(StockApps.VEHICLE) || openPackage("com.hkmc.software.vehiclesetting")) return true;
                showSettingsFallback(label);
                return true;
            case "settings-nav":
                openNavigationCommand(StockNavigation.SETTINGS);
                return true;
            case "settings-sound":
                return openSettingsPanel(Settings.ACTION_SOUND_SETTINGS, label);
            case "settings-devices":
                return open(StockApps.PROJECTION) || openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS, label);
            case "projection":
                return open(StockApps.PROJECTION) || openPackage("com.daudio.app.dipo");
            case "settings-display":
                return openSettingsPanel(Settings.ACTION_DISPLAY_SETTINGS, label);
            case "settings-profile":
                if (open(StockApps.PROFILE)) return true;
                showSettingsFallback(label);
                return true;
            case "settings-voice":
                showSettingsFallback(label);
                return true;
            case "source-fm":
            case "source-am":
                return openSaved("radio") || StockApps.openRadioBand(this, "source-fm".equals(action));
            case "source-bt":
                return StockApps.openPlayer(this, "bt");
            case "source-usb":
                return StockApps.openPlayer(this, "usb");
            case "source-nature":
                return open(StockApps.NATURE) || open(StockApps.MEDIA);
            case "nemesis":
                return open(new Intent(Intent.ACTION_MAIN)
                    .setClassName("com.nemesis.manager", "com.nemesis.manager.MainActivity"));
            case "nemesis-mydrive":
                return open(new ComponentName("io.github.k8ecs.hu", "io.github.k8ecs.hu.HuActivity"));
            case "nemesis-youtube":
                return open(new ComponentName("org.smarttube.stable",
                    "com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity"));
            default:
                return false;
        }
    }

    private boolean openSettingsRoot() {
        return openSaved("settings") || open(StockApps.SETTINGS) || open(new Intent(Settings.ACTION_SETTINGS));
    }

    private boolean openPackage(String packageName) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        return launch != null && open(launch);
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

    private boolean openNavigation(boolean withBar) {
        if (!open(StockNavigation.MAP)) {
            toast("이 기기에서 순정 내비게이션을 열 수 없습니다.");
            return false;
        }
        if (withBar && ThemePrefs.navBar(this)) {
            try {
                startService(new Intent(this, NavBarService.class).setAction(NavBarService.OPENED_MAP));
            } catch (RuntimeException e) {
                toast("하단바를 시작할 수 없습니다. 순정 하단줄을 사용합니다.");
            }
        }
        return true;
    }

    /**
     * Home / work buttons. The navi keeps its own saved points in a provider other apps cannot read,
     * so the theme saves a point from the current destination (DestinationInfo LAT/LON) when asked,
     * then starts guidance with the navi's route-search intent.
     */
    private void savedPlace(final String key) {
        final String label = "home".equals(key) ? "우리 집" : "회사";
        final String latitude = shortcuts.getString("place-" + key + "-lat", "");
        final String longitude = shortcuts.getString("place-" + key + "-lon", "");
        final String name = shortcuts.getString("place-" + key + "-name", label);
        StockRoute.Snapshot data = route == null ? null : route.snapshot();
        final String nowLat = data == null ? "" : StockNavigation.degrees(data.destLat);
        final String nowLon = data == null ? "" : StockNavigation.degrees(data.destLon);
        final String nowName = data == null ? "" : data.destination;
        boolean saved = !latitude.isEmpty() && !longitude.isEmpty();
        boolean canSave = !nowLat.isEmpty() && !nowLon.isEmpty();
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(label);
        if (saved) {
            dialog.setMessage(name + "(으)로 길안내를 시작합니다.")
                .setPositiveButton("안내 시작", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int index) {
                        if (StockNavigation.routeTo(HomeActivity.this, name, latitude, longitude)) openNavigation(false);
                        else toast("순정 내비가 경로 요청을 받지 않았습니다.");
                    }
                });
        } else {
            dialog.setMessage(canSave ? "지금 안내 중인 목적지(" + nowName + ")를 " + label + "(으)로 저장할 수 있습니다."
                : "아직 저장된 " + label + "이(가) 없습니다. " + label + "에 있을 때 '지금 위치 저장'을 누르거나, 순정 내비에서 "
                    + label + "으로 길안내를 시작한 뒤 다시 누르면 저장할 수 있습니다.")
                .setPositiveButton("순정 검색 열기", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int index) {
                        openNavigationCommand(StockNavigation.SEARCH);
                    }
                });
        }
        // Without a destination with coordinates, the current navi GPS position can be saved instead
        // (press the key while parked at home / work).
        final String hereLat = data == null ? "" : data.curLat;
        final String hereLon = data == null ? "" : data.curLon;
        if (!canSave && !hereLat.isEmpty() && !hereLon.isEmpty()) {
            dialog.setNeutralButton(saved ? "지금 위치로 바꾸기" : "지금 위치 저장", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int index) {
                    shortcuts.edit().putString("place-" + key + "-lat", hereLat).putString("place-" + key + "-lon", hereLon)
                        .putString("place-" + key + "-name", label).apply();
                    toast(label + " 위치를 지금 위치로 저장했습니다.");
                }
            });
        }
        if (canSave) {
            dialog.setNeutralButton(saved ? "현재 목적지로 바꾸기" : "현재 목적지 저장", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int index) {
                    shortcuts.edit().putString("place-" + key + "-lat", nowLat).putString("place-" + key + "-lon", nowLon)
                        .putString("place-" + key + "-name", nowName.isEmpty() ? label : nowName).apply();
                    toast(label + " 위치를 저장했습니다.");
                }
            });
        }
        dialog.setNegativeButton("취소", null).show();
    }

    private void openNavigationCommand(String action) {
        if (openNavigation(false)) {
            if (StockNavigation.SEARCH.equals(action) && ThemePrefs.navBar(this)) {
                try {
                    startService(new Intent(this, NavBarService.class).setAction(NavBarService.SEARCHING_MAP));
                } catch (RuntimeException ignored) {
                    toast("순정 내비 화면을 사용합니다.");
                }
            }
            StockNavigation.requestWhenMapOpens(this, action);
        }
    }

    private void sendMediaKey(int key) {
        if (!transport.send(media.source, key) && !MediaKeys.dispatch(this, key)) {
            toast("이 기기에서 미디어 키를 전달할 수 없습니다.");
        }
    }

    private boolean open(ComponentName component) {
        return StockApps.open(this, component);
    }

    private boolean open(Intent intent) {
        return StockApps.open(this, intent);
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
        pageReady = false;
        coverArt.close();
        stockMapPreview.setActive(false);
        splitMapPreview.setActive(false);
        mapSlot.removeAllViews();
        splitSlot.removeAllViews();
        root.removeView(web);
        web.removeJavascriptInterface("K8Host");
        web.destroy();
        super.onDestroy();
    }
}
