package io.github.k8home;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Read-only vehicle status from the firmware's automotive library, through reflection so a missing
 * class or a refused service only drops that value. Only no-argument getters that return a value are
 * called (the library returns -1 when the service is not connected). Nothing is set or requested,
 * and identity data (IMEI, phone number, SIM, digital key, modem) is never read.
 * Getter names come from automotive.odex (TMUManager, BCMManager, USMManager, ModeManager,
 * UserProfileManager); meanings of the numbers are checked in the car.
 */
final class StockVehicle {
    private static final String TAG = "K8StockVehicle";
    private static final long POLL_MS = 3000;

    /** {class, getter, json key}. */
    private static final String[][] READS = {
        {"com.hkmc.tmu.TMUManager", "getCarSpeed", "speed"},
        {"com.hkmc.tmu.TMUManager", "getEngSpeed", "rpm"},
        {"com.hkmc.tmu.TMUManager", "getFuelTankRatio", "fuel"},
        {"com.hkmc.tmu.TMUManager", "getOdometer", "odometer"},
        {"com.hkmc.tmu.TMUManager", "getTripDistance", "trip"},
        {"com.hkmc.tmu.TMUManager", "getEngineAverageFuelEconomy", "avgEconomy"},
        {"com.hkmc.tmu.TMUManager", "getFrontDropen", "doorOpen"},
        {"com.hkmc.tmu.TMUManager", "getFrontDrLock", "doorLock"},
        {"com.hkmc.tmu.TMUManager", "getLuggageLock", "trunkLock"},
        {"com.hkmc.tmu.TMUManager", "getEngineRoomOpenStatus", "hoodOpen"},
        {"com.hkmc.tmu.TMUManager", "getPrkTelltale", "parkBrakeLamp"},
        {"com.hkmc.tmu.TMUManager", "getHeatStrWheelStatus", "wheelHeat"},
        {"com.hkmc.bcm.BCMManager", "getDrvSeatHeatState", "driverSeatHeat"},
        {"com.hkmc.bcm.BCMManager", "getAstSeatHeatState", "passengerSeatHeat"},
        {"com.hkmc.bcm.BCMManager", "getDrvWdwOpenState", "driverWindow"},
        {"com.hkmc.bcm.BCMManager", "getAstWdwOpenState", "passengerWindow"},
        {"com.hkmc.bcm.BCMManager", "getRLWdwOpenState", "rearLeftWindow"},
        {"com.hkmc.bcm.BCMManager", "getRRWdwOpenState", "rearRightWindow"},
        {"com.hkmc.bcm.BCMManager", "getSunRoofOpnSta", "sunroof"},
        {"com.hkmc.bcm.BCMManager", "getTrunkStatus", "trunk"},
        {"com.hkmc.bcm.BCMManager", "getEpbStatus", "epb"},
        {"com.hkmc.bcm.BCMManager", "getEngineStatus", "engine"},
        {"com.hkmc.bcm.BCMManager", "getIGNStatus", "ignition"},
        {"com.hkmc.bcm.BCMManager", "getDriveTime", "driveTime"},
        {"com.hkmc.bcm.BCMManager", "getBatSocSegment", "batterySegment"},
        {"com.hkmc.usm.USMManager", "getDistanceSpeedUnit", "distanceUnit"},
        {"com.hkmc.usm.USMManager", "getFuelEfficiencyUnitKm", "economyUnit"},
        {"com.hkmc.mode.ModeManager", "getCurrentMode", "audioMode"},
        {"com.hkmc.mode.ModeManager", "isCallingStatus", "calling"},
        {"com.hkmc.mode.ModeManager", "isRearViewOn", "rearView"},
        {"com.hkmc.userprofile.UserProfileManager", "getUserType", "userType"},
        {"com.hkmc.userprofile.UserProfileManager", "isGuest", "guest"},
        // Stock night light (BluelightManager / BluelightConstants: mode 0 auto, 1 scheduled; isEnable 0/1).
        {"com.hkmc.bluelight.BluelightManager", "isEnable", "blueEnabled"},
        {"com.hkmc.bluelight.BluelightManager", "getMode", "blueMode"},
        {"com.hkmc.bluelight.BluelightManager", "getBluelightLevel", "blueLevel"},
        {"com.hkmc.bluelight.BluelightManager", "getStartTime", "blueStart"},
        {"com.hkmc.bluelight.BluelightManager", "getEndTime", "blueEnd"},
        // Micom vehicle info (VehicleInfoManager): P gear, tail lamp, illumination dimmer, eco coasting
        // setting, wireless charger warning (default 7 = no answer).
        {"com.hkmc.micom.VehicleInfoManager", "getGearPStatus", "gearP"},
        {"com.hkmc.micom.VehicleInfoManager", "getAvTailStatus", "tailLamp"},
        {"com.hkmc.micom.VehicleInfoManager", "getRheostatLevel", "rheostat"},
        {"com.hkmc.micom.VehicleInfoManager", "getAvailableRheostatLevel", "rheostatMax"},
        {"com.hkmc.micom.VehicleInfoManager", "getEcoCoastingStatus", "ecoCoasting"},
        {"com.hkmc.micom.VehicleInfoManager", "getWirelessChargingStatus", "wpc"},
        {"com.hkmc.power.MicomPowerManager", "isDisplayOffMode", "displayOff"},
        // Amplifier settings (MicomAudioManager plain getters only; the void get*Volume ones send requests).
        {"com.hkmc.media.MicomAudioManager", "getUserMuteState", "userMute"},
        {"com.hkmc.media.MicomAudioManager", "getRearSleepMode", "rearSleep"},
        {"com.hkmc.media.MicomAudioManager", "getActiveSoundDesign", "activeSound"},
        {"com.hkmc.media.MicomAudioManager", "getAutoAdjustingVolumeSetting", "autoVolume"},
        {"com.hkmc.media.MicomAudioManager", "getDimVolumeWhileInReverse", "reverseDim"},
        {"com.hkmc.media.MicomAudioManager", "getNaviDuringCall", "naviDuringCall"},
    };

