package com.gerbarium.runtime.config;

public class RuntimeConfig {
    public boolean debug = false;
    public int activationCheckIntervalTicks = 20;
    public int spawnCheckIntervalTicks = 100;
    public int resyncIntervalTicks = 1200;
    public int stateSaveIntervalTicks = 100;
    public int maxSpawnsPerTickCycle = 32;
    public int maxZonesProcessedPerSpawnTick = 20;
    public boolean autoReloadZones = false;
    public int autoReloadCheckIntervalTicks = 200;
}