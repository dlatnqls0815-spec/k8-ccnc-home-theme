package io.github.k8home;

import android.content.IntentFilter;
import android.os.Bundle;

import java.util.Locale;

/** Payloads consumed by the stock Gen5W launcher's audio widgets. */
final class StockMedia {
    static final String REQUEST = "com.hkmc.request.widgetupdate";
    private static final String BT = "com.hkmc.audio.btaudio.widgetupdate";
    private static final String USB = "com.hkmc.audio.usb.widgetupdate";
    static final String RADIO = "com.hkmc.radio.widgetupdate";
    private static final String NATURE = "com.hkmc.nature_sound.widgetupdate";
    private static final String CARPLAY = "com.hkmc.dipoaudio.widgetupdate";
    // Android Auto 음악(순정 HKMC_GPM): 블루투스·USB 와 같은 항목(filename·artist_name·playstate·audio_*_time), 2026-11-27 K8 확인
    private static final String ANDROID_AUTO = "com.hkmc.gpmaudio.widgetupdate";

    /** ModeManager.ACTION_MODE_CHANGED with ModeConstants$ModeType in EXTRA_MODE_TYPE (automotive.odex). */
    static final String MODE_CHANGED = "com.hkmc.intent.action.MODE_CHANGED";
    static final String MODE_TYPE = "com.hkmc.mode.EXTRA_MODE_TYPE";

    static final StockMedia EMPTY = new StockMedia("", "", "", "", null, -1, -1, -1, -1);

    final String source, title, artist, settings;
    final Boolean playing;
    final long position, duration;
    private final int repeatMode, shuffleMode;

    private StockMedia(String source, String title, String artist, String settings, Boolean playing,
            long position, long duration, int repeatMode, int shuffleMode) {
        this.source = source;
        this.title = title;
        this.artist = artist;
        this.settings = settings;
        this.playing = playing;
        this.position = position;
        this.duration = duration;
        this.repeatMode = repeatMode;
        this.shuffleMode = shuffleMode;
    }

    static void addActions(IntentFilter filter) {
        filter.addAction(BT);
        filter.addAction(USB);
        filter.addAction(RADIO);
        filter.addAction(NATURE);
        filter.addAction(CARPLAY);
        filter.addAction(ANDROID_AUTO);
        filter.addAction(MODE_CHANGED);
    }

    /** Stock audio mode number to the theme's source id and an initial label, or null when not audio. */
    static String[] modeSource(int mode) {
        switch (mode) {
            case 0x2: case 0x3: return new String[] {"fm", "FM"};
            case 0x4: return new String[] {"am", "AM"};
            case 0x36: return new String[] {"bt", "블루투스 오디오"};
            case 0x21: case 0x31: return new String[] {"usb", "USB 음악"};
            case 0x81: return new String[] {"nature", "자연의 소리"};
            case 0x73: return new String[] {"carplay", "CarPlay"};
            case 0x74: return new String[] {"aa", "Android Auto"};   // ModeType.GPM
            default: return null;
        }
    }

    StockMedia update(String action, Bundle data) {
        if (data == null) return this;
        String nextSource;
        if (MODE_CHANGED.equals(action)) {
            Object raw = data.get(MODE_TYPE);
            if (!(raw instanceof Number)) return this;
            String[] next = modeSource(((Number) raw).intValue());
            // Keep the current details when the source did not change; widget broadcasts fill the rest.
            if (next == null || next[0].equals(source)) return this;
            return new StockMedia(next[0], next[1], "", "", null, -1, -1, -1, -1);
        }
        if (RADIO.equals(action)) return radio(data);
        if (BT.equals(action)) nextSource = "bt";
        else if (USB.equals(action)) nextSource = "usb";
        else if (NATURE.equals(action)) nextSource = "nature";
        else if (CARPLAY.equals(action)) nextSource = "carplay";
        else if (ANDROID_AUTO.equals(action)) { nextSource = "aa"; android.util.Log.i("K8Media", "aa widget keys " + data.keySet()); }
        else return this;

        if (Boolean.TRUE.equals(flag(data.get("invisible")))
                || Boolean.FALSE.equals(flag(data.get("valid_media")))) {
            return nextSource.equals(source) ? EMPTY : this;
        }
        boolean sameSource = nextSource.equals(source);
        String titleKey = "nature".equals(nextSource) ? "title" : "filename";
        String nextTitle = data.containsKey(titleKey) ? text(data.get(titleKey)) : sameSource ? title : "";
        if (nextTitle.isEmpty()) return sameSource && data.containsKey(titleKey) ? EMPTY : this;
        boolean sameTitle = sameSource && nextTitle.equals(title);
        String nextArtist = data.containsKey("artist_name") ? text(data.get("artist_name")) : sameTitle ? artist : "";
        boolean sameTrack = sameTitle && nextArtist.equals(artist);
        // 자연의 소리는 playstate=1 바로 뒤에 -1(바뀜 없음)을 보낸다(2026-11-24 실차): 0/1 이 아니면 앞 값을 유지한다.
        Boolean sentPlaying = data.containsKey("playstate") ? flag(data.get("playstate")) : null;
        Boolean nextPlaying = sentPlaying != null ? sentPlaying : sameSource ? playing : null;
        long nextPosition = data.containsKey("audio_current_time") ? time(data.get("audio_current_time"))
            : sameTrack ? position : -1;
        long nextDuration = data.containsKey("audio_total_time") ? time(data.get("audio_total_time"))
            : sameTrack ? duration : -1;
        boolean usb = "usb".equals(nextSource);
        int nextRepeat = usb ? mode(data, "repeat", sameSource ? repeatMode : -1) : -1;
        int nextShuffle = usb ? mode(data, "shuffle", sameSource ? shuffleMode : -1) : -1;
        return new StockMedia(nextSource, nextTitle, nextArtist, usbModes(nextRepeat, nextShuffle),
            nextPlaying, nextPosition, nextDuration, nextRepeat, nextShuffle);
    }

