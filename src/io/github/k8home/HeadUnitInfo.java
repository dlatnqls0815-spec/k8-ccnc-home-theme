package io.github.k8home;

import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
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

    /**
     * 차량 관련 시스템 속성(읽기 전용).
     * 근거: 순정 차량 설정 앱 dex 문자열(docs/차량설정-연동조사.md 2절).
     * 값의 뜻은 아직 확인되지 않았으므로 화면에는 "순정 값 N"으로 보여 준다.
     */
    static void vehicleProps(JSONObject info) {
        try {
            info.put("vehicleSettingShown", property("persist.sys.showVehicleSetting"));
            info.put("driverAssistance", property("persist.sys.VSDriverAssistance"));
            info.put("ambientLight", property("persist.sys.usmAmbientLight"));
            info.put("moodLamp", property("persist.sys.usmMoodLamp"));
            info.put("asdSetting", property("persist.sys.usmASDsetting"));
            info.put("seatPositioning", property("persist.sys.seatPositioning"));
            info.put("seatWheelHeat", property("persist.sys.seatWheelHeat"));
            info.put("seatMemory", property("persist.sys.showSeatMemoryt"));
            info.put("frontSeatShown", property("persist.sys.showSWRCFrontSeat"));
            info.put("climateTunnel", property("persist.sys.climate.tunnelsect"));
            info.put("climateOther", property("persist.sys.climate.othersect"));
            info.put("climateRecMode", property("persist.sys.climate.recmodeplus"));
            info.put("climateSupport", property("persist.sys.climate.support"));
            info.put("smartImsOption", property("persist.sys.smartImsOption"));
            info.put("performanceGauge", property("persist.sys.performancegauge"));
        } catch (Exception ignored) { }
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
            collectedSettings(context, info, user);
            vehicleProps(info);
            preferStockTone(info, "soundTreble");
            preferStockTone(info, "soundMiddle");
            preferStockTone(info, "soundBass");
            preferStockTone(info, "soundAutoVolume");
            info.put("soundRaw", StockSound.raw(context));
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

    /** 문자열 값(시간·언어 등) 읽기. 접미사 키를 먼저 보고 없으면 접미사 없는 키를 본다. */
    private static void readStockString(ContentResolver resolver, JSONObject info, String field, String key,
                                        int driver) {
        String[] keys = driver > 0 ? new String[] {key + "_dr" + driver, key} : new String[] {key};
        for (String name : keys) {
            for (int space = 0; space < 3; space++) {
                try {
                    String value;
                    if (space == 0) {
                        value = Settings.System.getString(resolver, name);
                    } else if (space == 1) {
                        value = Settings.Global.getString(resolver, name);
                    } else {
                        value = Settings.Secure.getString(resolver, name);
                    }
                    if (value != null && !value.isEmpty()) {
                        info.put(field, value);
                        return;
                    }
                } catch (Exception ignored) { }
            }
        }
    }

    /** 현재 운전자 번호(user_profile_driver). 접미사 키(_dr1 등)를 고를 때 쓴다. */
    private static int driverNumber(ContentResolver resolver, int fallback) {
        try {
            int value = Settings.System.getInt(resolver, "user_profile_driver", -1);
            if (value >= 0) return value;
        } catch (Exception ignored) { }
        try {
            int value = Settings.Global.getInt(resolver, "user_profile_driver", -1);
            if (value >= 0) return value;
        } catch (Exception ignored) { }
        return fallback;
    }

    /**
     * 실차(5W)에서 모은 순정 설정 키를 읽기 전용으로 읽는다.
     * 근거: docs/5W-순정설정-실차수집.md 의 "테마가 읽을 수 있는 순정 설정 키" 표.
     * 쓰기는 하지 않는다.
     */
    private static void collectedSettings(Context context, JSONObject info, int driver) {
        try {
            ContentResolver resolver = context.getContentResolver();
            int drv = driverNumber(resolver, driver);
            // 사운드·안내·디스플레이·일반 등
            String[][] ints = {
                {"navGuideMute", "sound_mute_nav_guide"}, {"welcomeSound", "use_welcome_message"},
                {"voiceType", "voice_type_information"}, {"kakaoVoice", "kakao_friends_voice_information"},
                {"vrWakeup", "vr_enable_wakeup"}, {"vrBargein", "vr_enable_bargein"},
                {"rearVoiceLock", "rear_voice_recognition_lock"}, {"bluelightEnable", "bluelight_enable"},
                {"bluelightMode", "bluelight_mode"}, {"bluelightLevel", "bluelight_level"},
                {"keepLastMode", "keep_last_mode"}, {"mediaNotification", "media_notification"},
                {"timeFormat12", "time_12_24"}, {"gpsAutoTime", "gps_auto_time"}, {"summerTime", "summer_time"},
                {"volTuneSwap", "vol_tuneknob_swap"}, {"tuneKnobFunction", "tune_knob_function"},
                {"buttonSeek", "button_settings_seek"}, {"soundPositionMode", "sound_position_mode"},
                {"soundPositionSeat", "sound_position_seat"}, {"rearSleepMode", "rear_sleep_mode"},
                {"daylightBright", "daylight_bright"}, {"nightBright", "night_bright"},
                {"splitScreenOn", "split_screen_on"}, {"waitingDisplayType", "waiting_display_type"},
                {"clockWidgetType", "clock_widget_type"}, {"analogClockType", "analog_clock_type"},
                {"rearCameraOption", "rear_camera_option"}, {"cameraBrightDay", "camera_bright_setting_day"},
                {"mediaRepeatFolder", "media_repeat_folder"}, {"androidAutoOsd", "androidauto_ss_osd"},
                {"carPlayOsd", "carplay_ss_osd"}, {"soundRnc", "sound_rnc"}, {"soundAdpAnc", "sound_adp_anc"}
            };
            for (String[] row : ints) readStockInt(resolver, info, row[0], row[1], drv);
            // 접미사 없는 키(운전자 구분 없음)
            readStockInt(resolver, info, "videoAspectRatio", "video_aspect_ratio", 0);
            readStockInt(resolver, info, "driveModeNotification", "drivemode_notification", 0);
            readStockInt(resolver, info, "seatPositionAlert", "seat_position_alert", 0);
            // 문자열 값
            readStockString(resolver, info, "bluelightStart", "bluelight_starttime", drv);
            readStockString(resolver, info, "bluelightEnd", "bluelight_endtime", drv);
            readStockString(resolver, info, "languageType", "language_type", drv);
            // 고음/중음/저음: USER_ 접미사 키에 10을 0으로 저장한다(14 = +4)
            // 운전자 1·2는 _USER_1·_USER_2, 손님(0)은 _GUEST 로 저장된다(실차 설정 표).
            String user = drv > 0 ? "_USER_" + drv : "_GUEST";
            readTone(resolver, info, "soundTreble", "hkmc_sndset_treble" + user);
            readTone(resolver, info, "soundMiddle", "hkmc_sndset_middle" + user);
            readTone(resolver, info, "soundBass", "hkmc_sndset_bass" + user);
            // 오디오 음량 자동 줄이기
            readStockInt(resolver, info, "soundAutoVolumeKey", "hkmc_sndset_auto_adjusting_vol" + user, 0);
            // 사용자 버튼과 MODE 버튼은 시스템 속성이다
            info.put("buttonNavi", property("persist.sys.key.custom"));
            info.put("buttonHandle", property("persist.sys.key.swrccustom"));
            String mode = property("persist.sys.key.mode");
            if (mode != null && !mode.isEmpty()) {
                try {
                    info.put("buttonMode", Integer.parseInt(mode));
                } catch (NumberFormatException ignored) {
                    info.put("buttonMode", mode);
                }
            }
        } catch (Exception ignored) { }
    }

    /**
     * 앰프 상태 객체 값과 순정 설정 키 값이 다르면 설정 키 쪽 값을 쓴다. 다르면 로그에 남긴다.
     */
    private static void preferStockTone(JSONObject info, String field) {
        try {
            if (!info.has(field + "Key")) return;
            int fromKey = info.getInt(field + "Key");
            if (info.has(field)) {
                int fromAmp = info.optInt(field, fromKey);
                if (fromAmp != fromKey) {
                    Log.i("K8Settings", field + ": 앰프 " + fromAmp + " ≠ 설정 키 " + fromKey
                        + " → 설정 키 값을 씁니다");
                }
            }
            info.put(field, fromKey);
            info.remove(field + "Key");
        } catch (Exception ignored) { }
    }

    /** 톤 값(10 = 0 기준)을 화면용 수로 바꾼다. */
    private static void readTone(ContentResolver resolver, JSONObject info, String field, String key) {
        String[] names = key.startsWith("hkmc_sndset") && key.contains("_USER_") ? new String[] {key} :
            new String[] {key};
        for (String name : names) {
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
                        info.put(field, value - 10);
                        return;
                    }
                } catch (Exception ignored) { }
            }
        }
    }

    static int profile(Context context, JSONObject info) {
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
                {"soundPriority", "mSoundPriority"}, {"soundAmpType", "mAmpType"},
                {"soundAutoVolume", "mAutoAdjustingVolumeSetting"}, {"soundReverseDim", "mDimVolumeInReverse"},
                {"soundNaviDuringCall", "mNaviDuringCall"}, {"soundBeepMode", "mBeepMode"},
                {"soundSurround", "mSurround"}, {"soundBoseCp", "mBoseCPMode"},
                {"soundMeridian", "mMeridianHorizonMode"}, {"soundQLS", "mQLS"},
                {"soundLiveDynamic", "mLiveDynamic"}, {"soundEqMode", "mEqMode"},
                {"soundVariableEq", "mVariableEqMode"}, {"soundRearSleep", "mIsRearSleepOn"},
                {"soundPowerTreble", "mPowerTreble"}, {"soundVolume", "mVolume"},
                {"soundVuiVolume", "mVuiVolume"}, {"soundMicVolume", "mMicVolume"},
                {"soundUvoVolume", "mUvoBlueVolume"}, {"soundAsl", "mAsl"}, {"soundAsd", "mAsdMode"}
            };
            for (String[] field : fields) {
                try {
                    Object value = status.getClass().getField(field[1]).get(status);
                    if (value instanceof Boolean) {
                        info.put(field[0], ((Boolean) value).booleanValue() ? 1 : 0);
                    } else if (value instanceof Number && ((Number) value).intValue() != -1) {
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
