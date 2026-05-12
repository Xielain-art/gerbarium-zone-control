package com.gerbarium.runtime.client.dto;

public class ZoneSummaryDto {
    public String id;
    public boolean enabled;
    public String dimension;
    public boolean active;
    public boolean pendingActivation;
    public int nearbyPlayers;
    public int mobsCount;
    public int primaryAliveTotal;
    public long lastActivatedAt;
    public long lastDeactivatedAt;
    public long lastPlayerSeenAt;
    public String statusText = "";
}