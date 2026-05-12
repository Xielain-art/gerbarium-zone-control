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
    public String cooldownStart;
    
    // Runtime State
    public long lastAttemptAt;
    public String lastAttemptResult = "NONE";
    public String lastAttemptReason = "";
    public long lastSuccessAt;
    public long nextAvailableAt;
    public long totalAttempts;
    public long totalSuccesses;
}