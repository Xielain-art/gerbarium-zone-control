package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.RefillMode;
import com.gerbarium.runtime.state.RuleRuntimeState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TimedSpawnLogicTest {

    @Test
    public void testTimerProgresses() {
        MobRule rule = new MobRule();
        rule.refillMode = RefillMode.TIMED;
        rule.respawnSeconds = 10;

        RuleRuntimeState state = new RuleRuntimeState();
        long now = 1000000L;

        // First tick initializes lastTimedTickAt
        assertFalse(TimedSpawnLogic.shouldSpawn(now, rule, state));
        assertEquals(now, state.lastTimedTickAt);
        assertEquals(0, state.timedProgressMillis);

        // Second tick after 5 seconds
        now += 5000L;
        assertFalse(TimedSpawnLogic.shouldSpawn(now, rule, state));
        assertEquals(5000L, state.timedProgressMillis);

        // Third tick after another 5 seconds (total 10s)
        now += 5000L;
        assertTrue(TimedSpawnLogic.shouldSpawn(now, rule, state));
        assertEquals(0, state.timedProgressMillis);
    }

    @Test
    public void testTimerPausesAndResumes() {
        MobRule rule = new MobRule();
        rule.refillMode = RefillMode.TIMED;
        rule.respawnSeconds = 10;

        RuleRuntimeState state = new RuleRuntimeState();
        long now = 1000000L;

        // Initialize
        TimedSpawnLogic.shouldSpawn(now, rule, state);
        
        // Progress 5s
        now += 5000L;
        TimedSpawnLogic.shouldSpawn(now, rule, state);
        assertEquals(5000L, state.timedProgressMillis);

        // Deactivate (simulated by resetting lastTimedTickAt)
        TimedSpawnLogic.resetTimer(state);
        assertEquals(0, state.lastTimedTickAt);

        // Wait 1 hour (simulated)
        now += 3600000L;

        // Reactivate (first tick after pause)
        assertFalse(TimedSpawnLogic.shouldSpawn(now, rule, state));
        assertEquals(now, state.lastTimedTickAt);
        assertEquals(5000L, state.timedProgressMillis); // Progress was preserved!

        // Progress another 5s
        now += 5000L;
        assertTrue(TimedSpawnLogic.shouldSpawn(now, rule, state));
        assertEquals(0, state.timedProgressMillis);
    }
}