package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.config.RuntimeConfig;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.BoundaryMode;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.spawn.SpawnPositionFinder;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tracking.ManagedMobInfo;
import com.gerbarium.runtime.access.EntityPersistentDataHolder;
import com.gerbarium.runtime.tracking.MobTagger;
import com.gerbarium.runtime.tracking.MobTracker;
import com.gerbarium.runtime.util.RuntimeRuleValidationUtil;
import com.gerbarium.runtime.util.RuntimeWorldUtil;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Optional;

public final class BoundaryControlManager {
    private static long boundaryTickCounter = 0;

    private BoundaryControlManager() {
    }

    public static void tick(MinecraftServer server) {
        RuntimeConfig config = RuntimeConfigStorage.getConfig();
        if (!config.boundaryControlEnabled) {
            return;
        }

        int interval = Math.max(1, config.boundaryGlobalCheckIntervalTicks);
        boundaryTickCounter++;
        if (boundaryTickCounter % interval != 0) {
            return;
        }

        long now = System.currentTimeMillis();
        int processed = 0;
        int maxEntities = Math.max(1, config.boundaryMaxEntitiesPerTick);

        for (Zone zone : ZoneRepository.getEnabledZones()) {
            if (processed >= maxEntities) {
                return;
            }

            ZoneActivationManager.getZoneState(zone.id); // warm state
            if (!ZoneActivationManager.getZoneState(zone.id).active) {
                continue;
            }

            ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
            if (world == null) {
                continue;
            }

            for (MobRule rule : zone.mobs) {
                RuntimeStateStorage.getRuleState(zone.id, rule.id).boundaryOutsideCount = 0;
            }

            Box expandedBox = getZoneBox(zone).expand(Math.max(0, config.boundaryScanPadding));
            for (Entity entity : world.getOtherEntities(null, expandedBox)) {
                if (processed >= maxEntities) {
                    return;
                }
                processed++;

                Optional<ManagedMobInfo> infoOpt = MobTagger.getInfo(entity);
                if (infoOpt.isEmpty()) {
                    continue;
                }

                ManagedMobInfo info = infoOpt.get();
                if (!zone.id.equals(info.zoneId)) {
                    continue;
                }

                Optional<MobRule> ruleOpt = zone.mobs.stream().filter(rule -> rule.id.equals(info.ruleId)).findFirst();
                if (ruleOpt.isEmpty()) {
                    continue;
                }

                MobRule rule = ruleOpt.get();
                if (rule.boundaryMode == null) {
                    continue;
                }

                if (RuntimeRuleValidationUtil.getConfigStatus(rule) != null) {
                    continue;
                }

                BoundaryMode mode = RuntimeRuleValidationUtil.getBoundaryMode(rule);
                if (mode == null || mode == BoundaryMode.NONE) {
                    continue;
                }

                handleBoundary(server, world, zone, rule, entity, info, now, config, mode);
            }
        }
    }

