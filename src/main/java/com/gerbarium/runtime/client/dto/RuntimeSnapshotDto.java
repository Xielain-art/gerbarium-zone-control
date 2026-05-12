package com.gerbarium.runtime.client.dto;

import java.util.ArrayList;
import java.util.List;

public class RuntimeSnapshotDto {
    public boolean debug;
    public int totalZones;
    public int enabledZones;
    public int activeZones;
    public int managedPrimaryCount;
    public int managedCompanionCount;
    public int recentEventsCount;
    public List<ZoneSummaryDto> zones = new ArrayList<>();
    public List<RuntimeEventDto> recentEvents = new ArrayList<>();
}