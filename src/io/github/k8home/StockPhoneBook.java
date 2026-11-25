package io.github.k8home;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Phone book and call history of the connected 5W phone, read from the car's own stores (2026-11-24, paths seen
 * in the car's CallLogProvider log and the nowplaying overlay that already reads contacts_bt on this car):
 * - recent calls: content://call_log/calls, favourites: content://call_log/calls_fav
 * - Bluetooth phone book: content://com.android.contacts_bt/contacts_bt
 * Columns are read as-is because only the car shows them. Needs READ_CONTACTS and READ_CALL_LOG.
 * The stock passenger privacy mode (profile_privacy_mode) hides everything, like the stock phone screen does.
 * Names and numbers are personal: never logged, only counts and column names.
 */
final class StockPhoneBook {
    private static final String TAG = "K8PhoneBook";
    static final String ACTION_PRIVACY_CHANGED = "android.bluetooth.intent.action.ACTION_PRIVACY_MODE_CHANGED_ALERT";
    static final String ACTION_MAP_CONNECT_REQUEST = "android.bluetooth.intent.action.MAP_CONNECTION_START_REQUEST";
    static final String ACTION_MAP_CONNECTED = "android.bluetooth.intent.action.ACTION_MAP_CONNECTD";
    static final String ACTION_MAP_STATUS = "com.mobis.intent.action.MAP_CONNECTED_STATUS";
    private static final Uri PRIVACY = Uri.parse("content://com.android.contacts_bt/profile_privacy_mode");
    private static final Uri SMS_ALL = Uri.parse("content://sms");
    private static final int MAX_ROWS = 500;

    private final Context app;

    StockPhoneBook(Context context) {
        app = context.getApplicationContext();
    }

    private static Uri uriOf(String list) {
        switch (list) {
            case "recent": return Uri.parse("content://call_log/calls");
            case "favorites": return Uri.parse("content://call_log/calls_fav");
            case "messages": return Uri.parse("content://sms/inbox");   // 순정 전화 앱과 같은 곳(블루투스 MAP 로 받은 문자)
            case "contacts": return Uri.parse("content://com.android.contacts_bt/contacts");   // 실차: 폰북을 받은 뒤 185명(이름·phone1~5)
            default: return null;
        }
    }

    /** {"list":..,"privacy":bool,"columns":[..],"rows":[{column:value}]} for "recent", "favorites" or "contacts". */
    String list(String list, String btAddress) {
        JSONObject out = new JSONObject();
        try {
            out.put("list", list);
            Uri uri = uriOf(list);
            if (uri == null) return out.put("error", "list").toString();
            if (privacyMode()) return out.put("privacy", true).put("rows", new JSONArray()).toString();
            out.put("privacy", false);
            String where = null;   // shortcut: 폰이 둘 이상 저장돼 있으면 섞여 나옴, 그때 account_name 으로 거르기
            String[] columns = "contacts".equals(list) ? CONTACT_COLUMNS : "messages".equals(list) ? MESSAGE_COLUMNS : CALL_COLUMNS;
            String sort = "recent".equals(list) || "messages".equals(list) ? "date DESC" : "contacts".equals(list) ? "display_name" : null;
            Cursor cursor = query(uri, where, columns, sort);
            // 수신함(sms/inbox)이 비어 있으면 전체 SMS 저장소(content://sms)도 한 번 더 확인한다.
            if ("messages".equals(list) && (cursor == null || cursor.getCount() == 0)) {
                if (cursor != null) cursor.close();
                cursor = query(SMS_ALL, where, columns, sort);
            }
            if (cursor == null) return out.put("error", "null cursor").toString();
            JSONArray rows = new JSONArray();
            try {
                String[] names = cursor.getColumnNames();
                out.put("columns", new JSONArray(java.util.Arrays.asList(names)));
                while (cursor.moveToNext() && rows.length() < MAX_ROWS) {
                    JSONObject row = new JSONObject();
                    for (int i = 0; i < names.length; i++) {
                        if (cursor.getType(i) == Cursor.FIELD_TYPE_BLOB || row.has(names[i])) continue;
                        row.put(names[i], cursor.getString(i));
                    }
                    rows.put(row);
                }
                Log.i(TAG, "list " + list + " rows=" + rows.length() + " columns=" + out.opt("columns"));
            } finally {
                cursor.close();
            }
            out.put("rows", rows);
        } catch (RuntimeException | JSONException failure) {
            Log.w(TAG, "list " + list + " failed: " + failure);
            try { out.put("error", failure.getClass().getSimpleName()); } catch (JSONException ignored) { }
        }
        return out.toString();
    }

