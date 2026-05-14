package com.gerbarium.runtime.config;

public class RuntimeConfig {
    public boolean debug = false;
    public int activationCheckIntervalTicks = 20;
    public int spawnCheckIntervalTicks = 100;
    public int resyncIntervalTicks = 1200;
    public int stateSaveIntervalTicks = 100;
    public boolean boundaryControlEnabled = true;
    public int boundaryGlobalCheckIntervalTicks = 40;
    public int boundaryScanPadding = 32;
    public int boundaryMaxEntitiesPerTick = 128;
    public int maxSpawnsPerTickCycle = 32;
    public int maxZonesProcessedPerSpawnTick = 20;
    public int spawnPositionAttempts = 128;
    public int forceSpawnPositionAttempts = 512;
    public boolean autoReloadZones = false;
    public int autoReloadCheckIntervalTicks = 200;
}
