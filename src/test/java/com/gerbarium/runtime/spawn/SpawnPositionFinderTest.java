package com.gerbarium.runtime.spawn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnPositionFinderTest {
    @Test
    void insideZoneXzNormalizesReversedBounds() {
        assertTrue(SpawnMathUtil.isInsideZoneXZ(5, 5, 10, 0, 10, 0));
        assertFalse(SpawnMathUtil.isInsideZoneXZ(11, 5, 10, 0, 10, 0));
        assertFalse(SpawnMathUtil.isInsideZoneXZ(5, -1, 10, 0, 10, 0));
    }

    @Test
    void airOnlyGroundModeAllowsAirBelowSpawnPoint() {
        assertFalse(SpawnGroundUtil.isValidSpawnGround(false, false));
        assertTrue(SpawnGroundUtil.isValidSpawnGround(false, true));
        assertTrue(SpawnGroundUtil.isValidSpawnGround(true, false));
    }
}
