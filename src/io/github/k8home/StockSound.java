package io.github.k8home;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONObject;

import java.lang.reflect.Field;

/**
 * 고음/중음/저음·전후/좌우를 순정 사운드 서비스(com.hkmc.media.MicomAudioManager)로 직접 바꾼다.
 * 테마 설정의 "사운드 직접 변경" 스위치(기본 꺼짐)가 켜져 있을 때만 부른다.
 *
 * 앰프 값의 단위(0~20 인지 -10~10 인지)는 아직 차에서 확정하지 못했다. 그래서 매번 쓰기 전에
 * 앰프 값(getStatus 의 mTreble 등)과 순정 설정 키(hkmc_sndset_*: 10 이 0)를 비교해 단위를 정하고,
 * 두 값이 어느 단위로도 맞지 않으면 쓰지 않는다. 쓰고 나면 다시 읽어 결과를 돌려준다.
 * 모든 호출은 UI 스레드가 아닌 곳에서 한다.
 */
final class StockSound {
    private static final String TAG = "K8Sound";
    /** 앰프가 0~20(10 = 가운데)을 쓰면 10, -10~10 을 쓰면 0, 모르면 -1. */
    static final int UNKNOWN = -1;

    private StockSound() { }

    static final class Result {
        final boolean ok;
        final String message;
        Result(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    private static Object manager(Context context) throws Exception {
        Class<?> api = Class.forName("com.hkmc.media.MicomAudioManager");
        return api.getConstructor(Context.class).newInstance(context.getApplicationContext());
    }

    private static Object status(Object manager) throws Exception {
        return manager.getClass().getMethod("getStatus").invoke(manager);
    }

    private static int field(Object status, String name) {
        try {
            Field f = status.getClass().getField(name);
            Object value = f.get(status);
            return value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE;
        } catch (Exception ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static int key(ContentResolver resolver, String name) {
        for (int space = 0; space < 3; space++) {
            try {
                int value = space == 0 ? Settings.System.getInt(resolver, name, -1)
                    : space == 1 ? Settings.Global.getInt(resolver, name, -1)
                    : Settings.Secure.getInt(resolver, name, -1);
                if (value >= 0) return value;
            } catch (Exception ignored) { }
        }
        return -1;
    }

    /** 앰프 값과 순정 설정 키를 비교해 앰프 단위의 0 위치(10 또는 0)를 정한다. 근거가 엇갈리면 UNKNOWN. */
    static int offset(Context context, Object status) {
        int user = HeadUnitInfo.profile(context, new JSONObject());
        String suffix = user > 0 ? "_USER_" + user : "_GUEST";
        ContentResolver resolver = context.getContentResolver();
        String[][] pairs = {{"mTreble", "hkmc_sndset_treble"}, {"mMiddle", "hkmc_sndset_middle"},
            {"mBass", "hkmc_sndset_bass"}};
        int found = UNKNOWN;
        for (String[] pair : pairs) {
            int amp = field(status, pair[0]);
            int raw = key(resolver, pair[1] + suffix);
            if (amp == Integer.MIN_VALUE || raw < 0 || raw > 20) continue;
            int guess;
            if (amp == raw) guess = 10;
            else if (amp == raw - 10) guess = 0;
            else {
                Log.w(TAG, pair[0] + " 앰프 " + amp + " / 키 " + raw + " → 단위가 맞지 않습니다");
                return UNKNOWN;
            }
            if (found != UNKNOWN && found != guess) {
                Log.w(TAG, "단위 근거가 엇갈립니다: " + found + " / " + guess);
                return UNKNOWN;
            }
            found = guess;
        }
        return found;
    }

    private static boolean inRange(int value) { return value >= -10 && value <= 10; }

    /** 앰프 상태는 쓰고 나서 조금 뒤에 바뀐다(실차: 바로 읽으면 앞 값). */
    private static void settle() {
        try { Thread.sleep(400); } catch (InterruptedException ignored) { }
    }

    /** 테마 단위(-10~10, 0 = 가운데)의 저음·중음·고음을 쓴다. */
    static Result setTone(Context context, int bass, int middle, int treble) {
        if (!inRange(bass) || !inRange(middle) || !inRange(treble)) return new Result(false, "범위(-10~10)를 벗어난 값입니다.");
        try {
            Object manager = manager(context);
            Object before = status(manager);
            if (before == null) return new Result(false, "순정 사운드 상태를 읽지 못했습니다.");
            int off = offset(context, before);
            if (off == UNKNOWN) return new Result(false, "앰프 값 단위를 확인하지 못해 쓰지 않았습니다.");
            Object done = manager.getClass().getMethod("setBassMidTreb", int.class, int.class, int.class)
                .invoke(manager, bass + off, middle + off, treble + off);
            Log.i(TAG, "setBassMidTreb " + (bass + off) + "/" + (middle + off) + "/" + (treble + off) + " → " + done);
            settle();
            Object after = status(manager);
            String read = after == null ? "?" : field(after, "mBass") + "/" + field(after, "mMiddle") + "/" + field(after, "mTreble");
            boolean ok = !Boolean.FALSE.equals(done);
            return new Result(ok, (ok ? "보냄" : "거부됨") + " · 다시 읽은 앰프 값 저/중/고 " + read);
        } catch (SecurityException denied) {
            Log.w(TAG, "setBassMidTreb 거부", denied);
            return new Result(false, "순정 사운드 서비스가 거부했습니다.");
        } catch (Exception | LinkageError failure) {
            Log.w(TAG, "setBassMidTreb 실패", failure);
            return new Result(false, "순정 사운드 서비스를 부르지 못했습니다.");
        }
    }

    /**
     * 테마 단위(-10~10, 0 = 가운데, 전후 + = 앞, 좌우 + = 오른쪽)의 전후·좌우를 쓴다.
     * 차에서 확인(2026-11-23): 앰프·순정 키 전후는 값이 클수록 뒤(11 = 뒤로 한 칸), 좌우는 클수록 오른쪽.
     */
    static Result setFadeBalance(Context context, int fade, int balance) {
        if (!inRange(fade) || !inRange(balance)) return new Result(false, "범위(-10~10)를 벗어난 값입니다.");
        try {
            Object manager = manager(context);
            Object before = status(manager);
            if (before == null) return new Result(false, "순정 사운드 상태를 읽지 못했습니다.");
            int off = offset(context, before);
            if (off == UNKNOWN) return new Result(false, "앰프 값 단위를 확인하지 못해 쓰지 않았습니다.");
            int nowFade = field(before, "mFade"), nowBalance = field(before, "mBalance");
            if (!inRange(nowFade - off) || !inRange(nowBalance - off)) {
                return new Result(false, "지금 전후/좌우 앰프 값(" + nowFade + "/" + nowBalance + ")이 예상 단위와 달라 쓰지 않았습니다.");
            }
            Object done = manager.getClass().getMethod("setFadeBalance", int.class, int.class)
                .invoke(manager, off - fade, balance + off);
            Log.i(TAG, "setFadeBalance " + (off - fade) + "/" + (balance + off) + " → " + done);
            settle();
            Object after = status(manager);
            String read = after == null ? "?" : field(after, "mFade") + "/" + field(after, "mBalance");
            boolean ok = !Boolean.FALSE.equals(done);
            return new Result(ok, (ok ? "보냄" : "거부됨") + " · 다시 읽은 앰프 값 전후/좌우 " + read);
        } catch (SecurityException denied) {
            Log.w(TAG, "setFadeBalance 거부", denied);
            return new Result(false, "순정 사운드 서비스가 거부했습니다.");
        } catch (Exception | LinkageError failure) {
            Log.w(TAG, "setFadeBalance 실패", failure);
            return new Result(false, "순정 사운드 서비스를 부르지 못했습니다.");
        }
    }

    /** 화면에 보낼 앰프 원시 값(단위 확인용). */
    static JSONObject raw(Context context) {
        JSONObject out = new JSONObject();
        try {
            Object status = status(manager(context));
            if (status == null) return out;
            for (String name : new String[] {"mTreble", "mMiddle", "mBass", "mFade", "mBalance"}) {
                int value = field(status, name);
                if (value != Integer.MIN_VALUE) out.put(name, value);
            }
            out.put("offset", offset(context, status));
        } catch (Exception | LinkageError ignored) { }
        return out;
    }
}
