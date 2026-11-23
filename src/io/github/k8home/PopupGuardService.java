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
 * service, this small sticky service listens all the time and sends the stock cancel broadcast.
 * 처음 받은 제목도 닫는다: 재부팅 뒤 첫 곡 넘기기 때 팝업이 4초 동안 남았다(실차 2026-11-23, 앞 제목을 몰라 건너뜀).
 * 팝업이 없을 때의 취소는 순정이 그냥 버린다.
 */
public final class PopupGuardService extends Service {
    private static final String TAG = "K8HomePopup";
    private StockMedia media = StockMedia.EMPTY;
    /** 블루투스 서비스의 재생 위치 방송(1초마다). 곡이 바뀌면 위치 -1 이거나 길이가 바뀌거나 위치가 처음으로 돌아간다. */
    static final String BT_POSITION = "com.broadcom.bt.avrcp.profile.action.CURRENT_PLAY_POSITION";
    private long lastPos = Long.MIN_VALUE, lastLen = Long.MIN_VALUE;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (BT_POSITION.equals(intent.getAction())) {
                long pos = number(intent, "POSITION"), len = number(intent, "LENGTH");
                boolean changed = pos < 0 || (lastLen > 0 && len > 0 && len != lastLen)
                    || (lastPos > 5000 && pos >= 0 && pos + 3000 < lastPos);
                if (changed && lastPos != Long.MIN_VALUE) {
                    MediaKeys.cancelStockPopup(PopupGuardService.this);
                    Log.i(TAG, "track position reset: stock popup cancel window");
                }
                if (pos >= 0) lastPos = pos;
                if (len > 0) lastLen = len;
                return;
            }
            StockMedia next = media.update(intent.getAction(), intent.getExtras());
            if (next == media) return;
            if ("bt".equals(next.source) && !next.title.isEmpty()
                    && (media.title.isEmpty() || !"bt".equals(media.source) || !next.title.equals(media.title))) {
                MediaKeys.cancelStockPopup(PopupGuardService.this);
                Log.i(TAG, "track changed: stock popup cancel sent");
            }
            media = next;
        }
    };

    private static long number(Intent intent, String key) {
        Object v = intent.getExtras() == null ? null : intent.getExtras().get(key);
        return v instanceof Number ? ((Number) v).longValue() : Long.MIN_VALUE;
    }

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
        filter.addAction(BT_POSITION);
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
