package io.github.k8home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Locale;

/** Reads completed AVRCP covers using the same cache as K8 Now Playing. */
final class BluetoothCoverArt {
    interface Listener {
        void changed(String image);
    }

    private static final File[] DIRECTORIES = {
        new File("/mnt/bt_cover_art/0"), new File("/mnt/bt_cover_art/1")
    };
    private static String cachedTrack = "", cachedImage = "", cachedFile = "";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("k8-album-art");
    private final Handler worker;
    private final Listener listener;
    private String track = "";
    private String lastFile = "";
    private int generation;

    BluetoothCoverArt(Listener listener) {
        this.listener = listener;
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    void setTrack(String nextTrack) {
        setTrack(nextTrack, 0);
    }

    /** notBefore: 이 시각(ms) 뒤에 생긴 표지도 받는다. 테마가 가려져 있는 동안 곡이 바뀌면 표지 파일이 4초보다 오래돼
     *  돌아왔을 때 못 찾았다(2026-11-27): 가려진 시각부터 찾게 한다. 0 이면 평소처럼 4초 안. */
    void setTrack(String nextTrack, long notBefore) {
        if (track.equals(nextTrack)) return;
        track = nextTrack;
        int request = ++generation;
        worker.removeCallbacksAndMessages(null);
        if (!track.isEmpty() && track.equals(cachedTrack)) {
            lastFile = cachedFile;
            listener.changed(cachedImage);
            return;
        }
        listener.changed("");
        if (track.isEmpty()) return;
        String previousFile = lastFile.isEmpty() ? cachedFile : lastFile;
        long earliest = notBefore > 0 ? Math.min(notBefore, System.currentTimeMillis() - 4000) : System.currentTimeMillis() - 4000;
        long deadline = SystemClock.elapsedRealtime() + 10000;
        final boolean[] retried = {false};
        worker.post(new Runnable() {
            @Override public void run() {
                File cover = newestCover(earliest);
                String key = cover == null ? "" : cover.getPath() + ":" + cover.length() + ":" + cover.lastModified();
                String image = cover == null || key.equals(previousFile) ? "" : encodeCover(cover);
                final Runnable lookup = this;
                main.post(new Runnable() {
                    @Override public void run() {
                        if (generation != request) return;
                        if (!image.isEmpty()) {
                            lastFile = key;
                            cachedTrack = nextTrack;
                            cachedImage = image;
                            cachedFile = key;
                            listener.changed(image);
                        } else if (SystemClock.elapsedRealtime() < deadline) {
                            worker.postDelayed(lookup, 600);
                        } else if (!retried[0]) {
                            // 폰이 커버를 늦게 보낼 때가 있다(10초 뒤): 15초 뒤 한 번만 더 본다(곡 시작 뒤 파일이면 받음).
                            retried[0] = true;
                            worker.postDelayed(lookup, 15000);
                        }
                    }
                });
            }
        });
    }

    void close() {
        setTrack("");
        thread.quitSafely();
    }

    private static File newestCover(long earliest) {
        File newest = null;
        for (File directory : DIRECTORIES) {
            File[] files = directory.listFiles();
            if (files == null) continue;
            for (File file : files) {
                long modified = file.lastModified();
                if (file.getName().toLowerCase(Locale.ROOT).endsWith(".jpg") && file.length() > 0
                    && file.canRead() && modified >= earliest
                    && (newest == null || modified > newest.lastModified())) newest = file;
            }
        }
        return newest;
    }

    private static String encodeCover(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), options);
        int side = Math.max(options.outWidth, options.outHeight);
        if (options.outWidth <= 0 || options.outHeight <= 0) return "";
        options.inJustDecodeBounds = false;
        options.inSampleSize = 1;
        while (side / options.inSampleSize > 512) options.inSampleSize *= 2;
        // 화면에 그리는 크기(최대 약 384px)로 줄여 보낸다: 차 WebView 가 처음 그릴 때 그림을 푸는 데 96ms 걸렸다(2026-11-27).
        Bitmap decoded = BitmapFactory.decodeFile(file.getPath(), options);
        if (decoded == null) return "";
        int big = Math.max(decoded.getWidth(), decoded.getHeight());
        Bitmap bitmap = decoded;
        if (big > 384) {
            float k = 384f / big;
            bitmap = Bitmap.createScaledBitmap(decoded, Math.max(1, Math.round(decoded.getWidth() * k)),
                Math.max(1, Math.round(decoded.getHeight() * k)), true);
            if (bitmap != decoded) decoded.recycle();
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) return "";
            return "data:image/jpeg;base64," + Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
        } finally {
            bitmap.recycle();
        }
    }
}
