package io.github.k8home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;

/** One foreground subscription for the same OEM route on the home screen and map overlay. */
final class StockRoute {
    private static final String ROUTE_STATUS = "com.mnsoft.navi.action.Route_Guide_Status";
    private static final String DESTINATION_INFO = "com.mnsoft.navi.action.DestinationInfo";
    private static final String DEST = "com.mnsoft.navi.action.extra.";
    private static final String GPS_CHANGED = "com.hkmc.telematics.gis.action.NAVI_GPS_CHANGED";
    // Navi address lookup (NaviETCReceiver / NativeCall): request takes lat/lon/addressdisp doubles,
    // the reply carries address.step1..4 (시·도, 시·군·구, 읍·면·동, 리) like the ccNC "서울시 서초구 부근".
    private static final String ADDRESS_REQUEST = "com.mnsoft.navi.action.Request.NaviAddressInfo";
    private static final String ADDRESS_RESPONSE = "com.mnsoft.navi.action.Response.NaviAddressInfo";
    private static final String ADDRESS = "com.mnsoft.navi.action.extra.";
    private static final long AREA_MS = 60000;
    private static final String GIS = "com.hkmc.telematics.gis.extra.";
    private static final String TAG = "K8StockRoute";
    private static final long REFRESH_MS = 20000;
    private static final long EXPIRE_MS = 45000;

    interface Listener {
        void routeChanged(boolean guidanceStarted);
    }

    static final class Snapshot {
        final boolean known, active;
        final String destination, address, distance, remaining, arrival;
        String road = "", dayNight = "", mute = "", appVersion = "", mapVersion = "";
        /** Destination coordinates from DestinationInfo as sent (navi units), and its registpoint code. */
        String destLat = "", destLon = "", registPoint = "";

        Snapshot(boolean known, boolean active, String destination, String address, String distance,
                String remaining, String arrival) {
            this.known = known;
            this.active = active;
            this.destination = destination;
            this.address = address;
            this.distance = distance;
            this.remaining = remaining;
            this.arrival = arrival;
        }
    }

    private static StockRoute instance;

