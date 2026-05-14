package com.gerbarium.runtime.spawn;

import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public final class SpawnPositionResult {
    private final BlockPos position;
    private final String reason;
    private final PositionRejectStats stats;
    private final int attempts;

    private SpawnPositionResult(BlockPos position, String reason, PositionRejectStats stats, int attempts) {
        this.position = position;
        this.reason = reason;
        this.stats = stats;
        this.attempts = attempts;
    }

    public static SpawnPositionResult success(BlockPos position, PositionRejectStats stats, int attempts) {
        return new SpawnPositionResult(position, "success", stats, attempts);
    }

    public static SpawnPositionResult fail(String reason, PositionRejectStats stats, int attempts) {
        return new SpawnPositionResult(null, reason, stats, attempts);
    }

    public boolean success() {
        return position != null;
    }

    public Optional<BlockPos> position() {
        return Optional.ofNullable(position);
    }

    public String reason() {
        return success() ? "success" : stats.dominantReason(reason);
    }

    public PositionRejectStats stats() {
        return stats;
    }

    public int attempts() {
        return attempts;
    }

    public String failureSummary() {
        return "reason=" + reason() + " attempts=" + attempts + " " + stats.format();
    }
}
