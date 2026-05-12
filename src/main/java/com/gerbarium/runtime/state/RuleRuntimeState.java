package com.gerbarium.runtime.state;

public class RuleRuntimeState {
    public String zoneId;
    public String ruleId;

    public boolean encounterActive;
    public long encounterStartedAt;
    public long encounterClearedAt;
    public int encounterPrimaryAlive;
    public int encounterCompanionsAlive;
    public int lastEncounterPrimarySpawned;
    public int lastEncounterCompanionsSpawned;

    public long lastActivationSpawnAt;

    public long lastAttemptAt;
    public String lastAttemptResult = "NONE";
    public String lastAttemptReason = "";

    public long lastSuccessAt;
    public int lastSuccessfulPrimaryCount;
    public int lastSuccessfulCompanionCount;

    public long lastDeathAt;

    public long nextAttemptAt;
    public long nextAvailableAt;

    public long timedProgressMillis;
    public long lastTimedTickAt;
    public long nextTimedSpawnInMillis;

    public int knownAlive;

    public long totalAttempts;
    public long totalSuccesses;
    public long totalPrimarySpawned;
    public long totalCompanionsSpawned;
}