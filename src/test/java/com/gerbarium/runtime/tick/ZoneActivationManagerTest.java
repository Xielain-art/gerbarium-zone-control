package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.state.ZoneRuntimePersistentState;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ZoneActivationManagerTest {
    @Test
    void deactivationFlowResetsSharedZoneState() {
        Zone zone = new Zone();
        zone.id = "test-zone";

        ZoneRuntimeState zState = new ZoneRuntimeState(zone.id);
        zState.active = true;
        zState.firstSpawnDelayPassed = true;

        ZoneRuntimePersistentState pState = new ZoneRuntimePersistentState();
        long now = 123456789L;

        ZoneActivationManager.applyDeactivationState(
                zone,
                zState,
                pState,
                now,
                "force_deactivate by admin",
                "FORCE_DEACTIVATED",
                "Zone force deactivated by admin"
        );

        assertFalse(zState.active);
        assertFalse(zState.firstSpawnDelayPassed);
        assertEquals(now, pState.lastDeactivatedAt);
        assertEquals("force_deactivate by admin", pState.lastDeactivationReason);
    }
}
