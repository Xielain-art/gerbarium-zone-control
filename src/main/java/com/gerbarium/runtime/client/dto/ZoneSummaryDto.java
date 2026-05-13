package com.gerbarium.runtime.client.dto;

import java.util.ArrayList;
import java.util.List;

public class ZoneSummaryDto {
    public String id;
    public String name;
    public boolean enabled;
    public String dimension;
    public boolean active;
    public boolean pendingActivation;
    public int nearbyPlayers;
    public int mobsCount;
    public int primaryAliveTotal;
    public int totalRules;
    public int minX, minY, minZ;
    public int maxX, maxY, maxZ;
    public int priority;
    public long activationId;
    public long lastActivatedAt;
    public long lastDeactivatedAt;
    public long lastPlayerSeenAt;
    public int companionsAliveTotal;
    public boolean stateFileExists = true;
    public boolean dirty;
    public String currentStatus = "";
    public String attentionStatus = "";
    public String warningText = "";
    public String hintText = "";
    public String nextActionText = "";
    public String statusText = "";
    public List<RuleSummaryDto> rules = new ArrayList<>();
}
