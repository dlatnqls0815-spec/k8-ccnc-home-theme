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
    /**
     * 순정 팝업(RightSideOsd)은 곡 넘기기 키 뒤 1~1.5초, 곡 정보가 바뀐 뒤 0.1~1초 사이에 뜨고, 떠 있지 않을 때 받은 취소는 버린다.
     * 그래서 신호(키·위치 방송·곡 제목 방송)가 오면 2.5초 동안 40ms 마다 취소를 보낸다(겹치면 창만 늘린다).
     * 끄는 공식 스위치는 없다(BTAudio·RightScreenHome 확인: 내비 전체 화면이면 늘 띄움). 실차 2026-11-23.
     */
    private static final long CANCEL_WINDOW_MS = 2500, CANCEL_EVERY_MS = 40;
    private static long cancelUntil;
    private static boolean cancelling;
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

    /** 순정 내비 하위 화면에서 뒤로가기 키를 NEMESIS 도우미로 전달한다(메인 스레드 외에서 호출). */
    static String sendBack() {
        return askHelper("back");
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

    /** Closes the stock track popup: sends the BT app's own cancel broadcast every 40 ms for the next 2.5 s. */
    static void cancelStockPopup(Context context) {
        final Context app = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override public void run() {
                cancelUntil = Math.max(cancelUntil, SystemClock.uptimeMillis() + CANCEL_WINDOW_MS);
                if (cancelling) return;
                cancelling = true;
                handler.post(new Runnable() {
                    @Override public void run() {
                        app.sendBroadcast(new Intent(SPLIT_CONTROL_ACTION).putExtra(EXTRA_CANCEL_OSD, true));
                        if (SystemClock.uptimeMillis() < cancelUntil) handler.postDelayed(this, CANCEL_EVERY_MS);
                        else cancelling = false;
                    }
                });
            }
        });
    }
}
