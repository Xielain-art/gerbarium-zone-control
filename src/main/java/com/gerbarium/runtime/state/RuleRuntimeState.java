package com.gerbarium.runtime.state;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RuleRuntimeState {
    public String zoneId;
    public String ruleId;

    // UUID-based alive tracking
    public Set<UUID> aliveEntityUuids = new HashSet<>();

    public boolean encounterActive;
    public long encounterStartedAt;
    public long encounterClearedAt;
    public int encounterPrimaryAlive;
    public int encounterCompanionsAlive;
    public int lastEncounterPrimarySpawned;
    public int lastEncounterCompanionsSpawned;

    public long lastActivationSpawnAt;
    public long lastOnActivationAttemptActivationId;

    public long lastAttemptAt;
    public String lastAttemptResult = "NONE";
    public String lastAttemptReason = "";
    public int lastPositionSearchAttempts;
    public String lastPositionSearchReason = "";
    public String lastPositionSearchStats = "";

    public long lastSuccessAt;
    public int lastSuccessfulPrimaryCount;
    public int lastSuccessfulCompanionCount;

    public long lastDeathAt;
    public int deathCount;
    public long lastBoundaryActionAt;
    public String lastBoundaryActionType = "";
    public int boundaryOutsideCount;
    public long boundaryLastScanAt;
    public String boundaryLastHint = "";

    // Unified timer fields
    public long nextAllowedAttemptTimeMillis;
    public long nextAttemptAt;
    public long nextAvailableAt;

    // After-death respawn
    public boolean hasPendingAfterDeathRespawn;
    public long pendingAfterDeathRespawnTimeMillis;

    public long timedProgressMillis;
    public long lastTimedTickAt;
    public long nextTimedSpawnInMillis;
    public int timedSpawnedThisActivation;
    public long timedBudgetActivationId;
    public long lastTimedBudgetResetAt;
    public boolean timedBudgetExhausted;

    public int knownAlive;

    public long totalAttempts;
    public long totalSuccesses;
    public long totalPrimarySpawned;
    public long totalCompanionsSpawned;

    public int getCurrentAliveCount() {
        return aliveEntityUuids != null ? aliveEntityUuids.size() : 0;
    }
}
