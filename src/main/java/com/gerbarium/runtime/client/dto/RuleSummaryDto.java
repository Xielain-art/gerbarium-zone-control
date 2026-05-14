package com.gerbarium.runtime.client.dto;

public class RuleSummaryDto {
    public String id;
    public String zoneId;
    public String name;
    public String entity;
    public String spawnType;
    public int aliveCount;
    public int normalPrimaryAlive;
    public int forcedPrimaryAlive;
    public int normalCompanionAlive;
    public int forcedCompanionAlive;
    public int maxAlive;
    public boolean active;
    public boolean enabled;

    // Config Details
    public String refillMode;
    public String boundaryMode;
    public int boundaryMaxOutsideSeconds;
    public int boundaryCheckIntervalTicks;
    public boolean boundaryTeleportBack;
    public int spawnCount;
    public int respawnSeconds;
    public double chance;
    public Integer timedMaxSpawnsPerActivation;
    public String cooldownStart;
    public boolean spawnWhenReady;
    public int failedSpawnRetrySeconds;
    public boolean despawnWhenZoneInactive;
    public boolean announceOnSpawn;
    public String spawnMode;
    public Integer fixedX;
    public Integer fixedY;
    public Integer fixedZ;
    public boolean allowSmallRoom;
    public int positionAttempts;
    public int minDistanceBetweenSpawns;
    public boolean spreadSpawns;
    
    // Runtime State
    public int timedSpawnedThisActivation;
    public long timedProgressMillis;
    public long nextTimedSpawnInMillis;
    public boolean timedBudgetExhausted;
    public long lastTimedBudgetResetAt;
    public long lastOnActivationAttemptActivationId;
    public long knownAlive;
    public long lastActivationSpawnAt;
    public long lastAttemptAt;
    public String lastAttemptResult = "NONE";
    public String lastAttemptReason = "";
    public String lastPositionSearchStats = "";
    public long lastSuccessAt;
    public int lastSuccessfulPrimaryCount;
    public int lastSuccessfulCompanionCount;
    public long nextAvailableAt;
    public long nextAttemptAt;
    public long lastDeathAt;
    public long lastBoundaryActionAt;
    public String lastBoundaryActionType = "";
    public int boundaryOutsideCount;
    public long boundaryLastScanAt;
    public String boundaryLastHint = "";
    public boolean encounterActive;
    public long encounterStartedAt;
    public long encounterClearedAt;
    public int encounterPrimaryAlive;
    public int encounterCompanionsAlive;
    public int lastEncounterPrimarySpawned;
    public int lastEncounterCompanionsSpawned;
    public long totalAttempts;
    public long totalSuccesses;
    public long totalPrimarySpawned;
    public long totalCompanionsSpawned;
    
    // Display helpers
    public String currentStatus = "";
    public String statusText = "";
    public String nextActionText = "";
    public String warningText = "";
    public String hintText = "";
    public String boundaryStatus = "";
    public String boundaryHint = "";
}
