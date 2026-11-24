package io.github.k8home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayDeque;

/** Sends only the media commands used by the stock widgets. */
final class StockTransport {
    private static final String TAG = "K8Transport";
    private static final String ACTION = "android.intent.action.REMOTE_";
    private static final String API = "com.hkmc.remote.media.IRemote";
    private static final String NATURE_DIRECT_ACTION = "android.intent.action.NATURE_SOUND_REMOTE_SERVICE";
    private static final String NATURE_DIRECT_COMPONENT = "com.hkmc.av.app.naturesound/.service.NatureSoundService";
    private static final String NATURE_DIRECT_DESCRIPTOR =
        "com.hkmc.av.app.naturesound.remoteservice.aidl.INatureSoundServiceListener";
    // HKMC_NatureSound.odex 분석: INatureSoundServiceListener.u(int index) -> FilePlayerManager.playByIndex(index)
    private static final int NATURE_TX_PLAY_BY_INDEX = 19;
    private static final long CONNECT_TIMEOUT_MS = 1500;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Integer> pending = new ArrayDeque<>();
    private Remote remote;
    private NatureDirect natureDirect;
    private int pendingNatureIndex = -1;

    StockTransport(Context context) {
        this.context = context;
    }

    /** 자연의 소리 특정 트랙(0~9)을 하나씩 넘기지 않고 순정 원격 서비스로 즉시 재생한다. */
    boolean selectNature(int index) {
        if (index < 0 || index > 20) return false;
        pendingNatureIndex = index;
        if (natureDirect != null) {
            if (natureDirect.binder != null) return natureDirect.flush();
            return true;
        }
        try {
            NatureDirect conn = new NatureDirect();
            Intent intent = new Intent(NATURE_DIRECT_ACTION)
                .setComponent(ComponentName.unflattenFromString(NATURE_DIRECT_COMPONENT));
            if (!context.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
                pendingNatureIndex = -1;
                return false;
            }
            natureDirect = conn;
            handler.postDelayed(conn.timeout, CONNECT_TIMEOUT_MS);
            return true;
        } catch (RuntimeException failure) {
            Log.w(TAG, "selectNature bind failed", failure);
            pendingNatureIndex = -1;
            return false;
        }
    }

    void closeNatureDirect() {
        pendingNatureIndex = -1;
        if (natureDirect == null) return;
        NatureDirect conn = natureDirect;
        natureDirect = null;
        handler.removeCallbacks(conn.timeout);
        try {
            context.unbindService(conn);
        } catch (IllegalArgumentException ignored) { }
    }

    boolean send(String source, int key) {
        boolean radio = "am".equals(source) || "fm".equals(source) || "radio".equals(source);
        String mode;
        String action;
        String component;
        int code;
        if (radio) {
            if (key == KeyEvent.KEYCODE_MEDIA_NEXT) code = 5;
            else if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) code = 6;
            else return false;
            mode = "Radio";
            action = ACTION + "RADIO_SERVICE";
            component = "com.hkmc.av.app.radioge/.services.RadioAppService";
        } else if ("usb".equals(source) || "nature".equals(source)) {
            if (key == KeyEvent.KEYCODE_MEDIA_NEXT) code = 1;
            else if (key == KeyEvent.KEYCODE_MEDIA_PREVIOUS) code = 2;
            else if (key == KeyEvent.KEYCODE_MEDIA_PLAY || key == KeyEvent.KEYCODE_MEDIA_PAUSE
                    || key == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) code = 3;
            else return false;
            boolean usb = "usb".equals(source);
            mode = usb ? "Music" : "NatureSound";
            action = ACTION + (usb ? "MUSIC_SERVICE" : "NATURE_SOUND_SERVICE");
            component = usb ? "com.daudio.av.app.usbmusic/.service.MusicService"
                : "com.hkmc.av.app.naturesound/.service.NatureSoundService";
        } else {
            return false;
        }

        if (remote != null && remote.mode.equals(mode)) {
            if (pending.size() < 12) pending.addLast(code);
            remote.flush();
            return true;
        }
        close();
        try {
            Class<?> api = Class.forName(API + mode + "Service");
            Class<?> stub = Class.forName(api.getName() + "$Stub");
            Remote connection = new Remote(mode, stub.getMethod("asInterface", IBinder.class),
                api.getMethod("sendCommand", int.class, int.class));
            Intent intent = new Intent(action).setComponent(ComponentName.unflattenFromString(component));
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) return false;
            remote = connection;
            pending.addLast(code);
            handler.postDelayed(connection.timeout, CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception | LinkageError failure) {
            return false;
        }
    }

    void close() {
        closeNatureDirect();
        pending.clear();
        if (remote == null) return;
        Remote connection = remote;
        remote = null;
        handler.removeCallbacks(connection.timeout);
        try {
            context.unbindService(connection);
        } catch (IllegalArgumentException ignored) { }
    }

    private final class NatureDirect implements ServiceConnection {
        IBinder binder;
        final Runnable timeout = new Runnable() {
            @Override public void run() {
                if (natureDirect == NatureDirect.this) {
                    Log.w(TAG, "selectNature bind timeout");
                    closeNatureDirect();
                }
            }
        };

        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            if (natureDirect != this) return;
            handler.removeCallbacks(timeout);
            binder = service;
            flush();
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            if (natureDirect == this) closeNatureDirect();
        }

        boolean flush() {
            if (binder == null || pendingNatureIndex < 0) return false;
            int idx = pendingNatureIndex;
            pendingNatureIndex = -1;
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(NATURE_DIRECT_DESCRIPTOR);
                data.writeInt(idx);
                boolean ok = binder.transact(NATURE_TX_PLAY_BY_INDEX, data, reply, 0);
                reply.readException();
                Log.i(TAG, "selectNature direct index=" + idx + " ok=" + ok);
                return ok;
            } catch (Exception failure) {
                Log.w(TAG, "selectNature transact failed", failure);
                closeNatureDirect();
                return false;
            } finally {
                data.recycle();
                reply.recycle();
            }
        }
    }

    private final class Remote implements ServiceConnection {
        final String mode;
        final Method asInterface, sendCommand;
        Object service;
        final Runnable timeout = new Runnable() {
            @Override public void run() {
                if (remote == Remote.this) unavailable();
            }
        };

        Remote(String mode, Method asInterface, Method sendCommand) {
            this.mode = mode;
            this.asInterface = asInterface;
            this.sendCommand = sendCommand;
        }

        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (remote != this) return;
            handler.removeCallbacks(timeout);
            try {
                service = asInterface.invoke(null, binder);
                if (service == null) unavailable();
                else flush();
            } catch (Exception | LinkageError failure) {
                unavailable();
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            if (remote == this) close();
        }

        void flush() {
            if (service == null) return;
            try {
                while (!pending.isEmpty()) sendCommand.invoke(service, pending.removeFirst(), 0);
            } catch (Exception | LinkageError failure) {
                unavailable();
            }
        }
    }

    private void unavailable() {
        close();
        Toast.makeText(context, "순정 미디어 제어를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show();
    }
}
