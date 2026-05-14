package com.gerbarium.runtime.spawn;

public final class SpawnMathUtil {
    private SpawnMathUtil() {
    }

    public static boolean isInsideZoneXZ(int x, int z, int minX, int maxX, int minZ, int maxZ) {
        return x >= Math.min(minX, maxX) && x <= Math.max(minX, maxX)
                && z >= Math.min(minZ, maxZ) && z <= Math.max(minZ, maxZ);
    }
}
