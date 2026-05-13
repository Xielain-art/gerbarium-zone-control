package com.gerbarium.runtime.state;

public class RuntimeEvent {
    public long time;
    public String zoneId;
    public String ruleId;
    public String type;
    public String message;
    public String entityType;
    public String role;
    public boolean forced;
    public String action;
    public int x;
    public int y;
    public int z;

    public RuntimeEvent() {}

    public RuntimeEvent(long time, String zoneId, String ruleId, String type, String message) {
        this.time = time;
        this.zoneId = zoneId;
        this.ruleId = ruleId;
        this.type = type;
        this.message = message;
    }
}
