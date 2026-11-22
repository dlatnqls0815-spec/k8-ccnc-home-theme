package io.github.k8home;

import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

final class HeadUnitInfo {
    private HeadUnitInfo() {}

    static JSONObject androidSettings(Context context) {
        JSONObject info = new JSONObject();
        try {
            ContentResolver resolver = context.getContentResolver();
            int brightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, -1);
            int mode = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, -1);
            int night = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            info.put("clock24", DateFormat.is24HourFormat(context));
            info.put("language", context.getResources().getConfiguration().locale.getDisplayName());
            info.put("build", Build.DISPLAY);
            info.put("release", Build.VERSION.RELEASE);
            try {
                String version = context.getPackageManager().getPackageInfo(StockNavigation.PACKAGE, 0).versionName;
                if (version != null) info.put("navigationAppVersion", version);
            } catch (PackageManager.NameNotFoundException ignored) { }
            if (brightness >= 0 && brightness <= 255) info.put("brightness", Math.round(brightness * 100f / 255));
            if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) info.put("brightnessMode", "자동");
            else if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) info.put("brightnessMode", "수동");
            if (night == Configuration.UI_MODE_NIGHT_YES) info.put("night", "야간");
            else if (night == Configuration.UI_MODE_NIGHT_NO) info.put("night", "주간");
            String selected = Settings.Secure.getString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD);
            InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null && selected != null) {
                for (InputMethodInfo input : manager.getEnabledInputMethodList()) {
                    if (selected.equals(input.getId())) {
                        info.put("keyboard", StockMedia.text(input.loadLabel(context.getPackageManager())));
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // OEM builds may restrict individual settings. Only display values that were read.
        }
        return info;
    }

    static JSONObject savedVehicleSettings(Context context) {
        JSONObject info = new JSONObject();
        try {
            info.put("vehicleCode", property("ro.product.vehicleinfo"));
            info.put("driveModeOption", property("ro.product.opt.drivemode"));
            info.put("clusterTheme", property("persist.sys.setting.cluster"));
            sound(context, info);
            int user = profile(context, info);
            readProjectionSetting(context, info, "projectionUsb", "phoneprojection_setting_enable_usb_cable", user);
            readProjectionSetting(context, info, "projectionWireless", "phoneprojection_setting_enable_wireless", user);
            JSONObject stock = stockSettings(context, user);
            java.util.Iterator<String> keys = stock.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                info.put(key, stock.get(key));
            }
        } catch (Exception ignored) { }
        return info;
    }

    /**
     * Stock (5W) setting values the theme can read without any permission. The namespaces of these
     * keys are not documented, so system, global and secure are tried in turn.
     */
    static JSONObject stockSettings(Context context, int driver) {
        JSONObject info = new JSONObject();
        try {
            ContentResolver resolver = context.getContentResolver();
            readStockInt(resolver, info, "buttonNavLeft", "btn_n_left", driver);
            readStockInt(resolver, info, "buttonNavRight", "btn_n_right", driver);
            readStockInt(resolver, info, "buttonSeek", "button_settings_seek", driver);
            readStockInt(resolver, info, "tuneKnob", "tune_knob_function", driver);
            readStockInt(resolver, info, "autoTime", Settings.System.AUTO_TIME, driver);
            String clock = null;
            try {
                clock = Settings.System.getString(resolver, Settings.System.TIME_12_24);
            } catch (Exception ignored) { }
            if (clock != null && !clock.isEmpty()) {
                info.put("timeFormat", "24".equals(clock) ? "24시간" : "12시간");
            }
        } catch (Exception ignored) { }
        return info;
    }

    private static void readStockInt(ContentResolver resolver, JSONObject info, String field, String key,
                                     int driver) {
        String[] keys = driver > 0 ? new String[] {key + "_dr" + driver, key} : new String[] {key};
        for (String name : keys) {
            for (int space = 0; space < 3; space++) {
                try {
                    int value;
                    if (space == 0) {
                        value = Settings.System.getInt(resolver, name, -1);
                    } else if (space == 1) {
                        value = Settings.Global.getInt(resolver, name, -1);
                    } else {
                        value = Settings.Secure.getInt(resolver, name, -1);
                    }
                    if (value >= 0) {
                        info.put(field, value);
                        return;
                    }
                } catch (Exception ignored) { }
            }
        }
    }

    private static int profile(Context context, JSONObject info) {
        try {
            Class<?> api = Class.forName("com.hkmc.userprofile.UserProfileManager");
            Object manager = api.getConstructor(Context.class).newInstance(context.getApplicationContext());
            Object type = api.getMethod("getUserType").invoke(manager);
            if (!(type instanceof Integer)) return -1;
            int user = (Integer) type;
            if (user < 0 || user > 2) return -1;
            String name = StockMedia.text(api.getMethod("getUserName", int.class).invoke(manager, user));
            if (name.isEmpty() && user > 0) name = "운전자 " + user;
            if (!name.isEmpty()) info.put("profileName", name);
            return user;
        } catch (Exception | LinkageError ignored) {
            // The automotive library is optional and the firmware may require its own permissions.
            return -1;
        }
    }

    /** Raw amplifier values from the automotive library's MicomAudioStatus; the scale is not decoded yet. */
    private static void sound(Context context, JSONObject info) {
        try {
            Class<?> api = Class.forName("com.hkmc.media.MicomAudioManager");
            Object manager = api.getConstructor(Context.class).newInstance(context.getApplicationContext());
            Object status = api.getMethod("getStatus").invoke(manager);
            if (status == null) return;
            String[][] fields = {
                {"soundTreble", "mTreble"}, {"soundMiddle", "mMiddle"}, {"soundBass", "mBass"},
                {"soundBalance", "mBalance"}, {"soundFade", "mFade"}, {"soundSdvc", "mSDVC"},
                {"soundPriority", "mSoundPriority"}, {"soundAmpType", "mAmpType"}
            };
            for (String[] field : fields) {
                try {
                    Object value = status.getClass().getField(field[1]).get(status);
                    if (value instanceof Number && ((Number) value).intValue() != -1) {
                        info.put(field[0], ((Number) value).intValue());
                    }
                } catch (NoSuchFieldException ignored) { }
            }
        } catch (Exception | LinkageError ignored) {
            // The audio service may refuse a normal app. Only values that were read are shown.
        }
    }

    private static void readProjectionSetting(Context context, JSONObject info, String field, String key, int user)
            throws JSONException {
        if (user < 0) return;
        int flag = Settings.System.getInt(context.getContentResolver(), user == 0 ? key : key + "_dr" + user, -1);
        if (flag == 0 || flag == 1) info.put(field, flag == 1 ? "켜짐" : "꺼짐");
    }

    private static String property(String key) {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String text = StockMedia.text(reader.readLine());
                if (text.matches("[A-Za-z0-9._-]{1,64}")) return text;
            }
        } catch (IOException | SecurityException ignored) {
            // A missing or restricted property is unknown.
        } finally {
            if (process != null) process.destroy();
        }
        return "";
    }
}
