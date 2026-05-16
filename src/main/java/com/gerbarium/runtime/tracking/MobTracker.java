package com.gerbarium.runtime.tracking;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.access.EntityPersistentDataHolder;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.SpawnTrigger;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tick.ZoneActivationManager;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import com.gerbarium.runtime.util.RuntimeWorldUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MobTracker {
    // UUID-based tracking: zoneId:ruleId -> Set<UUID>
    private static final Map<String, Set<UUID>> primaryNormalUuids = new ConcurrentHashMap<>();
    private static final Map<String, Set<UUID>> primaryForcedUuids = new ConcurrentHashMap<>();
    private static final Map<String, Set<UUID>> companionNormalUuids = new ConcurrentHashMap<>();
    private static final Map<String, Set<UUID>> companionForcedUuids = new ConcurrentHashMap<>();

    // Legacy count maps for backward compatibility during transition
    private static final Map<String, Integer> primaryNormalCounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> primaryForcedCounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> companionNormalCounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> companionForcedCounts = new ConcurrentHashMap<>();

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            onEntityDeath(entity);
        });
    }

    public static void trackEntity(Entity entity, String zoneId, String ruleId, String role, boolean forced) {
        UUID uuid = entity.getUuid();
        String key = zoneId + ":" + ruleId;
        Map<String, Set<UUID>> targetUuids;
        Map<String, Integer> targetCounts;

        if ("PRIMARY".equals(role)) {
            targetUuids = forced ? primaryForcedUuids : primaryNormalUuids;
            targetCounts = forced ? primaryForcedCounts : primaryNormalCounts;
        } else if ("COMPANION".equals(role)) {
            targetUuids = forced ? companionForcedUuids : companionNormalUuids;
            targetCounts = forced ? companionForcedCounts : companionNormalCounts;
        } else {
            return;
        }

        targetUuids.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        targetCounts.merge(key, 1, Integer::sum);

        // Also track in RuleRuntimeState
        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zoneId, ruleId);
        if (state.aliveEntityUuids == null) {
            state.aliveEntityUuids = new HashSet<>();
        }
        state.aliveEntityUuids.add(uuid);
        RuntimeStateStorage.markDirty(zoneId);
    }

    public static void untrackEntity(UUID uuid, String zoneId, String ruleId, String role, boolean forced) {
        String key = zoneId + ":" + ruleId;
        Map<String, Set<UUID>> targetUuids;
        Map<String, Integer> targetCounts;

        if ("PRIMARY".equals(role)) {
            targetUuids = forced ? primaryForcedUuids : primaryNormalUuids;
            targetCounts = forced ? primaryForcedCounts : primaryNormalCounts;
        } else if ("COMPANION".equals(role)) {
            targetUuids = forced ? companionForcedUuids : companionNormalUuids;
            targetCounts = forced ? companionForcedCounts : companionNormalCounts;
        } else {
            return;
        }

        Set<UUID> set = targetUuids.get(key);
        if (set != null) {
            set.remove(uuid);
        }
        targetCounts.computeIfPresent(key, (k, v) -> Math.max(0, v - 1));

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zoneId, ruleId);
        if (state.aliveEntityUuids != null) {
            state.aliveEntityUuids.remove(uuid);
        }
    }

    private static void onEntityDeath(LivingEntity entity) {
        long now = System.currentTimeMillis();
        if (((EntityPersistentDataHolder) entity).getPersistentData().getBoolean(MobTagger.TAG_CLEANUP)) {
            decrementAny(entity);
            return;
        }

        Optional<ManagedMobInfo> infoOpt = MobTagger.getInfo(entity);
        if (infoOpt.isEmpty()) return;

        ManagedMobInfo info = infoOpt.get();

        if ("PRIMARY".equals(info.role)) {
            decrementPrimary(info.zoneId, info.ruleId, info.forced);
        } else if ("COMPANION".equals(info.role)) {
            decrementCompanion(info.zoneId, info.ruleId, info.forced);
        }

        if (!info.forced) {
            RuleRuntimeState state = RuntimeStateStorage.getRuleState(info.zoneId, info.ruleId);
            state.lastDeathAt = now;
            state.deathCount++;
            if (state.aliveEntityUuids != null) {
                state.aliveEntityUuids.remove(entity.getUuid());
            }

            // Schedule after-death respawn if applicable
            scheduleAfterDeathRespawn(info.zoneId, info.ruleId, now, state);

            RuntimeStateStorage.markDirty(info.zoneId);
            GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Death detected: zone={} rule={} entity={} uuid={}",
                    info.zoneId, info.ruleId, entity.getType().getTranslationKey(), entity.getUuid());
        }
    }

    private static void scheduleAfterDeathRespawn(String zoneId, String ruleId, long now, RuleRuntimeState state) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return;
        Optional<MobRule> ruleOpt = zoneOpt.get().mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return;

        MobRule rule = ruleOpt.get();
        rule.normalize();

        SpawnTrigger trigger = rule.spawnTrigger;
        if (trigger == SpawnTrigger.AFTER_DEATH || trigger == SpawnTrigger.TIMER_AND_AFTER_DEATH) {
            int delaySeconds = rule.afterDeathDelaySeconds > 0 ? rule.afterDeathDelaySeconds : rule.respawnSeconds;
            state.hasPendingAfterDeathRespawn = true;
            state.pendingAfterDeathRespawnTimeMillis = now + delaySeconds * 1000L;
            state.nextAllowedAttemptTimeMillis = state.pendingAfterDeathRespawnTimeMillis;
            GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] After-death respawn scheduled: zone={} rule={} delay={}s",
                    zoneId, ruleId, delaySeconds);
        }
    }

    private static void decrementAny(LivingEntity entity) {
        Optional<ManagedMobInfo> infoOpt = MobTagger.getInfo(entity);
        if (infoOpt.isEmpty()) return;
        ManagedMobInfo info = infoOpt.get();
        if ("PRIMARY".equals(info.role)) decrementPrimary(info.zoneId, info.ruleId, info.forced);
        else if ("COMPANION".equals(info.role)) decrementCompanion(info.zoneId, info.ruleId, info.forced);
    }

    public static void checkUniqueEncounterCleared(String zoneId, String ruleId, boolean forced, boolean cleanup) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return;

        Optional<MobRule> ruleOpt = zoneOpt.get().mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return;

        MobRule rule = ruleOpt.get();
        if (rule.spawnType != com.gerbarium.runtime.model.SpawnType.UNIQUE) return;

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zoneId, ruleId);

        if (forced) {
            RuntimeStateStorage.addEvent(zoneId, ruleId, "FORCED_UNIQUE_CLEARED", "Forced unique encounter cleared (normal schedule unchanged)");
            return;
        }

        if (!state.encounterActive) return;

        int pAlive = getNormalPrimaryAliveCount(zoneId, ruleId);
        int cAlive = getNormalCompanionAliveCount(zoneId, ruleId);

        state.encounterPrimaryAlive = pAlive;
        state.encounterCompanionsAlive = cAlive;

        if (pAlive == 0 && cAlive == 0) {
            state.encounterActive = false;
            state.encounterClearedAt = System.currentTimeMillis();
            state.lastDeathAt = state.encounterClearedAt;

            if (cleanup) {
                state.nextAttemptAt = state.encounterClearedAt + rule.failedSpawnRetrySeconds * 1000L;
                state.nextAvailableAt = state.nextAttemptAt;
                state.lastAttemptReason = "Unique encounter cleaned up, retry scheduled";
                RuntimeStateStorage.addEvent(zoneId, ruleId, "UNIQUE_ENCOUNTER_CLEARED", "Unique encounter cleared (cleanup), retry in " + rule.failedSpawnRetrySeconds + "s");
            } else if (rule.spawnTrigger == SpawnTrigger.AFTER_DEATH || rule.spawnTrigger == SpawnTrigger.TIMER_AND_AFTER_DEATH) {
                int delaySeconds = rule.afterDeathDelaySeconds > 0 ? rule.afterDeathDelaySeconds : rule.respawnSeconds;
                state.nextAttemptAt = state.encounterClearedAt + delaySeconds * 1000L;
                state.nextAvailableAt = state.nextAttemptAt;
                state.hasPendingAfterDeathRespawn = true;
                state.pendingAfterDeathRespawnTimeMillis = state.nextAttemptAt;
                state.lastAttemptReason = "Unique encounter cleared, next in " + delaySeconds + "s";
                RuntimeStateStorage.addEvent(zoneId, ruleId, "UNIQUE_ENCOUNTER_CLEARED", "Unique encounter cleared, next available in " + delaySeconds + "s");
            } else {
                RuntimeStateStorage.addEvent(zoneId, ruleId, "UNIQUE_ENCOUNTER_CLEARED", "Unique encounter cleared.");
            }
            RuntimeStateStorage.markDirty(zoneId);
        }
    }

    public static int getPrimaryAliveCount(String zoneId, String ruleId) {
        return getNormalPrimaryAliveCount(zoneId, ruleId) + getForcedPrimaryAliveCount(zoneId, ruleId);
    }

    public static int getCompanionAliveCount(String zoneId, String ruleId) {
        return getNormalCompanionAliveCount(zoneId, ruleId) + getForcedCompanionAliveCount(zoneId, ruleId);
    }

    public static int getNormalPrimaryAliveCount(String zoneId, String ruleId) {
        String key = zoneId + ":" + ruleId;
        Set<UUID> set = primaryNormalUuids.get(key);
        return set != null ? set.size() : primaryNormalCounts.getOrDefault(key, 0);
    }

    public static int getForcedPrimaryAliveCount(String zoneId, String ruleId) {
        String key = zoneId + ":" + ruleId;
        Set<UUID> set = primaryForcedUuids.get(key);
        return set != null ? set.size() : primaryForcedCounts.getOrDefault(key, 0);
    }

    public static int getNormalCompanionAliveCount(String zoneId, String ruleId) {
        String key = zoneId + ":" + ruleId;
        Set<UUID> set = companionNormalUuids.get(key);
        return set != null ? set.size() : companionNormalCounts.getOrDefault(key, 0);
    }

    public static int getForcedCompanionAliveCount(String zoneId, String ruleId) {
        String key = zoneId + ":" + ruleId;
        Set<UUID> set = companionForcedUuids.get(key);
        return set != null ? set.size() : companionForcedCounts.getOrDefault(key, 0);
    }

    public static void incrementPrimary(String zoneId, String ruleId, boolean forced) {
        Map<String, Integer> counts = forced ? primaryForcedCounts : primaryNormalCounts;
        counts.merge(zoneId + ":" + ruleId, 1, Integer::sum);
    }

    public static void decrementPrimary(String zoneId, String ruleId, boolean forced) {
        Map<String, Integer> counts = forced ? primaryForcedCounts : primaryNormalCounts;
        counts.computeIfPresent(zoneId + ":" + ruleId, (k, v) -> Math.max(0, v - 1));
    }

    public static void incrementCompanion(String zoneId, String ruleId, boolean forced) {
        Map<String, Integer> counts = forced ? companionForcedCounts : companionNormalCounts;
        counts.merge(zoneId + ":" + ruleId, 1, Integer::sum);
    }

    public static void decrementCompanion(String zoneId, String ruleId, boolean forced) {
        Map<String, Integer> counts = forced ? companionForcedCounts : companionNormalCounts;
        counts.computeIfPresent(zoneId + ":" + ruleId, (k, v) -> Math.max(0, v - 1));
    }

    public static void cleanupStaleUuids(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (Zone zone : ZoneRepository.getEnabledZones()) {
            ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
            if (world == null) continue;

            for (MobRule rule : zone.mobs) {
                String key = zone.id + ":" + rule.id;
                RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, rule.id);
                if (state.aliveEntityUuids == null || state.aliveEntityUuids.isEmpty()) continue;

                Set<UUID> stale = new HashSet<>();
                for (UUID uuid : state.aliveEntityUuids) {
                    Entity entity = world.getEntity(uuid);
                    if (entity == null || entity.isRemoved() || (entity instanceof LivingEntity living && !living.isAlive())) {
                        stale.add(uuid);
                    }
                }

                if (!stale.isEmpty()) {
                    state.aliveEntityUuids.removeAll(stale);
                    primaryNormalUuids.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).removeAll(stale);
                    primaryForcedUuids.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).removeAll(stale);
                    companionNormalUuids.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).removeAll(stale);
                    companionForcedUuids.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).removeAll(stale);

                    if (rule.respawnAfterDespawn && (rule.spawnTrigger == SpawnTrigger.TIMER || rule.spawnTrigger == SpawnTrigger.TIMER_AND_AFTER_DEATH)) {
                        // Despawn detected - schedule retry
                        state.nextAllowedAttemptTimeMillis = now + rule.retrySeconds * 1000L;
                        GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Stale UUID cleanup: zone={} rule={} removed={} despawnRetry scheduled",
                                zone.id, rule.id, stale.size());
                    }

                    RuntimeStateStorage.markDirty(zone.id);
                }
            }
        }
    }

    public static void resyncZone(MinecraftServer server, Zone zone) {
        String prefix = zone.id + ":";
        primaryNormalCounts.keySet().removeIf(key -> key.startsWith(prefix));
        primaryForcedCounts.keySet().removeIf(key -> key.startsWith(prefix));
        companionNormalCounts.keySet().removeIf(key -> key.startsWith(prefix));
        companionForcedCounts.keySet().removeIf(key -> key.startsWith(prefix));
        primaryNormalUuids.keySet().removeIf(key -> key.startsWith(prefix));
        primaryForcedUuids.keySet().removeIf(key -> key.startsWith(prefix));
        companionNormalUuids.keySet().removeIf(key -> key.startsWith(prefix));
        companionForcedUuids.keySet().removeIf(key -> key.startsWith(prefix));

        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return;

        Box box = getManagedMobScanBox(zone);

        for (Entity entity : world.getOtherEntities(null, box)) {
            if (isGone(entity)) continue;
            Optional<ManagedMobInfo> info = MobTagger.getInfo(entity);
            if (info.isPresent() && info.get().zoneId.equals(zone.id)) {
                String key = zone.id + ":" + info.get().ruleId;
                if ("PRIMARY".equals(info.get().role)) {
                    incrementPrimary(zone.id, info.get().ruleId, info.get().forced);
                    (info.get().forced ? primaryForcedUuids : primaryNormalUuids)
                            .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(entity.getUuid());
                } else if ("COMPANION".equals(info.get().role)) {
                    incrementCompanion(zone.id, info.get().ruleId, info.get().forced);
                    (info.get().forced ? companionForcedUuids : companionNormalUuids)
                            .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(entity.getUuid());
                }

                RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, info.get().ruleId);
                if (state.aliveEntityUuids == null) {
                    state.aliveEntityUuids = new HashSet<>();
                }
                state.aliveEntityUuids.add(entity.getUuid());
            }
        }

        for (MobRule rule : zone.mobs) {
            if (rule.spawnType == com.gerbarium.runtime.model.SpawnType.UNIQUE) {
                checkUniqueEncounterCleared(zone.id, rule.id, false, false);
            }
        }
    }

    public static void resyncActiveZones(MinecraftServer server) {
        Map<String, Integer> newPrimaryNormal = new HashMap<>();
        Map<String, Integer> newPrimaryForced = new HashMap<>();
        Map<String, Integer> newCompanionNormal = new HashMap<>();
        Map<String, Integer> newCompanionForced = new HashMap<>();
        Map<String, Set<UUID>> newPrimaryNormalUuids = new HashMap<>();
        Map<String, Set<UUID>> newPrimaryForcedUuids = new HashMap<>();
        Map<String, Set<UUID>> newCompanionNormalUuids = new HashMap<>();
        Map<String, Set<UUID>> newCompanionForcedUuids = new HashMap<>();

        for (Zone zone : ZoneRepository.getEnabledZones()) {
            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            if (!zState.active) continue;

            ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
            if (world == null) continue;

            Box box = getManagedMobScanBox(zone);

            for (Entity entity : world.getOtherEntities(null, box)) {
                if (isGone(entity)) continue;
                Optional<ManagedMobInfo> info = MobTagger.getInfo(entity);
                if (info.isPresent() && info.get().zoneId.equals(zone.id)) {
                    String key = zone.id + ":" + info.get().ruleId;
                    if ("PRIMARY".equals(info.get().role)) {
                        Map<String, Integer> targetCounts = info.get().forced ? newPrimaryForced : newPrimaryNormal;
                        targetCounts.merge(key, 1, Integer::sum);
                        Map<String, Set<UUID>> targetUuids = info.get().forced ? newPrimaryForcedUuids : newPrimaryNormalUuids;
                        targetUuids.computeIfAbsent(key, k -> new HashSet<>()).add(entity.getUuid());
                    } else if ("COMPANION".equals(info.get().role)) {
                        Map<String, Integer> targetCounts = info.get().forced ? newCompanionForced : newCompanionNormal;
                        targetCounts.merge(key, 1, Integer::sum);
                        Map<String, Set<UUID>> targetUuids = info.get().forced ? newCompanionForcedUuids : newCompanionNormalUuids;
                        targetUuids.computeIfAbsent(key, k -> new HashSet<>()).add(entity.getUuid());
                    }

                    RuleRuntimeState state = RuntimeStateStorage.getRuleState(zone.id, info.get().ruleId);
                    if (state.aliveEntityUuids == null) {
                        state.aliveEntityUuids = new HashSet<>();
                    }
                    state.aliveEntityUuids.add(entity.getUuid());
                }
            }
        }

        primaryNormalCounts.clear();
        primaryNormalCounts.putAll(newPrimaryNormal);
        primaryForcedCounts.clear();
        primaryForcedCounts.putAll(newPrimaryForced);
        companionNormalCounts.clear();
        companionNormalCounts.putAll(newCompanionNormal);
        companionForcedCounts.clear();
        companionForcedCounts.putAll(newCompanionForced);

        primaryNormalUuids.clear();
        primaryNormalUuids.putAll(newPrimaryNormalUuids);
        primaryForcedUuids.clear();
        primaryForcedUuids.putAll(newPrimaryForcedUuids);
        companionNormalUuids.clear();
        companionNormalUuids.putAll(newCompanionNormalUuids);
        companionForcedUuids.clear();
        companionForcedUuids.putAll(newCompanionForcedUuids);

        for (Zone zone : ZoneRepository.getEnabledZones()) {
            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            if (!zState.active) continue;
            for (MobRule rule : zone.mobs) {
                if (rule.spawnType == com.gerbarium.runtime.model.SpawnType.UNIQUE) {
                    checkUniqueEncounterCleared(zone.id, rule.id, false, false);
                }
            }
        }
    }

    private static Box getManagedMobScanBox(Zone zone) {
        int padding = Math.max(0, RuntimeConfigStorage.getConfig().boundaryScanPadding);
        return zone.getExpandedBox(padding);
    }

    private static boolean isGone(Entity entity) {
        return entity == null || entity.isRemoved() || (entity instanceof LivingEntity living && !living.isAlive());
    }
}
