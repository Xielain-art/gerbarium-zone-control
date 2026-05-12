package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.util.TimeUtil;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import static org.junit.jupiter.api.Assertions.*;

public class GuiFormattingTest {

    @Test
    public void testEventTimeFormatting() {
        long now = System.currentTimeMillis();
        long fiveMinutesAgo = now - (5 * 60 * 1000);
        
        // Expected behavior: "5 minutes ago"
        assertEquals("5 minutes ago", TimeUtil.formatRelative(fiveMinutesAgo, now));
    }

    @Test
    public void testRuleCooldownFormatting() {
        long now = System.currentTimeMillis();
        long inTenSeconds = now + (10 * 1000);
        
        // Expected behavior: "in 10 seconds"
        assertEquals("in 10 seconds", TimeUtil.formatRelative(inTenSeconds, now));
    }

    @Test
    public void testOldTimestampFormatting() {
        long now = System.currentTimeMillis();
        long twoDaysAgo = now - (2 * 24 * 60 * 60 * 1000);
        
        // Expected behavior: absolute date
        String expected = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(twoDaysAgo), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        assertEquals(expected, TimeUtil.formatRelative(twoDaysAgo, now));
    }
}
