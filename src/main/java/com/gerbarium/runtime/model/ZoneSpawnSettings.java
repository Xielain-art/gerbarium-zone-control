package com.gerbarium.runtime.model;

public class ZoneSpawnSettings {
    public int minDistanceFromPlayer = 16;
    public int maxDistanceFromPlayer = 64;
    public int maxPositionAttempts = 12;
    public boolean requireLoadedChunk = true;
    public boolean respectVanillaSpawnRules = true;
}