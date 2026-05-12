package com.gerbarium.runtime.util;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import static org.junit.jupiter.api.Assertions.*;

public class TimeUtilTest {

    private static final long SECOND = 1000L;
    private static final long MINUTE = 60 * SECOND;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    @Test
    public void testFormatRelativeShortDurations() {
        long now = 1715510400000L; // Fixed time
        
        // Just now
        assertEquals("just now", TimeUtil.formatRelative(now, now));
        
        // Single unit
        assertEquals("1 minute ago", TimeUtil.formatRelative(now - MINUTE, now));
        assertEquals("in 5 minutes", TimeUtil.formatRelative(now + 5 * MINUTE, now));
        
        // Two units
        assertEquals("1 hour and 30 minutes ago", TimeUtil.formatRelative(now - HOUR - 30 * MINUTE, now));
        assertEquals("in 2 hours and 1 minute", TimeUtil.formatRelative(now + 2 * HOUR + MINUTE, now));
        
        // Mixed units
        assertEquals("45 seconds ago", TimeUtil.formatRelative(now - 45 * SECOND, now));
    }

    @Test
    public void testFormatAbsoluteLongDurations() {
        long now = 1715510400000L; // Fixed time
        long olderThanADay = now - DAY - HOUR;
        long futureMoreThanADay = now + DAY + HOUR;

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String expectedOld = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(olderThanADay), ZoneId.systemDefault()).format(formatter);
        String expectedFuture = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(futureMoreThanADay), ZoneId.systemDefault()).format(formatter);

        assertEquals(expectedOld, TimeUtil.formatRelative(olderThanADay, now));
        assertEquals(expectedFuture, TimeUtil.formatRelative(futureMoreThanADay, now));
    }

    @Test
    public void testFormatDuration() {
        assertEquals("2 hours and 15 minutes", TimeUtil.formatDuration(2 * HOUR + 15 * MINUTE));
        assertEquals("45 seconds", TimeUtil.formatDuration(45 * SECOND));
        assertEquals("just now", TimeUtil.formatDuration(0));
    }
}
