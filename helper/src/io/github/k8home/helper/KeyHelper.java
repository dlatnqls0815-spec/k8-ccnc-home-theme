package io.github.k8home.helper;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.IBinder;
import android.os.Parcel;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Runs as the system uid (NEMESIS service). Accepts one media key per connection on the abstract
 * socket "k8home.keys", only from the K8 home theme app's uid and only previous / play-pause / next,
 * and replays it through IModeManager.generateKeyEvent like the steering-wheel switch.
 * A normal app cannot do this itself: the mode manager drops keys from callers without
 * INJECT_EVENTS (confirmed in the car on 2026-11-16).
 */
public final class KeyHelper {
    static final String SOCKET = "k8home.keys";
    static final String CLIENT_PACKAGE = "io.github.k8home";
    static final String SERVICE = "com.hkmc.micom.mode";
    static final String DESCRIPTOR = "com.hkmc.mode.IModeManager";
    static final int TRANSACTION_GENERATE_KEY_EVENT = 0x62;
    static final long MIN_INTERVAL_MS = 250;
    /**
     * "guide": the stock navigation's guidance-list expand/collapse button sits under the theme's navigation
     * row (bottom-right of the 1920x720 navi screen; another Gen5W theme taps the same spot from inside the
     * navi app). Only this one point, and only while the stock navi is in front.
     */
    static final int GUIDE = 1000;
    static final float GUIDE_X = 1850f, GUIDE_Y = 675f;
    static final String NAVI_PACKAGE = "com.mnsoft.navi";

    private static long lastKeyMs;

    private KeyHelper() {}

    public static void main(String[] args) throws IOException {
        log("start");
        LocalServerSocket server = new LocalServerSocket(SOCKET);
        while (true) {
            LocalSocket client = server.accept();
            try {
                handle(client);
            } catch (Exception e) {
                log("client error: " + e);
            } finally {
                try {
                    client.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    static void handle(LocalSocket client) throws Exception {
        int uid = client.getPeerCredentials().getUid();
        int allowed = clientUid();
        OutputStream out = client.getOutputStream();
        if (uid != allowed) {
            log("rejected uid " + uid);
            out.write("denied\n".getBytes("US-ASCII"));
            return;
        }
        int code = parseKey(readLine(client.getInputStream()));
        if (code < 0) {
            out.write("bad\n".getBytes("US-ASCII"));
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastKeyMs < MIN_INTERVAL_MS) {
            out.write("busy\n".getBytes("US-ASCII"));
            return;
        }
        lastKeyMs = now;
        if (code == GUIDE) {
            if (!NAVI_PACKAGE.equals(foregroundPackage())) {
                out.write("not-navi\n".getBytes("US-ASCII"));
                return;
            }
            tap(GUIDE_X, GUIDE_Y);
        } else {
            sendKey(code);
        }
        out.write("ok\n".getBytes("US-ASCII"));
    }

    /** Only the three media keys are accepted; anything else returns -1. */
    static int parseKey(String line) {
        if (line == null) return -1;
        String s = line.trim();
        if ("166".equals(s)) return 166;
        if ("91".equals(s)) return 91;
        if ("167".equals(s)) return 167;
        if ("guide".equals(s)) return GUIDE;
        return -1;
    }

    static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            int c = in.read();
            if (c < 0 || c == '\n') break;
            sb.append((char) c);
        }
        return sb.toString();
    }

    /** The app's uid from /data/system/packages.list ("name uid debug dataDir ..."), or -2. */
    static int clientUid() {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/data/system/packages.list"));
            String line;
            while ((line = r.readLine()) != null) {
                int uid = uidFromLine(line);
                if (uid >= 0) return uid;
            }
        } catch (IOException e) {
            log("packages.list: " + e);
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ignored) {
                }
            }
        }
        return -2;
    }

    static int uidFromLine(String line) {
        String[] f = line.trim().split("\\s+");
        if (f.length < 2 || !CLIENT_PACKAGE.equals(f[0])) return -1;
        try {
            int uid = Integer.parseInt(f[1]);
            return uid >= 10000 ? uid : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static void sendKey(int code) throws Exception {
        Class<?> sm = Class.forName("android.os.ServiceManager");
        Method get = sm.getMethod("getService", String.class);
        IBinder binder = (IBinder) get.invoke(null, SERVICE);
        if (binder == null) throw new IllegalStateException("mode service missing");
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(code);
            data.writeInt(0);
            binder.transact(TRANSACTION_GENERATE_KEY_EVENT, data, reply, 0);
            reply.readException();
            log("key " + code);
        } finally {
            data.recycle();
            reply.recycle();
        }
    }

    /** Foreground package from the mode manager (same service as the keys), or "". */
    static String foregroundPackage() {
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) sm.getMethod("getService", String.class).invoke(null, SERVICE);
            Class<?> stub = Class.forName("com.hkmc.mode.IModeManager$Stub");
            Object mode = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            Object pkg = mode.getClass().getMethod("getForegroundPackageName").invoke(mode);
            return pkg == null ? "" : pkg.toString();
        } catch (Exception e) {
            log("foreground: " + e);
            return "";
        }
    }

    /** One finger tap through InputManager (the system uid holds INJECT_EVENTS). */
    static void tap(float x, float y) throws Exception {
        Class<?> im = Class.forName("android.hardware.input.InputManager");
        Object manager = im.getMethod("getInstance").invoke(null);
        Method inject = im.getMethod("injectInputEvent", Class.forName("android.view.InputEvent"), int.class);
        long down = SystemClock.uptimeMillis();
        MotionEvent press = MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent release = MotionEvent.obtain(down, down + 60, MotionEvent.ACTION_UP, x, y, 0);
        press.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        release.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            inject.invoke(manager, press, 2); // INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH
            Thread.sleep(60);
            inject.invoke(manager, release, 2);
            log("guide tap " + (int) x + "," + (int) y);
        } finally {
            press.recycle();
            release.recycle();
        }
    }

    static final String LOG = "/data/nemesis/logs/k8home.keys/keys.log";
    static final long LOG_LIMIT = 256 * 1024;

    static void log(String msg) {
        String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date()) + " " + msg;
        System.out.println(line);
        try {
            File f = new File(LOG);
            if (f.length() > LOG_LIMIT) f.renameTo(new File(LOG + ".1"));
            FileWriter w = new FileWriter(f, true);
            try {
                w.write(line + "\n");
            } finally {
                w.close();
            }
        } catch (IOException ignored) {
        }
    }
}
