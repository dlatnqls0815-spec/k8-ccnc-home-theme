package io.github.k8home;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Stock 5W radio (HKMC_RadioGE), all from the radio app's own public entry points (2026-11-24 analysis of
 * radio-live.txt / HKMC_RadioGE manifest, user approved sending them):
 * - station / preset lists: exported RadioProvider, authority com.hkmc.av.app.radioge, paths fm_stations,
 *   am_stations, presets (columns are read as-is because only the car shows them).
 * - tune: GeRadioBroadcastReceiver VIT_NOTI_AV_RADIO_SET_FREQUENCY, Value1 band (1 FM, 2 AM),
 *   Value2 frequency in kHz (FM 89.1 -> 89100, AM 531), Value3 foreground option, Value5 preset (-1 none).
 * - logos: the app's embedded logos (res drawable img_radio_logo_0xNN) looked up by station name with the
 *   app's own name table (assets/radio_logos.tsv, names only). Images are read from the car, never copied.
 * - source change: com.hkmc.mode.ModeManager.changeMode(Integer, boolean) by reflection.
 */
final class StockRadio {
    private static final String TAG = "K8Radio";
    static final String PACKAGE = "com.hkmc.av.app.radioge";
    private static final String BT_PACKAGE = "com.daudio.av.app.btaudio.ui";
    private static final String MODE_CHANGE = "android.intent.action.VIT_NOTI_AV_MODE_CHANGE";
    private static final String SET_FREQUENCY = "android.intent.action.VIT_NOTI_AV_RADIO_SET_FREQUENCY";
    private static final int MAX_ROWS = 120;

    private static Handler modeWorker;
    private static Object cachedModeManager;
    private static Method launchModeByWidgetMethod;
    private static Method changeModeMethod;
    private static Method getCurrentModeMethod;
    private static boolean modeReflectionLoaded;
    private static int sourceSwitchSeq;

    private final Context app;
    private Map<String, String> logoNames;
    private Map<String, String> logoNamesLower;
    private final Map<String, String> logoCache = new HashMap<>();

    private static synchronized Handler modeWorker() {
        if (modeWorker == null) {
            HandlerThread thread = new HandlerThread("k8home-mode");
            thread.start();
            modeWorker = new Handler(thread.getLooper());
        }
        return modeWorker;
    }

    private static synchronized Object modeManager(Context context) {
        if (cachedModeManager != null) return cachedModeManager;
        if (modeReflectionLoaded) return null;
        modeReflectionLoaded = true;
        try {
            Class<?> type = Class.forName("com.hkmc.mode.ModeManager");
            cachedModeManager = type.getConstructor(Context.class).newInstance(context.getApplicationContext());
            try { launchModeByWidgetMethod = type.getMethod("launchModeByWidget", int.class, boolean.class); }
            catch (NoSuchMethodException ignored) { }
            try { changeModeMethod = type.getMethod("changeMode", Integer.class, boolean.class); }
            catch (NoSuchMethodException ignored) { }
            try { getCurrentModeMethod = type.getMethod("getCurrentMode"); }
            catch (NoSuchMethodException ignored) { }
        } catch (Exception | LinkageError failure) {
            Log.w(TAG, "ModeManager init failed: " + failure);
        }
        return cachedModeManager;
    }

    StockRadio(Context context) {
        app = context.getApplicationContext();
    }

    private static boolean isAllowedPath(String path) {
        return "fm_audible_stations".equals(path) || "am_audible_stations".equals(path)
            || "fm_avail_stations".equals(path) || "am_avail_stations".equals(path)
            || "presets".equals(path) || "preset_lists".equals(path)
            || "fm_stations".equals(path) || "am_stations".equals(path);
    }

