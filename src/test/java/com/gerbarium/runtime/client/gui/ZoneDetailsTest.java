package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.RuleSummaryDto;
import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ZoneDetailsTest {
    @Test
    public void testDtoBinding() {
        ZoneSummaryDto zone = new ZoneSummaryDto();
        zone.id = "test-zone";
        zone.minX = 10;
        zone.maxX = 20;
        
        assertEquals("test-zone", zone.id);
        assertEquals(10, zone.minX);
        assertEquals(20, zone.maxX);
        
        RuleSummaryDto rule = new RuleSummaryDto();
        rule.id = "test-rule";
        rule.maxAlive = 5;
        rule.respawnSeconds = 900;
        rule.chance = 0.5;
        zone.rules.add(rule);
        
        assertEquals(1, zone.rules.size());
        assertEquals("test-rule", zone.rules.get(0).id);
        assertEquals(5, zone.rules.get(0).maxAlive);
        assertEquals(900, zone.rules.get(0).respawnSeconds);
        assertEquals(0.5, zone.rules.get(0).chance);
    }
}