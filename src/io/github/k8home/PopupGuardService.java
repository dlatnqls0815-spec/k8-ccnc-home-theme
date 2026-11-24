package io.github.k8home;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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
    private Object splitOsdListener;
    private CustomKeyGuard customKeyGuard;

    private static boolean isMediaOsdType(int type) {
        return type == MediaKeys.OSD_TYPE_RADIO
            || type == MediaKeys.OSD_TYPE_MEDIA
            || type == MediaKeys.OSD_TYPE_NATURESOUND;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (CustomKeyGuard.ACTION_CUSTOM_SHORTCUT.equals(action)) {
                CustomKeyGuard.handleCustomShortcutBroadcast(PopupGuardService.this, intent);
                return;
            }
            if (BT_POSITION.equals(action)) {
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
            if (StockMedia.MODE_CHANGED.equals(action)) {
                Bundle extras = intent.getExtras();
                Object raw = extras == null ? null : extras.get(StockMedia.MODE_TYPE);
                if (raw instanceof Number) {
                    String[] src = StockMedia.modeSource(((Number) raw).intValue());
                    if (src != null) {
                        MediaKeys.cancelStockPopup(PopupGuardService.this);
                        Log.i(TAG, "mode changed to " + src[0] + ": stock popup cancel window");
                    }
                }
            } else if (StockMedia.RADIO.equals(action)) {
                // 라디오 위젯 방송은 대역/주파수/방송국/프리셋 변경 직후 오므로 즉시 라디오 OSD 취소 창을 연다.
                MediaKeys.cancelStockPopup(PopupGuardService.this);
            }
            StockMedia next = media.update(action, intent.getExtras());
            if (next == media) return;
            boolean radioSrc = "fm".equals(next.source) || "am".equals(next.source) || "radio".equals(next.source);
            boolean mediaSrc = "bt".equals(next.source) || radioSrc || "usb".equals(next.source) || "nature".equals(next.source);
            if (mediaSrc && !next.title.isEmpty()
                    && (media.title.isEmpty() || !next.source.equals(media.source)
                        || !next.title.equals(media.title)
                        || (radioSrc && !next.artist.equals(media.artist)))) {
                MediaKeys.cancelStockPopup(PopupGuardService.this);
                Log.i(TAG, next.source + " changed: stock popup cancel sent");
            }
            media = next;
        }
    };

    private void registerSplitOsdListener() {
        try {
            Object mgr = MediaKeys.splitScreenManager(this);
            if (mgr == null) return;
            Class<?> listenerType = Class.forName("com.hkmc.split.manager.SplitScreenManager$SplitOsdListener");
            Object proxy = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[] { listenerType },
                new InvocationHandler() {
                    @Override public Object invoke(Object p, Method method, Object[] args) {
                        String name = method.getName();
                        if ("onOsdShow".equals(name)) {
                            // 어떤 OSD든 뜰 때 비동기 스레드에서 라디오(6)·미디어(1)·자연의 소리(10) 여부만 확인해 즉시 닫는다.
                            MediaKeys.cancelMediaOsdAsync(PopupGuardService.this);
                        } else if ("onOsdChanged".equals(name) && args != null && args.length >= 2
                                && args[0] instanceof Number && args[1] instanceof Number) {
                            int a = ((Number) args[0]).intValue();
                            int b = ((Number) args[1]).intValue();
                            Log.i(TAG, "onOsdChanged(" + a + ", " + b + ")");
                            if (isMediaOsdType(a) || isMediaOsdType(b)) {
                                MediaKeys.cancelMediaOsdAsync(PopupGuardService.this);
                                MediaKeys.cancelStockPopup(PopupGuardService.this);
                            }
                        } else if ("toString".equals(name)) {
                            return "K8HomeSplitOsdListener";
                        } else if ("hashCode".equals(name)) {
                            return System.identityHashCode(p);
                        } else if ("equals".equals(name)) {
                            return p == (args != null && args.length > 0 ? args[0] : null);
                        }
                        return null;
                    }
                });
            mgr.getClass().getMethod("addSplitOSDListener", listenerType).invoke(mgr, proxy);
            splitOsdListener = proxy;
            Log.i(TAG, "SplitOsdListener registered");
        } catch (Exception | LinkageError failure) {
            Log.w(TAG, "registerSplitOsdListener failed: " + failure);
        }
    }

    private void unregisterSplitOsdListener() {
        if (splitOsdListener == null) return;
        try {
            Object mgr = MediaKeys.splitScreenManager(this);
            if (mgr != null) {
                Class<?> listenerType = Class.forName("com.hkmc.split.manager.SplitScreenManager$SplitOsdListener");
                mgr.getClass().getMethod("removeSplitOSDListener", listenerType).invoke(mgr, splitOsdListener);
            }
        } catch (Exception | LinkageError ignored) {
        } finally {
            splitOsdListener = null;
        }
    }

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
        filter.addAction(CustomKeyGuard.ACTION_CUSTOM_SHORTCUT);
        registerReceiver(receiver, filter);
        MediaKeys.initRadioPopupGuard(this);
        registerSplitOsdListener();
        customKeyGuard = new CustomKeyGuard(this);
        customKeyGuard.start();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (customKeyGuard != null) {
            customKeyGuard.stop();
            customKeyGuard = null;
        }
        unregisterSplitOsdListener();
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
