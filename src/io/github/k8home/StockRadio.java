package io.github.k8home;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
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
    private static final String MODE_CHANGE = "android.intent.action.VIT_NOTI_AV_MODE_CHANGE";
    private static final String SET_FREQUENCY = "android.intent.action.VIT_NOTI_AV_RADIO_SET_FREQUENCY";
    private static final int MAX_ROWS = 120;

    private final Context app;
    private Map<String, String> logoNames;
    private Map<String, String> logoNamesLower;
    private final Map<String, String> logoCache = new HashMap<>();

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

    private static void requestMediaRefresh(final Context context) {
        final Context appCtx = context.getApplicationContext();
        try { appCtx.sendBroadcast(new Intent(StockMedia.REQUEST)); } catch (RuntimeException ignored) { }
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                try { appCtx.sendBroadcast(new Intent(StockMedia.REQUEST)); } catch (RuntimeException ignored) { }
            }
        }, 320);
    }

    /** Stock audio source change without opening the player. */
    static boolean changeSource(Context context, String source) {
        if ("fm".equals(source) || "am".equals(source)) {
            // ModeManager.changeMode 는 FM1_SHORTCUT(EXTRA_RUNNING_STATE=1)을 보내 순정 라디오 화면이 앞으로 뜬다.
            // 순정 라디오의 VIT_NOTI_AV_MODE_CHANGE(Value2=0, DealerMode=1)는 화면을 띄우지 않고 소리만 바꾼다.
            Intent intent = new Intent(MODE_CHANGE).setPackage(PACKAGE)
                .putExtra("Value1", "fm".equals(source) ? 1 : 2)
                .putExtra("Value2", 0)
                .putExtra("DealerMode", 1);
            try {
                context.getApplicationContext().sendBroadcast(intent);
                MediaKeys.cancelStockPopup(context);
                requestMediaRefresh(context);
                Log.i(TAG, "changeSource radio bg " + source);
                return true;
            } catch (RuntimeException failure) {
                Log.w(TAG, "changeSource radio " + source + " failed: " + failure);
                return false;
            }
        }
        int mode;
        switch (source) {
            case "bt": mode = 0x36; break;
            case "usb": mode = 0x21; break;
            case "nature": mode = 0x81; break;
            default: return false;
        }
        try {
            Class<?> type = Class.forName("com.hkmc.mode.ModeManager");
            Object manager = type.getConstructor(Context.class).newInstance(context.getApplicationContext());
            Object result = type.getMethod("changeMode", Integer.class, boolean.class).invoke(manager, mode, false);
            MediaKeys.cancelStockPopup(context);
            Log.i(TAG, "changeMode 0x" + Integer.toHexString(mode) + " -> " + result);
            return Boolean.TRUE.equals(result);
        } catch (Exception | LinkageError failure) {
            Log.w(TAG, "changeMode " + source + " failed: " + failure);
            return false;
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