    /** Rows of one provider path as {"rows":[{column:value}]}. */
    String list(String path) {
        JSONObject out = new JSONObject();
        try {
            out.put("path", path);
            if (!isAllowedPath(path)) {
                return out.put("error", "path").toString();
            }
            JSONArray rows = new JSONArray();
            Cursor cursor = app.getContentResolver().query(Uri.parse("content://" + PACKAGE + "/" + path), null, null, null, null);
            if (cursor == null) return out.put("error", "null cursor").toString();
            try {
                String[] names = cursor.getColumnNames();
                while (cursor.moveToNext() && rows.length() < MAX_ROWS) {
                    JSONObject row = new JSONObject();
                    for (int i = 0; i < names.length; i++) {
                        if (cursor.getType(i) == Cursor.FIELD_TYPE_BLOB) continue;
                        // fm_audible_stations 등은 station_name_with_region 이 한글·영문 2열로 중복된다: 첫 번째(한글)를 유지한다.
                        if (!row.has(names[i])) row.put(names[i], cursor.getString(i));
                    }
                    rows.put(row);
                }
            } finally {
                cursor.close();
            }
            out.put("rows", rows);
            Log.i(TAG, "list " + path + " rows=" + rows.length() + (rows.length() > 0 ? " first=" + rows.opt(0) : ""));
        } catch (RuntimeException | JSONException failure) {
            Log.w(TAG, "list " + path + " failed", failure);
            try { out.put("error", failure.getClass().getSimpleName()); } catch (JSONException ignored) { }
        }
        return out.toString();
    }

    /** Tunes the stock radio (and makes it the audio source) without bringing the stock radio UI to the front. */
    boolean tune(boolean fm, int kHz) {
        if (fm ? kHz < 87500 || kHz > 108000 : kHz < 522 || kHz > 1629) return false;
        Intent intent = new Intent(SET_FREQUENCY).setPackage(PACKAGE)
            .putExtra("Value1", fm ? 1 : 2).putExtra("Value2", kHz).putExtra("Value3", 0).putExtra("Value5", -1);
        try {
            app.sendBroadcast(intent);
            MediaKeys.cancelStockPopup(app);
            requestMediaRefresh(app);
            Log.i(TAG, "tune " + (fm ? "FM " : "AM ") + kHz);
            return true;
        } catch (RuntimeException failure) {
            Log.w(TAG, "tune failed", failure);
            return false;
        }
    }

    static void requestMediaRefresh(final Context context) {
        requestMediaRefresh(context, false);
    }

