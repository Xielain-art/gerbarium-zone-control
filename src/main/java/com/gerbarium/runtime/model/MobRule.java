package com.gerbarium.runtime.model;

import java.util.ArrayList;
import java.util.List;

public class MobRule {
    public String id;
    public String name;
    public String entity;
    public boolean enabled = true;
    public SpawnType spawnType = SpawnType.PACK;
    public RefillMode refillMode = RefillMode.ON_ACTIVATION;
    public String boundaryMode = BoundaryMode.LEASH.name();
    public int boundaryMaxOutsideSeconds = 10;
    public int boundaryCheckIntervalTicks = 40;
    public boolean boundaryTeleportBack = true;
    public int maxAlive = 10;
    public int spawnCount = 4;
    public int respawnSeconds = 900;
    public double chance = 1.0;
    public Integer timedMaxSpawnsPerActivation = null;
    public CooldownStart cooldownStart = CooldownStart.AFTER_ACTIVATION;
    public boolean spawnWhenReady = true;
    public int failedSpawnRetrySeconds = 60;
    public boolean despawnWhenZoneInactive = false;
    public boolean announceOnSpawn = false;
    public SpawnMode spawnMode = SpawnMode.RANDOM_VALID_POSITION;
    public Integer fixedX;
    public Integer fixedY;
    public Integer fixedZ;
    public boolean allowSmallRoom = true;
    public int positionAttempts = 128;
    public int minDistanceBetweenSpawns = 2;
    public boolean spreadSpawns = true;
    public List<CompanionRule> companions = new ArrayList<>();

    public void normalize() {
        if (companions == null) {
            companions = new ArrayList<>();
        }
        if (cooldownStart == null) {
            cooldownStart = CooldownStart.AFTER_ACTIVATION;
        }
        if (refillMode == null) {
            refillMode = RefillMode.ON_ACTIVATION;
        }
        if (spawnType == null) {
            spawnType = SpawnType.PACK;
        }
        if (spawnMode == null) {
            spawnMode = SpawnMode.RANDOM_VALID_POSITION;
        }
        if (boundaryMode == null || boundaryMode.isBlank()) {
            boundaryMode = BoundaryMode.LEASH.name();
        }
        if (name == null || name.isBlank()) {
            name = id;
        }
        chance = Math.max(0.0D, Math.min(1.0D, chance));
        maxAlive = Math.max(0, maxAlive);
        spawnCount = Math.max(0, spawnCount);
        respawnSeconds = Math.max(0, respawnSeconds);
        failedSpawnRetrySeconds = Math.max(1, failedSpawnRetrySeconds);
        boundaryMaxOutsideSeconds = Math.max(0, boundaryMaxOutsideSeconds);
        positionAttempts = Math.max(1, positionAttempts);
        minDistanceBetweenSpawns = Math.max(0, minDistanceBetweenSpawns);
        if (spawnMode == SpawnMode.BOSS_ROOM) {
            allowSmallRoom = true;
        }
    }
}