    /**
     * Explicit columns: the car's stores fail on "all columns" (call log: default projection names "presentation";
     * phone book: "phone4_lookup_number"; car log 2026-11-25). Any column the car reports missing is dropped and
     * the query retried. A missing sort column falls back to the store's own order.
     */
    private Cursor query(Uri uri, String where, String[] wanted, String sort) {
        java.util.List<String> cols = new java.util.ArrayList<>(java.util.Arrays.asList(wanted));
        while (true) {
            try {
                return queryWith(uri, where, cols.toArray(new String[cols.size()]), sort);
            } catch (android.database.sqlite.SQLiteException again) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("no such column: (\\w+)").matcher(String.valueOf(again.getMessage()));
                if (!m.find() || !cols.remove(m.group(1)) || cols.isEmpty()) throw again;
            }
        }
    }

    private static final String[] MESSAGE_COLUMNS = {"_id", "address", "body", "date", "read", "type"};

    private static final String[] CONTACT_COLUMNS = {"_id", "display_name", "starred",
        "phone1_number", "phone1_type", "phone2_number", "phone2_type", "phone3_number", "phone3_type",
        "phone4_number", "phone4_type", "phone5_number", "phone5_type"};

    private static final String[] CALL_COLUMNS = {"_id", "number", "name", "type", "date", "duration", "numbertype",
        "numberlabel", "formatted_number", "given_name", "family_name", "date_daudio", "new"};

    private Cursor queryWith(Uri uri, String where, String[] projection, String sort) {
        try {
            return app.getContentResolver().query(uri, projection, where, null, sort);
        } catch (android.database.sqlite.SQLiteException failure) {
            // only a missing sort column ("date") is retried unsorted; other missing columns go back to the caller
            if (sort == null || !String.valueOf(failure.getMessage()).contains("no such column: " + sort.split(" ")[0])) throw failure;
            return app.getContentResolver().query(uri, projection, where, null, null);
        }
    }

    /** Stock passenger privacy mode (seat occupied + setting on), same query the stock phone uses. */
    boolean privacyMode() {
        String user = String.valueOf(Settings.System.getInt(app.getContentResolver(), "user_profile_driver", 1));
        Cursor cursor = null;
        try {
            cursor = app.getContentResolver().query(PRIVACY, null, null, new String[] {user, "1", "read"}, null);
            return cursor != null && cursor.moveToFirst() && cursor.getInt(0) == 1;
        } catch (RuntimeException failure) {
            Log.w(TAG, "privacy mode read failed: " + failure);
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /** 순정 BluetoothContactsProvider 의 profile_privacy_mode 를 직접 켜고 끈다(ARG_2 = "write"). */
    boolean setPrivacyMode(boolean on) {
        String user = String.valueOf(Settings.System.getInt(app.getContentResolver(), "user_profile_driver", 1));
        Cursor cursor = null;
        try {
            cursor = app.getContentResolver().query(PRIVACY, null, null, new String[] {user, on ? "1" : "0", "write"}, null);
        } catch (RuntimeException failure) {
            Log.w(TAG, "privacy mode write failed: " + failure);
        } finally {
            if (cursor != null) cursor.close();
        }
        boolean now = privacyMode();
        Log.i(TAG, "setPrivacyMode target=" + on + " actual=" + now);
        try {
            android.content.Intent alert = new android.content.Intent(ACTION_PRIVACY_CHANGED);
            alert.putExtra("privacy_mode", now);
            app.sendBroadcast(alert);
        } catch (RuntimeException ignored) { }
        return now;
    }

    /** 순정 블루투스 문자(MAP) 연결·동기화를 요청한다. */
    void requestMessagesSync() {
        try {
            app.sendBroadcast(new android.content.Intent(ACTION_MAP_CONNECT_REQUEST));
            Log.i(TAG, "requested MAP connection sync");
        } catch (RuntimeException failure) {
            Log.w(TAG, "MAP sync request failed: " + failure);
        }
    }
}
