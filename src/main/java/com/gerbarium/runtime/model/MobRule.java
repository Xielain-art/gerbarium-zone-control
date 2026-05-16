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
    public SpawnTrigger spawnTrigger = SpawnTrigger.TIMER;
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
    public int retrySeconds = -1;
    public boolean despawnWhenZoneInactive = false;
    public boolean announceOnSpawn = false;
    public boolean respawnAfterDeath = false;
    public boolean respawnAfterDespawn = false;
    public int afterDeathDelaySeconds = -1;
    public SpawnMode spawnMode = SpawnMode.RANDOM_VALID_POSITION;
    public Integer fixedX;
    public Integer fixedY;
    public Integer fixedZ;
    public boolean allowSmallRoom = true;
    public int positionAttempts = 128;
    public int minDistanceBetweenSpawns = 2;
    public boolean spreadSpawns = true;
    public boolean requirePlayerNearby = false;
    public int playerActivationRange = 64;
    public boolean requireChunkLoaded = true;
    public boolean allowForceLoad = false;
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

        // Backward compatibility: map old refillMode/spawnType to spawnTrigger
        if (spawnTrigger == null) {
            if (spawnType == SpawnType.UNIQUE) {
                if (refillMode == RefillMode.AFTER_DEATH) {
                    spawnTrigger = SpawnTrigger.AFTER_DEATH;
                } else if (refillMode == RefillMode.TIMED) {
                    spawnTrigger = SpawnTrigger.TIMER_AND_AFTER_DEATH;
                } else {
                    spawnTrigger = SpawnTrigger.TIMER;
                }
            } else {
                if (refillMode == RefillMode.TIMED) {
                    spawnTrigger = SpawnTrigger.TIMER;
                } else if (refillMode == RefillMode.AFTER_DEATH) {
                    spawnTrigger = SpawnTrigger.AFTER_DEATH;
                } else if (refillMode == RefillMode.ON_ACTIVATION) {
                    spawnTrigger = SpawnTrigger.TIMER;
                } else {
                    spawnTrigger = SpawnTrigger.TIMER;
                }
            }
        }

        // Defaults for new fields
        if (retrySeconds < 0) {
            retrySeconds = failedSpawnRetrySeconds;
        }
        if (afterDeathDelaySeconds < 0) {
            afterDeathDelaySeconds = respawnSeconds;
        }
        if (playerActivationRange <= 0) {
            playerActivationRange = 64;
        }

        chance = Math.max(0.0D, Math.min(1.0D, chance));
        maxAlive = Math.max(0, maxAlive);
        spawnCount = Math.max(0, spawnCount);
        respawnSeconds = Math.max(0, respawnSeconds);
        retrySeconds = Math.max(1, retrySeconds);
        failedSpawnRetrySeconds = Math.max(1, failedSpawnRetrySeconds);
        afterDeathDelaySeconds = Math.max(0, afterDeathDelaySeconds);
        boundaryMaxOutsideSeconds = Math.max(0, boundaryMaxOutsideSeconds);
        positionAttempts = Math.max(1, positionAttempts);
        minDistanceBetweenSpawns = Math.max(0, minDistanceBetweenSpawns);
        playerActivationRange = Math.max(1, playerActivationRange);
        if (spawnMode == SpawnMode.BOSS_ROOM) {
            allowSmallRoom = true;
        }
    }
}
