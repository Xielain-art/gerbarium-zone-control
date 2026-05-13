package com.gerbarium.runtime.admin;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.spawn.EntitySpawnService;
import com.gerbarium.runtime.spawn.SpawnContext;
import com.gerbarium.runtime.spawn.SpawnPositionFinder;
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

import java.util.Collection;
import java.util.Optional;

public class RuntimeAdminService {
    public static ActionResultDto reload(String adminName) {
        ZoneLoader.loadAll();
        Collection<Zone> newZones = ZoneRepository.getAll();
        RuntimeStateStorage.loadAll(newZones);

        int loaded = newZones.size();
        int enabled = (int) newZones.stream().filter(z -> z.enabled).count();

        GerbariumRegionsRuntime.LOGGER.info("Zones reloaded by " + adminName);

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

        zState.active = false;
        zState.firstSpawnDelayPassed = false;

        ZoneRuntimePersistentState pState = RuntimeStateStorage.getZoneState(zoneId).zone;
        pState.lastDeactivatedAt = System.currentTimeMillis();
        pState.lastDeactivationReason = "force_deactivate by " + adminName;

        RuntimeStateStorage.addEvent(zoneId, null, "FORCE_DEACTIVATED", "Zone force deactivated by " + adminName);
        RuntimeStateStorage.markDirty(zoneId);

        return new ActionResultDto(true, "Zone " + zoneId + " force deactivated");
    }

    public static ActionResultDto clearZoneMobs(String zoneId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        Box box = zone.getZoneBox();
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
        RuntimeStateStorage.addEvent(zoneId, null, "CLEAR_MOBS", "Cleared " + removed + " mobs by " + adminName);

        return new ActionResultDto(true, "Cleared " + removed + " managed mobs in zone " + zoneId);
    }

    public static ActionResultDto forceSpawnZone(String zoneId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        int totalPrimary = 0;
        int totalCompanions = 0;

        for (MobRule rule : zone.mobs) {
            if (!rule.enabled) continue;

            int alive = MobTracker.getNormalPrimaryAliveCount(zoneId, rule.id);
            int toSpawn = rule.spawnType == SpawnType.UNIQUE ? (alive >= 1 ? 0 : 1) : Math.max(0, Math.min(rule.spawnCount, rule.maxAlive - alive));

            for (int i = 0; i < toSpawn; i++) {
                Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, zState.nearbyPlayers);
                if (pos.isPresent()) {
                    var primary = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), SpawnContext.FORCED);
                    if (primary != null) {
                        totalPrimary++;
                        totalCompanions += EntitySpawnService.spawnCompanions(world, zone, rule, primary, SpawnContext.FORCED);
                    }
                }
            }
        }

        ActionResultDto result = new ActionResultDto(true, "Force spawn completed");
        result.primarySpawned = totalPrimary;
        result.companionsSpawned = totalCompanions;
        RuntimeStateStorage.addEvent(zoneId, null, "FORCE_SPAWN", "Zone force spawn by " + adminName + ": " + totalPrimary + "P + " + totalCompanions + "C");
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

        RuntimeStateStorage.addEvent(null, null, "CLEANUP_ORPHANS", "Removed " + removedCount + " orphaned mobs by " + adminName);
        return new ActionResultDto(true, "Cleanup complete. Removed " + removedCount + " orphaned managed mobs.");
    }

    public static ActionResultDto clearZoneState(String zoneId, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        RuntimeStateStorage.clearZoneState(zoneId);
        GerbariumRegionsRuntime.LOGGER.info("Cleared zone state for " + zoneId + " by " + adminName);
        return new ActionResultDto(true, "Zone state cleared for " + zoneId);
    }

    public static ActionResultDto clearRuleState(String zoneId, String ruleId, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Optional<MobRule> ruleOpt = zoneOpt.get().mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        RuntimeStateStorage.clearRuleState(zoneId, ruleId);
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
        Optional<MobRule> ruleOpt = zone.mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        MobRule rule = ruleOpt.get();
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        int alive = MobTracker.getNormalPrimaryAliveCount(zoneId, rule.id);
        int toSpawn = rule.spawnType == SpawnType.UNIQUE ? (alive >= 1 ? 0 : 1) : Math.max(0, Math.min(rule.spawnCount, rule.maxAlive - alive));

        int totalPrimary = 0;
        int totalCompanions = 0;
        for (int i = 0; i < toSpawn; i++) {
            Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, zState.nearbyPlayers);
            if (pos.isPresent()) {
                var primary = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), SpawnContext.FORCED);
                if (primary != null) {
                    totalPrimary++;
                    totalCompanions += EntitySpawnService.spawnCompanions(world, zone, rule, primary, SpawnContext.FORCED);
                }
            }
        }

        RuntimeStateStorage.addEvent(zoneId, ruleId, "FORCE_SPAWN_RULE", "Rule force spawn by " + adminName + ": " + totalPrimary + "P + " + totalCompanions + "C");
        ActionResultDto result = new ActionResultDto(true, "Force spawn completed for rule " + ruleId);
        result.primarySpawned = totalPrimary;
        result.companionsSpawned = totalCompanions;
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
        Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, zState.nearbyPlayers);
        if (pos.isEmpty()) return new ActionResultDto(false, "No valid spawn position found");

        var primary = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), SpawnContext.FORCED);
        if (primary != null) {
            RuntimeStateStorage.addEvent(zoneId, ruleId, "FORCE_SPAWN_PRIMARY", "Primary force spawned by " + adminName);
            return new ActionResultDto(true, "Primary mob spawned for rule " + ruleId);
        }
        return new ActionResultDto(false, "Spawn rejected");
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
                Entity entity = typeOpt.get().create(world);
                if (entity == null) continue;
                entity.refreshPositionAndAngles(refPos.getX() + 0.5, refPos.getY(), refPos.getZ() + 0.5, 0, 0);
                if (entity instanceof net.minecraft.entity.mob.MobEntity mob) {
                    mob.initialize(world, world.getLocalDifficulty(refPos), net.minecraft.entity.SpawnReason.SPAWNER, null, null);
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

        Box box = zone.getZoneBox();

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