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
    };

    private final Context app;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final java.util.Map<String, Object> managers = new java.util.HashMap<String, Object>();
    private final java.util.Set<String> missing = new java.util.HashSet<String>();
    private final java.util.Set<String> warned = new java.util.HashSet<String>();
    private HandlerThread thread;
    private Handler worker;
    private volatile JSONObject latest = new JSONObject();

    StockVehicle(Context context, Runnable changed) {
        this.app = context.getApplicationContext();
        this.changed = changed;
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
    }

    void stop() {
        if (thread == null) return;
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
