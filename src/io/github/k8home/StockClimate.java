package io.github.k8home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hkmc.datc.DATCListener;
import com.hkmc.datc.DATCManager;
import com.hkmc.datc.DatcStatus;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Read-only climate state from the firmware's automotive library (com.hkmc.datc).
 * Nothing here sends a climate command. Class references stay inside {@link Binding} so the
 * theme still loads when the optional library is missing or the service refuses a normal app.
 */
final class StockClimate {
    private static final String TAG = "K8StockClimate";
    // DATCFlag ids (automotive.odex com/hkmc/datc/DATCFlag).
    private static final int FLAG_BLOWER_LEVEL = 0x5, FLAG_BLOWER_MODE = 0x6, FLAG_AUTO = 0x8, FLAG_AC = 0x9,
        FLAG_INTAKE = 0xb, FLAG_OP_STATE = 0xc;

    private final Context app;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Binding binding;
    private float driver = Float.NaN, passenger = Float.NaN, outside = Float.NaN;
    private int unit = -1, ac = -1, auto = -1, blower = -1, mode = -1, intake = -1, power = -1;
    private String failure = "";

    StockClimate(Context context, Runnable changed) {
        this.app = context.getApplicationContext();
        this.changed = changed;
    }

    void start() {
        if (binding != null) return;
        try {
            binding = new Binding(this);
            failure = "";
        } catch (Throwable error) {
            binding = null;
            failure = error.getClass().getSimpleName();
            Log.w(TAG, "climate service unavailable", error);
        }
        changed.run();
    }

    void stop() {
        if (binding == null) return;
        try {
            binding.close();
        } catch (Throwable error) {
            Log.w(TAG, "climate listener removal failed", error);
        }
        binding = null;
    }

    JSONObject snapshot() {
        JSONObject info = new JSONObject();
        try {
            if (temperature(driver)) info.put("driver", driver);
            if (temperature(passenger)) info.put("passenger", passenger);
            if (temperature(outside) || outside == 0f && temperature(driver)) info.put("outside", outside);
            if (unit >= 0) info.put("unit", unit);
            if (ac >= 0) info.put("ac", ac);
            if (auto >= 0) info.put("auto", auto);
            if (blower >= 0) info.put("blower", blower);
            if (mode >= 0) info.put("mode", mode);
            if (intake >= 0) info.put("intake", intake);
            if (power >= 0) info.put("power", power);
            if (!failure.isEmpty()) info.put("failure", failure);
        } catch (JSONException ignored) { }
        return info;
    }

    private static boolean temperature(float value) {
        return !Float.isNaN(value) && value > -60f && value < 120f && value != 0f;
    }

    private void post(final Runnable update) {
        main.post(new Runnable() {
            @Override public void run() {
                update.run();
                changed.run();
            }
        });
    }

    private static final class Binding {
        private final DATCManager manager;
        private final Receiver receiver;

        Binding(StockClimate owner) {
            manager = new DATCManager(owner.app);
            readFlags(owner, manager);
            receiver = new Receiver(owner);
            manager.addDATCListener(receiver);
            Log.i(TAG, "climate listener added");
        }

        void close() {
            manager.removeDATCListener(receiver);
        }

        private static void readFlags(StockClimate owner, DATCManager manager) {
            owner.ac = flag(manager, FLAG_AC, owner.ac);
            owner.auto = flag(manager, FLAG_AUTO, owner.auto);
            owner.blower = flag(manager, FLAG_BLOWER_LEVEL, owner.blower);
            owner.mode = flag(manager, FLAG_BLOWER_MODE, owner.mode);
            owner.intake = flag(manager, FLAG_INTAKE, owner.intake);
            owner.power = flag(manager, FLAG_OP_STATE, owner.power);
            try {
                int value = manager.getTemperatureUnit();
                if (value >= 0) owner.unit = value;
            } catch (RuntimeException ignored) { }
        }

        private static int flag(DATCManager manager, int flag, int fallback) {
            try {
                int value = manager.getClimateInfo(flag);
                return value >= 0 && value != 0xff ? value : fallback;
            } catch (RuntimeException ignored) {
                return fallback;
            }
        }

    }

    private static final class Receiver extends DATCListener {
        private final StockClimate owner;

        Receiver(StockClimate owner) {
            this.owner = owner;
        }

        @Override public void onDatcStatus(final DatcStatus status) {
            if (status == null) return;
            final float dr = status.getDrTempC(), ps = status.getPsTempC(), amb = status.getAmbientTempC();
            final int tempUnit = status.getTempUnit(), aircon = status.getAirconOnOff(), automatic = status.getAuto(),
                level = status.getMainBlowerLevel(), vent = status.getModeDrive(), air = status.getIntake(),
                state = status.getOperationStatus();
            Log.i(TAG, "status dr=" + dr + " ps=" + ps + " amb=" + amb + " unit=" + tempUnit + " ac=" + aircon
                + " auto=" + automatic + " blower=" + level + " mode=" + vent + " intake=" + air + " op=" + state);
            owner.post(new Runnable() {
                @Override public void run() {
                    owner.driver = dr;
                    owner.passenger = ps;
                    owner.outside = amb;
                    if (tempUnit >= 0) owner.unit = tempUnit;
                    if (aircon >= 0) owner.ac = aircon;
                    if (automatic >= 0) owner.auto = automatic;
                    if (level >= 0) owner.blower = level;
                    if (vent >= 0) owner.mode = vent;
                    if (air >= 0) owner.intake = air;
                    if (state >= 0) owner.power = state;
                }
            });
        }

        @Override public void onTemperatureInformation(float dr, float ps, float amb, int tempUnit) {
            temperatures(dr, ps, amb, tempUnit);
        }

        @Override public void onTemperatureInformation(float dr, int drState, float ps, int psState,
                float amb, int ambState, int tempUnit) {
            temperatures(dr, ps, amb, tempUnit);
        }

        private void temperatures(final float dr, final float ps, final float amb, final int tempUnit) {
            Log.i(TAG, "temperature dr=" + dr + " ps=" + ps + " amb=" + amb + " unit=" + tempUnit);
            owner.post(new Runnable() {
                @Override public void run() {
                    owner.driver = dr;
                    owner.passenger = ps;
                    owner.outside = amb;
                    if (tempUnit >= 0) owner.unit = tempUnit;
                }
            });
        }
    }
}
