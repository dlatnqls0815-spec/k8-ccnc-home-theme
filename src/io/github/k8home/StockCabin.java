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
        C_SYNC = 0x16, C_TEMP_PS_UPDN = 0x31, C_TEMP_DR_UPDN = 0x32, C_OFF_REQ = 0x33, C_BLOWER_MODE = 0x6;

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

    /** 공조 리스너가 쓰는 연결된 DATCManager 를 같이 쓴다. */
    void useDatc(Object connected) {
        if (connected != null) datc = connected;
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
            // 앞좌석: USM(핸들 버튼 차) → 안 되면 BCM(K8: 공조 패널 버튼 차). 값의 뜻은 같다(2 끔, 3~5 통풍, 6~8 열선).
            int drvOpt = getInt(u, "getFrontSeatHeat_DrvOption"), astOpt = getInt(u, "getFrontSeatHeat_AstOption");
            frontViaBcm = drvOpt <= 0 && astOpt <= 0;
            if (frontViaBcm) {
                out.put("drvOpt", bcmInt(B_DRV_OPT)).put("drv", bcmInt(B_DRV_STATE));
                out.put("astOpt", bcmInt(B_AST_OPT)).put("ast", bcmInt(B_AST_STATE));
            } else {
                out.put("drvOpt", drvOpt).put("drv", getInt(u, "getFrontSeatHeat_DrvState"));
                out.put("astOpt", astOpt).put("ast", getInt(u, "getFrontSeatHeat_AstState"));
            }
            out.put("rlOpt", getInt(u, "getRearSeatHeat_LeftOption")).put("rl", getInt(u, "getRearSeatHeat_LeftState"));
            out.put("rrOpt", getInt(u, "getRearSeatHeat_RightOption")).put("rr", getInt(u, "getRearSeatHeat_RightState"));
            int swOpt = getInt(u, "getSWRC_SteeringwheelHeatingOpt");
            if (swOpt <= 0 && bcmInt(B_WHEEL_OPT) == 1) { swOpt = 1; wheelViaVr = true; }   // K8: BCM 이 1(있음)
            out.put("swOpt", swOpt).put("swInf", getInt(u, "getSWRC_SteeringwheelHeatingSWInf")).put("wheelState", wheelState);
            out.put("swStep1", getInt(u, "getSWRC_SteeringwheelHeatIndStep1")).put("swStep2", getInt(u, "getSWRC_SteeringwheelHeatIndStep2"));
            // 공조: K8 에선 DATCManager 가 일반 앱에서 서비스를 못 잡아(NPE) 순정 서비스 바인더를 직접 부른다(2026-11-27 차 확인).
            out.put("frontControl", datcCall(T_SUPPORT_FRONT)).put("climateType", datcCall(T_CLIMATE_TYPE));
            out.put("purifier", datcCall(T_IS_AIR_PURIFIER));
            out.put("airQuality", datcInfo(0x22)).put("airColor", datcCall(T_AIR_CLEANING_STEP_COLOR));
            JSONObject c = new JSONObject();
            // getClimateInfo 는 저장된 값(바람·풍향·내외기·온도)만 준다. A/C·AUTO·앞유리·SYNC·공기청정·켜짐(7·8·9·10·12·22)은
            // "flag error" 라 읽지 않는다(2026-11-27 K8 DATCService 로그). 내외기: 1 내기, 2 외기.
            c.put("intake", datcInfo(C_INTAKE) == 1 ? 1 : 0).put("mode", datcInfo(C_BLOWER_MODE));
            int level = datcInfo(C_BLOWER_LEVEL);   // 2..9 = 1..8단, 0 꺼짐
            c.put("blower", level >= 2 && level <= 9 ? level - 1 : level == 0 ? 0 : -1);
            c.put("drRaw", datcInfo(0x1c)).put("psRaw", datcInfo(0x1d)).put("unit", datcCall(T_TEMPERATURE_UNIT) == 2 ? 1 : 0);
            if (liveKnown) {
                synchronized (live) {
                    // 실시간 값(리스너)으로 채운다. 바람 단계 2..9 = 1..8, 켜짐 1, 내외기 1 내기.
                    c.put("ac", live.optInt("ac", -1)).put("auto", live.optInt("auto", -1));
                    c.put("autoStep", live.optInt("autoStep", -1)).put("autoText", live.optInt("autoText", -1));
                    int lv = live.optInt("blowerLevel", -1);
                    if (lv >= 0) c.put("blower", lv >= 2 && lv <= 9 ? lv - 1 : 0);
                    int op = live.optInt("op", -1);
                    if (op >= 0) c.put("power", op == 1 ? 1 : 0);
                    double dr = live.optDouble("drTemp", Double.NaN), ps = live.optDouble("psTemp", Double.NaN);
                    if (dr > 0 && dr < 60) c.put("driver", dr);
                    if (ps > 0 && ps < 60) c.put("passenger", ps);
                    // 풍향 modeDrive: 1 정면, 2 정면+발, 3 발, 4 발+앞유리, 5 앞유리, 6 자동 성에,
                    // 7~10 은 앞유리 성에가 섞인 값(K8 앞유리 버튼을 누르면 10, 2026-11-27 차)
                    int md = live.optInt("modeDrive", -1);
                    if (md >= 1) { c.put("mode", md); out.put("defog", md >= 5 ? 1 : 0); }
                    int ar = live.optInt("airCleanInd", -1);
                    if (ar >= 0) out.put("airClean", ar == 2 ? 1 : 0);   // ArClnIND: 2 켜짐, 1 꺼짐(2026-11-27 차)
                    int in = live.optInt("intakeRaw", -1);
                    if (in >= 0) c.put("intake", in == 1 ? 1 : 0);
                    if (live.has("sync")) out.put("sync", live.optInt("sync", -1));
                    out.put("live", live);
                }
            }
            out.put("climate", c);
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
        if (frontViaBcm && ("drv".equals(which) || "ast".equals(which))) return vrSend("drv".equals(which) ? V_FL_SEAT : V_FR_SEAT, next);
        String method = "drv".equals(which) ? "setFrontSeatHeatDrvStep" : "ast".equals(which) ? "setFrontSeatHeatAstStep"
            : "rl".equals(which) ? "setRearSeatHeatLeftStep" : "rr".equals(which) ? "setRearSeatHeatRightStep" : null;
        return method != null && call(usm(), method, new Class<?>[] {int.class}, next);
    }

    /** 핸들 열선은 누름(1)·뗌(0) 버튼이다. 0.15초 뒤 뗀다(부른 스레드=시트 창 작업 스레드에서). */
    boolean wheel() {
        if (wheelViaVr) {   // 순정 음성 경로: 켜기 1, 끄기 0(실차 확인)
            final int want = wheelState == 1 ? 0 : 1;
            wheelHeard = false;
            boolean sent = vrSend(V_WHEEL, want);
            // 상태는 바뀔 때만 알려 준다: 처음 상태를 모를 때 "켜기"를 보냈는데 1초 안에 알림이 없으면 이미 켜져 있던 것이다.
            new android.os.Handler(android.os.Looper.myLooper()).postDelayed(new Runnable() {
                @Override public void run() { if (!wheelHeard) wheelState = want; }
            }, 1000);
            return sent;
        }
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

    /** 앞좌석 공조 한 번 보내기(순정 서비스 바인더 직접). */
    boolean climate(int control, int value) {
        boolean ok = datcTransact(T_SET_FRONT_CONTROL, control, value) != Integer.MIN_VALUE;
        Log.i(TAG, (ok ? "SENT" : "FAILED") + " setFrontClimateControl(" + control + "," + value + ")");
        return ok;
    }

    // IDATCService$Stub 거래 번호(K8 automotive.dex)
    static final int T_CLIMATE_TYPE = 31, T_GET_INFO = 38, T_SUPPORT_FRONT = 64, T_SET_FRONT_CONTROL = 65,
        T_IS_AIR_PURIFIER = 71, T_AIR_CLEANING_STEP_COLOR = 61, T_TEMPERATURE_UNIT = 3;
    private static final String DATC_SERVICE = "com.hkmc.micom.datc", DATC_DESCRIPTOR = "com.hkmc.datc.IDATCService";
    private android.os.IBinder datcBinder;

    private int datcInfo(int flag) {
        int v = datcTransact(T_GET_INFO, flag);
        return v == Integer.MIN_VALUE ? -1 : v;
    }

    private int datcCall(int code) {
        int v = datcTransact(code);
        return v == Integer.MIN_VALUE ? -1 : v;
    }

    // 실시간 공조 값: 순정 공조 화면(HKMC_Climate)처럼 IDATCListener 를 서비스에 직접 붙인다(DATCManager 는 일반 앱에서 NPE).
    // onDatcStatus(3) 로 DatcStatus 가 온다(A/C·AUTO·AUTO 단계·온도·바람·풍향·내외기·켜짐·공기질).
    private static final String LISTENER_DESCRIPTOR = "com.hkmc.datc.IDATCListener";
    static final int T_REGISTER_LISTENER = 1, T_UNREGISTER_LISTENER = 2;
    private final JSONObject live = new JSONObject();
    private volatile boolean liveKnown;
    private final android.os.Binder listener = new android.os.Binder() {
        @Override protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(LISTENER_DESCRIPTOR); return true; }
            try {
                data.enforceInterface(LISTENER_DESCRIPTOR);
                if (code == 3 && data.readInt() != 0) readStatus(data);
                else if (code == 1) readTemperatures(data);
            } catch (Throwable failure) {
                Log.w(TAG, "datc listener " + code + ": " + failure);
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };
    private static final String[][] STATUS = {
        {"ac", "getAirconOnOff"}, {"auto", "getAuto"}, {"autoStep", "getAutoStep"}, {"autoText", "getMltAutoBlrTxt"},
        {"blowerAuto", "getFrontBlwAutoDisp"}, {"modeAuto", "getModeAutoDisp"}, {"blowerLevel", "getMainBlowerLevel"},
        {"modeDrive", "getModeDrive"}, {"intakeRaw", "getIntake"}, {"op", "getOperationStatus"}, {"aqi", "getAQIvalue"},
        {"sync", "getDual"}, {"driverOnly", "getDriverOnly"}, {"heat", "getHeatOnOff"}, {"ionClean", "getIonClean"},
        {"drTemp", "getDrTempC"}, {"psTemp", "getPsTempC"}, {"outTemp", "getAmbientTempC"}, {"syncDisp", "getSyncDisp"}, {"airCleanInd", "getArClnIND"}, {"aqs", "getAQS"}};

    private void readStatus(android.os.Parcel data) throws Exception {
        Class<?> type = Class.forName("com.hkmc.datc.DatcStatus");
        Object creator = type.getField("CREATOR").get(null);
        Object status = creator.getClass().getMethod("createFromParcel", android.os.Parcel.class).invoke(creator, data);
        synchronized (live) {
            for (String[] f : STATUS) {
                try { live.put(f[0], type.getMethod(f[1]).invoke(status)); } catch (Throwable ignored) { }
            }
        }
        liveKnown = true;
        Log.i(TAG, "datc status " + live);
    }

    /** onTemperatureInformation(float,int ×8): 온도 ▲▼ 는 DatcStatus 가 아니라 이것으로 온다(2026-11-27 차). */
    private void readTemperatures(android.os.Parcel data) {
        StringBuilder raw = new StringBuilder();
        float[] f = new float[8];
        int[] n = new int[8];
        for (int i = 0; i < 8; i++) { f[i] = data.readFloat(); n[i] = data.readInt(); raw.append(f[i]).append('/').append(n[i]).append(' '); }
        Log.i(TAG, "datc temps " + raw);
        synchronized (live) {
            try { live.put("drTemp", f[0]).put("psTemp", f[2]); } catch (JSONException ignored) { }
        }
        liveKnown = true;
    }

    /** 공조 리스너 붙이기(창이 뜰 때) / 떼기(닫을 때). */
    void listen(boolean on) {
        int r = datcTransactBinder(on ? T_REGISTER_LISTENER : T_UNREGISTER_LISTENER, listener);
        Log.i(TAG, (on ? "register" : "unregister") + " datc listener -> " + r);
    }

    private int datcTransactBinder(int code, android.os.IBinder binder) {
        try {
            if (datcBinder == null || !datcBinder.isBinderAlive()) {
                datcBinder = (android.os.IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, DATC_SERVICE);
            }
            if (datcBinder == null) return Integer.MIN_VALUE;
            android.os.Parcel data = android.os.Parcel.obtain(), reply = android.os.Parcel.obtain();
            try {
                data.writeInterfaceToken(DATC_DESCRIPTOR);
                data.writeStrongBinder(binder);
                datcBinder.transact(code, data, reply, 0);
                reply.readException();
                return reply.dataAvail() >= 4 ? reply.readInt() : 0;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable failure) {
            Log.w(TAG, "datc listener transact " + code + " failed: " + failure);
            return Integer.MIN_VALUE;
        }
    }

    /** 순정 서비스 바인더 직접 거래(읽기 전용 getter 용). 결과 int, 실패면 Integer.MIN_VALUE. */
    private static int serviceInt(String service, String descriptor, int code, int... args) {
        try {
            android.os.IBinder b = (android.os.IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, service);
            if (b == null) return Integer.MIN_VALUE;
            android.os.Parcel data = android.os.Parcel.obtain(), reply = android.os.Parcel.obtain();
            try {
                data.writeInterfaceToken(descriptor);
                for (int a : args) data.writeInt(a);
                b.transact(code, data, reply, 0);
                reply.readException();
                return reply.dataAvail() >= 4 ? reply.readInt() : 0;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable failure) {
            Log.w(TAG, service + " " + code + " failed: " + failure);
            return Integer.MIN_VALUE;
        }
    }

    // 앞좌석·핸들: K8 은 USM 이 -1 을 준다. 순정 음성 앱(HKMC_VoiceHandler)처럼 BCM 으로 읽는다(IBCMService$Stub).
    static final String BCM = "com.hkmc.micom.bcm", BCM_DESCRIPTOR = "com.hkmc.bcm.IBCMService";
    static final int B_DRV_OPT = 20, B_DRV_STATE = 21, B_AST_OPT = 22, B_AST_STATE = 23, B_WHEEL_OPT = 62;

    private volatile boolean frontViaBcm, wheelViaVr;

    private static int bcmInt(int code) {
        int v = serviceInt(BCM, BCM_DESCRIPTOR, code);
        return v == Integer.MIN_VALUE ? -1 : v;
    }

    // 앞좌석 열선·통풍 보내기: 순정 음성 명령 경로(IVRService set_FLSeatHeatVRcmd_Cmd_AVN 30 / FR 31, 값 = 좌석 상태 2~8).
    static final String VR = "com.hkmc.vr", VR_DESCRIPTOR = "com.hkmc.vr.IVRService";
    static final int V_FL_SEAT = 30, V_FR_SEAT = 31, V_WHEEL = 16;

    // 핸들 열선 상태: 음성 서비스 리스너(IVRListener)로 온다. All_StrgWhlHeated_Req(23) 로 물으면 답이 온다.
    private static final String VR_LISTENER = "com.hkmc.vr.IVRListener";
    private volatile int wheelState = -1;
    private volatile boolean wheelHeard;
    private final android.os.Binder vrListener = new android.os.Binder() {
        @Override protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(VR_LISTENER); return true; }
            try {
                data.enforceInterface(VR_LISTENER);
                int v = data.dataAvail() >= 4 ? data.readInt() : Integer.MIN_VALUE;
                Log.i(TAG, "vr event " + code + " = " + v);
                // 37(onExist_C_StrgWhlHeatedCmdAVN)은 "지원함" 표시라 상태가 아니다(2026-11-27 차: 1 이 와서 끄기만 보냈다).
            } catch (Throwable failure) {
                Log.w(TAG, "vr listener " + code + ": " + failure);
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };

    // BCM 알림: onRemoteTeleHeatingStatusChanged(종류, 값)(20) 로 핸들 열선 상태가 오는지 본다(텔레매틱스 TAS_BODY 가 이것으로 읽는 듯).
    private static final String BCM_LISTENER = "com.hkmc.bcm.IBCMListener";
    private final android.os.Binder bcmListener = new android.os.Binder() {
        @Override protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) {
            if (code == INTERFACE_TRANSACTION) { reply.writeString(BCM_LISTENER); return true; }
            try {
                data.enforceInterface(BCM_LISTENER);
                StringBuilder v = new StringBuilder();
                int[] n = new int[4];
                for (int i = 0; i < 4 && data.dataAvail() >= 4; i++) { n[i] = data.readInt(); v.append(n[i]).append(' '); }
                Log.i(TAG, "bcm event " + code + " = " + v);
                if (code == 20) wheelEvent(n[0], n[1]);
            } catch (Throwable failure) {
                Log.w(TAG, "bcm listener " + code + ": " + failure);
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };

    /** BCM 20 의 첫 값이 핸들 열선: 1 켜짐, 0 꺼짐(2026-11-27 차: 켜기 → "1 0", 끄기 → "0 0"). 바뀔 때만 온다. */
    private void wheelEvent(int state, int unused) {
        if (state == 0 || state == 1) wheelState = state;
        wheelHeard = true;
    }

    private static void register(String service, String descriptor, int code, android.os.IBinder listener) throws Exception {
        android.os.IBinder b = (android.os.IBinder) Class.forName("android.os.ServiceManager")
            .getMethod("getService", String.class).invoke(null, service);
        if (b == null) return;
        android.os.Parcel data = android.os.Parcel.obtain(), reply = android.os.Parcel.obtain();
        try {
            data.writeInterfaceToken(descriptor);
            data.writeStrongBinder(listener);
            b.transact(code, data, reply, 0);
            reply.readException();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    void listenVr(boolean on) {
        try { register(BCM, BCM_DESCRIPTOR, on ? 1 : 2, bcmListener); Log.i(TAG, (on ? "add" : "remove") + " bcm listener"); }
        catch (Throwable failure) { Log.w(TAG, "bcm listener failed: " + failure); }
        try {
            android.os.IBinder b = (android.os.IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, VR);
            if (b == null) return;
            android.os.Parcel data = android.os.Parcel.obtain(), reply = android.os.Parcel.obtain();
            try {
                data.writeInterfaceToken(VR_DESCRIPTOR);
                data.writeStrongBinder(vrListener);
                b.transact(on ? 1 : 2, data, reply, 0);
                reply.readException();
            } finally {
                data.recycle();
                reply.recycle();
            }
            Log.i(TAG, (on ? "add" : "remove") + " vr listener");
            if (on) vrSend(23, 1);   // All_StrgWhlHeated_Req: 핸들 열선 상태 물어보기
        } catch (Throwable failure) {
            Log.w(TAG, "vr listener " + on + " failed: " + failure);
        }
    }

    private static boolean vrSend(int code, int value) {
        boolean ok = serviceInt(VR, VR_DESCRIPTOR, code, value) != Integer.MIN_VALUE;
        Log.i(TAG, (ok ? "SENT" : "FAILED") + " vr " + code + "(" + value + ")");
        return ok;
    }

    /** 결과 int, 실패면 Integer.MIN_VALUE. */
    private int datcTransact(int code, int... args) {
        try {
            if (datcBinder == null || !datcBinder.isBinderAlive()) {
                datcBinder = (android.os.IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, DATC_SERVICE);
            }
            if (datcBinder == null) return Integer.MIN_VALUE;
            android.os.Parcel data = android.os.Parcel.obtain(), reply = android.os.Parcel.obtain();
            try {
                data.writeInterfaceToken(DATC_DESCRIPTOR);
                for (int a : args) data.writeInt(a);
                datcBinder.transact(code, data, reply, 0);
                reply.readException();
                return reply.dataAvail() >= 4 ? reply.readInt() : 0;
            } finally {
                data.recycle();
                reply.recycle();
            }
        } catch (Throwable failure) {
            Log.w(TAG, "datc " + code + " failed: " + failure);
            datcBinder = null;
            return Integer.MIN_VALUE;
        }
    }
}
