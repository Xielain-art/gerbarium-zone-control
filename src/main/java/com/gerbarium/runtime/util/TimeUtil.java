package com.gerbarium.runtime.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralized time formatting utility.
 * TODO: Replace hardcoded English strings with Text.translatable keys for proper localization (ru_ru/en_us).
 */
public class TimeUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    public static String formatRelative(long timestamp) {
        return formatRelative(timestamp, System.currentTimeMillis());
    }

    public static String formatRelative(long timestamp, long now) {
        if (timestamp == 0) return "never";
        
        long diff = timestamp - now;
        long absDiff = Math.abs(diff);

        if (absDiff < SECOND) {
            return "just now";
        }

        if (absDiff >= DAY) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()).format(DATE_FORMATTER);
        }

        String formattedDuration = formatDuration(absDiff);
        if (diff > 0) {
            return "in " + formattedDuration;
        } else {
            return formattedDuration + " ago";
        }
    }

    public static String formatDuration(long millis) {
        if (millis < SECOND) return "just now";

        long hours = millis / HOUR;
        millis %= HOUR;
        long minutes = millis / MINUTE;
        millis %= MINUTE;
        long seconds = millis / SECOND;

        List<String> parts = new ArrayList<>();
        if (hours > 0) parts.add(pluralize(hours, "hour"));
        if (minutes > 0) parts.add(pluralize(minutes, "minute"));
        if (parts.size() < 2 && seconds > 0) parts.add(pluralize(seconds, "second"));

        // Only take the first two units
        if (parts.size() > 2) {
            parts = parts.subList(0, 2);
        }

        if (parts.size() == 1) {
            return parts.get(0);
        } else if (parts.size() == 2) {
            return parts.get(0) + " and " + parts.get(1);
        }

        return "just now";
    }

    private static String pluralize(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s");
    }
}
