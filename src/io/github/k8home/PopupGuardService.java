package io.github.k8home;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

/**
 * Keeps the stock Bluetooth track popup closed whenever the track changes, not only while the theme is open.
 *
 * The stock BT app pops a track card over the right split screen on every track change (steering-wheel keys
 * included). The theme's own listeners live only while the home screen or the theme's map session is up, so
 * the popup came back on the plain stock navigation (car test 0.5.0-test19). Like K8 Now Playing's overlay
 * service, this small sticky service listens all the time and sends the stock cancel broadcast. The first
 * title after start is only remembered, so a reboot or service restart does not close a popup the user wants.
 */
public final class PopupGuardService extends Service {
    private static final String TAG = "K8HomePopup";
    private StockMedia media = StockMedia.EMPTY;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            StockMedia next = media.update(intent.getAction(), intent.getExtras());
            if (next == media) return;
            if ("bt".equals(next.source) && !next.title.isEmpty() && !media.title.isEmpty()
                    && "bt".equals(media.source) && !next.title.equals(media.title)) {
                MediaKeys.cancelStockPopup(PopupGuardService.this);
                Log.i(TAG, "track changed: stock popup cancel sent");
            }
            media = next;
        }
    };

    /** Starts the guard; safe to call often. */
    static void start(Context context) {
        try {
            context.startService(new Intent(context, PopupGuardService.class));
        } catch (RuntimeException failure) {
            Log.w(TAG, "could not start popup guard", failure);
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        IntentFilter filter = new IntentFilter();
        StockMedia.addActions(filter);
        registerReceiver(receiver, filter);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
