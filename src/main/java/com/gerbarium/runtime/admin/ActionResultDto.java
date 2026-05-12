package com.gerbarium.runtime.admin;

public class ActionResultDto {
    public boolean success;
    public String message = "";
    public String details = "";
    public int primarySpawned;
    public int companionsSpawned;
    public int removed;
    public int loadedZones;
    public int skippedZones;
    public int enabledZones;
    public String errorCode = "";

    public ActionResultDto(boolean success, String message) {
        this.success = success;
        this.message = message;
    }
}