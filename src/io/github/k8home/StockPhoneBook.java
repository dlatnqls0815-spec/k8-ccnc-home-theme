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
    private static final Uri PRIVACY = Uri.parse("content://com.android.contacts_bt/profile_privacy_mode");
    private static final int MAX_ROWS = 500;

    private final Context app;

    StockPhoneBook(Context context) {
        app = context.getApplicationContext();
    }

    private static Uri uriOf(String list) {
        switch (list) {
            case "recent": return Uri.parse("content://call_log/calls");
            case "favorites": return Uri.parse("content://call_log/calls_fav");
            case "contacts": return Uri.parse("content://com.android.contacts_bt/contacts_bt");
            default: return null;
        }
    }

    /** {"list":..,"privacy":bool,"columns":[..],"rows":[{column:value}]} for "recent", "favorites" or "contacts". */
    String list(String list) {
        JSONObject out = new JSONObject();
        try {
            out.put("list", list);
            Uri uri = uriOf(list);
            if (uri == null) return out.put("error", "list").toString();
            if (privacyMode()) return out.put("privacy", true).put("rows", new JSONArray()).toString();
            out.put("privacy", false);
            Cursor cursor = query(uri, "recent".equals(list) ? "date DESC" : null);
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

    /** Sorted query; falls back to the store's own order when the car's columns have no "date". */
    private Cursor query(Uri uri, String sort) {
        try {
            return app.getContentResolver().query(uri, null, null, null, sort);
        } catch (RuntimeException badSort) {
            if (sort == null) throw badSort;
            return app.getContentResolver().query(uri, null, null, null, null);
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
}
