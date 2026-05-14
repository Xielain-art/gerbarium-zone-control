package com.gerbarium.runtime.spawn;

public final class PositionRejectStats {
    public int chunkUnloaded;
    public int outsideWorldHeight;
    public int outsideZone;
    public int feetNotAir;
    public int headNotAir;
    public int noSolidFloor;
    public int collision;
    public int zoneTooShort;
    public int tooCloseToOtherSpawn;
    public int invalidDimension;
    public int validPositions;

    public String dominantReason(String fallback) {
        int max = 0;
        String reason = fallback == null || fallback.isBlank() ? "no_valid_position" : fallback;

        if (noSolidFloor > max) {
            max = noSolidFloor;
            reason = "no_solid_floor";
        }
        if (feetNotAir > max) {
            max = feetNotAir;
            reason = "feet_not_air";
        }
        if (headNotAir > max) {
            max = headNotAir;
            reason = "head_not_air";
        }
        if (collision > max) {
            max = collision;
            reason = "collision";
        }
        if (chunkUnloaded > max) {
            max = chunkUnloaded;
            reason = "chunk_unloaded";
        }
        if (tooCloseToOtherSpawn > max) {
            max = tooCloseToOtherSpawn;
            reason = "too_close_to_other_spawn";
        }
        if (zoneTooShort > max) {
            reason = "zone_too_short";
        }

        return reason;
    }

    public String format() {
        return "validPositions=" + validPositions
                + " rejected_not_air=" + (feetNotAir + headNotAir)
                + " rejected_no_ground=" + noSolidFloor
                + " rejected_chunk_unloaded=" + chunkUnloaded
                + " rejected_outside_world=" + outsideWorldHeight
                + " rejected_collision=" + collision
                + " rejected_too_close_to_other_spawn=" + tooCloseToOtherSpawn
                + " rejected_invalid_dimension=" + invalidDimension
                + " outsideZone=" + outsideZone
                + " zoneTooShort=" + zoneTooShort;
    }
}
