package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.config.RuntimeConfig;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.BoundaryMode;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.spawn.SpawnPositionFinder;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tracking.ManagedMobInfo;
import com.gerbarium.runtime.access.EntityPersistentDataHolder;
import com.gerbarium.runtime.tracking.MobTagger;
import com.gerbarium.runtime.tracking.MobTracker;
import com.gerbarium.runtime.util.RuntimeRuleValidationUtil;
import com.gerbarium.runtime.util.RuntimeWorldUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class BoundaryControlManager {
    private BoundaryControlManager() {
    }

    public static void tick(MinecraftServer server, long gameTickCounter) {
        RuntimeConfig config = RuntimeConfigStorage.getConfig();
        if (!config.boundaryControlEnabled) {
            return;
        }

        long now = System.currentTimeMillis();
        int processed = 0;
        int maxEntities = Math.max(1, config.boundaryMaxEntitiesPerTick);

        for (Zone zone : ZoneRepository.getEnabledZones()) {
            if (processed >= maxEntities) {
                return;
            }

            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            if (!zState.active) {
                continue;
            }

            ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
            if (world == null) {
                continue;
            }

            Map<String, RuleRuntimeState> activeRuleStates = new HashMap<>();
            Map<String, MobRule> activeRules = new HashMap<>();
            for (MobRule rule : zone.mobs) {
                if (rule.boundaryMode == null) continue;
                if (RuntimeRuleValidationUtil.getConfigStatus(rule) != null) continue;
                BoundaryMode mode = RuntimeRuleValidationUtil.getBoundaryMode(rule);
                if (mode == null || mode == BoundaryMode.NONE) continue;

                int interval = Math.max(1, rule.boundaryCheckIntervalTicks);
                if (gameTickCounter % interval != 0) continue;

                RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
                state.boundaryOutsideCount = 0;
                state.boundaryLastScanAt = now;
                activeRuleStates.put(rule.id, state);
                activeRules.put(rule.id, rule);
            }

            if (activeRuleStates.isEmpty()) {
                continue;
            }

            Box expandedBox = zone.getExpandedBox(Math.max(0, config.boundaryScanPadding));
            for (Entity entity : world.getOtherEntities(null, expandedBox)) {
                if (processed >= maxEntities) {
                    return;
                }

                Optional<ManagedMobInfo> infoOpt = MobTagger.getInfo(entity);
                if (infoOpt.isEmpty()) continue;

                ManagedMobInfo info = infoOpt.get();
                if (!zone.id.equals(info.zoneId)) continue;

                RuleRuntimeState state = activeRuleStates.get(info.ruleId);
                if (state == null) continue;

                MobRule rule = activeRules.get(info.ruleId);
                if (rule == null) continue;

                processed++;

                if (isInsideZone(entity, zone)) {
                    MobTagger.clearOutsideSince(entity);
                    continue;
                }

                state.boundaryOutsideCount++;
                BoundaryMode mode = RuntimeRuleValidationUtil.getBoundaryMode(rule);
                handleOutside(server, world, zone, rule, entity, info, now, config, mode, state);
            }
        }
    }

    private static void handleOutside(MinecraftServer server, ServerWorld world, Zone zone, MobRule rule, Entity entity,
                                       ManagedMobInfo info, long now, RuntimeConfig config, BoundaryMode mode, RuleRuntimeState state) {
        if (MobTagger.getOutsideSince(entity) <= 0L) {
            MobTagger.setOutsideSince(entity, now);
            RuntimeStateStorage.addBoundaryEvent(
                    zone.id, rule.id, entity.getUuid(),
                    "BOUNDARY_OUTSIDE", "Managed mob moved outside zone bounds.",
                    entity.getType().toString(), info.role, info.forced,
                    entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(), "outside"
            );
            RuntimeStateStorage.markDirty(zone.id);
        }

        long outsideSince = MobTagger.getOutsideSince(entity);
        long outsideMillis = now - outsideSince;
        long thresholdMillis = Math.max(0, rule.boundaryMaxOutsideSeconds) * 1000L;

        if (outsideMillis < thresholdMillis) {
            return;
        }

        if (mode == BoundaryMode.REMOVE_OUTSIDE) {
            removeOutsideMob(zone, rule, entity, info, now, state);
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
                    zone.id, rule.id, entity.getUuid(),
                    "BOUNDARY_FAILED_NO_POSITION", "No valid return position was available inside the zone.",
                    entity.getType().toString(), info.role, info.forced,
                    entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(), "failed_no_position"
            );
            RuntimeStateStorage.markDirty(zone.id);
            return;
        }

        BlockPos pos = returnPos.get();
        if (entity instanceof LivingEntity living) {
            living.teleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        } else {
            entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, entity.getYaw(), entity.getPitch());
        }
        entity.setVelocity(0.0, 0.0, 0.0);
        MobTagger.clearOutsideSince(entity);
        MobTagger.setLastBoundaryActionAt(entity, now);
        state.lastBoundaryActionAt = now;
        state.lastBoundaryActionType = "BOUNDARY_TELEPORT";
        state.boundaryLastHint = "";

        RuntimeStateStorage.addBoundaryEvent(
                zone.id, rule.id, entity.getUuid(),
                "BOUNDARY_TELEPORT", "Managed mob teleported back inside the zone.",
                entity.getType().toString(), info.role, info.forced,
                pos.getX(), pos.getY(), pos.getZ(), "teleport_back"
        );
        RuntimeStateStorage.markDirty(zone.id);
    }

    private static void removeOutsideMob(Zone zone, MobRule rule, Entity entity, ManagedMobInfo info, long now, RuleRuntimeState state) {
        ((EntityPersistentDataHolder) entity).getPersistentData().putBoolean(MobTagger.TAG_CLEANUP, true);
        MobTagger.clearOutsideSince(entity);
        MobTagger.setLastBoundaryActionAt(entity, now);

        if ("PRIMARY".equals(info.role)) {
            MobTracker.decrementPrimary(zone.id, rule.id, info.forced);
        } else if ("COMPANION".equals(info.role)) {
            MobTracker.decrementCompanion(zone.id, rule.id, info.forced);
        }

        entity.discard();

        state.lastBoundaryActionAt = now;
        state.lastBoundaryActionType = "BOUNDARY_REMOVED";
        state.boundaryLastHint = "";
        RuntimeStateStorage.addBoundaryEvent(
                zone.id, rule.id, entity.getUuid(),
                "BOUNDARY_REMOVED", "Managed mob removed after staying outside the zone too long.",
                entity.getType().toString(), info.role, info.forced,
                entity.getBlockX(), entity.getBlockY(), entity.getBlockZ(), "discard"
        );
        MobTracker.checkUniqueEncounterCleared(zone.id, rule.id, info.forced, true);
        RuntimeStateStorage.markDirty(zone.id);
    }

    private static boolean isInsideZone(Entity entity, Zone zone) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        return x >= zone.getMinX() && x < (zone.getMaxX() + 1.0)
                && y >= zone.getMinY() && y < (zone.getMaxY() + 1.0)
                && z >= zone.getMinZ() && z < (zone.getMaxZ() + 1.0);
    }
}
