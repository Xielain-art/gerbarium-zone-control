package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.model.CooldownStart;
import com.gerbarium.runtime.spawn.EntitySpawnService;
import com.gerbarium.runtime.spawn.SpawnPositionFinder;
import com.gerbarium.runtime.spawn.SpawnResult;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tracking.MobTracker;
import com.gerbarium.runtime.util.TimeUtil;
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
                if (!rule.spawnWhenReady) continue;
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
            // TIMED budget management with reactivation cooldown protection
            int budget = rule.timedMaxSpawnsPerActivation != null ? rule.timedMaxSpawnsPerActivation : rule.maxAlive;
            
            // Check if budget window needs reset
            if (state.timedBudgetActivationId != zState.activationId) {
                long reactivationCooldown = zone.activation.reactivationCooldownSeconds * 1000L;
                if (now - state.lastTimedBudgetResetAt >= reactivationCooldown) {
                    // Reset budget for new activation window
                    state.timedBudgetActivationId = zState.activationId;
                    state.timedSpawnedThisActivation = 0;
                    state.timedBudgetExhausted = false;
                    state.lastTimedBudgetResetAt = now;
                } else {
                    // Budget window not ready to reset
                    state.timedBudgetActivationId = zState.activationId;
                }
            }
            
            // Check budget exhaustion
            if (budget != -1 && state.timedSpawnedThisActivation >= budget) {
                state.timedBudgetExhausted = true;
                state.lastAttemptResult = "SKIPPED_TIMED_BUDGET_EXHAUSTED";
                state.lastAttemptReason = "TIMED budget exhausted (" + state.timedSpawnedThisActivation + "/" + budget + ") for this activation window";
                RuntimeStateStorage.markDirty(zone.id);
                return 0;
            }

            if (!TimedSpawnLogic.shouldSpawn(now, rule, state)) {
                return 0;
            }
        } else if (rule.refillMode == com.gerbarium.runtime.model.RefillMode.AFTER_DEATH) {
            // AFTER_DEATH: spawn only if cooldown passed since last death
            if (state.lastDeathAt == 0) {
                // No death yet, use activation spawn logic
                long cooldown = Math.max(zone.activation.reactivationCooldownSeconds, rule.respawnSeconds) * 1000L;
                if (now - state.lastActivationSpawnAt < cooldown) {
                    return 0;
                }
            } else {
                // Death occurred, check respawn cooldown
                long respawnCooldown = rule.respawnSeconds * 1000L;
                if (now - state.lastDeathAt < respawnCooldown) {
                    return 0;
                }
            }
        } else {
            // ON_ACTIVATION logic with anti-farm protection
            
            // Check if already attempted during this activation session
            if (state.lastOnActivationAttemptActivationId == zState.activationId) {
                state.lastAttemptResult = "SKIPPED_ALREADY_ATTEMPTED_THIS_ACTIVATION";
                state.lastAttemptReason = "ON_ACTIVATION already ran during this activation. Leave and re-enter after cooldown to run again.";
                RuntimeStateStorage.markDirty(zone.id);
                return 0;
            }
            
            // Check cooldown based on cooldownStart setting
            if (rule.cooldownStart == CooldownStart.AFTER_DEATH && state.lastDeathAt > 0) {
                // Cooldown starts after death
                long respawnCooldown = rule.respawnSeconds * 1000L;
                if (now - state.lastDeathAt < respawnCooldown) {
                    state.lastAttemptResult = "SKIPPED_COOLDOWN";
                    state.lastAttemptReason = "Cooldown active until " + TimeUtil.formatRelative(state.lastDeathAt + respawnCooldown);
                    return 0;
                }
            } else if (rule.cooldownStart == CooldownStart.AFTER_ACTIVATION) {
                // Cooldown starts after zone activation
                long cooldown = Math.max(zone.activation.reactivationCooldownSeconds, rule.respawnSeconds) * 1000L;
                if (now - state.lastActivationSpawnAt < cooldown) {
                    state.lastAttemptResult = "SKIPPED_COOLDOWN";
                    state.lastAttemptReason = "Cooldown active until " + TimeUtil.formatRelative(state.lastActivationSpawnAt + cooldown);
                    return 0;
                }
            } else {
                // AFTER_ATTEMPT (default): cooldown starts after spawn attempt
                long cooldown = rule.respawnSeconds * 1000L;
                if (now - state.lastAttemptAt < cooldown) {
                    state.lastAttemptResult = "SKIPPED_COOLDOWN";
                    state.lastAttemptReason = "Cooldown active until " + TimeUtil.formatRelative(state.lastAttemptAt + cooldown);
                    return 0;
                }
            }
        }

        int alive = MobTracker.getPrimaryAliveCount(zone.id, rule.id);
        if (alive >= rule.maxAlive) {
            // Mark ON_ACTIVATION as attempted even if blocked by maxAlive
            if (rule.refillMode == com.gerbarium.runtime.model.RefillMode.ON_ACTIVATION) {
                state.lastOnActivationAttemptActivationId = zState.activationId;
            }
            state.lastActivationSpawnAt = now;
            state.lastAttemptAt = now;
            state.lastAttemptResult = "SKIPPED_MAX_ALIVE";
            state.lastAttemptReason = "Alive count " + alive + "/" + rule.maxAlive;
            state.totalAttempts++;
            RuntimeStateStorage.markDirty(zone.id);
            return 0;
        }

        if (RANDOM.nextDouble() > rule.chance) {
            // Mark ON_ACTIVATION as attempted even if chance failed
            if (rule.refillMode == com.gerbarium.runtime.model.RefillMode.ON_ACTIVATION) {
                state.lastOnActivationAttemptActivationId = zState.activationId;
            }
            state.lastActivationSpawnAt = now;
            state.lastAttemptAt = now;
            state.lastAttemptResult = "SKIPPED_CHANCE_FAIL";
            state.lastAttemptReason = "Chance roll failed";
            state.totalAttempts++;
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

        // Mark ON_ACTIVATION as attempted
        if (rule.refillMode == com.gerbarium.runtime.model.RefillMode.ON_ACTIVATION) {
            state.lastOnActivationAttemptActivationId = zState.activationId;
        }
        
        state.lastActivationSpawnAt = now;
        state.lastAttemptAt = now;
        state.totalAttempts++;
        
        if (spawned > 0) {
            if (rule.refillMode == com.gerbarium.runtime.model.RefillMode.TIMED) {
                state.timedSpawnedThisActivation += spawned;
            }

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
            state.totalAttempts++;
            RuntimeStateStorage.markDirty(zone.id);
            return 0;
        }

        Optional<BlockPos> pos = SpawnPositionFinder.findSpawnPosition(world, zone, zState.nearbyPlayers);
        state.totalAttempts++;
        
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
