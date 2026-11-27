package io.github.k8home;

import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Accepts explicitly formatted route data without assigning units to undocumented codes. */
final class RouteMetrics {
    private static final Pattern DISTANCE = Pattern.compile("([0-9][0-9.,]*)\\s*(km|m|mi)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION = Pattern.compile("(?:(\\d{1,3})\\s*(?:시간|h)\\s*)?"
        + "(?:(\\d{1,4})\\s*(?:분|min|m)\\s*)?", Pattern.CASE_INSENSITIVE);
    // 차 순정 Response_Info_Destination 은 "오후 12 시 2 분"처럼 분이 한 자리이거나 정각("오후 12 시")일 수 있다.
    private static final Pattern CLOCK = Pattern.compile("(?:(AM|PM|오전|오후)\\s*)?"
        + "(\\d{1,2})(?:\\s*:\\s*(\\d{2})|\\s*시(?:\\s*(\\d{1,2})\\s*분?)?)\\s*(AM|PM|오전|오후)?",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPACT_CLOCK = Pattern.compile("(\\d{2})(\\d{2})");

    private RouteMetrics() {}

    static String distance(String value) {
        Matcher parsed = DISTANCE.matcher(value.trim());
        return parsed.matches() ? parsed.group(1) + " " + parsed.group(2).toLowerCase(Locale.ROOT) : "";
    }

    static String duration(String value) {
        Matcher parsed = DURATION.matcher(value.trim());
        if (!parsed.matches() || (parsed.group(1) == null && parsed.group(2) == null)) return "";
        int hours = parsed.group(1) == null ? 0 : Integer.parseInt(parsed.group(1));
        int minutes = parsed.group(2) == null ? 0 : Integer.parseInt(parsed.group(2));
        return minutesText(hours * 60 + minutes);
    }

    static String remainingFromArrival(String value, Calendar now) {
        int arrival = minuteOfDay(value);
        if (arrival < 0) return "";
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        int remaining = arrival - current;
        if (remaining < -720) remaining += 1440;
        return remaining >= 0 ? minutesText(remaining) : "";
    }

    static String arrivalLabel(String value) {
        int total = minuteOfDay(value);
        if (total < 0) return "";
        int hour = total / 60;
        return String.format(Locale.ROOT, "%d:%02d %s", hour % 12 == 0 ? 12 : hour % 12,
            total % 60, hour < 12 ? "AM" : "PM");
    }

    static int minuteOfDay(String value) {
        Matcher clock = CLOCK.matcher(value.trim());
        int hour;
        int minute;
        if (clock.matches()) {
            hour = Integer.parseInt(clock.group(2));
            String minStr = clock.group(3) != null ? clock.group(3) : clock.group(4);
            minute = minStr == null ? 0 : Integer.parseInt(minStr);
            String period = clock.group(1) == null ? clock.group(5) : clock.group(1);
            if (period != null) {
                if (hour < 1 || hour > 12) return -1;
                hour %= 12;
                if ("pm".equalsIgnoreCase(period) || "오후".equals(period)) hour += 12;
            }
        } else {
            Matcher compact = COMPACT_CLOCK.matcher(value.trim());
            if (!compact.matches()) return -1;
            hour = Integer.parseInt(compact.group(1));
            minute = Integer.parseInt(compact.group(2));
        }
        return hour < 24 && minute < 60 ? hour * 60 + minute : -1;
    }

    private static String minutesText(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        return hours == 0 ? rest + "분" : hours + "시간" + (rest == 0 ? "" : " " + rest + "분");
    }
}
