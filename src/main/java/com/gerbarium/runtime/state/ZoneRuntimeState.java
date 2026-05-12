package com.gerbarium.runtime.state;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class ZoneRuntimeState {
    public final String zoneId;
    public boolean active = false;
    public boolean pendingActivation = false;
    public long activatedAtMillis = 0;
    public long lastPlayerSeenAtMillis = 0;
    public List<ServerPlayerEntity> nearbyPlayers = new ArrayList<>();
    public boolean firstSpawnDelayPassed = false;

    public ZoneRuntimeState(String zoneId) {
        this.zoneId = zoneId;
    }
}