    /** System state broadcasts (ModeManager / PgsConstants / VehicleInfoManager action names). */
    private static final String[][] EVENTS = {
        {"com.hkmc.intent.action.SYSTEM_STATE_ILLUMINATE_ON", "illuminate", "true"},
        {"com.hkmc.intent.action.SYSTEM_STATE_ILLUMINATE_OFF", "illuminate", "false"},
        {"com.hkmc.intent.action.SYSTEM_STATE_DRIVE_ON", "driving", "true"},
        {"com.hkmc.intent.action.SYSTEM_STATE_DRIVE_OFF", "driving", "false"},
        {"com.hkmc.intent.action.SYSTEM_STATE_FUELWANING_ON", "fuelLow", "true"},
        {"com.hkmc.intent.action.SYSTEM_STATE_FUELWANING_OFF", "fuelLow", "false"},
        {"com.hkmc.intent.action.VALET_ON", "valet", "true"},
        {"com.hkmc.intent.action.VALET_OFF", "valet", "false"},
    };
    private static final String WPC_CHANGED = "com.mobis.intent.action.WPC_STATUS_CHANGED";
    // Vehicle settings DriveModeHandleService: extra "DriveMode" = Dic$MODE name (ECO, SPORT, SMART...).
    private static final String DRIVE_MODE = "com.hkmc.intent.action.DRIVE_MODE_CHANGED";
    // Broadcom hands-free status the stock split screen shows: STRENGTH / LEVEL ints (-1 unknown).
    private static final String PHONE_SIGNAL = "com.broadcom.bt.hfdevice.profile.action.MOBIS_BT_SIGNAL_STRENGTH";
    private static final String PHONE_BATTERY = "com.broadcom.bt.hfdevice.profile.action.MOBIS_BT_BATTERY_LEVEL";
    private static final String PHONE_BATTERY_REQUEST = "com.mobis.intent.action.GetBatteryLevel";
    private static final String WPC_EXTRA = "com.mobis.intent.extra.WPC_STATUS";

    private final Context app;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.Map<String, Object> managers = new java.util.HashMap<String, Object>();
    private final java.util.Set<String> missing = new java.util.HashSet<String>();
    private final java.util.Set<String> warned = new java.util.HashSet<String>();
    private HandlerThread thread;
    private Handler worker;
    private volatile JSONObject latest = new JSONObject();
    private final java.util.Map<String, Object> events = new java.util.concurrent.ConcurrentHashMap<String, Object>();
    private boolean listening;

