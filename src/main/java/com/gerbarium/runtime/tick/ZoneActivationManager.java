package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.state.RuntimeEvent;
import com.gerbarium.runtime.state.ZoneRuntimePersistentState;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.util.RuntimeWorldUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ZoneActivationManager {
    private static final Map<String, ZoneRuntimeState> transientStates = new ConcurrentHashMap<>();

    public static ZoneRuntimeState getZoneState(String zoneId) {
        return transientStates.computeIfAbsent(zoneId, ZoneRuntimeState::new);
    }

    public static void update(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (Zone zone : ZoneRepository.getEnabledZones()) {
            ZoneRuntimeState zState = getZoneState(zone.id);
            List<ServerPlayerEntity> nearby = findNearbyPlayers(server, zone);

            zState.nearbyPlayers = nearby;

            if (!nearby.isEmpty()) {
                zState.lastPlayerSeenAtMillis = now;

                ZoneRuntimePersistentState pState = RuntimeStateStorage.getZoneState(zone.id).zone;
                pState.lastPlayerSeenAt = now;

                if (!zState.active) {
                    activateZone(zone, zState, pState, now, nearby.get(0).getName().getString());
                }
            }

            if (zState.active) {
                if (now - zState.lastPlayerSeenAtMillis > zone.activation.deactivateAfterSeconds * 1000L) {
                    deactivateZone(server, zone, zState, now);
                } else if (!zState.firstSpawnDelayPassed && now - zState.activatedAtMillis > zone.activation.firstSpawnDelaySeconds * 1000L) {
                    zState.firstSpawnDelayPassed = true;
                }
            }
        }
    }

    private static void activateZone(Zone zone, ZoneRuntimeState zState, ZoneRuntimePersistentState pState, long now, String playerName) {
        zState.active = true;
        zState.activatedAtMillis = now;
        zState.activationId++;
        zState.firstSpawnDelayPassed = false;

        pState.lastActivatedAt = now;
        pState.lastActivationReason = "player_nearby (" + playerName + ")";
        pState.totalActivations++;

        RuntimeStateStorage.addEvent(zone.id, null, "ZONE_ACTIVATED", "Zone activated by player " + playerName);
    }

    private static void deactivateZone(MinecraftServer server, Zone zone, ZoneRuntimeState zState, long now) {
        zState.active = false;
        zState.firstSpawnDelayPassed = false;

        ZoneRuntimePersistentState pState = RuntimeStateStorage.getZoneState(zone.id).zone;
        if (pState != null) {
            pState.lastDeactivatedAt = now;
            pState.lastDeactivationReason = "no_players_after_timeout";
        }

        // Handle despawnWhenZoneInactive
        boolean anyDespawned = false;
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world != null) {
            Box box = zone.getZoneBox();

            for (net.minecraft.entity.Entity entity : world.getOtherEntities(null, box)) {
                var infoOpt = com.gerbarium.runtime.tracking.MobTagger.getInfo(entity);
                if (infoOpt.isPresent()) {
                    var info = infoOpt.get();
                    if (info.zoneId.equals(zone.id)) {
                        Optional<com.gerbarium.runtime.model.MobRule> rule = zone.mobs.stream().filter(m -> m.id.equals(info.ruleId)).findFirst();
                        if (rule.isPresent() && rule.get().despawnWhenZoneInactive) {
                            ((com.gerbarium.runtime.access.EntityPersistentDataHolder) entity).getPersistentData().putBoolean(com.gerbarium.runtime.tracking.MobTagger.TAG_CLEANUP, true);
                            entity.discard();
                            anyDespawned = true;
                        }
                    }
                }
            }
        }

        if (anyDespawned) {
            com.gerbarium.runtime.tracking.MobTracker.resyncZone(server, zone);
        }

        // Always reset TIMED baseline when zone deactivates to avoid catch-up
        for (com.gerbarium.runtime.model.MobRule rule : zone.mobs) {
            com.gerbarium.runtime.state.RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
            TimedSpawnLogic.resetTimer(state);

            if (anyDespawned && rule.spawnType == com.gerbarium.runtime.model.SpawnType.UNIQUE) {
                com.gerbarium.runtime.tracking.MobTracker.checkUniqueEncounterCleared(zone.id, rule.id, false, true);
            }
        }

        RuntimeStateStorage.addEvent(zone.id, null, "ZONE_DEACTIVATED", "Zone deactivated (timeout)");
    }

    private static List<ServerPlayerEntity> findNearbyPlayers(MinecraftServer server, Zone zone) {
        List<ServerPlayerEntity> nearby = new ArrayList<>();
        String dimension = zone.dimension;

        // Use expanded zone box with +1 on max to match isInsideZone semantics (inclusive max block)
        Box box = zone.getExpandedBox(zone.activation.range);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive() || player.isDisconnected()) {
                continue;
            }
            if (player.getWorld().getRegistryKey().getValue().toString().equals(dimension)) {
                if (box.contains(player.getPos())) {
                    nearby.add(player);
                }
            }
        }
        return nearby;
    }
}