    private static void requestMediaRefresh(final Context context, boolean bluetooth) {
        final Context appCtx = context.getApplicationContext();
        try { appCtx.sendBroadcast(new Intent(StockMedia.REQUEST)); } catch (RuntimeException ignored) { }
        Handler main = new Handler(Looper.getMainLooper());
        long[] delays = bluetooth ? new long[] { 200, 500, 950, 1500 } : new long[] { 280, 650 };
        for (long delay : delays) {
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    try { appCtx.sendBroadcast(new Intent(StockMedia.REQUEST)); } catch (RuntimeException ignored) { }
                }
            }, delay);
        }
    }

    /** Stock audio source change without opening the player (runs ModeManager IPC off the UI thread). */
    static boolean changeSource(Context context, final String source) {
        final Context appCtx = context.getApplicationContext();
        if ("fm".equals(source) || "am".equals(source)) {
            // ModeManager.changeMode 는 FM1_SHORTCUT(EXTRA_RUNNING_STATE=1)을 보내 순정 라디오 화면이 앞으로 뜬다.
            // 순정 라디오의 VIT_NOTI_AV_MODE_CHANGE(Value2=0, DealerMode=1)는 화면을 띄우지 않고 소리만 바꾼다.
            Intent intent = new Intent(MODE_CHANGE).setPackage(PACKAGE)
                .putExtra("Value1", "fm".equals(source) ? 1 : 2)
                .putExtra("Value2", 0)
                .putExtra("DealerMode", 1);
            try {
                appCtx.sendBroadcast(MediaKeys.homePageZero());   // 팝업이 뜰 틈을 주지 않게 먼저 알린다
                appCtx.sendBroadcast(intent);
                MediaKeys.cancelStockPopup(appCtx);
                requestMediaRefresh(appCtx, false);
                Log.i(TAG, "changeSource radio bg " + source);
                return true;
            } catch (RuntimeException failure) {
                Log.w(TAG, "changeSource radio " + source + " failed: " + failure);
                return false;
            }
        }
        final int mode;
        switch (source) {
            case "bt": mode = 0x36; break;
            case "usb": mode = 0x21; break;
            case "nature": mode = 0x81; break;
            default: return false;
        }
        final int seq = ++sourceSwitchSeq;
        try {
            appCtx.sendBroadcast(MediaKeys.homePageZero());
        } catch (RuntimeException ignored) { }
        MediaKeys.cancelStockPopup(appCtx);
        requestMediaRefresh(appCtx, "bt".equals(source));

        if ("bt".equals(source)) {
            // 순정 BTAudio 는 VIT_NOTI_AV_MODE_CHANGE(Value1=13, Value2=2)를 받으면 100ms+350ms 숏컷 지연 없이
            // 즉시 오디오 포커스를 요청하고 백그라운드로 BT 오디오를 시작한다(BTAudio.odex service/d 확인).
            try {
                Intent btBg = new Intent(MODE_CHANGE).setPackage(BT_PACKAGE)
                    .putExtra("Value1", 13)
                    .putExtra("Value2", 2);
                appCtx.sendBroadcast(btBg);
                Log.i(TAG, "changeSource bt fast broadcast sent");
            } catch (RuntimeException failure) {
                Log.w(TAG, "changeSource bt fast broadcast failed: " + failure);
            }
            // 서비스 미기동 등으로 120ms 안에 BT 모드로 안 바뀌었으면 ModeManager.launchModeByWidget 으로 폴백한다.
            modeWorker().postDelayed(new Runnable() {
                @Override public void run() {
                    if (seq != sourceSwitchSeq) return;
                    if (currentMode(appCtx) == 0x36) {
                        Log.i(TAG, "changeSource bt already active via fast broadcast");
                        return;
                    }
                    invokeModeSwitch(appCtx, source, mode);
                }
            }, 120);
            return true;
        }

        modeWorker().post(new Runnable() {
            @Override public void run() {
                if (seq != sourceSwitchSeq) return;
                invokeModeSwitch(appCtx, source, mode);
            }
        });
        return true;
    }

    private static void invokeModeSwitch(Context appCtx, String source, int mode) {
        Object manager = modeManager(appCtx);
        if (manager == null) return;
        if (launchModeByWidgetMethod != null) {
            try {
                launchModeByWidgetMethod.invoke(manager, mode, false);
                MediaKeys.cancelStockPopup(appCtx);
                Log.i(TAG, "launchModeByWidget 0x" + Integer.toHexString(mode) + " bg");
                return;
            } catch (Exception | LinkageError widgetFailure) {
                Log.w(TAG, "launchModeByWidget " + source + " failed: " + widgetFailure);
            }
        }
        if (changeModeMethod != null) {
            try {
                Object result = changeModeMethod.invoke(manager, mode, false);
                MediaKeys.cancelStockPopup(appCtx);
                Log.i(TAG, "changeMode 0x" + Integer.toHexString(mode) + " -> " + result);
            } catch (Exception | LinkageError failure) {
                Log.w(TAG, "changeMode " + source + " failed: " + failure);
            }
        }
    }

    /**
     * 자연의 소리 목록(차의 순정 HKMC_NatureSound, 2026-11-24 실차 순정 목록 화면을 그대로 읽음): 10개, 이 순서.
     * 이름(차 언어)과 배경 사진은 순정 앱에서 읽는다(그림을 테마에 넣지 않는다). [{name, art(data: URL)}]
     */
    private static final String NATURE = "com.hkmc.av.app.naturesound";
    private static final String[][] NATURE_SOUNDS = {
        {"forest_sound", "sound_bg_forest"}, {"waves_sound", "sound_bg_sea_wave"}, {"city_sound", "sound_bg_city"},
        {"space_sound", "sound_bg_space"}, {"rain_sound", "sound_bg_rain"}, {"cathedral_sound", "sound_bg_cathedral"},
        {"meditation_sound", "sound_bg_temple"}, {"spring_sound", "sound_bg_spring"}, {"summer_sound", "sound_bg_summer"},
        {"autumn_sound", "sound_bg_autumn"}};
    private volatile String natureJson;

    /** 첫 자연의 소리 진입 때 UI가 멈추지 않도록 백그라운드에서 목록·배경을 미리 준비한다. */
    void preloadNatureList() {
        if (natureJson != null) return;
        modeWorker().post(new Runnable() {
            @Override public void run() { natureList(); }
        });
    }

    synchronized String natureList() {
        if (natureJson != null) return natureJson;
        JSONArray out = new JSONArray();
        try {
            Resources res = app.createPackageContext(NATURE, 0).getResources();
            for (String[] sound : NATURE_SOUNDS) {
                int name = res.getIdentifier(sound[0], "string", NATURE);
                if (name == 0) continue;
                int art = sound[1].isEmpty() ? 0 : res.getIdentifier(sound[1], "drawable", NATURE);
                // 사진은 주소만 보낸다(바이트는 HomeActivity.shouldInterceptRequest 가 natureArt 로 답함):
                // 960px 10장을 base64 로 넘기면 1.6MB 라 테마 첫 화면에서 스와이프가 멈췄다(실차 2026-11-25).
                byte[] jpeg = art == 0 ? null : jpeg(res, art, 960);
                if (jpeg != null) natureArt.put(out.length(), jpeg);
                out.put(new JSONObject().put("name", res.getString(name))
                    .put("art", jpeg == null ? "" : NATURE_ART_URL + out.length() + ".jpg"));
            }
        } catch (Exception failure) {
            Log.w(TAG, "nature list failed: " + failure);
        }
        Log.i(TAG, "nature list " + out.length());
        if (out.length() > 0) natureJson = out.toString();
        return out.toString();
    }

    static final String NATURE_ART_URL = "file:///android_asset/k8nature/";
    private final Map<Integer, byte[]> natureArt = new java.util.concurrent.ConcurrentHashMap<>();

    /** JPEG bytes for a nature list picture link ("…/k8nature/3.jpg"), or null. */
    byte[] natureArt(String url) {
        try {
            return natureArt.get(Integer.parseInt(url.substring(NATURE_ART_URL.length()).replaceAll("\\.jpg.*$", "")));
        } catch (RuntimeException bad) {
            return null;
        }
    }

    /** Drawable as JPEG bytes, decoded at about maxWidth (the stock photos are 1~2 MB PNGs). */
    private static byte[] jpeg(Resources res, int id, int maxWidth) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, id, bounds);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        while (bounds.outWidth / (opts.inSampleSize * 2) >= maxWidth) opts.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeResource(res, id, opts);
        if (bitmap == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
        bitmap.recycle();
        return out.toByteArray();
    }

    /** Stock current audio mode (ModeManager.getCurrentMode, same as the stock home widget), -1 when unknown. */
    static int currentMode(Context context) {
        try {
            Object manager = modeManager(context);
            if (manager == null || getCurrentModeMethod == null) return -1;
            return ((Number) getCurrentModeMethod.invoke(manager)).intValue();
        } catch (Exception | LinkageError failure) {
            Log.w(TAG, "getCurrentMode failed: " + failure);
            return -1;
        }
    }

    /** data: URL of the stock logo for a station name ("KBS 2FM (Cool FM) [수도권]"), or "" when none. */
    String logo(String station) {
        if (station == null) return "";
        String name = station.replaceAll("\\s*\\[[^\\]]*\\]\\s*$", "").trim();
        if (name.isEmpty()) return "";
        if (logoCache.containsKey(name)) return logoCache.get(name);
        String drawable = names().get(name);
        if (drawable == null && logoNamesLower != null) drawable = logoNamesLower.get(name.toLowerCase(Locale.ROOT));
        String url = "";
        if (drawable != null) {
            try {
                Context radio = app.createPackageContext(PACKAGE, 0);
                int id = radio.getResources().getIdentifier(drawable, "drawable", PACKAGE);
                Bitmap bitmap = id == 0 ? null : BitmapFactory.decodeResource(radio.getResources(), id);
                if (bitmap != null) {
                    ByteArrayOutputStream png = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, png);
                    url = "data:image/png;base64," + Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
                }
            } catch (Exception failure) {
                Log.w(TAG, "logo " + name + " failed: " + failure);
            }
        }
        logoCache.put(name, url);
        return url;
    }

    private Map<String, String> names() {
        if (logoNames != null) return logoNames;
        logoNames = new HashMap<>();
        logoNamesLower = new HashMap<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(app.getAssets().open("radio_logos.tsv"), "UTF-8"))) {
            for (String line; (line = in.readLine()) != null; ) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    String key = line.substring(tab + 1).trim();
                    String val = line.substring(0, tab);
                    logoNames.put(key, val);
                    logoNamesLower.put(key.toLowerCase(Locale.ROOT), val);
                }
            }
        } catch (Exception failure) {
            Log.w(TAG, "logo table missing", failure);
        }
        return logoNames;
    }
}