    private static void handleBoundary(MinecraftServer server, ServerWorld world, Zone zone, MobRule rule, Entity entity,
                                       ManagedMobInfo info, long now, RuntimeConfig config, BoundaryMode mode) {
        if (boundaryTickCounter % Math.max(1, rule.boundaryCheckIntervalTicks) != 0) {
            return;
        }

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
        state.boundaryLastScanAt = now;

        if (isInsideZone(entity, zone)) {
            MobTagger.clearOutsideSince(entity);
            return;
        }

        state.boundaryOutsideCount++;
        if (MobTagger.getOutsideSince(entity) <= 0L) {
            MobTagger.setOutsideSince(entity, now);
            RuntimeStateStorage.addBoundaryEvent(
                    zone.id,
                    rule.id,
                    entity.getUuid(),
                    "BOUNDARY_OUTSIDE",
                    "Managed mob moved outside zone bounds.",
                    entity.getType().toString(),
                    info.role,
                    info.forced,
                    entity.getBlockX(),
                    entity.getBlockY(),
                    entity.getBlockZ(),
                    "track_outside"
            );
            RuntimeStateStorage.markDirty(zone.id);
        }

        long outsideSince = MobTagger.getOutsideSince(entity);
        long outsideMillis = now - outsideSince;
        long thresholdMillis = Math.max(0, rule.boundaryMaxOutsideSeconds) * 1000L;
        if (outsideSince <= 0L) {
            return;
        }

        if (outsideMillis < thresholdMillis) {
            return;
        }

        if (mode == BoundaryMode.REMOVE_OUTSIDE) {
            removeOutsideMob(zone, rule, entity, info, now);
            return;
        }

        boolean shouldTeleport = mode == BoundaryMode.TELEPORT_BACK || rule.boundaryTeleportBack;
        if (!shouldTeleport) {
            state.boundaryLastHint = "Teleport back disabled by rule.";
            return;
        }

        Optional<BlockPos> returnPos = SpawnPositionFinder.findReturnPosition(world, zone, rule, entity, ZoneActivationManager.getZoneState(zone.id).nearbyPlayers);
        if (returnPos.isEmpty()) {
            state.boundaryLastHint = "No valid return position found.";
            RuntimeStateStorage.addBoundaryEvent(
                    zone.id,
                    rule.id,
                    entity.getUuid(),
                    "BOUNDARY_FAILED_NO_POSITION",
                    "No valid return position was available inside the zone.",
                    entity.getType().toString(),
                    info.role,
                    info.forced,
                    entity.getBlockX(),
                    entity.getBlockY(),
                    entity.getBlockZ(),
                    "failed_no_position"
            );
            RuntimeStateStorage.markDirty(zone.id);
            return;
        }

        BlockPos pos = returnPos.get();
        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entity.getYaw(), entity.getPitch());
        entity.setVelocity(0.0, 0.0, 0.0);
        MobTagger.clearOutsideSince(entity);
        MobTagger.setLastBoundaryActionAt(entity, now);
        state.lastBoundaryActionAt = now;
        state.lastBoundaryActionType = "BOUNDARY_TELEPORT";
        state.boundaryLastHint = "";

        RuntimeStateStorage.addBoundaryEvent(
                zone.id,
                rule.id,
                entity.getUuid(),
                "BOUNDARY_TELEPORT",
                "Managed mob teleported back inside the zone.",
                entity.getType().toString(),
                info.role,
                info.forced,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                "teleport_back"
        );
        RuntimeStateStorage.markDirty(zone.id);
    }

    private static void removeOutsideMob(Zone zone, MobRule rule, Entity entity, ManagedMobInfo info, long now) {
        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
        ((EntityPersistentDataHolder) entity).getPersistentData().putBoolean(MobTagger.TAG_CLEANUP, true);
        MobTagger.clearOutsideSince(entity);
        MobTagger.setLastBoundaryActionAt(entity, now);
        entity.discard();

        if ("PRIMARY".equals(info.role)) {
            MobTracker.decrementPrimary(zone.id, rule.id, info.forced);
        } else if ("COMPANION".equals(info.role)) {
            MobTracker.decrementCompanion(zone.id, rule.id, info.forced);
        }

        state.lastBoundaryActionAt = now;
        state.lastBoundaryActionType = "BOUNDARY_REMOVED";
        state.boundaryLastHint = "";
        RuntimeStateStorage.addBoundaryEvent(
                zone.id,
                rule.id,
                entity.getUuid(),
                "BOUNDARY_REMOVED",
                "Managed mob removed after staying outside the zone too long.",
                entity.getType().toString(),
                info.role,
                info.forced,
                entity.getBlockX(),
                entity.getBlockY(),
                entity.getBlockZ(),
                "discard"
        );
        MobTracker.checkUniqueEncounterCleared(zone.id, rule.id, info.forced, true);
        RuntimeStateStorage.markDirty(zone.id);
    }

    private static boolean isInsideZone(Entity entity, Zone zone) {
        int minX = Math.min(zone.min.x, zone.max.x);
        int maxX = Math.max(zone.min.x, zone.max.x);
        int minY = Math.min(zone.min.y, zone.max.y);
        int maxY = Math.max(zone.min.y, zone.max.y);
        int minZ = Math.min(zone.min.z, zone.max.z);
        int maxZ = Math.max(zone.min.z, zone.max.z);

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        return x >= minX && x < (maxX + 1.0)
                && y >= minY && y < (maxY + 1.0)
                && z >= minZ && z < (maxZ + 1.0);
    }

    private static Box getZoneBox(Zone zone) {
        return new Box(
                Math.min(zone.min.x, zone.max.x), Math.min(zone.min.y, zone.max.y), Math.min(zone.min.z, zone.max.z),
                Math.max(zone.min.x, zone.max.x) + 1, Math.max(zone.min.y, zone.max.y) + 1, Math.max(zone.min.z, zone.max.z) + 1
        );
    }
}
