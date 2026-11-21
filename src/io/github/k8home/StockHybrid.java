package io.github.k8home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseIntArray;

import com.hkmc.hev.HEVListener;
import com.hkmc.hev.HEVManager;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Read-only hybrid state from the firmware's automotive library (com.hkmc.hev.HEVManager).
 * Only cached getters and the status listener are used; no request or setter is sent.
 * Flag ids come from HEVManager$HEVFlag in automotive.odex. The system mode index is the one the
 * stock split screen draws as an energy flow (RightScreenHome z/a UD table, see docs).
 */
final class StockHybrid {
    private static final String TAG = "K8StockHybrid";
    private static final int[] FLAGS = {
        0x6,  // HEV_SYSTEM_MODE: energy flow state 0..11
        0x19, // DISPLAY_SOC: battery display level
        0x5,  // ELECTRIC_MOTOR_USAGE
        0x1,  // AVG_FCU
        0x2,  // AVG_FCI
        0x8,  // ECO_LEVEL
        0x1a, // HEV_RDY
        0xa,  // GAS_DTE
        0xb,  // TOTAL_DTE
    };
    private static final String[] KEYS = {
        "mode", "soc", "motorUse", "avgFuel", "instFuel", "eco", "ready", "gasRange", "totalRange"
    };

    private final Context app;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final SparseIntArray values = new SparseIntArray();
    private Binding binding;
    private String failure = "";

    StockHybrid(Context context, Runnable changed) {
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
            Log.w(TAG, "hybrid service unavailable", error);
        }
        changed.run();
    }

    void stop() {
        if (binding == null) return;
        try {
            binding.close();
        } catch (Throwable error) {
            Log.w(TAG, "hybrid listener removal failed", error);
        }
        binding = null;
    }

    JSONObject snapshot() {
        JSONObject info = new JSONObject();
        try {
            for (int i = 0; i < FLAGS.length; i++) {
                int value = values.get(FLAGS[i], -1);
                if (value >= 0) info.put(KEYS[i], value);
            }
            if (!failure.isEmpty()) info.put("failure", failure);
        } catch (JSONException ignored) { }
        return info;
    }

    private void set(final int flag, final int value) {
        main.post(new Runnable() {
            @Override public void run() {
                if (value < 0 || values.get(flag, -1) == value) return;
                values.put(flag, value);
                changed.run();
            }
        });
    }

    private static boolean known(int flag) {
        for (int each : FLAGS) if (each == flag) return true;
        return false;
    }

    private static final class Binding {
        private final HEVManager manager;
        private final Receiver receiver;

        Binding(StockHybrid owner) {
            manager = new HEVManager(owner.app);
            for (int flag : FLAGS) {
                try {
                    int value = manager.getHEVStatus(flag);
                    if (value >= 0) owner.values.put(flag, value);
                } catch (RuntimeException ignored) { }
            }
            receiver = new Receiver(owner);
            manager.addListener(receiver);
            Log.i(TAG, "hybrid listener added " + owner.snapshot());
        }

        void close() {
            manager.removeListener(receiver);
        }
    }

    private static final class Receiver extends HEVListener {
        private final StockHybrid owner;

        Receiver(StockHybrid owner) {
            this.owner = owner;
        }

        @Override public void onHEVStatusChanged(int flag, int value) {
            if (!known(flag)) return;
            Log.i(TAG, "status flag=0x" + Integer.toHexString(flag) + " value=" + value);
            owner.set(flag, value);
        }
    }
}
