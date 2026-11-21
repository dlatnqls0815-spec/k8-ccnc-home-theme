package io.github.k8home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Stock weather (Kia Connect) through the same request the navigation uses: the navi broadcasts
 * request_weather_from_navi (NativeCall VIT_REQ_WEATHER_INFO) and the weather side answers with
 * response_weather_to_navi (NaviETCReceiver): navi_result (boolean), navi_temp (double),
 * navi_weathertype, navi_sunrise / navi_sunset (text), navi_precipitation (int).
 */
final class StockWeather {
    private static final String TAG = "K8StockWeather";
    static final String REQUEST = "com.hkmc.intent.action.request_weather_from_navi";
    static final String RESPONSE = "com.hkmc.intent.action.response_weather_to_navi";
    private static final String EXTRA = "com.hkmc.extras.weather.navi_";
    private static final long REFRESH_MS = 20 * 60 * 1000;

    private final Context app;
    private final Runnable changed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private JSONObject latest = new JSONObject();
    private boolean started;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            Bundle data = intent.getExtras();
            if (data == null) return;
            JSONObject next = new JSONObject();
            try {
                Object result = data.get(EXTRA + "result");
                next.put("ok", result instanceof Boolean ? (Boolean) result : !"0".equals(String.valueOf(result)));
                put(next, "temp", data.get(EXTRA + "temp"));
                put(next, "type", data.get(EXTRA + "weathertype"));
                put(next, "sunrise", data.get(EXTRA + "sunrise"));
                put(next, "sunset", data.get(EXTRA + "sunset"));
                put(next, "rain", data.get(EXTRA + "precipitation"));
                next.put("at", System.currentTimeMillis());
            } catch (JSONException ignored) { }
            Log.i(TAG, "weather " + next + " keys=" + data.keySet());
            latest = next;
            changed.run();
        }
    };

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!started) return;
            try {
                app.sendBroadcast(new Intent(REQUEST));
            } catch (RuntimeException failure) {
                Log.w(TAG, "weather request refused", failure);
            }
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    StockWeather(Context context, Runnable changed) {
        this.app = context.getApplicationContext();
        this.changed = changed;
    }

    void start() {
        if (started) return;
        started = true;
        app.registerReceiver(receiver, new IntentFilter(RESPONSE));
        handler.post(refresh);
    }

    void stop() {
        if (!started) return;
        started = false;
        handler.removeCallbacks(refresh);
        try {
            app.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) { }
    }

    JSONObject snapshot() {
        return latest;
    }

    private static void put(JSONObject target, String key, Object value) throws JSONException {
        if (value instanceof Number || value instanceof Boolean) target.put(key, value);
        else if (value != null && !value.toString().trim().isEmpty()) target.put(key, value.toString().trim());
    }
}
