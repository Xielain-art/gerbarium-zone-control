package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.state.RuleRuntimeState;

public class TimedSpawnLogic {
    public static boolean shouldSpawn(long now, MobRule rule, RuleRuntimeState state) {
        return tick(now, rule, state);
    }

    public static boolean tick(long now, MobRule rule, RuleRuntimeState state) {
        if (state.lastTimedTickAt == 0) {
            state.lastTimedTickAt = now;
            state.nextTimedSpawnInMillis = rule.respawnSeconds * 1000L;
            return false;
        }

        long delta = now - state.lastTimedTickAt;
        state.lastTimedTickAt = now;
        state.timedProgressMillis += delta;

        long intervalMillis = rule.respawnSeconds * 1000L;
        if (state.timedProgressMillis >= intervalMillis) {
            state.timedProgressMillis = 0; 
            state.nextTimedSpawnInMillis = 0;
            return true;
        }

        state.nextTimedSpawnInMillis = Math.max(0, intervalMillis - state.timedProgressMillis);
        return false;
    }
    
    public static void resetTimer(RuleRuntimeState state) {
        state.lastTimedTickAt = 0;
        state.nextTimedSpawnInMillis = 0;
    }
}
