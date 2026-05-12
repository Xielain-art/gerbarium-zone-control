package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.spawn.EntitySpawnService;
import com.gerbarium.runtime.spawn.SpawnPositionFinder;
import com.gerbarium.runtime.spawn.SpawnResult;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tracking.MobTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.Optional;
import java.util.Random;

public class ZoneMobSpawner {
    private static final Random RANDOM = new Random();

    public static void tick(MinecraftServer server) {
        Collection<Zone> zones = ZoneRepository.getEnabledZones();
        int zonesProcessed = 0;
        int totalSpawnsThisTick = 0;
        int maxSpawns = RuntimeConfigStorage.getConfig().maxSpawnsPerTickCycle;
        int maxZones = RuntimeConfigStorage.getConfig().maxZonesProcessedPerSpawnTick;

        for (Zone zone : zones) {
            if (zonesProcessed >= maxZones || totalSpawnsThisTick >= maxSpawns) break;

            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            if (!zState.active) continue;

            zonesProcessed++;

            ServerWorld world = server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(zone.dimension)));
            if (world == null) continue;

            for (MobRule rule : zone.mobs) {
                if (!rule.enabled) continue;
                if (totalSpawnsThisTick >= maxSpawns) break;

                if (rule.spawnType == SpawnType.PACK) {
                    totalSpawnsThisTick += handlePackRule(world, zone, rule, zState);
                } else if (rule.spawnType == SpawnType.UNIQUE) {
                    totalSpawnsThisTick += handleUniqueRule(world, zone, rule, zState);
                }
            }
        }
    }

    private static int handlePackRule(ServerWorld world, Zone zone, MobRule rule, ZoneRuntimeState zState) {
        if (!zState.firstSpawnDelayPassed) return 0;

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
        long now = System.currentTimeMillis();

        if (rule.refillMode == com.gerbarium.runtime.model.RefillMode.TIMED) {
            if (!TimedSpawnLogic.shouldSpawn(now, rule, state)) {
                return 0;
            }
        } else {
            // Default ON_ACTIVATION logic
            long cooldown = Math.max(zone.activation.reactivationCooldownSeconds, rule.respawnSeconds) * 1000L;
            if (now - state.lastActivationSpawnAt < cooldown) {
                return 0;
            }
        }

        int alive = MobTracker.getPrimaryAliveCount(zone.id, rule.id);
        if (alive >= rule.maxAlive) {
            state.lastActivationSpawnAt = now;
            state.lastAttemptResult = "SKIPPED_MAX_ALIVE";
            state.lastAttemptReason = "Alive count " + alive + "/" + rule.maxAlive;
            RuntimeStateStorage.markDirty(zone.id);
            return 0;
        }

        if (RANDOM.nextDouble() > rule.chance) {
            state.lastActivationSpawnAt = now;
            state.lastAttemptAt = now;
            state.lastAttemptResult = "SKIPPED_CHANCE_FAIL";
            state.lastAttemptReason = "Chance roll failed";
            RuntimeStateStorage.markDirty(zone.id);
            return 0;
        }

        int toSpawn = Math.min(rule.spawnCount, rule.maxAlive - alive);
        int spawned = 0;

        for (int i = 0; i < toSpawn; i++) {
            Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, zState.nearbyPlayers);
            if (pos.isPresent()) {
                SpawnResult result = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), false);
                if (result == SpawnResult.SUCCESS) {
                    spawned++;
                }
            }
        }

        state.lastActivationSpawnAt = now;
        state.lastAttemptAt = now;
        if (spawned > 0) {
            state.lastSuccessAt = now;
            state.lastAttemptResult = "SUCCESS";
            state.lastAttemptReason = "Spawned " + spawned + " primary mobs";
            state.totalSuccesses++;
            state.totalPrimarySpawned += spawned;
            RuntimeStateStorage.markDirty(zone.id);
        } else {
            state.lastAttemptResult = "FAILED_NO_POSITION";
            state.lastAttemptReason = "Could not find valid spawn position";
            RuntimeStateStorage.markDirty(zone.id);
        }

        return spawned;
    }

    private static int handleUniqueRule(ServerWorld world, Zone zone, MobRule rule, ZoneRuntimeState zState) {
        if (!zState.firstSpawnDelayPassed) return 0;

        int alive = MobTracker.getPrimaryAliveCount(zone.id, rule.id);
        if (alive >= 1) return 0;

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);

        long now = System.currentTimeMillis();
        if (state.nextAvailableAt > now || state.nextAttemptAt > now) return 0;

        if (RANDOM.nextDouble() > rule.chance) {
            state.lastAttemptAt = now;
            state.nextAttemptAt = now + rule.failedSpawnRetrySeconds * 1000L;
            state.lastAttemptResult = "SKIPPED_CHANCE_FAIL";
            state.lastAttemptReason = "Chance roll failed";
            RuntimeStateStorage.markDirty(zone.id);
            return 0;
        }

        Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, zState.nearbyPlayers);
        if (pos.isPresent()) {
            SpawnResult result = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), false);
            if (result == SpawnResult.SUCCESS) {
                state.encounterActive = true;
                state.encounterStartedAt = now;
                state.encounterClearedAt = 0;
                state.encounterPrimaryAlive = 1;
                state.encounterCompanionsAlive = MobTracker.getCompanionAliveCount(zone.id, rule.id);
                
                state.lastAttemptAt = now;
                state.lastSuccessAt = now;
                state.nextAttemptAt = 0;
                state.lastAttemptResult = "SUCCESS";
                state.lastAttemptReason = "Spawned unique primary mob";
                state.totalSuccesses++;
                state.totalPrimarySpawned++;
                RuntimeStateStorage.markDirty(zone.id);
                
                RuntimeStateStorage.addEvent(zone.id, rule.id, "UNIQUE_SUCCESS", "Unique encounter spawned");
                return 1;
            } else {
                state.lastAttemptAt = now;
                state.nextAttemptAt = now + rule.failedSpawnRetrySeconds * 1000L;
                state.lastAttemptResult = result.name();
                state.lastAttemptReason = "Spawn failed: " + result.name();
                RuntimeStateStorage.markDirty(zone.id);
            }
        } else {
            state.lastAttemptAt = now;
            state.nextAttemptAt = now + rule.failedSpawnRetrySeconds * 1000L;
            state.lastAttemptResult = "FAILED_NO_POSITION";
            state.lastAttemptReason = "Could not find valid spawn position";
            RuntimeStateStorage.markDirty(zone.id);
        }

        return 0;
    }
}