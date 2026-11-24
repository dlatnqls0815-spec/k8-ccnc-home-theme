package io.github.k8home;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
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
    private static final Uri DESTINATION_URI = Uri.parse("content://com.mnsoft.navi/Destination_Table");
    private static final Uri SPECIAL_POINT_URI = Uri.parse("content://com.mnsoft.navi/RegistPnt_Special_Table");

    /** 순정 내비게이션에서 읽은 목적지/즐겨찾기 좌표 정보. */
    static final class Place {
        final String name;
        final String address;
        final String latitude;
        final String longitude;

        Place(String name, String address, String latitude, String longitude) {
            this.name = name == null ? "" : name.trim();
            this.address = address == null ? "" : address.trim();
            this.latitude = latitude == null ? "" : latitude.trim();
            this.longitude = longitude == null ? "" : longitude.trim();
        }

        boolean hasCoords() {
            return !latitude.isEmpty() && !longitude.isEmpty();
        }

        String displayName(String fallback) {
            if (!name.isEmpty()) return name;
            if (!address.isEmpty()) return address;
            return fallback;
        }
    }

    private StockNavigation() {}

    /** Converts a navi coordinate (degrees, or degrees x 360000 as used inside the navi) to degrees. */
    static String degrees(String value) {
        if (value == null) return "";
        try {
            double number = Double.parseDouble(value.trim());
            if (Math.abs(number) > 1000) number /= 360000.0;
            if (Math.abs(number) < 0.001 || Math.abs(number) > 180) return "";
            return String.format(java.util.Locale.US, "%.6f", number);
        } catch (RuntimeException failure) {
            return "";
        }
    }

    /** 순정 내비 NaviVRProvider에서 현재 안내 중인 목적지(이름·주소·좌표)를 읽는다. */
    static Place currentDestination(Context context) {
        return queryFirstPlace(context, DESTINATION_URI, null, null);
    }

    /** 순정 내비 NaviVRProvider에서 등록된 특수 지점(1=우리집, 2=회사)을 읽는다. */
    static Place stockSpecialPlace(Context context, String key) {
        String listType = "home".equals(key) ? "1" : "2";
        return queryFirstPlace(context, SPECIAL_POINT_URI, "Info_ListType = ?", new String[] { listType });
    }

    private static Place queryFirstPlace(Context context, Uri uri, String selection, String[] args) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, null, selection, args, null);
            if (cursor == null || !cursor.moveToFirst()) return null;
            String name = columnText(cursor, "Info_Name");
            String addr = columnText(cursor, "Info_Address");
            String lat = degrees(columnText(cursor, "Info_Latitude"));
            String lon = degrees(columnText(cursor, "Info_Longitude"));
            if (lat.isEmpty() || lon.isEmpty()) return null;
            return new Place(name, addr, lat, lon);
        } catch (RuntimeException failure) {
            Log.w("K8StockNavigation", "NaviVRProvider query failed: " + uri, failure);
            return null;
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (RuntimeException ignored) {}
            }
        }
    }

    private static String columnText(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return "";
        String value = cursor.getString(index);
        if (value == null) return "";
        value = value.trim();
        return "NULL".equalsIgnoreCase(value) ? "" : value;
    }

    /** 순정 내비에 저장된 좌표로 즉시 길안내 시작을 요청한다. 거부되면 false 반환. */
    static boolean routeTo(Context context, String name, String latitude, String longitude) {
        String lat = degrees(latitude);
        String lon = degrees(longitude);
        if (lat.isEmpty() || lon.isEmpty()) return false;
        try {
            Intent intent = new Intent(ROUTE_SEARCH).setPackage(PACKAGE)
                .putExtra(EXTRA + "LATITUDE", lat)
                .putExtra(EXTRA + "LONGITUDE", lon)
                .putExtra(EXTRA + "KEYWORD", name == null ? "" : name)
                .putExtra(EXTRA + "BG_SEARCH", 0)
                .putExtra(EXTRA + "ROUTE_OPTION", false);
            context.sendBroadcast(intent);
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

    /**
     * Ends the current route. The navi only honours CANCEL_ROUTE_SHORTCUT when the reason extra is
     * 3 (steering wheel custom key), 18 or 19; without it the broadcast is silently dropped.
     */
    static boolean cancelRoute(Context context) {
        try {
            context.sendBroadcast(new Intent(CANCEL_ROUTE).setPackage(PACKAGE)
                .putExtra("com.hkmc.mode.EXTRA_SHORTCUT_REASON", 3));
            Log.i("K8StockNavigation", "route cancel sent");
            return true;
        } catch (RuntimeException failure) {
            Log.w("K8StockNavigation", "Route cancel rejected", failure);
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
