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
                        orphaned = zone.get().mobs.stream().noneMatch(m -> m.id.equals(info.ruleId));
                    }

                    if (orphaned) {
                        if ("PRIMARY".equals(info.role)) {
                            MobTracker.decrementPrimary(info.zoneId, info.ruleId, info.forced);
                        } else if ("COMPANION".equals(info.role)) {
                            MobTracker.decrementCompanion(info.zoneId, info.ruleId, info.forced);
                        }
                        entity.discard();
                        removedCount++;
                    }
                }
            }
        }

        ActionResultDto result = new ActionResultDto(true, "Cleanup completed");
        result.removed = removedCount;
        RuntimeStateStorage.addEvent("global", null, "ORPHANS_CLEANED", "Orphans cleanup by " + adminName + ": removed " + removedCount);
        GerbariumRegionsRuntime.LOGGER.info("Orphans cleanup by " + adminName + ". Removed: " + removedCount);
        return result;
    }

    public static ActionResultDto resetRuleCooldown(String zoneId, String ruleId, String adminName) {
        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zoneId, ruleId);
        state.nextAvailableAt = 0;
        state.nextAttemptAt = 0;
        state.lastActivationSpawnAt = 0;
        state.encounterActive = false;
        state.timedProgressMillis = 0;
        state.lastTimedTickAt = 0;
        state.timedSpawnedThisActivation = 0;
        state.timedBudgetActivationId = 0;
        state.lastTimedBudgetResetAt = 0;
        state.timedBudgetExhausted = false;
        RuntimeStateStorage.markDirty(zoneId);
        RuntimeStateStorage.addEvent(zoneId, ruleId, "COOLDOWN_RESET", "Cooldown reset by " + adminName);
        return new ActionResultDto(true, "Cooldown reset for " + ruleId);
    }

    public static ActionResultDto clearZoneState(String zoneId, String adminName) {
        RuntimeStateStorage.clearZoneState(zoneId);
        GerbariumRegionsRuntime.LOGGER.info("State cleared for zone " + zoneId + " by " + adminName);
        return new ActionResultDto(true, "State cleared for zone " + zoneId);
    }

    public static ActionResultDto clearRuleState(String zoneId, String ruleId, String adminName) {
        RuntimeStateStorage.clearRuleState(zoneId, ruleId);
        RuntimeStateStorage.addEvent(zoneId, null, "RULE_CLEARED", "State cleared for rule " + ruleId + " by " + adminName);
        return new ActionResultDto(true, "State cleared for rule " + ruleId);
    }

    public static ActionResultDto forceActivateZone(String zoneId, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        long now = System.currentTimeMillis();

        zState.active = true;
        zState.activatedAtMillis = now;
        zState.activationId++;
        zState.firstSpawnDelayPassed = true;
        zState.lastPlayerSeenAtMillis = now;

        var pState = RuntimeStateStorage.getZoneState(zoneId).zone;
        pState.lastActivatedAt = now;
        pState.lastActivationReason = "force_activate_command (" + adminName + ")";
        pState.totalActivations++;

        RuntimeStateStorage.markDirty(zoneId);
        RuntimeStateStorage.addEvent(zoneId, null, "ZONE_ACTIVATED", "Zone force activated by " + adminName);

        return new ActionResultDto(true, "Zone " + zoneId + " force activated");
    }

    public static ActionResultDto forceDeactivateZone(String zoneId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        long now = System.currentTimeMillis();

        zState.active = false;
        zState.firstSpawnDelayPassed = false;

        var pState = RuntimeStateStorage.getZoneState(zoneId).zone;
        pState.lastDeactivatedAt = now;
        pState.lastDeactivationReason = "force_deactivate_command (" + adminName + ")";

        int removed = 0;
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world != null) {
            Box box = new Box(
                    Math.min(zone.min.x, zone.max.x), Math.min(zone.min.y, zone.max.y), Math.min(zone.min.z, zone.max.z),
                    Math.max(zone.min.x, zone.max.x) + 1, Math.max(zone.min.y, zone.max.y) + 1, Math.max(zone.min.z, zone.max.z) + 1
            );

            for (Entity entity : world.getOtherEntities(null, box)) {
                var infoOpt = MobTagger.getInfo(entity);
                if (infoOpt.isPresent()) {
                    var info = infoOpt.get();
                    if (info.zoneId.equals(zoneId)) {
                        Optional<MobRule> rule = zone.mobs.stream().filter(m -> m.id.equals(info.ruleId)).findFirst();
                        if (rule.isPresent() && rule.get().despawnWhenZoneInactive) {
                            ((com.gerbarium.runtime.access.EntityPersistentDataHolder) entity).getPersistentData().putBoolean(MobTagger.TAG_CLEANUP, true);
                            entity.discard();
                            removed++;
                        }
                    }
                }
            }
        }

        if (removed > 0) {
            MobTracker.resyncZone(server, zone);
        }

        for (MobRule rule : zone.mobs) {
            RuleRuntimeState state = RuntimeStateStorage.getRuleState(zoneId, rule.id);
            state.lastTimedTickAt = 0;

            if (removed > 0 && rule.spawnType == SpawnType.UNIQUE) {
                MobTracker.checkUniqueEncounterCleared(zoneId, rule.id, false, true);
            }
        }

        RuntimeStateStorage.markDirty(zoneId);
        RuntimeStateStorage.addEvent(zoneId, null, "ZONE_DEACTIVATED", "Zone force deactivated by " + adminName);

        ActionResultDto result = new ActionResultDto(true, "Zone " + zoneId + " force deactivated");
        result.removed = removed;
        return result;
    }

    public static ActionResultDto clearZoneMobs(String zoneId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        Box box = new Box(
                Math.min(zone.min.x, zone.max.x), Math.min(zone.min.y, zone.max.y), Math.min(zone.min.z, zone.max.z),
                Math.max(zone.min.x, zone.max.x) + 1, Math.max(zone.min.y, zone.max.y) + 1, Math.max(zone.min.z, zone.max.z) + 1
        );

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
        RuntimeStateStorage.addEvent(zoneId, null, "ZONE_CLEARED", "Zone cleared by " + adminName + ": removed " + removed + " mobs");

        ActionResultDto result = new ActionResultDto(true, "Zone " + zoneId + " cleared");
        result.removed = removed;
        return result;
    }

    public static ActionResultDto forceSpawnRule(String zoneId, String ruleId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        Optional<MobRule> ruleOpt = zone.mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        MobRule rule = ruleOpt.get();
        String configStatus = RuntimeRuleValidationUtil.getConfigStatus(rule);
        if (configStatus != null) return new ActionResultDto(false, configStatus);

        String entityStatus = RuntimeRuleValidationUtil.getEntityStatus(rule);
        if (entityStatus != null) return new ActionResultDto(false, entityStatus);

        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        int alive = MobTracker.getNormalPrimaryAliveCount(zoneId, ruleId);
        int toSpawn = rule.spawnType == SpawnType.UNIQUE ? (alive >= 1 ? 0 : 1) : Math.max(0, Math.min(rule.spawnCount, rule.maxAlive - alive));

        int spawned = 0;
        int companionsSpawned = 0;

        for (int i = 0; i < toSpawn; i++) {
            Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, zState.nearbyPlayers);
            if (pos.isPresent()) {
                var primary = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), SpawnContext.FORCED);
                if (primary != null) {
                    spawned++;
                    companionsSpawned += EntitySpawnService.spawnCompanions(world, zone, rule, primary, SpawnContext.FORCED);
                }
            }
        }

        RuntimeStateStorage.addEvent(zoneId, ruleId, "FORCE_SPAWN", "Rule force spawn by " + adminName + ": " + spawned + "P + " + companionsSpawned + "C");

        ActionResultDto result = new ActionResultDto(true, "Force spawn completed for rule " + ruleId);
        result.primarySpawned = spawned;
        result.companionsSpawned = companionsSpawned;
        return result;
    }

    public static ActionResultDto forceSpawnPrimary(String zoneId, String ruleId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        Optional<MobRule> ruleOpt = zone.mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        MobRule rule = ruleOpt.get();
        String configStatus = RuntimeRuleValidationUtil.getConfigStatus(rule);
        if (configStatus != null) return new ActionResultDto(false, configStatus);

        String entityStatus = RuntimeRuleValidationUtil.getEntityStatus(rule);
        if (entityStatus != null) return new ActionResultDto(false, entityStatus);

        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, zState.nearbyPlayers);
        if (pos.isEmpty()) return new ActionResultDto(false, "No valid spawn position found");

        var primary = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), SpawnContext.FORCED);
        if (primary != null) {
            RuntimeStateStorage.addEvent(zoneId, ruleId, "FORCE_SPAWN_PRIMARY", "Primary force spawned by " + adminName);

            ActionResultDto result = new ActionResultDto(true, "Primary spawned for rule " + ruleId);
            result.primarySpawned = 1;
            return result;
        }

        return new ActionResultDto(false, "Spawn failed");
    }

    public static ActionResultDto forceSpawnCompanions(String zoneId, String ruleId, MinecraftServer server, ServerPlayerEntity player, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        Optional<MobRule> ruleOpt = zone.mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return new ActionResultDto(false, "Rule not found");

        MobRule rule = ruleOpt.get();
        if (rule.companions.isEmpty()) return new ActionResultDto(false, "Rule has no companions configured");

        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return new ActionResultDto(false, "World unavailable");

        if (player == null) return new ActionResultDto(false, "Companions spawn requires a player context");

        net.minecraft.entity.Entity fakeParent = new net.minecraft.entity.decoration.ArmorStandEntity(world, player.getX(), player.getY(), player.getZ());
        int spawned = EntitySpawnService.spawnCompanions(world, zone, rule, fakeParent, SpawnContext.FORCED);

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

        Box box = new Box(
                Math.min(zone.min.x, zone.max.x), Math.min(zone.min.y, zone.max.y), Math.min(zone.min.z, zone.max.z),
                Math.max(zone.min.x, zone.max.x) + 1, Math.max(zone.min.y, zone.max.y) + 1, Math.max(zone.min.z, zone.max.z) + 1
        );

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
                        com.gerbarium.runtime.GerbariumRegionsRuntime.LOGGER.info("Archived state file for missing zone: " + zoneId);
                    }
                }
            }
            
            String message = archived > 0 ? "Archived " + archived + " state files for missing zones." : "No missing zone states found.";
            com.gerbarium.runtime.GerbariumRegionsRuntime.LOGGER.info("State cleanup by " + adminName + ": " + message);
            
            ActionResultDto result = new ActionResultDto(true, message);
            result.removed = archived;
            return result;
        } catch (Exception e) {
            com.gerbarium.runtime.GerbariumRegionsRuntime.LOGGER.error("Failed to cleanup missing zone states", e);
            return new ActionResultDto(false, "Failed to cleanup: " + e.getMessage());
        }
    }
}
