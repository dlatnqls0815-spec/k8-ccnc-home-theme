package io.github.k8home;

import android.animation.TimeInterpolator;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
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

public class HomeActivity extends Activity {
    private static final String PAGE = "file:///android_asset/index.html";
    static final String EXTRA_OPEN_APPS = "io.github.k8home.extra.OPEN_APPS";
    static final String EXTRA_OPEN_MEDIA = "io.github.k8home.extra.OPEN_MEDIA";
    /** Which source the media screen should open on ("bt", "usb", "fm", ...). */
    static final String EXTRA_OPEN_MEDIA_SOURCE = "io.github.k8home.extra.OPEN_MEDIA_SOURCE";
    private static final String STOCK_PHONE = "com.hkmc.bluetooth";
    private static final String TAG_SETTINGS = "K8Settings";
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
    private FrameLayout mapClip;
    private FrameLayout mapSlot;
    private StockMapPreview stockMapPreview;
    private int mapAbsX = -1, mapAbsY = -1, mapW = -1, mapH = -1;
    private int clipX = 0, clipY = 0, clipW = -1, clipH = -1;
    private int appliedRelX = Integer.MIN_VALUE, appliedRelY = Integer.MIN_VALUE, appliedW = -1, appliedH = -1;
    private float mapTranslationX = 0f;
    private boolean mapLiveVisible = true;
    private volatile boolean capturingMapSnap;
    private static final TimeInterpolator MAP_EASE_OUT = new TimeInterpolator() {
        @Override public float getInterpolation(float t) {
            float u = 1f - t;
            return 1f - u * u * u;
        }
    };
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
    private String pendingMediaSource = "";
    private String pendingPlaceKey = "";
    private String pendingPlacePrevCoord = "";
    private int pendingNatureIndex = -1;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable applyPendingNature = new Runnable() {
        @Override public void run() {
            if (isDestroyed() || pendingNatureIndex < 0) return;
            int idx = pendingNatureIndex;
            pendingNatureIndex = -1;
            if (transport.selectNature(idx)) {
                MediaKeys.cancelStockPopup(HomeActivity.this);
                StockRadio.requestMediaRefresh(HomeActivity.this);
            }
        }
    };
    private StockRoute route;
    private StockPhone phone;
    private StockTransport transport;
    private StockRadio radio;
    private StockPhoneBook phoneBook;
    private StockLyrics lyrics;
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
            // 곡이 바뀔 때 순정 곡 팝업 닫기는 항상 떠 있는 PopupGuardService 가 맡는다.
            media = next;
            coverArt.setTrack("bt".equals(media.source) ? media.title + "\n" + media.artist : "");
            if ("nature".equals(media.source) && pendingNatureIndex >= 0) {
                int idx = pendingNatureIndex;
                pendingNatureIndex = -1;
                uiHandler.removeCallbacks(applyPendingNature);
                if (transport.selectNature(idx)) {
                    MediaKeys.cancelStockPopup(HomeActivity.this);
                    StockRadio.requestMediaRefresh(HomeActivity.this);
                }
            }
            syncMedia();
        }
    };

    private boolean relaunching;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        android.content.ComponentName opened = getIntent().getComponent();
        if (opened != null && opened.getClassName().endsWith(".HomeActivity")) {
            // 예전 이름(별칭)으로 처음 열리면 순정 앱이 보는 앞 화면 이름도 별칭이 되어 팝업 막기가 안 된다: 진짜 이름으로 다시 연다.
            startActivity(new Intent(getIntent()).setClass(this, com.android.launcher3.view.Launcher.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));   // 별칭 기록을 지우고 진짜 이름으로 새로
            relaunching = true;
            return;
        }
        transport = new StockTransport(this);
        radio = new StockRadio(this);
        radio.preloadNatureList();
        phoneBook = new StockPhoneBook(this);
        lyrics = new StockLyrics(this);
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
        pendingMediaSource = getIntent() != null ? getIntent().getStringExtra(EXTRA_OPEN_MEDIA_SOURCE) : null;
        if (pendingMediaSource == null) pendingMediaSource = "";
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
        // 차 시스템 글꼴 크기가 WebView 글자 배율로 들어가 본체 글자만 작아지고 하단바(배율 100 고정)와 어긋났다.
        // 하단바·모서리 WebView 처럼 100 으로 고정한다(2026-11-22 실차 캡처 비교).
        settings.setTextZoom(100);
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
                    syncSavedPlaces();
                    syncPhoto("standby");
                    syncPhoto("split");
                    syncPhoto("profile0");
                    syncPhoto("profile1");
                    openPendingHome();
                }
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        mapClip = new FrameLayout(this);
        mapClip.setClipChildren(true);
        mapClip.setClipToPadding(true);
        root.addView(mapClip, new FrameLayout.LayoutParams(-1, -1));
        mapSlot = new FrameLayout(this);
        mapSlot.setVisibility(View.GONE);
        mapClip.addView(mapSlot, new FrameLayout.LayoutParams(0, 0));
        splitSlot = new FrameLayout(this);
        splitSlot.setVisibility(View.GONE);
        root.addView(splitSlot, new FrameLayout.LayoutParams(0, 0));
        setContentView(root);
        stockMapPreview = new StockMapPreview(this, new StockMapPreview.Listener() {
            @Override public void frameChanged(boolean ready) {
                mapFrameReady = ready;
                syncMapFrame();
                if (ready) {
                    web.postDelayed(new Runnable() {
                        @Override public void run() { pushMapSnapshot(); }
                    }, 350);
                }
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
        PopupGuardService.start(this); // 테마가 닫혀 있어도 곡이 바뀌면 순정 곡 팝업을 닫는다
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
        String script;
        if (pendingApps) {
            script = "window.K8Home.openHome(); window.K8Home.showPage('apps1')";
        } else if (pendingMedia) {
            script = "window.K8Home.setMediaSource(" + javascriptString(pendingMediaSource)
                + "); window.K8Home.openHome(); window.K8Home.showPage('media')";
        } else {
            script = "window.K8Home.openHome()";
        }
        pendingApps = false;
        pendingMedia = false;
        pendingMediaSource = "";
        web.evaluateJavascript(script, null);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingHome = true;
        pendingApps = intent != null && intent.getBooleanExtra(EXTRA_OPEN_APPS, false);
        pendingMedia = intent != null && intent.getBooleanExtra(EXTRA_OPEN_MEDIA, false);
        pendingMediaSource = intent != null ? intent.getStringExtra(EXTRA_OPEN_MEDIA_SOURCE) : null;
        if (pendingMediaSource == null) pendingMediaSource = "";
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
        // 순정 라디오·자연의 소리는 "앞 화면이 순정 홈(com.android.launcher3.view.Launcher)이고 홈이 미디어 위젯 페이지(0)"면
        // 오른쪽 팝업을 띄우지 않는다(위젯이 이미 보여 준다, RadioUtils.isDisabledAVWidget). 테마가 앞에 올 때 홈 0쪽이라고 알린다.
        sendBroadcast(MediaKeys.homePageZero());
        if (media.source.isEmpty()) syncVehicle();   // 테마가 다시 켜진 뒤 소스를 모르면(자연의 소리는 바뀔 때만 알림) 순정 모드를 읽는다
        checkPendingSavedPlace();
        syncSavedPlaces();
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
            if (mode <= 0) mode = StockRadio.currentMode(this);
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
            + ThemePrefs.naviSkin(this) + "," + ThemePrefs.stockWrite(this) + "," + ThemePrefs.soundWrite(this) + ","
            + ThemePrefs.customKeyHome(this) + ")", null);
    }

    private interface SoundWrite { StockSound.Result run(); }

    /** 사운드 쓰기는 한 번에 하나만, UI 스레드 밖에서. 끝나면 결과를 화면에 알리고 값을 다시 읽는다. */
    private volatile boolean writingSound;
    private void writeSound(final SoundWrite write) {
        if (!ThemePrefs.soundWrite(this)) {
            // JavaBridge 스레드에서 불리므로 WebView 는 UI 스레드에서 건드린다(실차 오류 2026-11-23).
            runOnUiThread(new Runnable() {
                @Override public void run() { reportSoundResult(false, "사운드 직접 변경 스위치가 꺼져 있습니다."); }
            });
            return;
        }
        if (writingSound) return;
        writingSound = true;
        new Thread(new Runnable() {
            @Override public void run() {
                final StockSound.Result result = write.run();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        writingSound = false;
                        if (isDestroyed()) return;
                        reportSoundResult(result.ok, result.message);
                        refreshVehicleSettings();
                    }
                });
            }
        }, "K8HomeSoundWrite").start();
    }

    private void reportSoundResult(boolean ok, String message) {
        if (!pageReady) return;
        web.evaluateJavascript("window.K8Home.soundWriteResult(" + ok + "," + javascriptString(message) + ")", null);
    }

    /** Tells the page whether a stock setting write worked, so it can show the reason. */
    private void reportSettingResult(String key, boolean ok, String message) {
        if (!pageReady) return;
        web.evaluateJavascript("window.K8Home.stockSettingResult(" + javascriptString(key) + ","
            + ok + "," + javascriptString(message) + ")", null);
    }

    /**
     * Writes one stock (5W) setting value. The theme's test switch has to be on, and the head unit
     * usually refuses the write because a normal app has no WRITE_SETTINGS. Both the reason and the
     * key go to the log so the car test can tell "not allowed" from "wrong key".
     */
    private void writeStockSetting(String key, String value) {
        if (!ThemePrefs.stockWrite(this)) {
            reportSettingResult(key, false, "순정 설정 직접 변경 스위치가 꺼져 있습니다.");
            return;
        }
        // 화면에 버튼이 있는 키와 값만 받는다. 페이지가 아무 키나 쓰게 두지 않는다.
        boolean allowed = ("auto_time".equals(key) && ("0".equals(value) || "1".equals(value)))
            || ("time_12_24".equals(key) && ("12".equals(value) || "24".equals(value)));
        if (!allowed) {
            Log.w(TAG_SETTINGS, "refused " + key + "=" + value);
            reportSettingResult(key, false, "허용 목록에 없는 설정입니다.");
            return;
        }
        ContentResolver resolver = getContentResolver();
        Integer number;
        try {
            number = Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            number = null;
        }
        SecurityException denied = null;
        for (String space : new String[] {"system", "global", "secure"}) {
            try {
                if ("screen_brightness".equals(key)) {
                    if (!"system".equals(space) || number == null) continue;
                    Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, number);
                } else if ("auto_time".equals(key)) {
                    if (!"system".equals(space) || number == null) continue;
                    Settings.System.putInt(resolver, Settings.System.AUTO_TIME, number);
                } else if ("time_12_24".equals(key)) {
                    if (!"system".equals(space)) continue;
                    Settings.System.putString(resolver, Settings.System.TIME_12_24, value);
                } else {
                    continue;
                }
                Log.i(TAG_SETTINGS, "wrote " + space + " " + key + "=" + value);
                reportSettingResult(key, true, space + " 저장에 성공했습니다.");
                return;
            } catch (SecurityException failure) {
                denied = failure;
            } catch (IllegalArgumentException failure) {
                Log.w(TAG_SETTINGS, "bad value for " + key, failure);
                reportSettingResult(key, false, "이 키는 이 방식으로 저장할 수 없습니다.");
                return;
            }
        }
        Log.w(TAG_SETTINGS, "denied writing " + key + " (WRITE_SETTINGS 없음)", denied);
        reportSettingResult(key, false, "차량이 이 앱의 쓰기를 막았습니다(WRITE_SETTINGS 없음).");
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

    private void pushMapSnapshot() {
        if (isFinishing() || isDestroyed() || !pageReady || capturingMapSnap || stockMapPreview == null) return;
        final Bitmap snap = stockMapPreview.captureSnapshot(320);
        if (snap == null) return;
        capturingMapSnap = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                    snap.compress(Bitmap.CompressFormat.JPEG, 75, jpeg);
                    snap.recycle();
                    final String dataUrl = "data:image/jpeg;base64,"
                        + Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (!isFinishing() && !isDestroyed() && pageReady) {
                                web.evaluateJavascript(
                                    "window.K8Home.mapSnapshotReady && window.K8Home.mapSnapshotReady("
                                        + javascriptString(dataUrl) + ")", null);
                            }
                        }
                    });
                } catch (RuntimeException ignored) {
                    if (!snap.isRecycled()) snap.recycle();
                } finally {
                    capturingMapSnap = false;
                }
            }
        }, "k8home-map-snap").start();
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
                    // 순정 블루투스 전화가 받는 바로 걸기(OutgoingCallBroadcaster, BLUETOOTH 권한). 안 되면 다이얼 화면.
                    Intent call = new Intent("android.bluetooth.intent.action.CALL", Uri.fromParts("tel", number, null))
                        .setClassName(STOCK_PHONE, "com.android.phone.OutgoingCallBroadcaster");
                    Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
                    if (!open(call) && !open(new Intent(dial).setPackage(STOCK_PHONE)) && !open(dial) && !open(StockApps.PHONE)) {
                        toast("이 기기에서 전화 걸기 화면을 열 수 없습니다.");
                    }
                }
            });
        }

        /** Lyrics for the playing track (StockLyrics); the answer arrives as K8Home.lyricsChanged(key, json). */
        @JavascriptInterface public void lyrics(String key, String title, String artist, int seconds) {
            if (key == null || title == null || title.trim().isEmpty()) return;
            lyrics.request(key, title.trim(), artist == null ? "" : artist.trim(), seconds, new StockLyrics.Callback() {
                @Override public void done(final String doneKey, final String json) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (pageReady && !isDestroyed()) web.evaluateJavascript("window.K8Home.lyricsChanged("
                                + javascriptString(doneKey) + "," + javascriptString(json) + ")", null);
                        }
                    });
                }
            });
        }

        /** Car phone book of the connected phone: "recent", "favorites" or "contacts" (StockPhoneBook, JSON). */
        @JavascriptInterface public String phoneBook(String list) {
            return phoneBook.list(String.valueOf(list));
        }

        /** Stock radio provider rows: "fm_stations", "am_stations" or "presets" (StockRadio). */
        @JavascriptInterface public String radioList(String path) {
            return radio.list(path);
        }

        /** Stock radio logo (data: URL) for a station name, "" when the radio app has none. */
        @JavascriptInterface public String radioLogo(String station) {
            synchronized (radio) { return radio.logo(station); }
        }

        /** Stock nature sounds [{name, art}] read from the car's nature sound app. */
        @JavascriptInterface public String natureList() {
            return radio.natureList();
        }

        /** 자연의 소리 특정 트랙(0~9)을 하나씩 넘기지 않고 바로 재생한다. */
        @JavascriptInterface public void selectNature(final int index) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed()) return;
                    if ("nature".equals(media.source)) {
                        pendingNatureIndex = -1;
                        uiHandler.removeCallbacks(applyPendingNature);
                        if (transport.selectNature(index)) {
                            MediaKeys.cancelStockPopup(HomeActivity.this);
                            StockRadio.requestMediaRefresh(HomeActivity.this);
                        }
                    } else {
                        pendingNatureIndex = index;
                        StockRadio.changeSource(HomeActivity.this, "nature");
                        uiHandler.removeCallbacks(applyPendingNature);
                        uiHandler.postDelayed(applyPendingNature, 280);
                    }
                }
            });
        }

        @JavascriptInterface public void radioTune(final boolean fm, final int kHz) {
            if (!radio.tune(fm, kHz)) runOnUiThread(new Runnable() {
                @Override public void run() { toast("이 주파수로 맞출 수 없습니다."); }
            });
        }

        @JavascriptInterface public void performAction(final String action) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (!isFinishing() && !isDestroyed()) handleAction(action);
                }
            });
        }

        /** Writes one stock (5W) setting value. Only used while the theme's test switch is on. */
        @JavascriptInterface public void setStockSetting(final String key, final String value) {
            if (key == null || value == null) return;
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed()) return;
                    writeStockSetting(key, value);
                }
            });
        }

        /** 고음/중음/저음(테마 단위 -10~10). "사운드 직접 변경" 스위치가 켜져 있을 때만 쓴다. */
        @JavascriptInterface public void setSoundTone(final int bass, final int middle, final int treble) {
            writeSound(new SoundWrite() {
                @Override public StockSound.Result run() { return StockSound.setTone(HomeActivity.this, bass, middle, treble); }
            });
        }

        /** 전후/좌우(테마 단위 -10~10). "사운드 직접 변경" 스위치가 켜져 있을 때만 쓴다. */
        @JavascriptInterface public void setSoundFadeBalance(final int fade, final int balance) {
            writeSound(new SoundWrite() {
                @Override public StockSound.Result run() { return StockSound.setFadeBalance(HomeActivity.this, fade, balance); }
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

        private void applyMapSlotLayout() {
            if (mapW <= 0 || mapH <= 0) return;
            int relX = mapAbsX - clipX;
            int relY = mapAbsY - clipY;
            if (relX == appliedRelX && relY == appliedRelY && mapW == appliedW && mapH == appliedH) return;
            FrameLayout.LayoutParams bounds = new FrameLayout.LayoutParams(mapW, mapH);
            bounds.leftMargin = relX;
            bounds.topMargin = relY;
            mapSlot.setLayoutParams(bounds);
            appliedRelX = relX; appliedRelY = relY; appliedW = mapW; appliedH = mapH;
        }

        private void applyMapLiveVisibility() {
            if (mapSlot.getVisibility() != View.VISIBLE) return;
            if (mapLiveVisible) {
                mapSlot.setTranslationX(mapTranslationX);
                mapSlot.setAlpha(1f);
            } else {
                mapSlot.setAlpha(0f);
                mapSlot.setTranslationX(-(web.getWidth() + 200f));
            }
        }

        /** 넘김 중 순정 지도 미리보기(Surface)는 끄지 않고 표시만 숨겨 웹뷰 카드 스냅샷과 100% 칼싱크로 넘긴다. */
        @JavascriptInterface public void setMapLiveVisible(final boolean visible) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed() || mapSlot.getChildCount() == 0) return;
                    mapSlot.animate().cancel();
                    mapLiveVisible = visible;
                    applyMapLiveVisibility();
                }
            });
        }

        /** 현재 순정 지도 미리보기 프레임을 JPEG 스냅샷으로 웹뷰(#home-map-snap)에 보낸다. */
        @JavascriptInterface public void captureMapSnapshot() {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    pushMapSnapshot();
                }
            });
        }

        /** deck 영역에 맞춰 네이티브 지도 클립 경계를 고정한다(넘김 중 카드 테두리와 동일하게 잘림). */
        @JavascriptInterface public void setMapClip(final int viewport, final int left, final int top,
                                                    final int width, final int height) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed() || mapClip == null || viewport <= 0 || width <= 0 || height <= 0) return;
                    float scale = web.getWidth() / (float) viewport;
                    int x = Math.max(0, Math.round(left * scale));
                    int y = Math.max(0, Math.round(top * scale));
                    int w = Math.min(Math.round(width * scale), web.getWidth() - x);
                    int h = Math.min(Math.round(height * scale), web.getHeight() - y);
                    if (w <= 0 || h <= 0) return;
                    if (x != clipX || y != clipY || w != clipW || h != clipH) {
                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
                        lp.leftMargin = x;
                        lp.topMargin = y;
                        mapClip.setLayoutParams(lp);
                        clipX = x; clipY = y; clipW = w; clipH = h;
                        applyMapSlotLayout();
                    }
                }
            });
        }

        @JavascriptInterface public void updateBounds(final int viewport, final int left, final int top,
                                                       final int width, final int height) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed() || mapSlot.getChildCount() == 0) return;
                    if (viewport <= 0 || width <= 0 || height <= 0 || left < 0 || top < 0) {
                        mapSlot.animate().cancel();
                        stockMapPreview.setActive(false);
                        mapTranslationX = 0f;
                        mapSlot.setTranslationX(0f);
                        mapSlot.setVisibility(View.GONE);
                        mapAbsX = mapAbsY = mapW = mapH = -1;
                        appliedRelX = appliedRelY = Integer.MIN_VALUE; appliedW = appliedH = -1;
                        return;
                    }
                    float scale = web.getWidth() / (float) viewport;
                    int x = Math.round(left * scale);
                    int y = Math.round(top * scale);
                    int w = Math.min(Math.round(width * scale), web.getWidth() - x);
                    int h = Math.min(Math.round(height * scale), web.getHeight() - y);
                    if (w <= 0 || h <= 0) {
                        mapSlot.animate().cancel();
                        stockMapPreview.setActive(false);
                        mapTranslationX = 0f;
                        mapSlot.setTranslationX(0f);
                        mapSlot.setVisibility(View.GONE);
                        mapAbsX = mapAbsY = mapW = mapH = -1;
                        appliedRelX = appliedRelY = Integer.MIN_VALUE; appliedW = appliedH = -1;
                        return;
                    }
                    mapAbsX = x; mapAbsY = y; mapW = w; mapH = h;
                    applyMapSlotLayout();
                    if (mapSlot.getVisibility() != View.VISIBLE) {
                        mapSlot.setVisibility(View.VISIBLE);
                    }
                    applyMapLiveVisibility();
                    stockMapPreview.setActive(activityResumed && hasWindowFocus());
                }
            });
        }

        /** 홈 화면 좌우 넘김 중 레이아웃·미리보기 재시작 없이 지도 슬롯 위치만 가로로 옮긴다. */
        @JavascriptInterface public void setMapOffset(final int viewport, final float dx) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed() || mapSlot.getChildCount() == 0) return;
                    mapSlot.animate().cancel();
                    float px = (viewport <= 0 || dx == 0f) ? 0f : dx * (web.getWidth() / (float) viewport);
                    mapTranslationX = px;
                    mapSlot.setTranslationX(px);
                }
            });
        }

        /** 손을 뗀 뒤 웹뷰 CSS 전환과 같은 ease-out 곡선으로 네이티브 지도 슬롯을 한 번에 미끄러뜨린다. */
        @JavascriptInterface public void animateMapOffset(final int viewport, final int fromDx, final int toDx,
                                                          final int durationMs) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (isFinishing() || isDestroyed() || mapSlot.getChildCount() == 0) return;
                    mapSlot.animate().cancel();
                    float scale = viewport > 0 ? web.getWidth() / (float) viewport : 0f;
                    float fromPx = fromDx * scale;
                    float toPx = toDx * scale;
                    mapSlot.setTranslationX(fromPx);
                    mapTranslationX = toPx;
                    if (durationMs <= 0) {
                        mapSlot.setTranslationX(toPx);
                        return;
                    }
                    mapSlot.animate()
                        .translationX(toPx)
                        .setDuration(durationMs)
                        .setInterpolator(MAP_EASE_OUT)
                        .start();
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
        } else if (action.startsWith("source-direct-")) {
            // ccNC bottom tabs: change the audio source in place; open the stock player only if refused.
            String source = action.substring("source-direct-".length());
            if (!"nature".equals(source)) {
                pendingNatureIndex = -1;
                uiHandler.removeCallbacks(applyPendingNature);
            }
            if (!StockRadio.changeSource(this, source)) handleAction("source-" + source);
        } else if ("media-refresh".equals(action)) {
            sendBroadcast(new Intent(StockMedia.REQUEST));
        } else if ("media-player".equals(action)) {
            if (!StockApps.openPlayer(this, media.source)) toast("순정 플레이어를 열 수 없습니다.");
        } else if ("nav-home".equals(action) || "nav-work".equals(action)) {
            savedPlace("nav-home".equals(action) ? "home" : "work", false);
        } else if ("nav-home-edit".equals(action) || "nav-work-edit".equals(action)) {
            savedPlace("nav-home-edit".equals(action) ? "home" : "work", true);
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
        } else if ("stock-settings-write-toggle".equals(action)) {
            boolean on = !ThemePrefs.stockWrite(this);
            ThemePrefs.setStockWrite(this, on);
            syncFlags();
            if (pageReady) {
                web.evaluateJavascript("window.K8Home.stockWriteChanged(" + on + ")", null);
            }
            toast(on ? "순정 설정을 테마에서 직접 바꿉니다. 되돌리려면 순정 설정 화면을 쓰세요."
                    : "순정 설정은 읽기만 합니다. 바꾸려면 순정 설정 화면을 여세요.");
        } else if ("sound-write-toggle".equals(action)) {
            boolean on = !ThemePrefs.soundWrite(this);
            ThemePrefs.setSoundWrite(this, on);
            syncFlags();
            toast(on ? "고음/중음/저음·전후/좌우를 테마에서 직접 바꿉니다(시험). 되돌리려면 순정 사운드 설정을 쓰세요."
                    : "사운드 값은 보기만 합니다. 바꾸려면 순정 사운드 설정을 여세요.");
        } else if ("custom-key-home-toggle".equals(action)) {
            boolean on = !ThemePrefs.customKeyHome(this);
            ThemePrefs.setCustomKeyHome(this, on);
            if (on) PopupGuardService.start(this);
            syncFlags();
            toast(on ? "즐겨찾기 버튼: 짧게 ccNC 테마 · 길게 순정 5W 홈"
                    : "즐겨찾기 버튼 홈 전환을 껐습니다.");
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
                // 순정 일반 설정 활동(com.android.generalsettings/.GeneralSettings)을 먼저 연다.
                if (openActivity(StockApps.GENERAL)) return true;
                return openSettingsRoot();
            case "settings-car":
                if (open(StockApps.VEHICLE) || openPackage("com.hkmc.software.vehiclesetting")) return true;
                showSettingsFallback(label);
                return true;
            case "settings-nav":
                openNavigationCommand(StockNavigation.SETTINGS);
                return true;
            case "settings-sound":
                if (openActivity(StockApps.SOUND)) return true;
                return openSettingsPanel(Settings.ACTION_SOUND_SETTINGS, label);
            case "settings-devices":
                if (openActivity(StockApps.BLUETOOTH_SETTINGS)) return true;
                return openSettingsPanel(Settings.ACTION_BLUETOOTH_SETTINGS, label);
            case "projection":
                return open(StockApps.PROJECTION) || openPackage("com.daudio.app.dipo");
            case "settings-display":
                if (openActivity(StockApps.DISPLAY)) return true;
                return openSettingsPanel(Settings.ACTION_DISPLAY_SETTINGS, label);
            case "settings-profile":
                if (open(StockApps.PROFILE)) return true;
                showSettingsFallback(label);
                return true;
            case "settings-voice":
                showSettingsFallback(label);
                return true;
            case "settings-buttons":
                if (openActivity(StockApps.BUTTONS) || openPackage("com.android.buttonsettings")) return true;
                showSettingsFallback(label);
                return true;
            case "settings-cluster":
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
     * 홈 화면 '우리 집' / '회사' 원형 버튼 처리.
     * - 이미 등록되어 있고 짧게 누르면(forceEdit=false): 팝업 없이 순정 내비로 즉시 길안내를 시작한다.
     * - 미등록 상태이거나 1초 이상 꾹 눌렀을 때(forceEdit=true): 등록/변경 선택 창을 연다.
     */
    private void savedPlace(final String key, boolean forceEdit) {
        final String label = "home".equals(key) ? "우리 집" : "회사";
        final String latitude = shortcuts.getString("place-" + key + "-lat", "");
        final String longitude = shortcuts.getString("place-" + key + "-lon", "");
        final String name = shortcuts.getString("place-" + key + "-name", label);
        boolean saved = !latitude.isEmpty() && !longitude.isEmpty();
        if (!forceEdit && saved) {
            startSavedPlaceGuidance(label, name, latitude, longitude);
            return;
        }
        editSavedPlace(key, label, saved, name, latitude, longitude);
    }

    private void startSavedPlaceGuidance(String label, String name, String latitude, String longitude) {
        if (!StockNavigation.routeTo(this, name, latitude, longitude)) {
            toast("순정 내비가 경로 요청을 받지 않았습니다.");
            return;
        }
        String shown = name == null || name.isEmpty() || name.equals(label)
            ? label : label + "(" + name + ")";
        toast(shown + " 길안내를 시작합니다.");
        if (ThemePrefs.navBar(this)) {
            try {
                startService(new Intent(this, NavBarService.class).setAction(NavBarService.OPENED_MAP));
            } catch (RuntimeException ignored) {
            }
        }
        // NaviOpenAPIRecevier가 자체적으로 NaviApp을 전면으로 올린다. 600ms 뒤에도 홈에 남아 있으면 직접 연다.
        uiHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (activityResumed && !isDestroyed()) openNavigation(false);
            }
        }, 600);
    }

    private void editSavedPlace(final String key, final String label, final boolean saved,
            final String savedName, final String savedLat, final String savedLon) {
        final StockNavigation.Place currentDest = readCurrentDestination();
        final StockNavigation.Place stockSpecial = StockNavigation.stockSpecialPlace(this, key);
        StockRoute.Snapshot data = route == null ? null : route.snapshot();
        final String hereLat = data == null ? "" : StockNavigation.degrees(data.curLat);
        final String hereLon = data == null ? "" : StockNavigation.degrees(data.curLon);
        final String hereName = data == null || data.road == null || data.road.isEmpty() ? label : data.road;

        final ArrayList<String> items = new ArrayList<>();
        final ArrayList<Runnable> actions = new ArrayList<>();

        if (currentDest != null && currentDest.hasCoords()) {
            final String destName = currentDest.displayName(label);
            items.add((saved ? "현재 안내 목적지로 변경 (" : "현재 안내 목적지로 등록 (") + destName + ")");
            actions.add(new Runnable() {
                @Override public void run() {
                    savePlace(key, destName, currentDest.latitude, currentDest.longitude);
                    toast(label + "을(를) '" + destName + "'(으)로 저장했습니다.");
                }
            });
        }

        if (stockSpecial != null && stockSpecial.hasCoords()) {
            final String spName = stockSpecial.displayName(label);
            String spInfo = stockSpecial.address.isEmpty() || stockSpecial.address.equals(spName)
                ? spName : spName + " · " + stockSpecial.address;
            items.add("순정 내비 등록 위치 불러오기 (" + spInfo + ")");
            actions.add(new Runnable() {
                @Override public void run() {
                    savePlace(key, spName, stockSpecial.latitude, stockSpecial.longitude);
                    toast(label + "을(를) 순정 내비 등록 위치('" + spName + "')(으)로 저장했습니다.");
                }
            });
        }

        items.add("순정 내비 검색으로 " + (saved ? "변경" : "등록"));
        actions.add(new Runnable() {
            @Override public void run() {
                pendingPlaceKey = key;
                pendingPlaceTries = 0;
                pendingPlacePrevCoord = currentDest != null && currentDest.hasCoords()
                    ? (currentDest.latitude + "," + currentDest.longitude) : "";
                toast("순정 내비에서 길안내를 시작한 뒤 홈으로 돌아오면 " + label + "(으)로 자동 저장됩니다.");
                openNavigationCommand(StockNavigation.SEARCH);
            }
        });

        if (!hereLat.isEmpty() && !hereLon.isEmpty()) {
            items.add("지금 현재 위치로 " + (saved ? "변경" : "등록"));
            actions.add(new Runnable() {
                @Override public void run() {
                    savePlace(key, hereName, hereLat, hereLon);
                    toast(label + "을(를) 지금 위치로 저장했습니다.");
                }
            });
        }

        if (saved) {
            items.add("지금 바로 길안내 시작 (" + savedName + ")");
            actions.add(new Runnable() {
                @Override public void run() {
                    startSavedPlaceGuidance(label, savedName, savedLat, savedLon);
                }
            });
            items.add(label + " 등록 해제");
            actions.add(new Runnable() {
                @Override public void run() {
                    shortcuts.edit()
                        .remove("place-" + key + "-lat")
                        .remove("place-" + key + "-lon")
                        .remove("place-" + key + "-name")
                        .apply();
                    syncSavedPlaces();
                    toast(label + " 등록을 해제했습니다.");
                }
            });
        }

        String title = saved ? (label + " 변경 · " + savedName) : (label + " 목적지 등록");
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items.toArray(new CharSequence[items.size()]), new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    if (which >= 0 && which < actions.size()) actions.get(which).run();
                }
            })
            .setNegativeButton("닫기", null)
            .show();
    }

    private StockNavigation.Place readCurrentDestination() {
        StockNavigation.Place fromProvider = StockNavigation.currentDestination(this);
        if (fromProvider != null && fromProvider.hasCoords()) return fromProvider;
        StockRoute.Snapshot data = route == null ? null : route.snapshot();
        if (data == null) return null;
        String lat = StockNavigation.degrees(data.destLat);
        String lon = StockNavigation.degrees(data.destLon);
        if (lat.isEmpty() || lon.isEmpty()) return null;
        return new StockNavigation.Place(data.destination, data.address, lat, lon);
    }

    private void savePlace(String key, String name, String latitude, String longitude) {
        String label = "home".equals(key) ? "우리 집" : "회사";
        String cleanName = name == null || name.trim().isEmpty() ? label : name.trim();
        shortcuts.edit()
            .putString("place-" + key + "-lat", latitude)
            .putString("place-" + key + "-lon", longitude)
            .putString("place-" + key + "-name", cleanName)
            .apply();
        syncSavedPlaces();
    }

    private int pendingPlaceTries;
    private final Runnable retryPendingPlace = new Runnable() {
        @Override public void run() { if (!isDestroyed()) checkPendingSavedPlace(); }
    };

    private void checkPendingSavedPlace() {
        if (pendingPlaceKey.isEmpty()) return;
        uiHandler.removeCallbacks(retryPendingPlace);
        // 내비에서 길안내를 시작하지 않고 돌아오면 목적지 표엔 예전 목적지가 남아 있다: 안내 중일 때만 저장한다.
        // 길 정보는 테마가 다시 보일 때 들어오므로 1.5초씩 4번(6초) 기다려 본다.
        StockRoute.Snapshot guide = route == null ? null : route.snapshot();
        if (guide == null || !guide.active) {
            if (++pendingPlaceTries <= 4) uiHandler.postDelayed(retryPendingPlace, 1500);
            else { pendingPlaceKey = ""; pendingPlacePrevCoord = ""; pendingPlaceTries = 0; }
            return;
        }
        pendingPlaceTries = 0;
        String key = pendingPlaceKey;
        String prevCoord = pendingPlacePrevCoord;
        pendingPlaceKey = "";
        pendingPlacePrevCoord = "";
        StockNavigation.Place current = readCurrentDestination();
        if (current == null || !current.hasCoords()) return;
        String nowCoord = current.latitude + "," + current.longitude;
        if (!prevCoord.isEmpty() && prevCoord.equals(nowCoord)) return;
        String label = "home".equals(key) ? "우리 집" : "회사";
        String name = current.displayName(label);
        savePlace(key, name, current.latitude, current.longitude);
        toast(label + "을(를) '" + name + "'(으)로 등록했습니다. 이제 짧게 누르면 바로 길안내를 시작합니다.");
    }

    private void syncSavedPlaces() {
        if (!pageReady) return;
        boolean homeSaved = !shortcuts.getString("place-home-lat", "").isEmpty()
            && !shortcuts.getString("place-home-lon", "").isEmpty();
        boolean workSaved = !shortcuts.getString("place-work-lat", "").isEmpty()
            && !shortcuts.getString("place-work-lon", "").isEmpty();
        String homeName = shortcuts.getString("place-home-name", "");
        String workName = shortcuts.getString("place-work-name", "");
        web.evaluateJavascript("window.K8Home.savedPlacesChanged("
            + homeSaved + "," + javascriptString(homeName) + ","
            + workSaved + "," + javascriptString(workName) + ")", null);
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
        if (transport.send(media.source, key)) {
            MediaKeys.cancelStockPopup(this);
            if ("nature".equals(media.source)) StockRadio.requestMediaRefresh(this);
        } else if (!MediaKeys.dispatch(this, key)) {
            toast("이 기기에서 미디어 키를 전달할 수 없습니다.");
        }
    }

    private boolean open(ComponentName component) {
        return StockApps.open(this, component);
    }

    /** 순정 설정 활동(런처 등록이 없는 것)을 직접 연다. */
    private boolean openActivity(ComponentName component) {
        return StockApps.openActivity(this, component);
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

    /**
     * 핸들 곡 넘기기·재생 키(166 이전, 167 다음, 91 재생/정지)는 테마가 받지 않는다.
     * 차의 키 분배기는 앞 화면이 키를 "처리했다"고 하면 블루투스 플레이어로 넘기지 않는데, WebView 가 이 키를 처리한 것으로
     * 돌려줘 테마가 앞에 있을 때 핸들·도우미 곡 넘기기가 모두 먹혔다(실차 2026-11-23 logcat: isViewHandled=true).
     */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == CustomKeyGuard.KEYCODE_SHORTCUT_CUSTOM && CustomKeyGuard.handleActivityKey(this, event)) {
            return true;
        }
        if (code == MediaKeys.WHEEL_PREVIOUS || code == MediaKeys.WHEEL_NEXT || code == MediaKeys.WHEEL_PLAY_PAUSE) {
            // 곡 넘기기 키면 순정 팝업이 1~1.5초 뒤 뜬다: 지금부터 닫기 창을 연다.
            if (code != MediaKeys.WHEEL_PLAY_PAUSE && event.getAction() == KeyEvent.ACTION_DOWN) MediaKeys.cancelStockPopup(this);
            return false;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (pageReady) web.evaluateJavascript("window.K8Home.back()", null);
        else finish();
    }

    @Override protected void onDestroy() {
        if (relaunching) { super.onDestroy(); return; }
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