    private final android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context context, android.content.Intent intent) {
            String action = intent.getAction();
            if (WPC_CHANGED.equals(action)) {
                if (intent.hasExtra(WPC_EXTRA)) events.put("wpc", (long) intent.getIntExtra(WPC_EXTRA, 0));
            } else if (DRIVE_MODE.equals(action)) {
                Object mode = intent.getExtras() == null ? null : intent.getExtras().get("DriveMode");
                if (mode != null && mode.toString().length() <= 24) events.put("driveMode", mode.toString());
            } else if (PHONE_SIGNAL.equals(action)) {
                level("phoneSignal", intent.getIntExtra("STRENGTH", -1));
            } else if (PHONE_BATTERY.equals(action)) {
                level("phoneBattery", intent.getIntExtra("LEVEL", -1));
            } else {
                for (String[] event : EVENTS) {
                    if (event[0].equals(action)) events.put(event[1], Boolean.valueOf(event[2]));
                }
            }
            Log.i(TAG, "event " + action);
            Handler w = worker;
            if (w != null) {
                w.removeCallbacks(poll);
                w.post(poll);
            }
        }
    };

    StockVehicle(Context context, Runnable changed) {
        this.app = context.getApplicationContext();
        this.changed = changed;
    }

    private void level(String key, int value) {
        if (value >= 0) events.put(key, (long) value);
        else events.remove(key);
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            JSONObject next = new JSONObject();
            for (String[] read : READS) {
                Object value = read(read[0], read[1]);
                if (value == null) continue;
                try {
                    next.put(read[2], value);
                } catch (JSONException ignored) { }
            }
            for (java.util.Map.Entry<String, Object> event : events.entrySet()) {
                try {
                    next.put(event.getKey(), event.getValue());
                } catch (JSONException ignored) { }
            }
            if (!next.toString().equals(latest.toString())) {
                latest = next;
                Log.i(TAG, "status " + next);
                main.post(changed);
            }
            if (worker != null) worker.postDelayed(this, POLL_MS);
        }
    };

    void start() {
        if (thread != null) return;
        thread = new HandlerThread("K8StockVehicle");
        thread.start();
        worker = new Handler(thread.getLooper());
        worker.post(poll);
        android.content.IntentFilter filter = new android.content.IntentFilter(WPC_CHANGED);
        filter.addAction(DRIVE_MODE);
        filter.addAction(PHONE_SIGNAL);
        filter.addAction(PHONE_BATTERY);
        for (String[] event : EVENTS) filter.addAction(event[0]);
        try {
            app.registerReceiver(receiver, filter);
            listening = true;
            // Same request the stock split screen sends so the phone battery level is resent.
            app.sendBroadcast(new android.content.Intent(PHONE_BATTERY_REQUEST));
        } catch (RuntimeException failure) {
            Log.w(TAG, "state broadcasts unavailable", failure);
        }
    }

    void stop() {
        if (thread == null) return;
        if (listening) {
            try {
                app.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) { }
            listening = false;
        }
        worker.removeCallbacksAndMessages(null);
        worker = null;
        thread.quit();
        thread = null;
    }

    JSONObject snapshot() {
        return latest;
    }

    private Object read(String className, String getter) {
        if (missing.contains(className)) return null;
        try {
            Object manager = managers.get(className);
            if (manager == null) {
                Class<?> type = Class.forName(className);
                Constructor<?> constructor = type.getDeclaredConstructor(Context.class);
                constructor.setAccessible(true);
                try {
                    manager = constructor.newInstance(app);
                } catch (Throwable refused) {
                    missing.add(className);
                    Log.w(TAG, className + " refused: " + refused.getClass().getSimpleName());
                    return null;
                }
                managers.put(className, manager);
                Log.i(TAG, "connected " + className);
            }
            Method method = manager.getClass().getMethod(getter);
            Object value = method.invoke(manager);
            if (value instanceof Boolean) return value;
            if (value instanceof String) {
                String text = ((String) value).trim();
                return text.isEmpty() || text.length() > 24 ? null : text;
            }
            if (value instanceof Number) {
                Number number = (Number) value;
                if (value instanceof Float || value instanceof Double) {
                    double d = number.doubleValue();
                    return d < 0 || Double.isNaN(d) ? null : Math.round(d * 10) / 10.0;
                }
                long l = number.longValue();
                return l < 0 ? null : l;
            }
            return null;
        } catch (ClassNotFoundException | NoSuchMethodException failure) {
            if (failure instanceof ClassNotFoundException) missing.add(className);
            return null;
        } catch (Throwable failure) {
            if (warned.add(className + getter)) {
                Log.w(TAG, className + "." + getter + " unavailable: " + failure.getClass().getSimpleName());
            }
            return null;
        }
    }
}
