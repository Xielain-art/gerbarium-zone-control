package com.gerbarium.runtime.client.dto;

import java.util.ArrayList;
import java.util.List;

public class ZoneSummaryDto {
    public String id;
    public boolean enabled;
    public String dimension;
    public boolean active;
    public boolean pendingActivation;
    public int nearbyPlayers;
    public int mobsCount;
    public int primaryAliveTotal;
    public int minX, minY, minZ;
    public int maxX, maxY, maxZ;
    public int priority;
    public long lastActivatedAt;
    public long lastDeactivatedAt;
    public long lastPlayerSeenAt;
    public String statusText = "";
    public List<RuleSummaryDto> rules = new ArrayList<>();
}