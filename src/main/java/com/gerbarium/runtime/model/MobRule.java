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
    public int maxAlive = 10;
    public int spawnCount = 4;
    public int respawnSeconds = 900;
    public double chance = 1.0;
    public CooldownStart cooldownStart = CooldownStart.AFTER_ACTIVATION;
    public boolean spawnWhenReady = true;
    public int failedSpawnRetrySeconds = 60;
    public boolean despawnWhenZoneInactive = false;
    public boolean announceOnSpawn = false;
    public List<CompanionRule> companions = new ArrayList<>();
}