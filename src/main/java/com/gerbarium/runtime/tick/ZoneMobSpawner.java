package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.SpawnMode;
import com.gerbarium.runtime.model.SpawnTrigger;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public class ZoneMobSpawner {
    private static final Random RANDOM = new Random();

    public static void tick(MinecraftServer server) {
        List<Zone> zones = ZoneRepository.getEnabledZones();
        int zonesProcessed = 0;
        int totalSpawnsThisTick = 0;
        int maxSpawns = RuntimeConfigStorage.getConfig().maxSpawnsPerTickCycle;
        int maxZones = RuntimeConfigStorage.getConfig().maxZonesProcessedPerSpawnTick;
        long now = System.currentTimeMillis();

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
            if (mobs == null) continue;

            for (MobRule rule : mobs) {
                if (totalSpawnsThisTick >= maxSpawns) {
                    break;
                }

                rule.normalize();

                if (!rule.enabled) {
                    debugSkip(zone.id, rule.id, "rule_disabled");
                    continue;
                }

                if (rule.spawnTrigger == SpawnTrigger.MANUAL_ONLY) {
                    debugSkip(zone.id, rule.id, "manual_only");
                    continue;
                }

                String configStatus = RuntimeRuleValidationUtil.getConfigStatus(rule);
                String entityStatus = RuntimeRuleValidationUtil.getEntityStatus(rule);
                if (configStatus != null || entityStatus != null) {
                    debugSkip(zone.id, rule.id, configStatus != null ? configStatus : entityStatus);
                    continue;
                }

                debugCheck(zone, rule);

                int spawned = processRuleAutoSpawn(world, zone, rule, zState, now);
                totalSpawnsThisTick += spawned;
            }
        }
    }

    private static int processRuleAutoSpawn(ServerWorld world, Zone zone, MobRule rule, ZoneRuntimeState zState, long now) {
        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
        ZoneRuntimePersistentState zoneState = RuntimeStateStorage.getZoneState(zone.id).zone;

        if (!zState.firstSpawnDelayPassed) {
            debugSkip(zone.id, rule.id, "first_spawn_delay");
            return 0;
        }

        // Cleanup stale UUIDs before counting
        cleanupStaleUuidsForRule(world, zone, rule, state);

        int alive = MobTracker.getNormalPrimaryAliveCount(zone.id, rule.id);
        if (alive >= rule.maxAlive) {
            debugSkip(zone.id, rule.id, "max_alive current=" + alive + " max=" + rule.maxAlive);
            return 0;
        }

        // Cooldown check
        long nextAttempt = Math.max(state.nextAllowedAttemptTimeMillis, Math.max(state.nextAvailableAt, state.nextAttemptAt));
        if (now < nextAttempt) {
            long remaining = nextAttempt - now;
            debugSkip(zone.id, rule.id, "cooldown remaining=" + (remaining / 1000L) + "s");
            return 0;
        }

        SpawnTrigger trigger = rule.spawnTrigger;

        // AFTER_DEATH trigger: only spawn if no alive and pending respawn is ready (or no pending)
        if (trigger == SpawnTrigger.AFTER_DEATH) {
            if (alive > 0) {
                debugSkip(zone.id, rule.id, "after_death_alive_exists");
                return 0;
            }
            if (state.hasPendingAfterDeathRespawn && now < state.pendingAfterDeathRespawnTimeMillis) {
                long remaining = state.pendingAfterDeathRespawnTimeMillis - now;
                debugSkip(zone.id, rule.id, "after_death_pending remaining=" + (remaining / 1000L) + "s");
                return 0;
            }
        }

        // TIMER_AND_AFTER_DEATH: timer can spawn, but if alive==0 and pending, respect pending
        if (trigger == SpawnTrigger.TIMER_AND_AFTER_DEATH) {
            if (alive == 0 && state.hasPendingAfterDeathRespawn && now < state.pendingAfterDeathRespawnTimeMillis) {
                long remaining = state.pendingAfterDeathRespawnTimeMillis - now;
                debugSkip(zone.id, rule.id, "timer_and_death_pending remaining=" + (remaining / 1000L) + "s");
                return 0;
            }
        }

        // Player proximity check
        if (rule.requirePlayerNearby) {
            boolean playerNear = isPlayerNearZone(world, zone, rule.playerActivationRange);
            if (!playerNear) {
                debugSkip(zone.id, rule.id, "no_player_nearby range=" + rule.playerActivationRange);
                return 0;
            }
        }

        // Chance check
        if (RANDOM.nextDouble() > rule.chance) {
            state.lastAttemptAt = now;
            state.lastAttemptResult = "SKIPPED_CHANCE_FAIL";
            state.lastAttemptReason = "Chance roll failed";
            state.nextAllowedAttemptTimeMillis = now + rule.retrySeconds * 1000L;
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

        // Spawn
        SpawnResult result = spawnEntities(world, zone, rule, zState, state, now, SpawnContext.NORMAL, toSpawn);

        if (result.spawned > 0) {
            state.lastAttemptAt = now;
            state.lastAttemptResult = "SUCCESS";
            state.lastAttemptReason = "Auto spawned " + result.spawned + " primary mobs";
            state.lastSuccessAt = now;
            state.lastSuccessfulPrimaryCount = result.spawned;
            state.lastSuccessfulCompanionCount = result.companionsSpawned;
            state.totalAttempts++;
            state.totalSuccesses++;
            state.totalPrimarySpawned += result.spawned;
            state.totalCompanionsSpawned += result.companionsSpawned;
            zoneState.totalSpawnAttempts++;
            zoneState.totalSuccessfulSpawns++;
            state.nextAllowedAttemptTimeMillis = now + rule.respawnSeconds * 1000L;
            state.hasPendingAfterDeathRespawn = false;
            state.pendingAfterDeathRespawnTimeMillis = 0;
            RuntimeStateStorage.addEvent(zone.id, rule.id, "AUTO_SPAWN_SUCCESS", "Auto spawned " + result.spawned + " primary mobs");
            if (result.companionsSpawned > 0) {
                RuntimeStateStorage.addEvent(zone.id, rule.id, "COMPANIONS_SPAWNED", "Spawned " + result.companionsSpawned + " companions");
            }
            GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Auto spawn success: zone={} rule={} spawned={} nextIn={}s",
                    zone.id, rule.id, result.spawned, rule.respawnSeconds);
        } else {
            state.lastAttemptAt = now;
            state.lastAttemptResult = "FAILED_NO_POSITION";
            state.lastAttemptReason = result.failureReason;
            state.nextAllowedAttemptTimeMillis = now + rule.retrySeconds * 1000L;
            state.totalAttempts++;
            zoneState.totalSpawnAttempts++;
            RuntimeStateStorage.addEvent(zone.id, rule.id, "AUTO_SPAWN_FAILED", "No valid position: " + result.failureReason);
            debugSkip(zone.id, rule.id, "no_valid_position attempts=" + result.attempts + " reason=" + result.failureReason);
        }

        RuntimeStateStorage.markDirty(zone.id);
        return result.spawned;
    }

    public static SpawnResult spawnEntities(ServerWorld world, Zone zone, MobRule rule, ZoneRuntimeState zState, RuleRuntimeState state, long now, SpawnContext context, int requestedCount) {
        int spawned = 0;
        int companionsSpawned = 0;
        List<BlockPos> selectedPositions = new ArrayList<>();
        String lastFailureReason = "no_valid_position";
        int totalAttempts = 0;

        int batchSize = effectiveBatchSize(rule, requestedCount);
        int positionAttempts = context == SpawnContext.FORCED
                ? Math.max(Math.max(512, RuntimeConfigStorage.getConfig().forceSpawnPositionAttempts), rule.positionAttempts)
                : normalPositionAttempts(zone, rule);

        for (int i = 0; i < batchSize; i++) {
            SpawnPositionResult positionResult = SpawnPositionFinder.findSpawnPositionResult(world, zone, rule, zState != null ? zState.nearbyPlayers : null, positionAttempts, selectedPositions);
            totalAttempts = Math.max(totalAttempts, positionResult.attempts());
            Optional<BlockPos> pos = positionResult.position();

            if (pos.isEmpty()) {
                lastFailureReason = positionResult.reason();
                recordPositionResult(state, positionResult);
                debugPositionFailure(zone, rule, positionResult);
                break;
            }
            recordPositionResult(state, positionResult);

            var primaryEntity = EntitySpawnService.spawnPrimary(world, zone, rule, pos.get(), context);
            if (primaryEntity == null) {
                lastFailureReason = "spawn_rejected";
                break;
            }

            spawned++;
            selectedPositions.add(pos.get());
            companionsSpawned += EntitySpawnService.spawnCompanions(world, zone, rule, primaryEntity, context);
        }

        state.lastEncounterPrimarySpawned = spawned;
        state.lastEncounterCompanionsSpawned = companionsSpawned;
        state.knownAlive = state.getCurrentAliveCount();
        state.lastSuccessAt = spawned > 0 ? now : state.lastSuccessAt;
        state.lastSuccessfulPrimaryCount = spawned;
        state.lastSuccessfulCompanionCount = companionsSpawned;

        SpawnResult result = new SpawnResult();
        result.spawned = spawned;
        result.companionsSpawned = companionsSpawned;
        result.failureReason = lastFailureReason;
        result.attempts = totalAttempts;
        return result;
    }

    private static void cleanupStaleUuidsForRule(ServerWorld world, Zone zone, MobRule rule, RuleRuntimeState state) {
        if (state.aliveEntityUuids == null || state.aliveEntityUuids.isEmpty()) return;

        List<UUID> stale = new ArrayList<>();
        for (UUID uuid : state.aliveEntityUuids) {
            var entity = world.getEntity(uuid);
            if (entity == null || entity.isRemoved() || (entity instanceof net.minecraft.entity.LivingEntity living && !living.isAlive())) {
                stale.add(uuid);
            }
        }

        if (!stale.isEmpty()) {
            state.aliveEntityUuids.removeAll(stale);
            for (UUID uuid : stale) {
                MobTracker.untrackEntity(uuid, zone.id, rule.id, "PRIMARY", false);
                MobTracker.untrackEntity(uuid, zone.id, rule.id, "COMPANION", false);
            }
        }
    }

    private static boolean isPlayerNearZone(ServerWorld world, Zone zone, int range) {
        Box box = zone.getZoneBox().expand(range);
        for (PlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            if (box.contains(player.getX(), player.getY(), player.getZ())) {
                return true;
            }
        }
        return false;
    }

    private static void debugCheck(Zone zone, MobRule rule) {
        if (!RuntimeConfigStorage.getConfig().debug) return;
        GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Spawn check: zone={} rule={} entity={}", zone.id, rule.id, rule.entity);
    }

    private static void debugSkip(String zoneId, String ruleId, String reason) {
        if (!RuntimeConfigStorage.getConfig().debug) return;
        GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Spawn skipped: zone={} rule={} reason={}", zoneId, ruleId, reason);
    }

    private static void debugPositionFailure(Zone zone, MobRule rule, SpawnPositionResult result) {
        GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Spawn position search failed: zone={} rule={} entity={} {}",
                zone.id, rule.id, rule.entity, result.failureSummary());
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

    public static class SpawnResult {
        public int spawned;
        public int companionsSpawned;
        public String failureReason = "";
        public int attempts;
    }
}
