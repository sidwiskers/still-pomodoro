package app.still.pomodoro;

import android.content.SharedPreferences;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

final class FocusHistory {
    private static final String DAY_PREFIX = "focus_day_";
    private final SharedPreferences prefs;

    FocusHistory(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    void addInterval(SharedPreferences.Editor edit, long startMillis, long endMillis) {
        if (startMillis <= 0L || endMillis <= startMillis) return;
        ZoneId zone = ZoneId.systemDefault();
        long cursor = startMillis;
        while (cursor < endMillis) {
            LocalDate day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate();
            long nextDay = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long sliceEnd = Math.min(endMillis, nextDay);
            if (sliceEnd <= cursor) break;
            String key = key(day);
            long current = Math.max(0L, prefs.getLong(key, 0L));
            edit.putLong(key, safeAdd(current, sliceEnd - cursor));
            cursor = sliceEnd;
        }
    }

    long millisFor(LocalDate day, long liveStartMillis, long liveEndMillis) {
        long total = Math.max(0L, prefs.getLong(key(day), 0L));
        if (liveStartMillis <= 0L || liveEndMillis <= liveStartMillis) return total;
        ZoneId zone = ZoneId.systemDefault();
        long dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long overlapStart = Math.max(dayStart, liveStartMillis);
        long overlapEnd = Math.min(dayEnd, liveEndMillis);
        if (overlapEnd > overlapStart) total = safeAdd(total, overlapEnd - overlapStart);
        return total;
    }

    private static String key(LocalDate day) {
        return DAY_PREFIX + day;
    }

    private static long safeAdd(long left, long right) {
        if (right <= 0L) return left;
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }
}
