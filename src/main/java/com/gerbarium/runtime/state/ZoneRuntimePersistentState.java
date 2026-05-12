package com.gerbarium.runtime.state;

public class ZoneRuntimePersistentState {
    public String zoneId;
    public long lastActivatedAt;
    public long lastDeactivatedAt;
    public long lastPlayerSeenAt;
    public String lastActivationReason = "";
    public String lastDeactivationReason = "";
    public long totalActivations;
    public long totalSpawnAttempts;
    public long totalSuccessfulSpawns;
}