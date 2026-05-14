package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.CooldownStart;
import com.gerbarium.runtime.model.RefillMode;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.spawn.EntitySpawnService;
import com.gerbarium.runtime.spawn.SpawnContext;
import com.gerbarium.runtime.spawn.SpawnPositionFinder;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.state.ZoneRuntimePersistentState;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tracking.MobTracker;
import com.gerbarium.runtime.util.RuntimeRuleValidationUtil;
import com.gerbarium.runtime.util.RuntimeWorldUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class ZoneMobSpawner {
    private static final Random RANDOM = new Random();

    public static void tick(MinecraftServer server) {
        List<Zone> zones = ZoneRepository.getEnabledZones();
        int zonesProcessed = 0;
        int totalSpawnsThisTick = 0;
        int maxSpawns = RuntimeConfigStorage.getConfig().maxSpawnsPerTickCycle;
        int maxZones = RuntimeConfigStorage.getConfig().maxZonesProcessedPerSpawnTick;

        for (Zone zone : zones) {
            if (zonesProcessed >= maxZones || totalSpawnsThisTick >= maxSpawns) {
                break;
            }

            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            if (!zState.active) {
                continue;
            }

            zonesProcessed++;

            ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
            if (world == null) {
                continue;
            }

            List<MobRule> mobs = zone.mobs;
            if (mobs == null) {
                continue;
            }

            for (MobRule rule : mobs) {
                if (totalSpawnsThisTick >= maxSpawns) {
                    break;
                }

                if (!rule.enabled || !rule.spawnWhenReady) {
                    continue;
                }

                int spawned = 0;
                if (rule.spawnType == SpawnType.PACK) {
                    spawned = handlePackRule(world, zone, rule, zState);
                } else if (rule.spawnType == SpawnType.UNIQUE) {
                    spawned = handleUniqueRule(world, zone, rule, zState);
                }

                totalSpawnsThisTick += spawned;
            }
        }
    }

    private static int handlePackRule(ServerWorld world, Zone zone, MobRule rule, ZoneRuntimeState zState) {
        if (!zState.firstSpawnDelayPassed) {
            return 0;
        }

        if (RuntimeRuleValidationUtil.getConfigStatus(rule) != null || RuntimeRuleValidationUtil.getEntityStatus(rule) != null) {
            return 0;
        }

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
        ZoneRuntimePersistentState zoneState = RuntimeStateStorage.getZoneState(zone.id).zone;
        long now = System.currentTimeMillis();

        if (rule.refillMode == RefillMode.TIMED) {
            return handleTimedPack(world, zone, rule, zState, state, zoneState, now);
        }

        return handleOnActivationPack(world, zone, rule, zState, state, zoneState, now);
    }

    private static int handleOnActivationPack(ServerWorld world, Zone zone, MobRule rule, ZoneRuntimeState zState, RuleRuntimeState state, ZoneRuntimePersistentState zoneState, long now) {
        CooldownStart cooldownStart = rule.cooldownStart == null ? CooldownStart.AFTER_ACTIVATION : rule.cooldownStart;
        boolean afterDeathCooldown = cooldownStart == CooldownStart.AFTER_DEATH;
        if (!afterDeathCooldown && state.lastOnActivationAttemptActivationId == zState.activationId) {
            return 0;
        }

        long effectiveCooldown = Math.max(rule.respawnSeconds, zone.activation.reactivationCooldownSeconds) * 1000L;
        if (afterDeathCooldown) {
            if (state.nextAvailableAt > now || state.nextAttemptAt > now) {
                return 0;
            }
        } else {
            long lastRealAttempt = Math.max(state.lastActivationSpawnAt, state.lastAttemptAt);
            if (lastRealAttempt > 0 && now - lastRealAttempt < effectiveCooldown) {
                return 0;
            }
        }

        int alive = MobTracker.getNormalPrimaryAliveCount(zone.id, rule.id);
        if (alive >= rule.maxAlive) {
            return 0;
        }

        if (cooldownStart == CooldownStart.AFTER_ATTEMPT) {
            state.nextAvailableAt = now + effectiveCooldown;
        }

        if (RANDOM.nextDouble() > rule.chance) {
            state.lastAttemptAt = now;
            state.lastAttemptResult = "SKIPPED_CHANCE_FAIL";
            state.lastAttemptReason = "Chance roll failed";
            if (!afterDeathCooldown) {
                state.lastOnActivationAttemptActivationId = zState.activationId;
            }
            state.totalAttempts++;
            zoneState.totalSpawnAttempts++;
            RuntimeStateStorage.markDirty(zone.id);
            return 0;
        }

        int toSpawn = Math.max(0, Math.min(rule.spawnCount, rule.maxAlive - alive));
        if (toSpawn <= 0) {
            return 0;
        }

        int spawned = spawnPackPrimaryAndCompanions(world, zone, rule, zState, state, now, SpawnContext.NORMAL, toSpawn);
        if (spawned > 0) {
            state.lastActivationSpawnAt = now;
            if (!afterDeathCooldown) {
                state.lastOnActivationAttemptActivationId = zState.activationId;
            }
            if (cooldownStart == CooldownStart.AFTER_ACTIVATION) {
                state.nextAvailableAt = now + effectiveCooldown;
            }
            state.lastAttemptAt = now;
            state.lastAttemptResult = "SUCCESS";
            state.lastAttemptReason = "Spawned " + spawned + " primary mobs";
            state.lastSuccessAt = now;
            state.lastSuccessfulPrimaryCount = spawned;
            state.lastSuccessfulCompanionCount = state.lastEncounterCompanionsSpawned;
            state.totalAttempts++;
            state.totalSuccesses++;
            state.totalPrimarySpawned += spawned;
            state.totalCompanionsSpawned += state.lastEncounterCompanionsSpawned;
            zoneState.totalSpawnAttempts++;
            zoneState.totalSuccessfulSpawns++;
            RuntimeStateStorage.addEvent(zone.id, rule.id, "PACK_SUCCESS", "Pack spawned " + spawned + " primary mobs");
            if (state.lastEncounterCompanionsSpawned > 0) {
                RuntimeStateStorage.addEvent(zone.id, rule.id, "COMPANIONS_SPAWNED", "Spawned " + state.lastEncounterCompanionsSpawned + " companions");
            }
        } else {
            state.lastAttemptAt = now;
            state.lastAttemptResult = "FAILED_NO_POSITION";
            state.lastAttemptReason = "Could not find valid spawn position";
            if (!afterDeathCooldown) {
                state.lastOnActivationAttemptActivationId = zState.activationId;
            }
            state.totalAttempts++;
            zoneState.totalSpawnAttempts++;
            state.nextAttemptAt = now + rule.failedSpawnRetrySeconds * 1000L;
        }

        RuntimeStateStorage.markDirty(zone.id);
        return spawned;
    }

    private static int handleTimedPack(ServerWorld world, Zone zone, MobRule rule, ZoneRuntimeState zState, RuleRuntimeState state, ZoneRuntimePersistentState zoneState, long now) {
        int budget = rule.timedMaxSpawnsPerActivation != null ? rule.timedMaxSpawnsPerActivation : rule.maxAlive;

        if (state.timedBudgetActivationId != zState.activationId) {
            long reactivationCooldownMillis = zone.activation.reactivationCooldownSeconds * 1000L;
            if (state.lastTimedBudgetResetAt == 0 || now - state.lastTimedBudgetResetAt >= reactivationCooldownMillis) {
                state.timedBudgetActivationId = zState.activationId;
                state.timedSpawnedThisActivation = 0;
                state.timedBudgetExhausted = false;
                state.lastTimedBudgetResetAt = now;
            }
        }

        if (budget != -1 && state.timedSpawnedThisActivation >= budget) {
            state.timedBudgetExhausted = true;
            state.nextTimedSpawnInMillis = 0;
            RuntimeStateStorage.markDirty(zone.id);
            return 0;
        }

        boolean intervalReached = TimedSpawnLogic.tick(now, rule, state);
        if (!intervalReached) {
            return 0;
        }

        int alive = MobTracker.getNormalPrimaryAliveCount(zone.id, rule.id);
        if (alive >= rule.maxAlive) {
            return 0;
        }

        if (RuntimeRuleValidationUtil.getConfigStatus(rule) != null || RuntimeRuleValidationUtil.getEntityStatus(rule) != null) {
            return 0;
        }

        int toSpawn = Math.min(rule.spawnCount, rule.maxAlive - alive);
        if (budget != -1) {
            toSpawn = Math.min(toSpawn, budget - state.timedSpawnedThisActivation);
        }
        if (toSpawn <= 0) {
            return 0;
        }

        int spawned = spawnPackPrimaryAndCompanions(world, zone, rule, zState, state, now, SpawnContext.NORMAL, toSpawn);
        if (spawned > 0) {
            state.lastAttemptAt = now;
            state.lastAttemptResult = "SUCCESS";
            state.lastAttemptReason = "Timed pack spawned " + spawned + " primary mobs";
            state.lastSuccessAt = now;
            state.lastSuccessfulPrimaryCount = spawned;
            state.lastSuccessfulCompanionCount = state.lastEncounterCompanionsSpawned;
            state.totalAttempts++;
            state.totalSuccesses++;
            state.totalPrimarySpawned += spawned;
            state.totalCompanionsSpawned += state.lastEncounterCompanionsSpawned;
            zoneState.totalSpawnAttempts++;
            zoneState.totalSuccessfulSpawns++;
            state.timedSpawnedThisActivation += spawned;
            if (budget != -1 && state.timedSpawnedThisActivation >= budget) {
                state.timedBudgetExhausted = true;
                state.nextTimedSpawnInMillis = 0;
            }
            RuntimeStateStorage.addEvent(zone.id, rule.id, "PACK_SUCCESS", "Timed pack spawned " + spawned + " primary mobs");
            if (state.lastEncounterCompanionsSpawned > 0) {
                RuntimeStateStorage.addEvent(zone.id, rule.id, "COMPANIONS_SPAWNED", "Spawned " + state.lastEncounterCompanionsSpawned + " companions");
            }
        }

        RuntimeStateStorage.markDirty(zone.id);
        return spawned;
    }

    private static int handleUniqueRule(ServerWorld world, Zone zone, MobRule rule, ZoneRuntimeState zState) {
        if (!zState.firstSpawnDelayPassed) {
            return 0;
        }

        if (RuntimeRuleValidationUtil.getConfigStatus(rule) != null || RuntimeRuleValidationUtil.getEntityStatus(rule) != null) {
            return 0;
        }

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
        ZoneRuntimePersistentState zoneState = RuntimeStateStorage.getZoneState(zone.id).zone;
        long now = System.currentTimeMillis();

        if (state.encounterActive) {
            return 0;
        }

        if (state.nextAttemptAt > now || state.nextAvailableAt > now) {
            return 0;
        }

        int alive = MobTracker.getNormalPrimaryAliveCount(zone.id, rule.id);
        if (alive >= 1) {
            return 0;
        }

        CooldownStart cooldownStart = rule.cooldownStart == null ? CooldownStart.AFTER_ACTIVATION : rule.cooldownStart;
        if (cooldownStart == CooldownStart.AFTER_ATTEMPT) {
            state.nextAvailableAt = now + (long) rule.respawnSeconds * 1000L;
        }

        if (RANDOM.nextDouble() > rule.chance) {
            state.lastAttemptAt = now;
            state.nextAttemptAt = now + rule.failedSpawnRetrySeconds * 1000L;
            state.lastAttemptResult = "SKIPPED_CHANCE_FAIL";
            state.lastAttemptReason = "Chance roll failed";
            state.totalAttempts++;
            zoneState.totalSpawnAttempts++;
            RuntimeStateStorage.markDirty(zone.id);
            return 0;
        }

        Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, rule, zState.nearbyPlayers);
        state.totalAttempts++;
        zoneState.totalSpawnAttempts++;

        if (pos.isEmpty()) {
            state.lastAttemptAt = now;
            state.nextAttemptAt = now + rule.failedSpawnRetrySeconds * 1000L;
            state.lastAttemptResult = "FAILED_NO_POSITION";
            state.lastAttemptReason = "Could not find valid spawn position";
            RuntimeStateStorage.markDirty(zone.id);
            return 0;
        }

        var primaryEntity = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), SpawnContext.NORMAL);
        if (primaryEntity == null) {
            state.lastAttemptAt = now;
            state.nextAttemptAt = now + rule.failedSpawnRetrySeconds * 1000L;
            state.lastAttemptResult = "FAILED_SPAWN_REJECTED";
            state.lastAttemptReason = "Spawn rejected";
            RuntimeStateStorage.markDirty(zone.id);
            return 0;
        }

        int companionsSpawned = 0;
        companionsSpawned = EntitySpawnService.spawnCompanions(world, zone, rule, primaryEntity, SpawnContext.NORMAL);

        state.encounterActive = true;
        state.encounterStartedAt = now;
        state.encounterClearedAt = 0;
        state.encounterPrimaryAlive = 1;
        state.encounterCompanionsAlive = companionsSpawned;
        state.lastEncounterPrimarySpawned = 1;
        state.lastEncounterCompanionsSpawned = companionsSpawned;
        if (cooldownStart == CooldownStart.AFTER_ACTIVATION) {
            state.nextAvailableAt = now + (long) rule.respawnSeconds * 1000L;
        }
        state.lastAttemptAt = now;
        state.lastSuccessAt = now;
        state.lastAttemptResult = "SUCCESS";
        state.lastAttemptReason = "Spawned unique primary mob";
        state.lastSuccessfulPrimaryCount = 1;
        state.lastSuccessfulCompanionCount = companionsSpawned;
        state.totalSuccesses++;
        state.totalPrimarySpawned++;
        state.totalCompanionsSpawned += companionsSpawned;
        zoneState.totalSuccessfulSpawns++;
        RuntimeStateStorage.markDirty(zone.id);
        RuntimeStateStorage.addEvent(zone.id, rule.id, "UNIQUE_SUCCESS", "Unique encounter spawned");
        if (companionsSpawned > 0) {
            RuntimeStateStorage.addEvent(zone.id, rule.id, "COMPANIONS_SPAWNED", "Spawned " + companionsSpawned + " companions");
        }
        return 1;
    }

    private static int spawnPackPrimaryAndCompanions(ServerWorld world, Zone zone, MobRule rule, ZoneRuntimeState zState, RuleRuntimeState state, long now, SpawnContext context, int spawnAttempts) {
        int spawned = 0;
        int companionsSpawned = 0;

        for (int i = 0; i < spawnAttempts; i++) {
            Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, rule, zState.nearbyPlayers);
            if (pos.isEmpty()) {
                state.lastAttemptAt = now;
                state.lastAttemptResult = "FAILED_NO_POSITION";
                state.lastAttemptReason = "Could not find valid spawn position";
                break;
            }

            var primaryEntity = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), context);
            if (primaryEntity == null) {
                state.lastAttemptAt = now;
                state.lastAttemptResult = "FAILED_SPAWN_REJECTED";
                state.lastAttemptReason = "Spawn rejected";
                break;
            }

            spawned++;
            companionsSpawned += EntitySpawnService.spawnCompanions(world, zone, rule, primaryEntity, context);
        }

        state.lastEncounterPrimarySpawned = spawned;
        state.lastEncounterCompanionsSpawned = companionsSpawned;
        state.knownAlive = MobTracker.getPrimaryAliveCount(zone.id, rule.id) + MobTracker.getCompanionAliveCount(zone.id, rule.id);
        state.lastSuccessAt = spawned > 0 ? now : state.lastSuccessAt;
        state.lastSuccessfulPrimaryCount = spawned;
        state.lastSuccessfulCompanionCount = companionsSpawned;

        return spawned;
    }
}
