package io.github.k8home;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;

import java.util.Set;
import java.util.TreeSet;

final class StockPhone implements BluetoothProfile.ServiceListener {
    private static final String CALL = "com.hkmc.phone.widgetupdate";
    private static final String PROJECTION_CALL = "com.hkmc.carplay.call.widgetupdate";
    private static final String CONNECT_CALL = "com.hkmc.bluelink.call.widgetupdate";
    private static final String OEM_CONNECTION = "com.broadcom.bt.hfdevice.hfbtcm.action.BTCM_CONNECTION_STATE_CHANGED";
    private static final String OEM_HEADSET = "com.broadcom.bt.hfdevice.BluetoothHfDevice";
    private static final String OEM_AUDIO = "com.broadcom.bt.avrcp.BluetoothAvrcpController";

    private final Context app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable connectionChanged;
    private final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothProfile headset, a2dp, oemHeadset, oemAudio;
    private boolean started;
    private int session;

    private String callSource = "", state = "", caller = "";

    StockPhone(Context context, Runnable connectionChanged) {
        app = context.getApplicationContext();
        this.connectionChanged = connectionChanged;
    }

    static void addActions(IntentFilter filter) {
        filter.addAction(CALL);
        filter.addAction(PROJECTION_CALL);
        filter.addAction(CONNECT_CALL);
        filter.addAction(OEM_CONNECTION);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
    }

    static boolean isConnectionAction(String action) {
        return OEM_CONNECTION.equals(action) || BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)
            || BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED.equals(action);
    }

    void start() {
        if (started || adapter == null) return;
        started = true;
        try {
            adapter.getProfileProxy(app, this, BluetoothProfile.HEADSET);
            adapter.getProfileProxy(app, this, BluetoothProfile.A2DP);
        } catch (SecurityException ignored) { }
        bindOem(OEM_HEADSET, false);
        bindOem(OEM_AUDIO, true);
    }

    private void bindOem(String className, final boolean audio) {
        final int bindingSession = session;
        try {
            Class<?> api = Class.forName(className);
            api.getMethod("getProxy", Context.class, BluetoothProfile.ServiceListener.class)
                .invoke(null, app, new BluetoothProfile.ServiceListener() {
                    @Override public void onServiceConnected(int kind, final BluetoothProfile profile) {
                        handler.post(new Runnable() {
                            @Override public void run() {
                                if (!started || session != bindingSession) {
                                    closeOem(profile);
                                    return;
                                }
                                BluetoothProfile previous = audio ? oemAudio : oemHeadset;
                                if (previous != profile) closeOem(previous);
                                if (audio) oemAudio = profile;
                                else oemHeadset = profile;
                                connectionChanged.run();
                            }
                        });
                    }

                    @Override public void onServiceDisconnected(int kind) {
                        handler.post(new Runnable() {
                            @Override public void run() {
                                if (!started || session != bindingSession) return;
                                if (audio) oemAudio = null;
                                else oemHeadset = null;
                                connectionChanged.run();
                            }
                        });
                    }
                });
        } catch (Exception | LinkageError ignored) { }
    }

    private static void closeOem(BluetoothProfile profile) {
        if (profile == null) return;
        try {
            profile.getClass().getMethod("closeProxy").invoke(profile);
        } catch (Exception | LinkageError ignored) { }
    }

    void stop() {
        started = false;
        session++;
        closeOem(oemHeadset);
        closeOem(oemAudio);
        oemHeadset = oemAudio = null;
        close(BluetoothProfile.HEADSET, headset);
        close(BluetoothProfile.A2DP, a2dp);
        headset = a2dp = null;
        clearCall();
    }

    private void close(int kind, BluetoothProfile profile) {
        if (profile == null) return;
        try {
            adapter.closeProfileProxy(kind, profile);
        } catch (RuntimeException ignored) { }
    }

    @Override public void onServiceConnected(int kind, BluetoothProfile profile) {
        if (!started) {
            close(kind, profile);
            return;
        }
        if (kind == BluetoothProfile.HEADSET) {
            if (headset != profile) close(kind, headset);
            headset = profile;
        } else if (kind == BluetoothProfile.A2DP) {
            if (a2dp != profile) close(kind, a2dp);
            a2dp = profile;
        }
        connectionChanged.run();
    }

    @Override public void onServiceDisconnected(int kind) {
        if (kind == BluetoothProfile.HEADSET) headset = null;
        else if (kind == BluetoothProfile.A2DP) a2dp = null;
        if (started) connectionChanged.run();
    }

    JSONArray callDevices() { return preferredNames(oemHeadset, headset); }

    /** Address of the connected call phone (the stock phone book is kept per phone, bt_address), or "". */
    String callAddress() {
        for (BluetoothProfile profile : new BluetoothProfile[] {oemHeadset, headset}) {
            if (profile == null) continue;
            try {
                for (BluetoothDevice device : profile.getConnectedDevices()) return device.getAddress();
            } catch (RuntimeException ignored) { }
        }
        return "";
    }
    JSONArray audioDevices() { return preferredNames(oemAudio, a2dp); }

    private static JSONArray preferredNames(BluetoothProfile preferred, BluetoothProfile fallback) {
        JSONArray connected = names(preferred);
        return connected.length() > 0 ? connected : names(fallback);
    }

    private static JSONArray names(BluetoothProfile profile) {
        Set<String> names = new TreeSet<>();
        if (profile != null) {
            try {
                for (BluetoothDevice device : profile.getConnectedDevices()) {
                    String name = StockMedia.text(device.getName());
                    names.add(name.isEmpty() ? "이름 없는 기기" : name);
                }
            } catch (RuntimeException ignored) { }
        }
        return new JSONArray(names);
    }

    String callState() { return state; }
    String caller() { return caller; }
    String callSource() { return callSource; }

    void clearBluetoothCall() {
        if ("블루투스".equals(callSource)) clearCall();
    }

    private void clearCall() {
        state = caller = callSource = "";
    }

    boolean updateCall(String action, Bundle data) {
        if (data == null) return false;
        String source;
        if (CALL.equals(action)) source = "블루투스";
        else if (PROJECTION_CALL.equals(action)) source = "Apple CarPlay";
        else if (CONNECT_CALL.equals(action)) source = "Kia Connect";
        else return false;
        Object code = data.get("phone_call_state");
        if (!(code instanceof Number)) return false;

        String label;
        switch (((Number) code).intValue()) {
            case 0:
            case 3:
                if (!callSource.equals(source)) return false;
                clearCall();
                return true;
            case 1:
            case 2: label = "통화 진행 중"; break;
            case 4: label = "통화 전환 중"; break;
            case 5: label = "통화 보류"; break;
            case 6: label = "전화 수신 중"; break;
            case 9: label = "수신 통화 대기"; break;
            default: return false;
        }
        state = label;
        callSource = source;
        caller = StockMedia.text(data.get("phone_call_name"));
        if (caller.isEmpty()) caller = StockMedia.text(data.get("phone_call_number"));
        return true;
    }
}
