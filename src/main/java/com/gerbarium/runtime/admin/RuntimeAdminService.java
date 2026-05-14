package com.gerbarium.runtime.admin;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.SpawnMode;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.spawn.EntitySpawnService;
import com.gerbarium.runtime.spawn.SpawnContext;
import com.gerbarium.runtime.spawn.SpawnPositionFinder;
import com.gerbarium.runtime.spawn.SpawnPositionResult;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.state.RuntimeEvent;
import com.gerbarium.runtime.state.ZoneRuntimePersistentState;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneLoader;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tick.ZoneActivationManager;
import com.gerbarium.runtime.tracking.MobTagger;
import com.gerbarium.runtime.tracking.MobTracker;
import com.gerbarium.runtime.util.RuntimeRuleValidationUtil;
import com.gerbarium.runtime.util.RuntimeWorldUtil;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class RuntimeAdminService {
    public static ActionResultDto reload(String adminName) {
        ZoneLoader.loadAll();
        Collection<Zone> newZones = ZoneRepository.getAll();
        RuntimeStateStorage.loadAll(newZones);

        int loaded = newZones.size();
        int enabled = (int) newZones.stream().filter(z -> z.enabled).count();

        GerbariumRegionsRuntime.LOGGER.info("Zones reloaded by " + adminName);
        RuntimeStateStorage.addEvent(null, null, "RELOAD", "Zones reloaded by " + adminName);

        ActionResultDto result = new ActionResultDto(true, "Reloaded zones successfully");
        result.loadedZones = loaded;
        result.enabledZones = enabled;
        return result;
    }

    public static ActionResultDto setDebugEnabled(boolean enabled, String adminName) {
        RuntimeConfigStorage.getConfig().debug = enabled;
        RuntimeConfigStorage.save();

        GerbariumRegionsRuntime.LOGGER.info("Runtime debug " + (enabled ? "enabled" : "disabled") + " by " + adminName);

        return new ActionResultDto(true, enabled ? "Debug enabled." : "Debug disabled.");
    }

    public static ActionResultDto forceActivateZone(String zoneId, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        if (zState.active) return new ActionResultDto(true, "Zone is already active");

        zState.active = true;
        zState.activatedAtMillis = System.currentTimeMillis();
        zState.activationId++;
        zState.firstSpawnDelayPassed = true;

        ZoneRuntimePersistentState pState = RuntimeStateStorage.getZoneState(zoneId).zone;
        pState.lastActivatedAt = System.currentTimeMillis();
        pState.lastActivationReason = "force_activate by " + adminName;
        pState.totalActivations++;

        RuntimeStateStorage.addEvent(zoneId, null, "FORCE_ACTIVATED", "Zone force activated by " + adminName);
        RuntimeStateStorage.markDirty(zoneId);

        return new ActionResultDto(true, "Zone " + zoneId + " force activated");
    }

    public static ActionResultDto forceDeactivateZone(String zoneId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        if (!zState.active) return new ActionResultDto(true, "Zone is already inactive");

        long now = System.currentTimeMillis();
        ZoneActivationManager.deactivateZone(
                server,
                zone,
                zState,
                now,
                "force_deactivate by " + adminName,
                "FORCE_DEACTIVATED",
                "Zone force deactivated by " + adminName
        );

        return new ActionResultDto(true, "Zone " + zoneId + " force deactivated");
    }

    public static ActionResultDto clearZoneMobs(String zoneId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        Box box = zone.getExpandedBox(RuntimeConfigStorage.getConfig().boundaryScanPadding);
        int removed = 0;
        for (Entity entity : world.getOtherEntities(null, box)) {
            var infoOpt = MobTagger.getInfo(entity);
            if (infoOpt.isPresent() && infoOpt.get().zoneId.equals(zoneId)) {
                ((com.gerbarium.runtime.access.EntityPersistentDataHolder) entity).getPersistentData().putBoolean(MobTagger.TAG_CLEANUP, true);
                entity.discard();
                removed++;
            }
        }

        MobTracker.resyncZone(server, zone);
        RuntimeStateStorage.addEvent(zoneId, null, "ZONE_CLEARED", "Cleared " + removed + " mobs by " + adminName);

        return new ActionResultDto(true, "Cleared " + removed + " managed mobs in zone " + zoneId);
    }

    public static ActionResultDto forceSpawnZone(String zoneId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        if (!zone.enabled) return failed("disabled_zone", "Zone is disabled");
        if (RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty()) return failed("dimension_not_found", "Dimension not found: " + zone.dimension);

        int totalPrimary = 0;
        int totalCompanions = 0;
        int attempted = 0;

        for (MobRule rule : zone.mobs) {
            if (!rule.enabled) continue;
            attempted++;
            ActionResultDto ruleResult = forceSpawnRule(zoneId, rule.id, server, adminName);
            totalPrimary += ruleResult.primarySpawned;
            totalCompanions += ruleResult.companionsSpawned;
        }

        if (attempted == 0) return failed("no_enabled_rules", "No enabled mob rules in zone");
        if (totalPrimary <= 0) return failed("no_mobs_spawned", "Force spawn completed, but no mobs spawned");

        ActionResultDto result = new ActionResultDto(true, "Force spawn completed");
        result.primarySpawned = totalPrimary;
        result.companionsSpawned = totalCompanions;
        RuntimeStateStorage.addEvent(zoneId, null, "FORCE_SPAWN", "Zone force spawn by " + adminName + ": " + totalPrimary + "P + " + totalCompanions + "C");
        RuntimeStateStorage.markDirty(zoneId);
        return result;
    }

    public static ActionResultDto cleanupOrphans(MinecraftServer server, String adminName) {
        int removedCount = 0;
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                Optional<com.gerbarium.runtime.tracking.ManagedMobInfo> infoOpt = MobTagger.getInfo(entity);
                if (infoOpt.isPresent()) {
                    var info = infoOpt.get();
                    Optional<Zone> zone = ZoneRepository.getById(info.zoneId);
                    boolean orphaned = zone.isEmpty();
                    if (!orphaned) {
                        Optional<MobRule> rule = zone.get().mobs.stream().filter(m -> m.id.equals(info.ruleId)).findFirst();
                        orphaned = rule.isEmpty();
                    }
                    if (orphaned) {
                        ((com.gerbarium.runtime.access.EntityPersistentDataHolder) entity).getPersistentData().putBoolean(MobTagger.TAG_CLEANUP, true);
                        entity.discard();
                        removedCount++;
                    }
                }
            }
        }

        RuntimeStateStorage.addEvent(null, null, "ORPHANS_CLEANED", "Removed " + removedCount + " orphaned mobs by " + adminName);
        return new ActionResultDto(true, "Cleanup complete. Removed " + removedCount + " orphaned managed mobs.");
    }

    public static ActionResultDto clearZoneState(String zoneId, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        RuntimeStateStorage.clearZoneState(zoneId);
        RuntimeStateStorage.addEvent(zoneId, null, "STATE_CLEARED", "Zone state cleared by " + adminName);
        GerbariumRegionsRuntime.LOGGER.info("Cleared zone state for " + zoneId + " by " + adminName);
        return new ActionResultDto(true, "Zone state cleared for " + zoneId);
    }

    public static ActionResultDto clearRuleState(String zoneId, String ruleId, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Optional<MobRule> ruleOpt = zoneOpt.get().mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        RuntimeStateStorage.clearRuleState(zoneId, ruleId);
        RuntimeStateStorage.addEvent(zoneId, ruleId, "STATE_CLEARED", "Rule state cleared by " + adminName);
        GerbariumRegionsRuntime.LOGGER.info("Cleared rule state for " + zoneId + ":" + ruleId + " by " + adminName);
        return new ActionResultDto(true, "Rule state cleared for " + zoneId + ":" + ruleId);
    }

    public static ActionResultDto resetRuleCooldown(String zoneId, String ruleId, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Optional<MobRule> ruleOpt = zoneOpt.get().mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zoneId, ruleId);
        state.nextAvailableAt = 0;
        state.nextAttemptAt = 0;
        state.lastOnActivationAttemptActivationId = 0;
        state.timedSpawnedThisActivation = 0;
        state.timedBudgetExhausted = false;
        state.encounterActive = false;

        RuntimeStateStorage.markDirty(zoneId);
        RuntimeStateStorage.addEvent(zoneId, ruleId, "COOLDOWN_RESET", "Cooldown reset by " + adminName);
        GerbariumRegionsRuntime.LOGGER.info("Reset cooldown for " + zoneId + ":" + ruleId + " by " + adminName);
        return new ActionResultDto(true, "Cooldown reset for " + zoneId + ":" + ruleId);
    }

    public static ActionResultDto forceSpawnRule(String zoneId, String ruleId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        if (!zone.enabled) return recordFailure(zoneId, ruleId, "disabled_zone", "Zone is disabled");

        Optional<MobRule> ruleOpt = zone.mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        MobRule rule = ruleOpt.get();
        rule.normalize();
        if (!rule.enabled) return recordFailure(zoneId, ruleId, "disabled_rule", "Rule is disabled");

        String configStatus = RuntimeRuleValidationUtil.getConfigStatus(rule);
        if (configStatus != null) return recordFailure(zoneId, ruleId, configStatus, "Invalid rule config: " + configStatus);

        String entityStatus = RuntimeRuleValidationUtil.getEntityStatus(rule);
        if (entityStatus != null) return recordFailure(zoneId, ruleId, entityStatus, "Invalid entity: " + rule.entity);

        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return recordFailure(zoneId, ruleId, "dimension_not_found", "Dimension not found: " + zone.dimension);

        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        MobTracker.resyncZone(server, zone);
        int alive = MobTracker.getPrimaryAliveCount(zone.id, rule.id);
        if (alive >= rule.maxAlive) {
            return recordFailure(zoneId, ruleId, "max_alive", "Max alive reached: " + alive + "/" + rule.maxAlive);
        }

        SpawnMode spawnMode = rule.spawnMode == null ? SpawnMode.RANDOM_VALID_POSITION : rule.spawnMode;
        int requested = rule.spawnType == SpawnType.UNIQUE || spawnMode == SpawnMode.BOSS_ROOM ? 1 : Math.max(1, rule.spawnCount);
        int toSpawn = Math.min(requested, rule.maxAlive - alive);
        if (toSpawn <= 0) return recordFailure(zoneId, ruleId, "max_alive", "Max alive reached");

        int totalPrimary = 0;
        int totalCompanions = 0;
        long now = System.currentTimeMillis();
        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
        state.totalAttempts++;
        state.lastAttemptAt = now;
        int positionAttempts = Math.max(Math.max(512, RuntimeConfigStorage.getConfig().forceSpawnPositionAttempts), rule.positionAttempts);
        SpawnPositionResult lastPositionResult = null;
        List<BlockPos> selectedPositions = new ArrayList<>();

        for (int i = 0; i < toSpawn; i++) {
            SpawnPositionResult positionResult = SpawnPositionFinder.findSpawnPositionResult(world, zone, rule, zState.nearbyPlayers, positionAttempts, selectedPositions);
            lastPositionResult = positionResult;
            recordPositionResult(state, positionResult);
            Optional<BlockPos> pos = positionResult.position();
            if (pos.isPresent()) {
                var primary = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), SpawnContext.FORCED);
                if (primary != null) {
                    totalPrimary++;
                    selectedPositions.add(pos.get());
                    totalCompanions += EntitySpawnService.spawnCompanions(world, zone, rule, primary, SpawnContext.FORCED);
                    GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Force spawn success: zone={} rule={} entity={} pos={},{},{}", zone.id, rule.id, rule.entity, pos.get().getX(), pos.get().getY(), pos.get().getZ());
                }
            }
        }

        state.lastSuccessfulPrimaryCount = totalPrimary;
        state.lastSuccessfulCompanionCount = totalCompanions;
        state.knownAlive = MobTracker.getPrimaryAliveCount(zone.id, rule.id);
        if (totalPrimary <= 0) {
            state.lastAttemptResult = "FAILED_NO_POSITION";
            state.lastAttemptReason = lastPositionResult == null ? "no_valid_position" : lastPositionResult.reason();
            RuntimeStateStorage.addEvent(zoneId, ruleId, "FORCE_SPAWN_FAILED", state.lastAttemptReason + " by " + adminName);
            RuntimeStateStorage.markDirty(zoneId);
            String summary = lastPositionResult == null ? "reason=no_valid_position attempts=" + positionAttempts : lastPositionResult.failureSummary();
            GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Force spawn failed: zone={} rule={} entity={} {}", zone.id, rule.id, rule.entity, summary);
            return failed(state.lastAttemptReason, "Force spawn failed: " + state.lastAttemptReason + " " + (lastPositionResult == null ? "" : lastPositionResult.stats().format()));
        }

        state.lastAttemptResult = "SUCCESS";
        state.lastAttemptReason = "Force spawned " + totalPrimary + " primary mobs";
        state.lastSuccessAt = now;
        state.totalSuccesses++;
        state.totalPrimarySpawned += totalPrimary;
        state.totalCompanionsSpawned += totalCompanions;
        RuntimeStateStorage.addEvent(zoneId, ruleId, "FORCE_SPAWN", "Rule force spawn by " + adminName + ": " + totalPrimary + "P + " + totalCompanions + "C");
        RuntimeStateStorage.markDirty(zoneId);
        ActionResultDto result = new ActionResultDto(true, "Force spawn success: zone=" + zoneId + " rule=" + ruleId + " entity=" + rule.entity);
        result.primarySpawned = totalPrimary;
        result.companionsSpawned = totalCompanions;
        return result;
    }

    private static ActionResultDto recordFailure(String zoneId, String ruleId, String code, String message) {
        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zoneId, ruleId);
        state.lastAttemptAt = System.currentTimeMillis();
        state.lastAttemptResult = "FAILED";
        state.lastAttemptReason = code;
        state.totalAttempts++;
        RuntimeStateStorage.addEvent(zoneId, ruleId, "FORCE_SPAWN_SKIPPED", message);
        RuntimeStateStorage.markDirty(zoneId);
        GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Spawn skipped: zone={} rule={} reason={}", zoneId, ruleId, code);
        return failed(code, message);
    }

    private static ActionResultDto failed(String code, String message) {
        ActionResultDto result = new ActionResultDto(false, message);
        result.errorCode = code;
        return result;
    }

    public static ActionResultDto forceSpawnPrimary(String zoneId, String ruleId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        Optional<MobRule> ruleOpt = zone.mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        MobRule rule = ruleOpt.get();
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        SpawnPositionResult positionResult = SpawnPositionFinder.findSpawnPositionResult(world, zone, rule, zState.nearbyPlayers,
                Math.max(Math.max(512, RuntimeConfigStorage.getConfig().forceSpawnPositionAttempts), rule.positionAttempts));
        Optional<BlockPos> pos = positionResult.position();
        if (pos.isEmpty()) return failed(positionResult.reason(), "No valid spawn position found: " + positionResult.failureSummary());

        var primary = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), SpawnContext.FORCED);
        if (primary != null) {
            RuntimeStateStorage.addEvent(zoneId, ruleId, "FORCE_SPAWN_PRIMARY", "Primary force spawned by " + adminName);
            return new ActionResultDto(true, "Primary mob spawned for rule " + ruleId);
        }
        return new ActionResultDto(false, "Spawn rejected");
    }

    private static void recordPositionResult(RuleRuntimeState state, SpawnPositionResult result) {
        state.lastPositionSearchAttempts = result.attempts();
        state.lastPositionSearchReason = result.reason();
        state.lastPositionSearchStats = result.stats().format();
    }

    public static ActionResultDto forceSpawnCompanions(String zoneId, String ruleId, MinecraftServer server, ServerPlayerEntity player, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        Optional<MobRule> ruleOpt = zone.mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        MobRule rule = ruleOpt.get();
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        if (player == null) return new ActionResultDto(false, "Companions spawn requires a player context");

        // Use player position as reference for companion spawn location
        BlockPos refPos = player.getBlockPos();
        int spawned = 0;
        for (var companion : rule.companions) {
            if (companion == null || companion.entity == null) continue;
            net.minecraft.util.Identifier entityId = net.minecraft.util.Identifier.tryParse(companion.entity);
            if (entityId == null) continue;
            var typeOpt = net.minecraft.registry.Registries.ENTITY_TYPE.getOrEmpty(entityId);
            if (typeOpt.isEmpty()) continue;
            for (int i = 0; i < companion.count; i++) {
                Optional<BlockPos> pos = SpawnPositionFinder.findCompanionPosition(world, zone, typeOpt.get(), refPos, companion.radius);
                if (pos.isEmpty()) continue;
                Entity entity = typeOpt.get().create(world);
                if (entity == null) continue;
                BlockPos spawnPos = pos.get();
                entity.refreshPositionAndAngles(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0, 0);
                if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
                    mob.initialize(world, world.getLocalDifficulty(spawnPos), net.minecraft.entity.SpawnReason.SPAWNER, null, null);
                    mob.setPersistent();
                }
                com.gerbarium.runtime.tracking.MobTagger.tagCompanion(entity, zone.id, rule.id, companion.id, true);
                if (world.spawnEntity(entity)) {
                    com.gerbarium.runtime.tracking.MobTracker.incrementCompanion(zone.id, rule.id, true);
                    spawned++;
                }
            }
        }

        RuntimeStateStorage.addEvent(zoneId, ruleId, "FORCE_SPAWN_COMPANIONS", "Companions force spawned by " + adminName + ": " + spawned);
        ActionResultDto result = new ActionResultDto(true, "Companions spawned for rule " + ruleId);
        result.companionsSpawned = spawned;
        return result;
    }

    public static ActionResultDto killManagedMobs(String zoneId, String ruleId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        Optional<MobRule> ruleOpt = zone.mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        Box box = zone.getExpandedBox(RuntimeConfigStorage.getConfig().boundaryScanPadding);

        int removed = 0;
        for (Entity entity : world.getOtherEntities(null, box)) {
            var infoOpt = MobTagger.getInfo(entity);
            if (infoOpt.isPresent()) {
                var info = infoOpt.get();
                if (info.zoneId.equals(zoneId) && info.ruleId.equals(ruleId)) {
                    ((com.gerbarium.runtime.access.EntityPersistentDataHolder) entity).getPersistentData().putBoolean(MobTagger.TAG_CLEANUP, true);
                    entity.discard();
                    removed++;
                }
            }
        }

        MobTracker.resyncZone(server, zone);
        RuntimeStateStorage.addEvent(zoneId, ruleId, "RULE_CLEARED", "Rule mobs killed by " + adminName + ": removed " + removed);

        ActionResultDto result = new ActionResultDto(true, "Killed managed mobs for rule " + ruleId);
        result.removed = removed;
        return result;
    }

    public static ActionResultDto cleanupMissingZoneStates(String adminName) {
        try {
            java.nio.file.Path statesDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("gerbarium/zones-control/states");
            java.nio.file.Path archiveDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("gerbarium/zones-control/states-archive");

            if (!java.nio.file.Files.exists(statesDir)) {
                return new ActionResultDto(true, "No states directory found.");
            }

            java.nio.file.Files.createDirectories(archiveDir);

            int archived = 0;
            java.io.File[] files = statesDir.toFile().listFiles((dir, name) -> name.endsWith(".runtime-state.json"));

            if (files != null) {
                for (java.io.File file : files) {
                    String filename = file.getName();
                    String zoneId = filename.substring(0, filename.length() - ".runtime-state.json".length());

                    Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
                    if (zoneOpt.isEmpty()) {
                        java.nio.file.Path target = archiveDir.resolve(filename);
                        java.nio.file.Files.move(file.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        archived++;
                        GerbariumRegionsRuntime.LOGGER.info("Archived state file for missing zone: " + zoneId);
                    }
                }
            }

            String message = archived > 0 ? "Archived " + archived + " state files for missing zones." : "No missing zone states found.";
            GerbariumRegionsRuntime.LOGGER.info("State cleanup by " + adminName + ": " + message);

            ActionResultDto result = new ActionResultDto(true, message);
            result.removed = archived;
            return result;
        } catch (Exception e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to cleanup missing zone states", e);
            return new ActionResultDto(false, "Failed to cleanup: " + e.getMessage());
        }
    }
}
