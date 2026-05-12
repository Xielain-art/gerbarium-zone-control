package com.gerbarium.runtime.admin;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.spawn.EntitySpawnService;
import com.gerbarium.runtime.spawn.SpawnPositionFinder;
import com.gerbarium.runtime.spawn.SpawnResult;
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
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class RuntimeAdminService {
    public static ActionResultDto reload(String adminName) {
        ZoneLoader.loadAll();
        Collection<Zone> newZones = ZoneRepository.getAll();
        int loaded = newZones.size();
        int enabled = (int) newZones.stream().filter(z -> z.enabled).count();

        // RELOAD is a global event, for now log it and add to all loaded states or a global log
        GerbariumRegionsRuntime.LOGGER.info("Zones reloaded by " + adminName);
        
        ActionResultDto result = new ActionResultDto(true, "Reloaded zones successfully");
        result.loadedZones = loaded;
        result.enabledZones = enabled;
        return result;
    }

    public static ActionResultDto forceSpawnZone(String zoneId, MinecraftServer server, String adminName) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return new ActionResultDto(false, "Zone not found");

        Zone zone = zoneOpt.get();
        ServerWorld world = server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(zone.dimension)));
        if (world == null) return new ActionResultDto(false, "World unavailable");

        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        int totalPrimary = 0;

        for (MobRule rule : zone.mobs) {
            if (!rule.enabled) continue;

            int alive = MobTracker.getPrimaryAliveCount(zoneId, rule.id);
            int toSpawn = rule.spawnType == SpawnType.UNIQUE ? (alive >= 1 ? 0 : 1) : Math.min(rule.spawnCount, rule.maxAlive - alive);

            for (int i = 0; i < toSpawn; i++) {
                Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, zState.nearbyPlayers);
                if (pos.isPresent()) {
                    SpawnResult res = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), true);
                    if (res == SpawnResult.SUCCESS) {
                        totalPrimary++;
                        if (rule.spawnType == SpawnType.UNIQUE) {
                            RuleRuntimeState rs = RuntimeStateStorage.getRuleState(zoneId, rule.id);
                            rs.encounterActive = true;
                            rs.encounterStartedAt = System.currentTimeMillis();
                            rs.encounterPrimaryAlive = 1;
                            rs.encounterCompanionsAlive = MobTracker.getCompanionAliveCount(zoneId, rule.id);
                        }
                    }
                }
            }
        }

        ActionResultDto result = new ActionResultDto(true, "Force spawn completed");
        result.primarySpawned = totalPrimary;
        RuntimeStateStorage.addEvent(zoneId, null, "FORCE_SPAWN", "Zone force spawn by " + adminName);
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
                        entity.discard();
                        removedCount++;
                    }
                }
            }
        }

        ActionResultDto result = new ActionResultDto(true, "Cleanup completed");
        result.removed = removedCount;
        // Global event log
        GerbariumRegionsRuntime.LOGGER.info("Orphans cleanup by " + adminName + ". Removed: " + removedCount);
        return result;
    }

    public static ActionResultDto resetRuleCooldown(String zoneId, String ruleId, String adminName) {
        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zoneId, ruleId);
        state.nextAvailableAt = 0;
        state.nextAttemptAt = 0;
        state.lastActivationSpawnAt = 0;
        state.encounterActive = false;
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
}