package io.github.k8home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import com.hkmc.mode.ModeKeyListener;
import com.hkmc.mode.ModeManager;

/**
 * 즐겨찾기(☆) 물리 버튼(KEYCODE_SHORTCUT_CUSTOM = 279) 처리기.
 * 테마 설정(ThemePrefs.customKeyHome)이 켜져 있을 때만 동작한다:
 * - 짧게 누르기 (< 0.55초) -> ccNC 테마 홈 열기
 * - 길게 누르기 (>= 0.55초 또는 FLAG_LONG_PRESS) -> 순정 5W 홈(com.android.launcher3/.view.Launcher) 열기
 *
 * HomeActivity.dispatchKeyEvent(테마 앞)와 ModeManager.registerModeKeyListener(ALLKEY, 다른 앱 앞)가
 * 같은 상태 머신과 쿨다운을 공유해 어느 화면에서나 1회 누름 = 정확히 1회 전환을 보장한다.
 */
final class CustomKeyGuard {
    private static final String TAG = "K8CustomKey";
    /** 차량 즐겨찾기(☆) 물리 버튼 키코드(실차 확인: KEYCODE_SHORTCUT_CUSTOM). */
    static final int KEYCODE_SHORTCUT_CUSTOM = 279;
    /** 순정 ModeManagerService가 즐겨찾기 버튼 입력 시 전 앱에 보내는 공식 브로드캐스트(백업 경로). */
    static final String ACTION_CUSTOM_SHORTCUT = "com.mobis.intent.action.CUSTOM_SHORTCUT";
    /** ModeConstants$ModeType.ALLKEY (0x79, automotive.dex 확인). */
    private static final int MODE_ALLKEY = 121;
    private static final ComponentName STOCK_5W_HOME =
        new ComponentName("com.android.launcher3", "com.android.launcher3.view.Launcher");
    private static final long LONG_PRESS_MS = 550L;
    private static final long ACTION_COOLDOWN_MS = 450L;
    private static final long BROADCAST_BACKUP_COOLDOWN_MS = 1500L;

    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean keyDown;
    private static boolean longTriggered;
    private static long lastLaunchAt;
    private static int launchSeq;
    private static Runnable pendingLong;

    private final Context app;
    private Binding binding;

    CustomKeyGuard(Context context) {
        this.app = context.getApplicationContext();
    }

    void start() {
        if (binding != null) return;
        try {
            binding = new Binding(this);
        } catch (Throwable error) {
            binding = null;
            Log.w(TAG, "ModeKeyListener unavailable", error);
        }
    }

    void stop() {
        if (binding == null) return;
        try {
            binding.close();
        } catch (Throwable ignored) {
        }
        binding = null;
    }

    /** HomeActivity.dispatchKeyEvent 경로: 스위치가 켜져 있으면 279 키를 직접 소비해 순정 버튼 설정이 뜨지 않게 한다. */
    static boolean handleActivityKey(Context context, KeyEvent event) {
        if (event == null || event.getKeyCode() != KEYCODE_SHORTCUT_CUSTOM) return false;
        if (!ThemePrefs.customKeyHome(context)) return false;
        processKey(context.getApplicationContext(), "activity",
            event.getAction(), event.getFlags(), event.getRepeatCount());
        return true;
    }

