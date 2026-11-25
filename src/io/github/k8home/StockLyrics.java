package io.github.k8home;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Lyrics for the playing Bluetooth/USB track from LRCLIB (lrclib.net, free, no key), then NetEase Cloud Music
 * (music.163.com, unofficial, has many Korean songs LRCLIB lacks). Only title, artist and length leave the car. Results (also "not found") are cached in the app's cache dir so a song is fetched once.
 * Result JSON for the page: {"synced": LRC text, "plain": text, "album": name} or {"none": true}.
 */
final class StockLyrics {
    interface Callback { void done(String key, String json); }

    private static final String TAG = "K8Lyrics";
    private static final String API = "https://lrclib.net/api/";
    private static final String NETEASE = "https://music.163.com/api/";
    // NetEase puts credits as timed lines ("[00:00.00] 作词 : …"); they are not lyrics.
    private static final java.util.regex.Pattern CREDIT = java.util.regex.Pattern.compile(
        "(?m)^\\[[^\\]]*\\][ \\t]*(作词|作曲|编曲|制作人|制作|混音|母带|监制|和声|吉他|贝斯|鼓|录音|出品|发行|词|曲)[^:：\\n]{0,8}[:：].*(\\n|$)");
    private static final String NONE = "{\"none\":true}";

    private final File dir;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    StockLyrics(Context context) {
        dir = new File(context.getCacheDir(), "lyrics3");   // test60: NetEase·앨범 추가 전 저장분은 다시 찾는다
    }

    /** Fetches on a background thread; {@code callback} runs there too (caller moves it to the UI thread). */
    void request(final String key, final String title, final String artist, final int seconds, final Callback callback) {
        worker.execute(new Runnable() {
            @Override public void run() {
                File cache = new File(dir, Integer.toHexString(key.hashCode()) + ".json");
                String json = read(cache, key);
                if (json == null) {
                    json = fetch(title, artist, seconds);
                    if (json == null) {   // 네트워크 실패: 저장하지 않아 다음에 다시 시도
                        callback.done(key, NONE);
                        return;
                    }
                    write(cache, key, json);
                }
                callback.done(key, json);
            }
        });
    }

    /** Lyrics JSON, NONE when LRCLIB has none, null when the network failed. */
    private static String fetch(String title, String artist, int seconds) {
        try {
            JSONObject hit = null;
            if (!artist.isEmpty() && seconds > 0) {
                String body = get(API + "get?track_name=" + enc(title) + "&artist_name=" + enc(artist) + "&duration=" + seconds);
                if (body != null) hit = new JSONObject(body);
            }
            if (hit == null || !hasLyrics(hit)) {
                String query = artist.isEmpty() ? "q=" + enc(title) : "track_name=" + enc(title) + "&artist_name=" + enc(artist);
                String body = get(API + "search?" + query);
                hit = body == null ? null : best(new JSONArray(body), seconds);
            }
            Log.i(TAG, "lyrics " + (hit == null ? "none" : hit.optString("syncedLyrics").isEmpty() ? "plain" : "synced"));
            if (hit == null) return netease(title, artist, seconds);
            return new JSONObject().put("synced", hit.optString("syncedLyrics", ""))
                .put("plain", hit.optString("plainLyrics", "")).put("album", hit.optString("albumName", "")).toString();
        } catch (IOException | JSONException | RuntimeException failure) {
            Log.w(TAG, "lyrics fetch failed: " + failure);
            return null;
        }
    }

    /** NetEase search: a song with the same title and a length within 3 s (or the same artist when the length is unknown). */
    private static String netease(String title, String artist, int seconds) throws IOException, JSONException {
        String body = get(NETEASE + "search/get?type=1&limit=10&s=" + enc(artist.isEmpty() ? title : title + " " + artist));
        JSONArray songs = body == null ? null : new JSONObject(body).optJSONObject("result") == null ? null
            : new JSONObject(body).getJSONObject("result").optJSONArray("songs");
        long id = -1;
        String album = "";
        for (int i = 0; songs != null && i < songs.length() && id < 0; i++) {
            JSONObject o = songs.getJSONObject(i);
            if (!same(o.optString("name"), title)) continue;
            JSONArray artists = o.optJSONArray("artists");
            boolean artistOk = artists != null && artists.length() > 0 && same(artists.getJSONObject(0).optString("name"), artist);
            if (seconds > 0 ? Math.abs(o.optLong("duration") / 1000.0 - seconds) <= 3 : artistOk) {
                id = o.optLong("id");
                JSONObject al = o.optJSONObject("album");
                album = al == null ? "" : al.optString("name", "");
            }
        }
        if (id < 0) {
            Log.i(TAG, "netease none");
            return NONE;
        }
        String lyric = get(NETEASE + "song/lyric?lv=1&id=" + id);
        JSONObject lrc = lyric == null ? null : new JSONObject(lyric).optJSONObject("lrc");
        String text = lrc == null ? "" : CREDIT.matcher(lrc.optString("lyric", "")).replaceAll("").trim();
        if (text.isEmpty()) return NONE;
        boolean synced = text.matches("(?s)\\s*\\[\\d+:\\d+.*");
        Log.i(TAG, "netease " + (synced ? "synced" : "plain"));
        return new JSONObject().put("synced", synced ? text : "").put("plain", synced ? "" : text).put("album", album).toString();
    }

