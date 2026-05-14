package com.gerbarium.runtime.tracking;
import com.gerbarium.runtime.access.EntityPersistentDataHolder;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.CooldownStart;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.state.RuntimeEvent;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tick.ZoneActivationManager;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import com.gerbarium.runtime.util.RuntimeWorldUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MobTracker {
    private static final Map<String, Integer> primaryNormalCounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> primaryForcedCounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> companionNormalCounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> companionForcedCounts = new ConcurrentHashMap<>();

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            onEntityDeath(entity);
        });
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
            if (!isUniqueRule(info.zoneId, info.ruleId)) {
                state.lastDeathAt = now;
                maybeStartCooldownOnDeath(info.zoneId, info.ruleId, now, state);
            }
            RuntimeStateStorage.markDirty(info.zoneId);
        }

        checkUniqueEncounterCleared(info.zoneId, info.ruleId, info.forced, false);
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
        if (ruleOpt.isEmpty() || ruleOpt.get().spawnType != SpawnType.UNIQUE) return;

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zoneId, ruleId);

        // Forced encounters should not affect normal encounter state
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

            CooldownStart cooldownStart = ruleOpt.get().cooldownStart == null ? CooldownStart.AFTER_ACTIVATION : ruleOpt.get().cooldownStart;
            if (cleanup) {
                state.nextAttemptAt = state.encounterClearedAt + ruleOpt.get().failedSpawnRetrySeconds * 1000L;
                state.nextAvailableAt = state.nextAttemptAt;
                state.lastAttemptReason = "Unique encounter cleaned up, retry scheduled";
                RuntimeStateStorage.addEvent(zoneId, ruleId, "UNIQUE_ENCOUNTER_CLEARED", "Unique encounter cleared (cleanup), retry in " + ruleOpt.get().failedSpawnRetrySeconds + "s");
            } else if (cooldownStart == CooldownStart.AFTER_DEATH) {
                state.nextAttemptAt = state.encounterClearedAt + ruleOpt.get().respawnSeconds * 1000L;
                state.nextAvailableAt = state.nextAttemptAt;
                state.lastAttemptReason = "Unique encounter cleared, next in " + ruleOpt.get().respawnSeconds + "s";
                RuntimeStateStorage.addEvent(zoneId, ruleId, "UNIQUE_ENCOUNTER_CLEARED", "Unique encounter cleared, next available in " + ruleOpt.get().respawnSeconds + "s");
            } else {
                RuntimeStateStorage.addEvent(zoneId, ruleId, "UNIQUE_ENCOUNTER_CLEARED", "Unique encounter cleared.");
            }
            RuntimeStateStorage.markDirty(zoneId);
        }
    }

    private static boolean isUniqueRule(String zoneId, String ruleId) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return false;
        return zoneOpt.get().mobs.stream().anyMatch(rule -> rule.id.equals(ruleId) && rule.spawnType == SpawnType.UNIQUE);
    }

    private static void maybeStartCooldownOnDeath(String zoneId, String ruleId, long now, RuleRuntimeState state) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return;
        Optional<MobRule> ruleOpt = zoneOpt.get().mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return;

        MobRule rule = ruleOpt.get();
        CooldownStart cooldownStart = rule.cooldownStart == null ? CooldownStart.AFTER_ACTIVATION : rule.cooldownStart;
        if (cooldownStart == CooldownStart.AFTER_DEATH) {
            state.nextAttemptAt = now + rule.respawnSeconds * 1000L;
            state.nextAvailableAt = state.nextAttemptAt;
        }
    }

    public static int getPrimaryAliveCount(String zoneId, String ruleId) {
        return getNormalPrimaryAliveCount(zoneId, ruleId) + getForcedPrimaryAliveCount(zoneId, ruleId);
    }

    public static int getCompanionAliveCount(String zoneId, String ruleId) {
        return getNormalCompanionAliveCount(zoneId, ruleId) + getForcedCompanionAliveCount(zoneId, ruleId);
    }

    public static int getNormalPrimaryAliveCount(String zoneId, String ruleId) {
        return primaryNormalCounts.getOrDefault(zoneId + ":" + ruleId, 0);
    }

    public static int getForcedPrimaryAliveCount(String zoneId, String ruleId) {
        return primaryForcedCounts.getOrDefault(zoneId + ":" + ruleId, 0);
    }

    public static int getNormalCompanionAliveCount(String zoneId, String ruleId) {
        return companionNormalCounts.getOrDefault(zoneId + ":" + ruleId, 0);
    }

    public static int getForcedCompanionAliveCount(String zoneId, String ruleId) {
        return companionForcedCounts.getOrDefault(zoneId + ":" + ruleId, 0);
    }

    public static void incrementPrimary(String zoneId, String ruleId, boolean forced) {
        (forced ? primaryForcedCounts : primaryNormalCounts).merge(zoneId + ":" + ruleId, 1, Integer::sum);
    }

    public static void decrementPrimary(String zoneId, String ruleId, boolean forced) {
        Map<String, Integer> counts = forced ? primaryForcedCounts : primaryNormalCounts;
        counts.computeIfPresent(zoneId + ":" + ruleId, (k, v) -> Math.max(0, v - 1));
    }

    public static void incrementCompanion(String zoneId, String ruleId, boolean forced) {
        (forced ? companionForcedCounts : companionNormalCounts).merge(zoneId + ":" + ruleId, 1, Integer::sum);
    }

    public static void decrementCompanion(String zoneId, String ruleId, boolean forced) {
        Map<String, Integer> counts = forced ? companionForcedCounts : companionNormalCounts;
        counts.computeIfPresent(zoneId + ":" + ruleId, (k, v) -> Math.max(0, v - 1));
    }

    public static void resyncZone(MinecraftServer server, Zone zone) {
        String prefix = zone.id + ":";
        primaryNormalCounts.keySet().removeIf(key -> key.startsWith(prefix));
        primaryForcedCounts.keySet().removeIf(key -> key.startsWith(prefix));
        companionNormalCounts.keySet().removeIf(key -> key.startsWith(prefix));
        companionForcedCounts.keySet().removeIf(key -> key.startsWith(prefix));

        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return;

        Box box = getManagedMobScanBox(zone);

        for (net.minecraft.entity.Entity entity : world.getOtherEntities(null, box)) {
            Optional<ManagedMobInfo> info = MobTagger.getInfo(entity);
            if (info.isPresent() && info.get().zoneId.equals(zone.id)) {
                if ("PRIMARY".equals(info.get().role)) incrementPrimary(zone.id, info.get().ruleId, info.get().forced);
                else if ("COMPANION".equals(info.get().role)) incrementCompanion(zone.id, info.get().ruleId, info.get().forced);
            }
        }

        // After resync, check if any unique encounters should be marked cleared
        for (MobRule rule : zone.mobs) {
            if (rule.spawnType == SpawnType.UNIQUE) {
                checkUniqueEncounterCleared(zone.id, rule.id, false, false);
            }
        }
    }

    public static void resyncActiveZones(MinecraftServer server) {
        // Build new counts in temporary maps to avoid race with death/spawn events
        Map<String, Integer> newPrimaryNormal = new HashMap<>();
        Map<String, Integer> newPrimaryForced = new HashMap<>();
        Map<String, Integer> newCompanionNormal = new HashMap<>();
        Map<String, Integer> newCompanionForced = new HashMap<>();

        for (Zone zone : ZoneRepository.getEnabledZones()) {
            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            if (!zState.active) continue;

            ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
            if (world == null) continue;

            Box box = getManagedMobScanBox(zone);

            for (net.minecraft.entity.Entity entity : world.getOtherEntities(null, box)) {
                Optional<ManagedMobInfo> info = MobTagger.getInfo(entity);
                if (info.isPresent() && info.get().zoneId.equals(zone.id)) {
                    String key = zone.id + ":" + info.get().ruleId;
                    if ("PRIMARY".equals(info.get().role)) {
                        Map<String, Integer> target = info.get().forced ? newPrimaryForced : newPrimaryNormal;
                        target.merge(key, 1, Integer::sum);
                    } else if ("COMPANION".equals(info.get().role)) {
                        Map<String, Integer> target = info.get().forced ? newCompanionForced : newCompanionNormal;
                        target.merge(key, 1, Integer::sum);
                    }
                }
            }
        }

        // Swap atomically: clear old maps and put new values
        primaryNormalCounts.clear();
        primaryNormalCounts.putAll(newPrimaryNormal);
        primaryForcedCounts.clear();
        primaryForcedCounts.putAll(newPrimaryForced);
        companionNormalCounts.clear();
        companionNormalCounts.putAll(newCompanionNormal);
        companionForcedCounts.clear();
        companionForcedCounts.putAll(newCompanionForced);

        for (Zone zone : ZoneRepository.getEnabledZones()) {
            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            if (!zState.active) continue;
            for (MobRule rule : zone.mobs) {
                if (rule.spawnType == SpawnType.UNIQUE) {
                    checkUniqueEncounterCleared(zone.id, rule.id, false, false);
                }
            }
        }
    }

    private static Box getManagedMobScanBox(Zone zone) {
        int padding = Math.max(0, RuntimeConfigStorage.getConfig().boundaryScanPadding);
        return zone.getExpandedBox(padding);
    }
}
