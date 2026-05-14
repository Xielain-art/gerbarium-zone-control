package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.CooldownStart;
import com.gerbarium.runtime.model.RefillMode;
import com.gerbarium.runtime.model.SpawnMode;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.spawn.EntitySpawnService;
import com.gerbarium.runtime.spawn.SpawnContext;
import com.gerbarium.runtime.spawn.SpawnPositionFinder;
import com.gerbarium.runtime.spawn.SpawnPositionResult;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
                debugSkip(zone.id, "-", "zone_inactive");
                continue;
            }

            zonesProcessed++;

            ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
            if (world == null) {
                debugSkip(zone.id, "-", "dimension_not_found dimension=" + zone.dimension);
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
                    debugSkip(zone.id, rule.id, "rule_disabled");
                    continue;
                }

                debugCheck(zone, rule);

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
            debugSkip(zone.id, rule.id, "first_spawn_delay");
            return 0;
        }

        String configStatus = RuntimeRuleValidationUtil.getConfigStatus(rule);
        String entityStatus = RuntimeRuleValidationUtil.getEntityStatus(rule);
        if (configStatus != null || entityStatus != null) {
            debugSkip(zone.id, rule.id, configStatus != null ? configStatus : entityStatus);
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
            debugSkip(zone.id, rule.id, "activation_already_attempted");
            return 0;
        }

        long effectiveCooldown = Math.max(rule.respawnSeconds, zone.activation.reactivationCooldownSeconds) * 1000L;
        if (afterDeathCooldown) {
            long remaining = cooldownRemainingMillis(state, now);
            if (remaining > 0) {
                debugSkip(zone.id, rule.id, cooldownMessage(remaining));
                return 0;
            }
        } else {
            long lastRealAttempt = Math.max(state.lastActivationSpawnAt, state.lastAttemptAt);
            long remaining = lastRealAttempt > 0 ? effectiveCooldown - (now - lastRealAttempt) : 0;
            if (remaining > 0) {
                debugSkip(zone.id, rule.id, cooldownMessage(remaining));
                return 0;
            }
        }

        int alive = MobTracker.getNormalPrimaryAliveCount(zone.id, rule.id);
        if (alive >= rule.maxAlive) {
            debugSkip(zone.id, rule.id, "max_alive current=" + alive + " max=" + rule.maxAlive);
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
            debugSkip(zone.id, rule.id, "chance");
            return 0;
        }

        int toSpawn = Math.max(0, Math.min(rule.spawnCount, rule.maxAlive - alive));
        if (toSpawn <= 0) {
            debugSkip(zone.id, rule.id, "nothing_to_spawn");
            return 0;
        }

        int spawned = spawnPackPrimaryAndCompanions(world, zone, rule, zState, state, now, SpawnContext.NORMAL, effectiveBatchSize(rule, toSpawn));
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
            debugSkip(zone.id, rule.id, state.lastPositionSearchReason + " attempts=" + state.lastPositionSearchAttempts);
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
            debugSkip(zone.id, rule.id, "timed_budget_exhausted");
            return 0;
        }

        boolean intervalReached = TimedSpawnLogic.tick(now, rule, state);
        if (!intervalReached) {
            debugSkip(zone.id, rule.id, cooldownMessage(Math.max(1L, state.nextTimedSpawnInMillis)));
            return 0;
        }

        int alive = MobTracker.getNormalPrimaryAliveCount(zone.id, rule.id);
        if (alive >= rule.maxAlive) {
            debugSkip(zone.id, rule.id, "max_alive current=" + alive + " max=" + rule.maxAlive);
            return 0;
        }

        String configStatus = RuntimeRuleValidationUtil.getConfigStatus(rule);
        String entityStatus = RuntimeRuleValidationUtil.getEntityStatus(rule);
        if (configStatus != null || entityStatus != null) {
            debugSkip(zone.id, rule.id, configStatus != null ? configStatus : entityStatus);
            return 0;
        }

        int toSpawn = Math.min(rule.spawnCount, rule.maxAlive - alive);
        if (budget != -1) {
            toSpawn = Math.min(toSpawn, budget - state.timedSpawnedThisActivation);
        }
        if (toSpawn <= 0) {
            debugSkip(zone.id, rule.id, "nothing_to_spawn");
            return 0;
        }

        int spawned = spawnPackPrimaryAndCompanions(world, zone, rule, zState, state, now, SpawnContext.NORMAL, effectiveBatchSize(rule, toSpawn));
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
            debugSkip(zone.id, rule.id, "first_spawn_delay");
            return 0;
        }

        String configStatus = RuntimeRuleValidationUtil.getConfigStatus(rule);
        String entityStatus = RuntimeRuleValidationUtil.getEntityStatus(rule);
        if (configStatus != null || entityStatus != null) {
            debugSkip(zone.id, rule.id, configStatus != null ? configStatus : entityStatus);
            return 0;
        }

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
        ZoneRuntimePersistentState zoneState = RuntimeStateStorage.getZoneState(zone.id).zone;
        long now = System.currentTimeMillis();

        if (state.encounterActive) {
            debugSkip(zone.id, rule.id, "encounter_active");
            return 0;
        }

        long remaining = cooldownRemainingMillis(state, now);
        if (remaining > 0) {
            debugSkip(zone.id, rule.id, cooldownMessage(remaining));
            return 0;
        }

        int alive = MobTracker.getNormalPrimaryAliveCount(zone.id, rule.id);
        if (alive >= 1) {
            debugSkip(zone.id, rule.id, "max_alive current=" + alive + " max=1");
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
            debugSkip(zone.id, rule.id, "chance");
            return 0;
        }

        SpawnPositionResult positionResult = SpawnPositionFinder.findSpawnPositionResult(world, zone, rule, zState.nearbyPlayers, normalPositionAttempts(zone, rule));
        Optional<BlockPos> pos = positionResult.position();
        state.totalAttempts++;
        zoneState.totalSpawnAttempts++;

        if (pos.isEmpty()) {
            state.lastAttemptAt = now;
            state.nextAttemptAt = now + rule.failedSpawnRetrySeconds * 1000L;
            state.lastAttemptResult = "FAILED_NO_POSITION";
            state.lastAttemptReason = positionResult.reason();
            recordPositionResult(state, positionResult);
            RuntimeStateStorage.markDirty(zone.id);
            debugPositionFailure(zone, rule, positionResult);
            return 0;
        }
        recordPositionResult(state, positionResult);

        var primaryEntity = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), SpawnContext.NORMAL);
        if (primaryEntity == null) {
            state.lastAttemptAt = now;
            state.nextAttemptAt = now + rule.failedSpawnRetrySeconds * 1000L;
            state.lastAttemptResult = "FAILED_SPAWN_REJECTED";
            state.lastAttemptReason = "Spawn rejected";
            RuntimeStateStorage.markDirty(zone.id);
            debugSkip(zone.id, rule.id, "spawn_rejected");
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
        debugSuccess(zone.id, rule.id, rule.entity, pos.get(), 1);
        return 1;
    }

    private static int spawnPackPrimaryAndCompanions(ServerWorld world, Zone zone, MobRule rule, ZoneRuntimeState zState, RuleRuntimeState state, long now, SpawnContext context, int spawnAttempts) {
        int spawned = 0;
        int companionsSpawned = 0;
        List<BlockPos> selectedPositions = new ArrayList<>();

        for (int i = 0; i < spawnAttempts; i++) {
            SpawnPositionResult positionResult = SpawnPositionFinder.findSpawnPositionResult(world, zone, rule, zState.nearbyPlayers, normalPositionAttempts(zone, rule), selectedPositions);
            Optional<BlockPos> pos = positionResult.position();
            if (pos.isEmpty()) {
                state.lastAttemptAt = now;
                state.lastAttemptResult = "FAILED_NO_POSITION";
                state.lastAttemptReason = positionResult.reason();
                recordPositionResult(state, positionResult);
                debugPositionFailure(zone, rule, positionResult);
                break;
            }
            recordPositionResult(state, positionResult);

            var primaryEntity = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), context);
            if (primaryEntity == null) {
                state.lastAttemptAt = now;
                state.lastAttemptResult = "FAILED_SPAWN_REJECTED";
                state.lastAttemptReason = "Spawn rejected";
                break;
            }

            spawned++;
            selectedPositions.add(pos.get());
            debugSuccess(zone.id, rule.id, rule.entity, pos.get(), 1);
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

    private static void debugCheck(Zone zone, MobRule rule) {
        if (!RuntimeConfigStorage.getConfig().debug) {
            return;
        }
        GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Spawn check: zone={} rule={} entity={}", zone.id, rule.id, rule.entity);
    }

    private static void debugSkip(String zoneId, String ruleId, String reason) {
        if (!RuntimeConfigStorage.getConfig().debug) {
            return;
        }
        GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Spawn skipped: zone={} rule={} reason={}", zoneId, ruleId, reason);
    }

    private static void debugSuccess(String zoneId, String ruleId, String entity, BlockPos pos, int count) {
        GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Spawn success: zone={} rule={} entity={} pos={},{},{} count={}",
                zoneId, ruleId, entity, pos.getX(), pos.getY(), pos.getZ(), count);
    }

    private static long cooldownRemainingMillis(RuleRuntimeState state, long now) {
        long next = Math.max(state.nextAvailableAt, state.nextAttemptAt);
        return Math.max(0, next - now);
    }

    private static String cooldownMessage(long remainingMillis) {
        long remainingTicks = Math.max(1L, (remainingMillis + 49L) / 50L);
        double remainingSeconds = remainingMillis / 1000.0D;
        return "cooldown remainingTicks=" + remainingTicks + " remainingSeconds=" + String.format(Locale.ROOT, "%.2f", remainingSeconds);
    }

    private static int normalPositionAttempts(Zone zone, MobRule rule) {
        int ruleAttempts = rule == null ? 128 : rule.positionAttempts;
        return Math.max(128, Math.max(ruleAttempts, Math.max(RuntimeConfigStorage.getConfig().spawnPositionAttempts, zone.spawn.maxPositionAttempts)));
    }

    private static int effectiveBatchSize(MobRule rule, int requested) {
        SpawnMode mode = rule == null || rule.spawnMode == null ? SpawnMode.RANDOM_VALID_POSITION : rule.spawnMode;
        if (mode == SpawnMode.BOSS_ROOM) {
            return Math.min(requested, 1);
        }
        return requested;
    }

    private static void recordPositionResult(RuleRuntimeState state, SpawnPositionResult result) {
        state.lastPositionSearchAttempts = result.attempts();
        state.lastPositionSearchReason = result.reason();
        state.lastPositionSearchStats = result.stats().format();
    }

    private static void debugPositionFailure(Zone zone, MobRule rule, SpawnPositionResult result) {
        GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Spawn position search failed: zone={} rule={} entity={} {}",
                zone.id, rule.id, rule.entity, result.failureSummary());
    }
}
