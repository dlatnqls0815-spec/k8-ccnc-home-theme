package io.github.k8home;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import java.io.InputStream;
import java.lang.reflect.Method;

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
     * 순정 팝업(RightSideOsd)은 곡/방송 전환 직후 0.1~1.5초 사이에 뜨고, 떠 있지 않을 때 받은 취소는 버린다.
     * - 블루투스 뷰(SplitScreenViewBTAudio)는 ACTION_SPLITSCREEN_CONTROL 브로드캐스트로 닫힌다.
     * - 라디오 뷰(SplitScreenViewRadioKR)는 위 브로드캐스트를 받지 않고, SplitScreenHome.cancelSSOsdByType도
     *   순정 핸들러가 빈 구현(no-op)이다. 순정 라디오 앱(RadioAppService)이 직접 쓰는
     *   SplitRadioManager.setRadioRightNotification(false)만 라디오 RightSideOsd를 닫는다.
     *   이 호출은 라디오 뷰 전용이라 공조·전화·드라이브모드 등 다른 OSD를 건드리지 않는다.
     */
    static final int OSD_TYPE_MEDIA = 1;
    static final int OSD_TYPE_RADIO = 6;
    static final int OSD_TYPE_NATURESOUND = 10;
    private static final long CANCEL_WINDOW_MS = 2500, CANCEL_EVERY_MS = 35;
    private static long cancelUntil;
    private static boolean cancelling;
    private static Handler osdHandler;
    private static boolean splitInitTried;
    private static Object splitManager;
    private static Method isOsdShowingMethod;
    private static boolean radioSplitInitTried;
    private static Object splitRadioManager;
    private static Method setRadioRightNotiMethod;
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

    /** Opens the connected phone projection screen ("aa" or "carplay") through the system-uid helper; blocking. */
    static String openProjection(String which) {
        return askHelper(which);
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

    private static synchronized Handler osdWorker() {
        if (osdHandler == null) {
            HandlerThread thread = new HandlerThread("k8home-osd");
            thread.start();
            osdHandler = new Handler(thread.getLooper());
        }
        return osdHandler;
    }

    /** 순정 SplitScreenManager 인스턴스(OSD 감시용)를 1회만 준비한다. */
    static synchronized Object splitScreenManager(Context context) {
        if (splitInitTried) return splitManager;
        splitInitTried = true;
        try {
            Class<?> cls = Class.forName("com.hkmc.split.manager.SplitScreenManager");
            Object mgr = cls.getMethod("getInstance", Context.class).invoke(null, context.getApplicationContext());
            if (mgr != null) {
                isOsdShowingMethod = cls.getMethod("isSSOsdShowing", int.class);
                splitManager = mgr;
                Log.i(TAG, "SplitScreenManager ready for OSD guard");
            }
        } catch (Exception | LinkageError failure) {
            Log.w(TAG, "SplitScreenManager unavailable: " + failure);
        }
        return splitManager;
    }

    /** 순정 SplitRadioManager를 메인 스레드에서 1회 생성·바인드(initBinding)한다. */
    static void initRadioPopupGuard(Context context) {
        final Context app = context.getApplicationContext();
        Runnable init = new Runnable() {
            @Override public void run() {
                synchronized (MediaKeys.class) {
                    if (radioSplitInitTried) return;
                    radioSplitInitTried = true;
                    try {
                        Class<?> cls = Class.forName("com.hkmc.split.radio.SplitRadioManager");
                        Object mgr = cls.getConstructor(Context.class).newInstance(app);
                        cls.getMethod("initBinding").invoke(mgr);
                        setRadioRightNotiMethod = cls.getMethod("setRadioRightNotification", boolean.class);
                        splitRadioManager = mgr;
                        Log.i(TAG, "SplitRadioManager bound for radio OSD guard");
                    } catch (Exception | LinkageError failure) {
                        Log.w(TAG, "SplitRadioManager unavailable: " + failure);
                    }
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) init.run();
        else new Handler(Looper.getMainLooper()).post(init);
    }

    /** 라디오 전용 오른쪽 팝업(SplitScreenViewRadioKR)을 즉시 닫는다. 다른 OSD는 건드리지 않는다. */
    static boolean cancelRadioOsdNow(Context context) {
        if (!radioSplitInitTried) initRadioPopupGuard(context);
        Object mgr = splitRadioManager;
        Method method = setRadioRightNotiMethod;
        if (mgr == null || method == null) return false;
        try {
            method.invoke(mgr, Boolean.FALSE);
            return true;
        } catch (Exception failure) {
            Log.w(TAG, "setRadioRightNotification(false) failed: " + failure);
            return false;
        }
    }

    /** 순정 홈이 "0쪽(미디어 위젯 쪽)을 보여 준다"는 방송. 순정 앱·SplitScreen 이 이것을 보고 미디어 팝업을 띄우지 않거나 닫는다. */
    static Intent homePageZero() {
        return new Intent("com.android.launcher3.PAGE_CHANGED").putExtra("com.android.launcher3.current_page", 0);
    }

    /** 비동기 스레드에서 라디오·블루투스 팝업을 즉시 닫는다. */
    static void cancelMediaOsdAsync(Context context) {
        final Context app = context.getApplicationContext();
        Runnable check = new Runnable() {
            @Override public void run() {
                boolean radioShowing = false;
                boolean mediaShowing = false;
                Object mgr = splitScreenManager(app);
                // 자연의 소리는 먼저 보고 바로 닫는다(다른 확인 2~3번 뒤에 닫으면 팝업이 잠깐 보였다, 실차 2026-11-25).
                if (natureOsdShowing(mgr)) {
                    app.sendBroadcast(homePageZero());
                    Log.i(TAG, "nature OSD showing -> home page 0");
                    return;
                }
                if (mgr != null && isOsdShowingMethod != null) {
                    try {
                        radioShowing = Boolean.TRUE.equals(isOsdShowingMethod.invoke(mgr, OSD_TYPE_RADIO));
                        mediaShowing = Boolean.TRUE.equals(isOsdShowingMethod.invoke(mgr, OSD_TYPE_MEDIA));
                    } catch (Exception ignored) { }
                }
                // setRadioRightNotification(false)는 라디오 뷰 전용이라 다른 OSD(공조·전화 등)에는 영향이 없다.
                cancelRadioOsdNow(app);
                if (radioShowing) {
                    Log.i(TAG, "radio OSD showing -> setRadioRightNotification(false)");
                }
                if (mediaShowing) {
                    app.sendBroadcast(new Intent(SPLIT_CONTROL_ACTION).putExtra(EXTRA_CANCEL_OSD, true));
                }
                if (radioShowing || mediaShowing) {
                    // 순정 SplitScreen 은 홈 0쪽 방송을 받으면 떠 있는 미디어 팝업을 닫는다("current page == 0 -> SS OSD canceled",
                    // RightScreenHome, 2026-11-24 실차: 자연의 소리 팝업이 이것으로 닫힘). 자연의 소리는 다른 닫기 신호를 받지 않는다.
                    app.sendBroadcast(homePageZero());
                    Log.i(TAG, "media OSD showing (radio=" + radioShowing + ") -> home page 0");
                }
            }
        };
        osdWorker().post(check);
        // onOsdShow 가 '보이는 중' 표시보다 먼저 올 수 있어 잠시 뒤 한 번 더 본다. shortcut: 150ms 는 어림값, 차 로그로 재기.
        osdWorker().postDelayed(new Runnable() {
            @Override public void run() {
                if (natureOsdShowing(splitScreenManager(app))) {
                    app.sendBroadcast(homePageZero());
                    Log.i(TAG, "nature OSD showing (recheck) -> home page 0");
                }
            }
        }, 150);
    }

    private static boolean natureOsdShowing(Object mgr) {
        try {
            return mgr != null && isOsdShowingMethod != null
                && Boolean.TRUE.equals(isOsdShowingMethod.invoke(mgr, OSD_TYPE_NATURESOUND));
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Closes the stock BT/radio popup: sends BT cancel broadcast and SplitRadioManager.setRadioRightNotification(false). */
    static void cancelStockPopup(Context context) {
        final Context app = context.getApplicationContext();
        if (!radioSplitInitTried) initRadioPopupGuard(app);
        final Handler worker = osdWorker();
        worker.post(new Runnable() {
            @Override public void run() {
                cancelUntil = Math.max(cancelUntil, SystemClock.uptimeMillis() + CANCEL_WINDOW_MS);
                if (cancelling) return;
                cancelling = true;
                worker.post(new Runnable() {
                    private long lastPageZero;
                    @Override public void run() {
                        app.sendBroadcast(new Intent(SPLIT_CONTROL_ACTION).putExtra(EXTRA_CANCEL_OSD, true));
                        cancelRadioOsdNow(app);
                        // 자연의 소리·라디오 팝업은 홈 0쪽 방송으로만 닫힌다. 팝업 알림이 안 올 때도 있어(2026-11-24 실차)
                        // 창이 열린 동안 0.25초마다 보내 팝업이 0.25초 넘게 남지 않게 한다.
                        long now = SystemClock.uptimeMillis();
                        if (now - lastPageZero >= 250) { lastPageZero = now; app.sendBroadcast(homePageZero()); }
                        if (SystemClock.uptimeMillis() < cancelUntil) worker.postDelayed(this, CANCEL_EVERY_MS);
                        else cancelling = false;
                    }
                });
            }
        });
    }
}
