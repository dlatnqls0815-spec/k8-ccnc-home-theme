package io.github.k8home;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;

/**
 * 시트 열선·통풍, 핸들 열선(순정 USMManager)과 앞좌석 공조 조작(순정 DATCManager), test66.
 * 차의 automotive 라이브러리 함수를 이름으로 부른다(K8 automotive.dex 에서 확인한 이름: setFrontSeatHeatDrvStep 등).
 * 펌웨어마다 다른 바인더 번호는 쓰지 않는다. 값의 뜻은 JacobPantuso/ccnc-gen5w(MIT) 의 정리를 따랐고,
 * 공조 값(켜기 1/끄기 0, 온도 올림 1/내림 2, 바람 2~9)은 그쪽도 "가정"이라 K8 실차 확인 전이다.
 *
 * 좌석 상태 값(SEAT_STATES): 2 끔, 3/4/5 통풍 1/2/3단, 6/7/8 열선 1/2/3단. 옵션: 0 없음, 1·2 열선, 3·4 통풍, 5·6 둘 다.
 */
final class StockCabin {
    private static final String TAG = "K8Cabin";
    // DATCFlag 번호(K8 automotive.odex 와 같은 값을 StockClimate 가 이미 읽고 있다).
    static final int C_AIR_CLEANING = 0x7, C_BLOWER_LEVEL = 0x5, C_AUTO = 0x8, C_AC = 0x9, C_FRONT_DEFOG = 0xa, C_INTAKE = 0xb,
        C_SYNC = 0x16, C_TEMP_PS_UPDN = 0x31, C_TEMP_DR_UPDN = 0x32, C_OFF_REQ = 0x33;

    private final Context app;
    private Object usm, datc;
    private long lastWheel;

    StockCabin(Context context) {
        app = context.getApplicationContext();
    }

    private Object usm() {
        if (usm == null) {
            try {
                usm = Class.forName("com.hkmc.usm.USMManager").getConstructor(Context.class).newInstance(app);
            } catch (Throwable failure) {
                Log.w(TAG, "USMManager unavailable", failure);
            }
        }
        return usm;
    }

    private Object datc() {
        if (datc == null) {
            try {
                datc = Class.forName("com.hkmc.datc.DATCManager").getConstructor(Context.class).newInstance(app);
            } catch (Throwable failure) {
                Log.w(TAG, "DATCManager unavailable", failure);
            }
        }
        return datc;
    }

    private static int getInt(Object target, String name) {
        if (target == null) return -1;
        try {
            Object value = target.getClass().getMethod(name).invoke(target);
            return value instanceof Integer ? (Integer) value : value instanceof Boolean ? ((Boolean) value ? 1 : 0) : -1;
        } catch (Throwable failure) {
            return -1;
        }
    }

    private static int getInt(Object target, String name, int arg) {
        if (target == null) return -1;
        try {
            Object value = target.getClass().getMethod(name, int.class).invoke(target, arg);
            return value instanceof Integer ? (Integer) value : -1;
        } catch (Throwable failure) {
            return -1;
        }
    }

    private static boolean call(Object target, String name, Class<?>[] types, Object... args) {
        if (target == null) return false;
        try {
            Method method = target.getClass().getMethod(name, types);
            method.invoke(target, args);
            StringBuilder text = new StringBuilder();
            for (Object arg : args) text.append(text.length() == 0 ? "" : ",").append(arg);
            Log.i(TAG, "SENT " + name + "(" + text + ")");
            return true;
        } catch (Throwable failure) {
            Log.w(TAG, name + " refused", failure);
            return false;
        }
    }

    /** 화면에 보낼 상태. 읽을 수 없는 값은 -1. */
    JSONObject snapshot() {
        JSONObject out = new JSONObject();
        Object u = usm(), d = datc();
        try {
            out.put("drvOpt", getInt(u, "getFrontSeatHeat_DrvOption")).put("drv", getInt(u, "getFrontSeatHeat_DrvState"));
            out.put("astOpt", getInt(u, "getFrontSeatHeat_AstOption")).put("ast", getInt(u, "getFrontSeatHeat_AstState"));
            out.put("rlOpt", getInt(u, "getRearSeatHeat_LeftOption")).put("rl", getInt(u, "getRearSeatHeat_LeftState"));
            out.put("rrOpt", getInt(u, "getRearSeatHeat_RightOption")).put("rr", getInt(u, "getRearSeatHeat_RightState"));
            out.put("swOpt", getInt(u, "getSWRC_SteeringwheelHeatingOpt")).put("swInf", getInt(u, "getSWRC_SteeringwheelHeatingSWInf"));
            out.put("swStep1", getInt(u, "getSWRC_SteeringwheelHeatIndStep1")).put("swStep2", getInt(u, "getSWRC_SteeringwheelHeatIndStep2"));
            out.put("frontControl", getInt(d, "supportFrontClimateControl"));
            out.put("climateType", getInt(d, "getClimateType"));
            out.put("defog", getInt(d, "getClimateInfo", C_FRONT_DEFOG)).put("sync", getInt(d, "getClimateInfo", C_SYNC));
            // 공기청정: 달려 있는지(isAirPurifier), 켜짐(DATCFlag AIR_CLEANING 7), 공기질 값(AIR_QUALITY 0x22)·단계 색
            out.put("purifier", getInt(d, "isAirPurifier")).put("airClean", getInt(d, "getClimateInfo", C_AIR_CLEANING));
            out.put("airQuality", getInt(d, "getClimateInfo", 0x22)).put("airColor", getInt(d, "getAirCleaningStepColor"));
        } catch (JSONException ignored) { }
        return out;
    }

    /** 좌석 버튼: 순정 팝업과 같은 순서로 돈다(열선 8→7→6→2, 통풍 5→4→3→2). 다른 쪽이 켜져 있으면 처음부터. */
    boolean seat(String which, boolean heat, int current) {
        int next;
        int[] cycle = heat ? new int[] {8, 7, 6, 2} : new int[] {5, 4, 3, 2};
        next = cycle[0];
        for (int i = 0; i < cycle.length - 1; i++) if (cycle[i] == current) next = cycle[i + 1];
        if (current == cycle[cycle.length - 2]) next = 2;
        String method = "drv".equals(which) ? "setFrontSeatHeatDrvStep" : "ast".equals(which) ? "setFrontSeatHeatAstStep"
            : "rl".equals(which) ? "setRearSeatHeatLeftStep" : "rr".equals(which) ? "setRearSeatHeatRightStep" : null;
        return method != null && call(usm(), method, new Class<?>[] {int.class}, next);
    }

    /** 핸들 열선은 누름(1)·뗌(0) 버튼이다. 0.15초 뒤 뗀다(부른 스레드=시트 창 작업 스레드에서). */
    boolean wheel() {
        final Object u = usm();
        long now = System.currentTimeMillis();
        if (u == null || now - lastWheel < 800) return false;
        lastWheel = now;
        boolean sent = call(u, "setSWRC_SteeringwheelWarmerSet", new Class<?>[] {int.class}, 1);
        new android.os.Handler(android.os.Looper.myLooper()).postDelayed(new Runnable() {
            @Override public void run() { call(u, "setSWRC_SteeringwheelWarmerSet", new Class<?>[] {int.class}, 0); }
        }, 150);
        return sent;
    }

    /** 앞좌석 공조 한 번 보내기. */
    boolean climate(int control, int value) {
        return call(datc(), "setFrontClimateControl", new Class<?>[] {int.class, int.class}, control, value);
    }
}
