package io.github.k8home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/** Foreground-only voice memo recording and playback. All calls run on the UI thread. */
final class VoiceMemos {
    static final int EXPORT_REQUEST = 25;
    interface Listener {
        void changed(String mode, String active, JSONArray list, long freeMb);
    }

    private final Activity activity;
    private final File directory;
    private final AudioManager audio;
    private final Listener listener;
    private final AudioManager.OnAudioFocusChangeListener focusListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override public void onAudioFocusChange(int change) { if (change < 0) stop(); }
    };
    private MediaRecorder recorder;
    private MediaPlayer player;
    private File pending;
    private String playing = "";
    private String exporting;

    VoiceMemos(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        directory = new File(activity.getFilesDir(), "voice-memos");
        audio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
    }

    void action(String action) {
        if ("memo-refresh".equals(action)) {
            publish();
        } else if ("memo-record".equals(action)) {
            record();
        } else if ("memo-stop".equals(action)) {
            stop();
        } else if (action.startsWith("memo-play:")) {
            String id = action.substring("memo-play:".length());
            if (validId(id)) play(id);
        } else if (action.startsWith("memo-delete:")) {
            String id = action.substring("memo-delete:".length());
            if (validId(id) && recorder == null) confirmDelete(id);
        } else if (action.startsWith("memo-export:")) {
            String id = action.substring("memo-export:".length());
            if (validId(id) && recorder == null) chooseExportLocation(id);
        }
    }

    String exportId() { return exporting; }

    void restoreExport(String id) { if (id != null && validId(id)) exporting = id; }

    private void chooseExportLocation(String id) {
        if (exporting != null || !file(id).isFile()) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("audio/mp4")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "음성 메모-" + id + ".m4a");
        try {
            exporting = id;
            activity.startActivityForResult(intent, EXPORT_REQUEST);
        } catch (ActivityNotFoundException | SecurityException ignored) {
            exporting = null;
            message("이 기기에서 저장 위치를 선택할 수 없습니다.");
        }
    }

    void exportResult(final Uri uri) {
        if (exporting == null) return;
        final File source = file(exporting);
        exporting = null;
        if (uri == null) return;
        new Thread(new Runnable() {
            @Override public void run() {
                boolean success = false;
                try (FileInputStream input = new FileInputStream(source);
                     OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IOException("Output unavailable");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.flush();
                    success = true;
                } catch (IOException | SecurityException ignored) {
                    success = false;
                }
                final boolean completed = success;
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!activity.isDestroyed()) message(completed ? "음성 메모를 내보냈습니다." : "음성 메모를 내보낼 수 없습니다.");
                    }
                });
            }
        }, "memo-export").start();
    }

    private void record() {
        if (recorder != null) return;
        stop();
        if (!requestAudio()) return;
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Voice memo storage unavailable");
            pending = File.createTempFile("memo-", ".tmp", directory);
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(pending.getAbsolutePath());
            recorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
                @Override public void onError(MediaRecorder source, int what, int extra) {
                    if (source == recorder) recordingFailed("마이크 녹음이 중단되었습니다.");
                }
            });
            recorder.prepare();
            recorder.start();
            publish();
        } catch (IOException | RuntimeException ignored) {
            recordingFailed("이 기기에서 마이크 녹음을 시작할 수 없습니다.");
        }
    }

    void stop() {
        if (recorder == null && player == null) return;
        if (recorder != null) {
            try {
                recorder.stop();
                long id = Math.max(0, System.currentTimeMillis());
                File finished = file(String.format(Locale.US, "%013d", id));
                while (finished.exists()) finished = file(String.format(Locale.US, "%013d", ++id));
                if (!pending.renameTo(finished)) message("음성 메모를 저장할 수 없습니다.");
            } catch (RuntimeException ignored) {
                message("저장할 녹음이 없습니다.");
            } finally {
                releaseRecorder();
            }
        }
        if (player != null) {
            player.release();
            player = null;
            playing = "";
        }
        releaseAudio();
        publish();
    }

    private void play(String id) {
        if (recorder != null || !file(id).isFile()) return;
        stop();
        if (!requestAudio()) return;
        player = new MediaPlayer();
        playing = id;
        try {
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(file(id).getAbsolutePath());
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer source) {
                    if (source != player) return;
                    try { source.start(); } catch (IllegalStateException ignored) { playbackFailed(); }
                }
            });
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer source) { if (source == player) stop(); }
            });
            player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer source, int what, int extra) {
                    if (source == player) playbackFailed();
                    return true;
                }
            });
            player.prepareAsync();
            publish();
        } catch (IOException | RuntimeException ignored) {
            playbackFailed();
        }
    }

    private void confirmDelete(final String id) {
        if (!file(id).isFile()) return;
        new AlertDialog.Builder(activity).setMessage("이 음성 메모를 삭제할까요?")
            .setNegativeButton("취소", null)
            .setPositiveButton("삭제", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    if (id.equals(playing)) stop();
                    if (!file(id).delete()) message("음성 메모를 삭제할 수 없습니다.");
                    publish();
                }
            }).show();
    }

    private boolean requestAudio() {
        if (audio != null && audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return true;
        message("오디오를 사용할 수 없습니다.");
        return false;
    }

    private void releaseAudio() {
        if (audio != null) audio.abandonAudioFocus(focusListener);
    }

    private void recordingFailed(String description) {
        releaseRecorder();
        releaseAudio();
        message(description);
        publish();
    }

    private void releaseRecorder() {
        if (recorder != null) recorder.release();
        recorder = null;
        if (pending != null && pending.exists()) pending.delete();
        pending = null;
    }

    private void playbackFailed() {
        message("음성 메모를 재생할 수 없습니다.");
        stop();
    }

    private void publish() {
        String[] names = directory.list(new FilenameFilter() {
            @Override public boolean accept(File dir, String name) { return name.matches("[0-9]{13}\\.m4a"); }
        });
        JSONArray list = new JSONArray();
        if (names != null) {
            Arrays.sort(names, Collections.reverseOrder());
            for (String name : names) list.put(name.substring(0, 13));
        }
        listener.changed(recorder != null ? "recording" : player != null ? "playing" : "idle", playing,
            list, activity.getFilesDir().getUsableSpace() / (1024 * 1024));
    }

    private static boolean validId(String id) { return id.matches("[0-9]{13}"); }

    private File file(String id) { return new File(directory, id + ".m4a"); }

    private void message(String value) { Toast.makeText(activity, value, Toast.LENGTH_SHORT).show(); }
}