    static StockRoute of(Context context) {
        if (instance == null) instance = new StockRoute(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private boolean known, active;
    private long validatedAt, detailsAt, lastRequest, endedAt;
    private String name = "", address = "", distance = "", duration = "", eta = "";
    private String road = "", dayNight = "", mute = "", appVersion = "", mapVersion = "";
    private String destLat = "", destLon = "", registPoint = "";
    private double gpsLat = Double.NaN, gpsLon = Double.NaN;
    private String area = "";
    private long areaRequest, areaAt;
    private long roadAt, lastLocationRequest;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            Bundle data = intent.getExtras();
            if (data == null) return;
            String action = intent.getAction();
            if (GPS_CHANGED.equals(action)) {
                double lat = koreanDegrees(data.get(GIS + "LAT"), 32, 39.5);
                double lon = koreanDegrees(data.get(GIS + "LON"), 123, 132.5);
                if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                    gpsLat = lat;
                    gpsLon = lon;
                    requestArea();
                }
            } else if (ADDRESS_RESPONSE.equals(action)) {
                String next = joinArea(text(data.get(ADDRESS + "address.step1")), text(data.get(ADDRESS + "address.step2")));
                Log.i(TAG, "area reply " + (next.isEmpty() ? "empty" : "ok") + " keys=" + data.keySet());
                if (!next.isEmpty()) {
                    areaAt = SystemClock.elapsedRealtime();
                    if (!next.equals(area)) {
                        area = next;
                        notifyListeners(false);
                    }
                }
                return;
            }
            if (StockNavigation.CURRENT_LOCATION_RESPONSE.equals(action)) {
                String place = text(data.get("address"));
                Object onRoad = data.get("on_road");
                Log.i(TAG, "current location: on_road=" + onRoad + " (" + (onRoad == null ? "null"
                    : onRoad.getClass().getSimpleName()) + "), address length=" + place.length());
                boolean offRoad = Boolean.FALSE.equals(onRoad) || Integer.valueOf(0).equals(onRoad);
                String next = offRoad ? "" : place;
                roadAt = next.isEmpty() ? 0 : SystemClock.elapsedRealtime();
                if (!next.equals(road)) {
                    road = next;
                    notifyListeners(false);
                }
                return;
            }
            if (StockNavigation.DAY_NIGHT.equals(action) || StockNavigation.MUTE_STATE.equals(action)
                    || StockNavigation.BOOT_END.equals(action)) {
                String before = dayNight + mute + appVersion + mapVersion;
                if (data.containsKey("daynight_status")) dayNight = text(data.get("daynight_status"));
                if (data.containsKey("Value")) mute = text(data.get("Value"));
                if (data.containsKey("appVersion")) appVersion = text(data.get("appVersion"));
                if (data.containsKey("mapVersion")) mapVersion = text(data.get("mapVersion"));
                Log.i(TAG, "navi status " + action + " " + data.keySet());
                if (!before.equals(dayNight + mute + appVersion + mapVersion)) notifyListeners(false);
                return;
            }
            if (ROUTE_STATUS.equals(action)) {
                int status = data.getInt("ROUTE_GUIDE_STATE", -1);
                if (status == 0) {
                    markEnded();
                } else if (status == 1) {
                    activate();
                    notifyListeners(!isInitialStickyBroadcast());
                    refresh();
                }
            } else if (StockNavigation.DESTINATION_RESPONSE.equals(action)) {
                String destination = text(data.get("has_dest"));
                if ("0".equals(destination) || "false".equals(destination)) {
                    markEnded();
                } else if ("1".equals(destination) || "true".equals(destination)) {
                    if (SystemClock.elapsedRealtime() - endedAt < 2000 && endedAt != 0) return;
                    boolean started = !active;
                    activate();
                    String arrival = text(data.get("arrival_time"));
                    update(text(data.get("poi")), text(data.get("address")),
                        text(data.get("remain_dist")), arrival, arrival);
                    notifyListeners(started);
                } else if (active) {
                    String arrival = text(data.get("arrival_time"));
                    if (update(text(data.get("poi")), text(data.get("address")),
                            text(data.get("remain_dist")), arrival, arrival)) notifyListeners(false);
                }
            } else if (active) {
                if (DESTINATION_INFO.equals(action)) {
                    String lat = text(data.get(DEST + "LAT")), lon = text(data.get(DEST + "LON"));
                    if (!lat.isEmpty() && !lon.isEmpty()) {
                        destLat = lat;
                        destLon = lon;
                    }
                    if (data.containsKey(DEST + "registpoint")) registPoint = text(data.get(DEST + "registpoint"));
                    Log.i(TAG, "destination info lat=" + (lat.isEmpty() ? "-" : "set") + " type="
                        + text(data.get(DEST + "TYPE")) + " registpoint=" + registPoint);
                }
                boolean changed = DESTINATION_INFO.equals(action)
                    ? update(text(data.get(DEST + "PoiName")), text(data.get(DEST + "Address")),
                        text(data.get(DEST + "DestDistance")), text(data.get(DEST + "DestRemainTime")), "")
                    : update("", "", text(data.get(GIS + "DestDistance")), text(data.get(GIS + "DestRemainTime")), "");
                if (changed) notifyListeners(false);
            }
        }
    };

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (listeners.isEmpty()) return;
            if (validatedAt != 0 && SystemClock.elapsedRealtime() - validatedAt >= EXPIRE_MS) {
                known = active = false;
                clearDetails();
                validatedAt = 0;
            }
            if (detailsAt != 0 && SystemClock.elapsedRealtime() - detailsAt >= EXPIRE_MS) clearDetails();
            if (roadAt != 0 && SystemClock.elapsedRealtime() - roadAt >= EXPIRE_MS) {
                road = "";
                roadAt = 0;
                notifyListeners(false);
            }
            if (areaAt != 0 && SystemClock.elapsedRealtime() - areaAt >= 5 * AREA_MS) {
                area = "";
                areaAt = 0;
                notifyListeners(false);
            }
            requestLocation();
            if (active || !known) refresh();
            if (active || !known) notifyListeners(false);
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    private StockRoute(Context app) {
        this.app = app;
    }

    void start(Listener listener) {
        if (!listeners.add(listener)) return;
        if (listeners.size() == 1) {
            IntentFilter filter = new IntentFilter(ROUTE_STATUS);
            filter.addAction(DESTINATION_INFO);
            filter.addAction(StockNavigation.DESTINATION_RESPONSE);
            filter.addAction(GPS_CHANGED);
            filter.addAction(StockNavigation.CURRENT_LOCATION_RESPONSE);
            filter.addAction(ADDRESS_RESPONSE);
            filter.addAction(StockNavigation.DAY_NIGHT);
            filter.addAction(StockNavigation.MUTE_STATE);
            filter.addAction(StockNavigation.BOOT_END);
            app.registerReceiver(receiver, filter);
            handler.post(poll);
        }
        listener.routeChanged(false);
    }

    void stop(Listener listener) {
        if (!listeners.remove(listener) || !listeners.isEmpty()) return;
        app.unregisterReceiver(receiver);
        handler.removeCallbacks(poll);
        known = active = false;
        endedAt = validatedAt = lastRequest = lastLocationRequest = roadAt = 0;
        road = area = "";
        areaRequest = areaAt = 0;
        clearDetails();
    }

    /** GIS coordinates arrive as raw numbers; pick the scale that lands inside Korea. */
    static double koreanDegrees(Object value, double min, double max) {
        if (!(value instanceof Number) && !(value instanceof CharSequence)) return Double.NaN;
        double raw;
        try {
            raw = Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException failure) {
            return Double.NaN;
        }
        for (double scale : new double[] {1, 360000, 1e6, 1e7, 1e5}) {
            double degrees = raw / scale;
            if (degrees >= min && degrees <= max) return degrees;
        }
        return Double.NaN;
    }

    static String joinArea(String first, String second) {
        String text = (first + " " + second).trim();
        return text.isEmpty() ? "" : text + " 부근";
    }

    private void requestArea() {
        long now = SystemClock.elapsedRealtime();
        if (areaRequest != 0 && now - areaRequest < AREA_MS) return;
        areaRequest = now;
        try {
            app.sendBroadcast(new Intent(ADDRESS_REQUEST).setPackage(StockNavigation.PACKAGE)
                .putExtra(ADDRESS + "lat", gpsLat).putExtra(ADDRESS + "lon", gpsLon)
                .putExtra(ADDRESS + "addressdisp", 0d));
        } catch (RuntimeException failure) {
            Log.w(TAG, "area request refused", failure);
        }
    }

    private void requestLocation() {
        long now = SystemClock.elapsedRealtime();
        if (lastLocationRequest != 0 && now - lastLocationRequest < 5000) return;
        lastLocationRequest = now;
        StockNavigation.request(app, StockNavigation.REQUEST_CURRENT_LOCATION);
    }

    void refresh() {
        long now = SystemClock.elapsedRealtime();
        if (lastRequest != 0 && now - lastRequest < 1500) return;
        lastRequest = now;
        StockNavigation.request(app, StockNavigation.REQUEST_DESTINATION);
    }

    Snapshot snapshot() {
        String remaining = duration.isEmpty()
            ? RouteMetrics.remainingFromArrival(eta, Calendar.getInstance()) : duration;
        Snapshot snapshot = new Snapshot(known, active, name.isEmpty() ? address : name, address, distance,
            remaining, RouteMetrics.arrivalLabel(eta));
        // ccNC shows the district ("서울시 서초구 부근"); the road name is the fallback.
        snapshot.road = area.isEmpty() ? road : area;
        snapshot.dayNight = dayNight;
        snapshot.mute = mute;
        snapshot.appVersion = appVersion;
        snapshot.mapVersion = mapVersion;
        snapshot.destLat = active ? destLat : "";
        snapshot.destLon = active ? destLon : "";
        snapshot.registPoint = active ? registPoint : "";
        return snapshot;
    }

    private void activate() {
        known = active = true;
        endedAt = 0;
        validatedAt = SystemClock.elapsedRealtime();
    }

    private void markEnded() {
        if (known && !active) return;
        known = true;
        active = false;
        endedAt = SystemClock.elapsedRealtime();
        validatedAt = 0;
        clearDetails();
        notifyListeners(false);
    }

    private boolean update(String poi, String location, String measuredDistance, String remain, String arrival) {
        boolean newDestination = !poi.isEmpty() && !poi.equals(name)
            || !location.isEmpty() && !address.isEmpty() && !location.equals(address);
        if (newDestination) clearDetails();
        if (!poi.isEmpty()) name = poi;
        if (!location.isEmpty()) address = location;
        String formattedDistance = RouteMetrics.distance(measuredDistance);
        if (!formattedDistance.isEmpty()) distance = formattedDistance;
        String formattedDuration = RouteMetrics.duration(remain);
        if (!formattedDuration.isEmpty()) duration = formattedDuration;
        boolean hasArrival = RouteMetrics.minuteOfDay(arrival) >= 0;
        if (hasArrival) {
            eta = arrival;
            if (formattedDuration.isEmpty()) duration = "";
        }
        boolean hasData = !poi.isEmpty() || !location.isEmpty() || !formattedDistance.isEmpty()
            || !formattedDuration.isEmpty() || hasArrival;
        if (hasData) validatedAt = detailsAt = SystemClock.elapsedRealtime();
        return hasData;
    }

    private void clearDetails() {
        name = address = distance = duration = eta = "";
        destLat = destLon = registPoint = "";
        detailsAt = 0;
    }

    private void notifyListeners(boolean guidanceStarted) {
        for (Listener listener : new ArrayList<>(listeners)) listener.routeChanged(guidanceStarted);
    }

    private static String text(Object value) {
        if (!(value instanceof CharSequence) && !(value instanceof Number) && !(value instanceof Boolean)) return "";
        String text = value.toString().trim();
        return text.substring(0, Math.min(160, text.length()));
    }
}