    private static boolean same(String a, String b) {
        return a.replaceAll("\\s+", "").equalsIgnoreCase(b.replaceAll("\\s+", ""));
    }

    private static boolean hasLyrics(JSONObject o) {
        return !o.optString("syncedLyrics", "").isEmpty() || !o.optString("plainLyrics", "").isEmpty();
    }

    /** First result with synced lyrics and a length within 3 s, else the first one with any lyrics. */
    private static JSONObject best(JSONArray results, int seconds) {
        JSONObject any = null;
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.optJSONObject(i);
            if (o == null || !hasLyrics(o)) continue;
            boolean close = seconds <= 0 || Math.abs(o.optDouble("duration", 0) - seconds) <= 3;
            if (close && !o.optString("syncedLyrics", "").isEmpty()) return o;
            if (any == null && close) any = o;
        }
        return any;
    }

    /** Body of a 200 reply, null for 404; throws on network errors. */
    private static String get(String url) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        if (http instanceof HttpsURLConnection) ((HttpsURLConnection) http).setSSLSocketFactory(Tls12.INSTANCE);
        http.setConnectTimeout(8000);
        http.setReadTimeout(10000);
        http.setRequestProperty("User-Agent", "K8ccNCHome/0.5");
        if (url.startsWith(NETEASE)) http.setRequestProperty("Referer", "https://music.163.com/");
        try {
            int code = http.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new IOException("HTTP " + code);
            return new String(readAll(http.getInputStream()), "UTF-8");
        } finally {
            http.disconnect();
        }
    }

    private static String enc(String value) throws IOException {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) > 0; ) out.write(buffer, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /** Cached JSON for this exact key, or null. The first line of the file is the key (guards hash collisions). */
    private static String read(File file, String key) {
        if (!file.isFile()) return null;
        try {
            String text = new String(readAll(new FileInputStream(file)), "UTF-8");
            int cut = text.indexOf('\u0000');
            return cut > 0 && text.substring(0, cut).equals(key) ? text.substring(cut + 1) : null;
        } catch (IOException failure) {
            return null;
        }
    }

    private void write(File file, String key, String json) {
        if (!dir.isDirectory() && !dir.mkdirs()) return;
        try {
            FileOutputStream out = new FileOutputStream(file);
            try { out.write((key + '\u0000' + json).getBytes("UTF-8")); } finally { out.close(); }
        } catch (IOException failure) {
            Log.w(TAG, "lyrics cache write failed: " + failure);
        }
    }

    /** Android 4.4 supports TLS 1.2 but leaves it off; lrclib.net refuses TLS 1.0. */
    private static final class Tls12 extends SSLSocketFactory {
        static final Tls12 INSTANCE = new Tls12();
        private final SSLSocketFactory base;

        private Tls12() {
            SSLSocketFactory factory;
            try {
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, null, null);
                factory = context.getSocketFactory();
            } catch (Exception failure) {
                factory = HttpsURLConnection.getDefaultSSLSocketFactory();
            }
            base = factory;
        }

        private static Socket on(Socket socket) {
            if (socket instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) socket;
                List<String> want = new ArrayList<>();
                List<String> supported = Arrays.asList(ssl.getSupportedProtocols());
                for (String p : new String[] {"TLSv1.2", "TLSv1.1", "TLSv1"}) if (supported.contains(p)) want.add(p);
                ssl.setEnabledProtocols(want.toArray(new String[want.size()]));
            }
            return socket;
        }

        @Override public String[] getDefaultCipherSuites() { return base.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return base.getSupportedCipherSuites(); }
        @Override public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return on(base.createSocket(s, host, port, autoClose));
        }
        @Override public Socket createSocket(String host, int port) throws IOException { return on(base.createSocket(host, port)); }
        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException {
            return on(base.createSocket(host, port, local, localPort));
        }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException { return on(base.createSocket(host, port)); }
        @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
            return on(base.createSocket(host, port, local, localPort));
        }
    }
}
