package com.gerbarium.runtime.tracking;

public class ManagedMobInfo {
    public final String zoneId;
    public final String ruleId;
    public final String role;
    public final boolean forced;

    public ManagedMobInfo(String zoneId, String ruleId, String role, boolean forced) {
        this.zoneId = zoneId;
        this.ruleId = ruleId;
        this.role = role;
        this.forced = forced;
    }
}