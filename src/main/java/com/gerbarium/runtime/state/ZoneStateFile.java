package com.gerbarium.runtime.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZoneStateFile {
    public int version = 1;
    public String zoneId;
    public ZoneRuntimePersistentState zone;
    public Map<String, RuleRuntimeState> rules = new HashMap<>();
    public List<RuntimeEvent> recentEvents = new ArrayList<>();
    public transient boolean dirty = false;

    public ZoneStateFile() {}

    public ZoneStateFile(String zoneId) {
        this.zoneId = zoneId;
        this.zone = new ZoneRuntimePersistentState();
        this.zone.zoneId = zoneId;
    }
}
