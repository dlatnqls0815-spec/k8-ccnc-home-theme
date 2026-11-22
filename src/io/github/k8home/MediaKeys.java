package io.github.k8home;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import java.io.InputStream;

/**
 * Media keys for the stock players.
 *
 * Bluetooth audio only follows the steering-wheel codes (166 previous, 167 next, 91 play/pause) sent
 * through IModeManager.generateKeyEvent, and the mode manager drops those from a normal app (no
 * INJECT_EVENTS). So the key goes to the NEMESIS helper on the abstract socket "k8home.keys" (system
 * uid, helper/KeyHelper.java) when it runs; otherwise it falls back to AudioManager, which on this head
 * unit only reaches the phone app (checked in the car on 2026-11-22: 167 via generateKeyEvent skipped
 * the Bluetooth track, the AudioManager key did not).
 */
final class MediaKeys {
    static final String SOCKET = "k8home.keys";
    static final int WHEEL_PREVIOUS = 166, WHEEL_NEXT = 167, WHEEL_PLAY_PAUSE = 91;
    /** Stock split-screen panel: closes the track popup the BT app shows on a key or track change. */
    static final String SPLIT_CONTROL_ACTION = "ACTION_SPLITSCREEN_CONTROL";
    static final String EXTRA_CANCEL_OSD = "EXTRA_SPLITSCREEN_CONTROL_CANCEL_OSD";
    private static final long[] CANCEL_OSD_DELAYS_MS = {150, 600, 1200};
    private static final String TAG = "K8HomeKeys";

    private MediaKeys() {}

    /** Sends a media key; returns true when it was handed to the helper or AudioManager. */
    static boolean dispatch(final Context context, final int keyCode) {
        final int wheel = wheelCode(keyCode);
        if (wheel < 0) return dispatchAudio(context, keyCode);
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                if (!sendToHelper(wheel)) dispatchAudio(app, keyCode);
                cancelStockPopup(app);
            }
        }, "k8home-key").start();
        return true;
    }

    static int wheelCode(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) return WHEEL_NEXT;
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) return WHEEL_PREVIOUS;
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) return WHEEL_PLAY_PAUSE;
        return -1;
    }

    private static boolean sendToHelper(int wheel) {
        return "ok".equals(askHelper(String.valueOf(wheel)));
    }

    /**
     * Asks the helper to tap the stock navigation's guidance-list button (hidden under the theme row).
     * Blocking: call off the main thread. Returns the helper's reply ("ok", "not-navi", ...) or "" without it.
     */
    static String toggleStockGuide() {
        return askHelper("guide");
    }

    private static String askHelper(String command) {
        LocalSocket socket = new LocalSocket();
        try {
            socket.connect(new LocalSocketAddress(SOCKET));
            socket.setSoTimeout(2000);
            socket.getOutputStream().write((command + "\n").getBytes("US-ASCII"));
            InputStream in = socket.getInputStream();
            byte[] buf = new byte[16];
            int n = in.read(buf);
            String reply = n > 0 ? new String(buf, 0, n, "US-ASCII").trim() : "";
            Log.i(TAG, "helper " + command + " -> " + reply);
            return reply;
        } catch (Exception missing) {
            return ""; // helper not installed or not running
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) { }
        }
    }

    private static boolean dispatchAudio(Context context, int keyCode) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return false;
        long time = SystemClock.uptimeMillis();
        try {
            audio.dispatchMediaKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, keyCode, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, keyCode, 0));
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    /**
     * Closes the stock track popup (the BT app's MusicService sends the same broadcast). It can open a
     * moment after the metadata, so the broadcast goes out a few times (same as K8 Now Playing).
     */
    static void cancelStockPopup(Context context) {
        final Context app = context.getApplicationContext();
        Handler handler = new Handler(Looper.getMainLooper());
        for (long delay : CANCEL_OSD_DELAYS_MS) {
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    app.sendBroadcast(new Intent(SPLIT_CONTROL_ACTION).putExtra(EXTRA_CANCEL_OSD, true));
                }
            }, delay);
        }
    }
}
