package com.gerbarium.runtime.spawn;

public final class SpawnGroundUtil {
    private SpawnGroundUtil() {
    }

    public static boolean isValidSpawnGround(boolean belowIsSolid, boolean allowNonSolidGround) {
        return allowNonSolidGround || belowIsSolid;
    }
}
