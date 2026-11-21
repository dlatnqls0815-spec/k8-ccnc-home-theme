package io.github.k8home;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.List;

final class StockNavigation {
    static final String PACKAGE = "com.mnsoft.navi";
    static final ComponentName MAP = new ComponentName(PACKAGE, PACKAGE + ".NaviApp");
    static final String SEARCH = "com.hkmc.intent.action.ACTION_NAVI_TOTALSEARCH";
    static final String CURRENT_POSITION = "com.hkmc.intent.action.ACTION_NAVI_CURRENTPOS";
    static final String SETTINGS = "com.hkmc.intent.action.ACTION_NAVI_SETUP";
    static final String CANCEL_ROUTE = "com.mobis.intent.action.CANCEL_ROUTE_SHORTCUT";
    static final String REQUEST_DESTINATION = "com.hkmc.intent.action.Request_Info_Destination";
    static final String DESTINATION_RESPONSE = "com.hkmc.intent.action.Response_Info_Destination";
    // Same NaviService filter as Request_Info_Destination; reply carries "address" and "on_road".
    static final String REQUEST_CURRENT_LOCATION = "com.hkmc.intent.action.Request_Info_CurrentLoc";
    static final String CURRENT_LOCATION_RESPONSE = "com.hkmc.intent.action.Response_Info_CurrentLoc";
    // Plain broadcasts sent by HKMC_Navi_ExSL (message/c.smali); value meanings still need a field check.
    static final String DAY_NIGHT = "com.mnsoft.navi.action.DAYNIGHT_CHANGED";
    static final String MUTE_STATE = "android.intent.action.VIT_NOTI_NAVI_MUTE_STATE";
    static final String BOOT_END = "android.intent.action.VIT_NOTI_NAVI_BOOTEND_SERVICE";

    /** Navi open API (NaviOpenAPIRecevier): coordinates are decimal-degree text, name is free text. */
    /** ModeManager (automotive.odex): the rear camera view opened or closed. */
    static final String REARVIEW_ON = "com.hkmc.intent.action.SYSTEM_STATE_REARVIEW_ON";
    static final String REARVIEW_OFF = "com.hkmc.intent.action.SYSTEM_STATE_REARVIEW_OFF";
    static final String ROUTE_SEARCH = "com.hkmc.intent.action.ACTION_ROUTE_SEARCH";
    // NaviService receivers (NaviETCReceiver): open the navi's nearby category list; ignored while
    // the navi is already in front, so they are sent before the map is brought up.
    static final String NEARBY_PETROL = "com.hkmc.intent.action.ACTION_INFO_PETROLSTATIONS";
    static final String NEARBY_PARKING = "com.hkmc.intent.action.ACTION_INFO_PARKING";
    private static final String EXTRA = "com.hkmc.navi.EXTRA_";

    private StockNavigation() {}

    /** Converts a navi coordinate (degrees, or degrees x 360000 as used inside the navi) to degrees. */
    static String degrees(String value) {
        try {
            double number = Double.parseDouble(value.trim());
            if (Math.abs(number) > 1000) number /= 360000.0;
            if (Math.abs(number) < 0.001 || Math.abs(number) > 180) return "";
            return String.format(java.util.Locale.US, "%.6f", number);
        } catch (RuntimeException failure) {
            return "";
        }
    }

    /** Asks the stock navigation to search a route to a saved point. Returns false when refused. */
    static boolean routeTo(Context context, String name, String latitude, String longitude) {
        if (latitude.isEmpty() || longitude.isEmpty()) return false;
        try {
            context.sendBroadcast(new Intent(ROUTE_SEARCH).setPackage(PACKAGE)
                .putExtra(EXTRA + "LATITUDE", latitude).putExtra(EXTRA + "LONGITUDE", longitude)
                .putExtra(EXTRA + "KEYWORD", name));
            Log.i("K8StockNavigation", "route search sent to saved point " + name);
            return true;
        } catch (RuntimeException failure) {
            Log.w("K8StockNavigation", "Route search rejected", failure);
            return false;
        }
    }

    static boolean request(Context context, String action) {
        try {
            context.sendBroadcast(new Intent(action).setPackage(PACKAGE));
            return true;
        } catch (RuntimeException failure) {
            Log.w("K8StockNavigation", "Stock request rejected: " + action, failure);
            return false;
        }
    }

    static void requestWhenMapOpens(Context context, final String action) {
        final Context app = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());
        final long deadline = SystemClock.elapsedRealtime() + 5000;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (MAP.equals(foreground(app))) {
                    request(app, action);
                } else if (SystemClock.elapsedRealtime() < deadline) {
                    handler.postDelayed(this, 350);
                }
            }
        }, 350);
    }

    @SuppressWarnings("deprecation")
    static ComponentName foreground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return null;
        try {
            List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
            return tasks.isEmpty() ? null : tasks.get(0).topActivity;
        } catch (RuntimeException failure) {
            return null;
        }
    }
}
