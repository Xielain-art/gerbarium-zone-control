package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
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
import net.minecraft.util.math.Vec3d;

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
                    deactivateZone(server, zone, zState, now, "no_players_after_timeout", "ZONE_DEACTIVATED", "Zone deactivated (timeout)");
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

    public static void deactivateZone(MinecraftServer server, Zone zone, ZoneRuntimeState zState, long now,
                                      String deactivationReason, String eventType, String eventMessage) {
        ZoneRuntimePersistentState pState = RuntimeStateStorage.getZoneState(zone.id).zone;
        applyDeactivationState(zone, zState, pState, now, deactivationReason, eventType, eventMessage);
        RuntimeStateStorage.addEvent(zone.id, null, eventType, eventMessage);

        boolean anyDespawned = despawnInactiveZoneMobs(server, zone);

        resetRuleStateOnDeactivation(zone, anyDespawned);
    }

    static void applyDeactivationState(Zone zone, ZoneRuntimeState zState, ZoneRuntimePersistentState pState,
                                       long now, String deactivationReason, String eventType, String eventMessage) {
        zState.active = false;
        zState.firstSpawnDelayPassed = false;

        if (pState != null) {
            pState.lastDeactivatedAt = now;
            pState.lastDeactivationReason = deactivationReason;
        }

    }

    private static boolean despawnInactiveZoneMobs(MinecraftServer server, Zone zone) {
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) {
            return false;
        }

        Box box = zone.getExpandedBox(Math.max(0, RuntimeConfigStorage.getConfig().boundaryScanPadding));
        boolean anyDespawned = false;
        for (net.minecraft.entity.Entity entity : world.getOtherEntities(null, box)) {
            var infoOpt = com.gerbarium.runtime.tracking.MobTagger.getInfo(entity);
            if (infoOpt.isEmpty()) continue;

            var info = infoOpt.get();
            if (!info.zoneId.equals(zone.id)) continue;

            Optional<com.gerbarium.runtime.model.MobRule> rule = zone.mobs.stream().filter(m -> m.id.equals(info.ruleId)).findFirst();
            if (rule.isPresent() && rule.get().despawnWhenZoneInactive) {
                ((com.gerbarium.runtime.access.EntityPersistentDataHolder) entity).getPersistentData().putBoolean(com.gerbarium.runtime.tracking.MobTagger.TAG_CLEANUP, true);
                entity.discard();
                anyDespawned = true;
            }
        }

        if (anyDespawned) {
            com.gerbarium.runtime.tracking.MobTracker.resyncZone(server, zone);
        }
        return anyDespawned;
    }

    private static void resetRuleStateOnDeactivation(Zone zone, boolean anyDespawned) {
        for (com.gerbarium.runtime.model.MobRule rule : zone.mobs) {
            com.gerbarium.runtime.state.RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
            TimedSpawnLogic.resetTimer(state);

            if (anyDespawned && rule.spawnType == com.gerbarium.runtime.model.SpawnType.UNIQUE) {
                com.gerbarium.runtime.tracking.MobTracker.checkUniqueEncounterCleared(zone.id, rule.id, false, true);
            }
        }
    }

    private static List<ServerPlayerEntity> findNearbyPlayers(MinecraftServer server, Zone zone) {
        List<ServerPlayerEntity> nearby = new ArrayList<>();
        String dimension = zone.dimension;

        Box box = zone.getZoneBox();
        double rangeSquared = (double) zone.activation.range * zone.activation.range;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.isAlive() || player.isDisconnected()) {
                continue;
            }
            if (player.getWorld().getRegistryKey().getValue().toString().equals(dimension)) {
                Vec3d pos = player.getPos();
                if (box.contains(pos) || squaredDistanceToBox(pos, box) <= rangeSquared) {
                    nearby.add(player);
                }
            }
        }
        return nearby;
    }

    private static double squaredDistanceToBox(Vec3d pos, Box box) {
        double dx = axisDistance(pos.x, box.minX, box.maxX);
        double dy = axisDistance(pos.y, box.minY, box.maxY);
        double dz = axisDistance(pos.z, box.minZ, box.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0;
    }
}
