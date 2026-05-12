package com.gerbarium.runtime.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RuleRuntimeStateSerializationTest {
    private static final Gson GSON = new GsonBuilder().create();

    @Test
    public void testTimedFieldsSerialization() {
        RuleRuntimeState state = new RuleRuntimeState();
        state.zoneId = "test-zone";
        state.ruleId = "test-rule";
        
        // These fields don't exist yet, so this will fail to compile
        state.timedProgressMillis = 1500L;
        state.lastTimedTickAt = 1000L;
        state.nextTimedSpawnInMillis = 5000L;

        String json = GSON.toJson(state);
        RuleRuntimeState deserialized = GSON.fromJson(json, RuleRuntimeState.class);

        assertEquals(1500L, deserialized.timedProgressMillis);
        assertEquals(1000L, deserialized.lastTimedTickAt);
        assertEquals(5000L, deserialized.nextTimedSpawnInMillis);
    }
}