    private StockMedia radio(Bundle data) {
        String band = text(data.get("radio_mode")).toUpperCase(Locale.ROOT);
        String nextSource = band.equals("AM") ? "am" : band.startsWith("FM") ? "fm"
            : (source.equals("am") || source.equals("fm") || source.equals("radio")) ? source : "radio";
        boolean sameSource = nextSource.equals(source);
        String nextFrequency = data.containsKey("frq_total") ? frequency(nextSource, data.get("frq_total"))
            : sameSource ? title : "";
        String station = data.containsKey("broad_name") ? text(data.get("broad_name")) : sameSource ? artist : "";
        Object preset = data.get("preset_num");
        String presetLabel = preset instanceof Number && ((Number) preset).intValue() > 0
            ? "프리셋 " + ((Number) preset).intValue() : data.containsKey("preset_num") ? "" : sameSource ? settings : "";
        Boolean off = flag(data.get("radio_sound_off"));
        if (nextFrequency.isEmpty() && station.isEmpty() && off == null) return this;
        String label = nextFrequency.isEmpty() ? (Boolean.TRUE.equals(off) ? "라디오 꺼짐" : station) : nextFrequency;
        Boolean playing = off == null ? sameSource ? this.playing : null : !off;
        return new StockMedia(nextSource, label, label.equals(station) ? "" : station, presetLabel, playing, -1, -1, -1, -1);
    }

    static String frequency(String band, Object value) {
        if (!(value instanceof Number)) return "";
        double frequency = ((Number) value).doubleValue();
        if ("am".equals(band) && frequency >= 500 && frequency <= 1800 && frequency == Math.rint(frequency)) {
            return ((Number) value).intValue() + " kHz";
        }
        if ("fm".equals(band) && frequency >= 76 && frequency <= 108) {
            return String.format(Locale.US, "%.1f MHz", frequency);
        }
        return "";
    }

    private static int mode(Bundle data, String key, int current) {
        Object value = data.get(key);
        return value instanceof Number ? ((Number) value).intValue() : current;
    }

    private static String usbModes(int repeatMode, int shuffleMode) {
        String repeat;
        switch (repeatMode) {
            case 0: repeat = "반복 끔"; break;
            case 1: repeat = "한 곡 반복"; break;
            case 2: repeat = "폴더 반복"; break;
            case 3: repeat = "반복 켜짐"; break;
            default: repeat = ""; break;
        }
        String shuffle = "";
        if (shuffleMode == 7) shuffle = "셔플 끔";
        else if (shuffleMode == 9 || shuffleMode == 10) shuffle = "셔플 켜짐";
        if (repeat.isEmpty()) return shuffle;
        return shuffle.isEmpty() ? repeat : repeat + " · " + shuffle;
    }

    static String text(Object value) {
        if (!(value instanceof CharSequence)) return "";
        String text = value.toString().trim();
        return text.substring(0, Math.min(160, text.length()));
    }

    private static Boolean flag(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        String flag = String.valueOf(value);
        if ("1".equals(flag) || "true".equalsIgnoreCase(flag)) return true;
        if ("0".equals(flag) || "false".equalsIgnoreCase(flag)) return false;
        return null;
    }

    private static long time(Object value) {
        if (value == null) return -1;
        try {
            long result = value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
            return Math.max(-1, result);
        } catch (NumberFormatException failure) {
            return -1;
        }
    }
}
