package com.gerbarium.runtime.client.dto;

public class RuleSummaryDto {
    public String id;
    public String zoneId;
    public String name;
    public String entity;
    public String spawnType;
    public int aliveCount;
    public int maxAlive;
    public boolean active;

    // Config Details
    public String refillMode;
    public int spawnCount;
    public int respawnSeconds;
    public double chance;
    public Integer timedMaxSpawnsPerActivation;
    public String cooldownStart;
    
    // Runtime State
    public int timedSpawnedThisActivation;
    public long timedProgressMillis;
    public boolean timedBudgetExhausted;
    public long lastTimedBudgetResetAt;
    public long lastAttemptAt;
    public String lastAttemptResult = "NONE";
    public String lastAttemptReason = "";
    public long lastSuccessAt;
    public long nextAvailableAt;
    public long nextAttemptAt;
    public long totalAttempts;
    public long totalSuccesses;
    
    // UNIQUE fields
    public boolean encounterActive;
    public long encounterStartedAt;
    public long encounterClearedAt;
    public int encounterPrimaryAlive;
    public int encounterCompanionsAlive;
    
    // Display helpers
    public String statusText = "";
    public String nextActionText = "";
    public String warningText = "";
    public String hintText = "";
}