    private static void processKey(final Context app, String source, int action, int flags, int repeat) {
        if (!ThemePrefs.customKeyHome(app)) {
            if (action == KeyEvent.ACTION_UP) {
                synchronized (LOCK) {
                    cancelPendingLongLocked();
                    keyDown = false;
                    longTriggered = false;
                }
            }
            return;
        }
        boolean isLong = (flags & KeyEvent.FLAG_LONG_PRESS) != 0;
        long now = SystemClock.uptimeMillis();
        Log.i(TAG, source + " action=" + action + " flags=0x" + Integer.toHexString(flags)
            + " repeat=" + repeat + " long=" + isLong);

        boolean triggerShort = false;
        boolean triggerLong = false;
        synchronized (LOCK) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (!keyDown && repeat == 0) {
                    keyDown = true;
                    longTriggered = false;
                    cancelPendingLongLocked();
                    pendingLong = new Runnable() {
                        @Override public void run() {
                            boolean fire = false;
                            synchronized (LOCK) {
                                pendingLong = null;
                                if (keyDown && !longTriggered && ThemePrefs.customKeyHome(app)) {
                                    longTriggered = true;
                                    lastLaunchAt = SystemClock.uptimeMillis();
                                    fire = true;
                                }
                            }
                            if (fire) launchTargetWithGuard(app, true);
                        }
                    };
                    MAIN.postDelayed(pendingLong, LONG_PRESS_MS);
                } else if (isLong && !longTriggered) {
                    longTriggered = true;
                    cancelPendingLongLocked();
                    if (now - lastLaunchAt >= ACTION_COOLDOWN_MS) {
                        lastLaunchAt = now;
                        triggerLong = true;
                    }
                }
            } else if (action == KeyEvent.ACTION_UP) {
                cancelPendingLongLocked();
                boolean canceled = (flags & KeyEvent.FLAG_CANCELED) != 0;
                boolean wasLong = longTriggered || isLong;
                boolean wasDown = keyDown;
                keyDown = false;
                longTriggered = false;
                if (wasDown && !canceled && !wasLong && now - lastLaunchAt >= ACTION_COOLDOWN_MS) {
                    lastLaunchAt = now;
                    triggerShort = true;
                }
            }
        }

        if (triggerLong) launchTargetWithGuard(app, true);
        else if (triggerShort) launchTargetWithGuard(app, false);
    }

    /**
     * 백업 경로: onAllKeyForService가 직전(1.5초 이내)에 처리하지 못한 경우에만
     * CUSTOM_SHORTCUT 브로드캐스트로 ccNC 테마 홈을 연다.
     */
    static void handleCustomShortcutBroadcast(Context context, Intent intent) {
        if (!ThemePrefs.customKeyHome(context)) return;
        final Context app = context.getApplicationContext();
        long now = SystemClock.uptimeMillis();
        synchronized (LOCK) {
            if (now - lastLaunchAt < BROADCAST_BACKUP_COOLDOWN_MS) return;
            lastLaunchAt = now;
        }
        Log.i(TAG, "broadcast CUSTOM_SHORTCUT backup -> open ccNC theme home");
        launchTargetWithGuard(app, false);
    }

    /**
     * 목표 홈(short=ccNC 테마, long=순정 5W 홈)을 즉시 열고,
     * 다른 앱 화면에서 누를 때 순정 ButtonSettings(버튼 설정)가 100~800ms 뒤 덮으려 하면 다시 목표 홈으로 올린다.
     * 빠른 연속 조작 시 이전 가드가 새 전환을 방해하지 않도록 launchSeq로 최신 요청만 실행한다.
     */
    private static void launchTargetWithGuard(final Context app, final boolean toStock5w) {
        final int seq;
        synchronized (LOCK) {
            seq = ++launchSeq;
        }
        if (toStock5w) openStock5wHome(app);
        else openCcncHome(app);
        Runnable guard = new Runnable() {
            @Override public void run() {
                if (!ThemePrefs.customKeyHome(app)) return;
                synchronized (LOCK) {
                    if (seq != launchSeq) return;
                }
                ComponentName top = StockNavigation.foreground(app);
                String pkg = top == null ? "" : top.getPackageName();
                String wantPkg = toStock5w ? STOCK_5W_HOME.getPackageName() : app.getPackageName();
                if (!wantPkg.equals(pkg)) {
                    if (toStock5w) openStock5wHome(app);
                    else openCcncHome(app);
                }
            }
        };
        MAIN.postDelayed(guard, 240L);
        MAIN.postDelayed(guard, 520L);
        MAIN.postDelayed(guard, 850L);
    }

    private static void cancelPendingLongLocked() {
        if (pendingLong != null) {
            MAIN.removeCallbacks(pendingLong);
            pendingLong = null;
        }
    }

    static void openCcncHome(Context app) {
        try {
            Intent intent = new Intent(app, com.android.launcher3.view.Launcher.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                .putExtra(HomeActivity.EXTRA_OPEN_APPS, false);
            app.startActivity(intent);
            Log.i(TAG, "open ccNC theme home");
        } catch (RuntimeException failure) {
            Log.w(TAG, "openCcncHome failed", failure);
        }
    }

    static void openStock5wHome(Context app) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setComponent(STOCK_5W_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            app.startActivity(intent);
            Log.i(TAG, "open stock 5W home");
        } catch (RuntimeException failure) {
            Log.w(TAG, "openStock5wHome failed", failure);
        }
    }

    private static final class Binding {
        private final ModeManager manager;
        private final Receiver receiver;

        Binding(CustomKeyGuard owner) {
            manager = new ModeManager(owner.app);
            receiver = new Receiver(owner);
            manager.registerModeKeyListener(receiver, new int[] { MODE_ALLKEY });
            Log.i(TAG, "ModeKeyListener registered (ALLKEY=" + MODE_ALLKEY + ")");
        }

        void close() {
            manager.unregisterModeKeyListener(receiver);
        }
    }

    private static final class Receiver extends ModeKeyListener {
        private final CustomKeyGuard owner;

        Receiver(CustomKeyGuard owner) {
            this.owner = owner;
        }

        @Override public void onAllKeyForService(int action, int flags, int deviceId, int keyCode,
                int scanCode, int metaState, int repeatCount, boolean isViewHandled) {
            if (keyCode != KEYCODE_SHORTCUT_CUSTOM) return;
            processKey(owner.app, "onAllKeyForService", action, flags, repeatCount);
        }
    }
}
