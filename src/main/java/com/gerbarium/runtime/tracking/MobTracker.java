package com.gerbarium.runtime.tracking;

import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.state.RuntimeEvent;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MobTracker {
    // Key: zoneId + ":" + ruleId
    private static final Map<String, Integer> primaryCounts = new ConcurrentHashMap<>();
    private static final Map<String, Integer> companionCounts = new ConcurrentHashMap<>();

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            onEntityDeath(entity);
        });
    }

    private static void onEntityDeath(LivingEntity entity) {
        if (entity.getPersistentData().getBoolean(MobTagger.TAG_CLEANUP)) {
            decrementAny(entity);
            return;
        }

        Optional<ManagedMobInfo> infoOpt = MobTagger.getInfo(entity);
        if (infoOpt.isEmpty()) return;

        ManagedMobInfo info = infoOpt.get();
        if ("PRIMARY".equals(info.role)) {
            decrementPrimary(info.zoneId, info.ruleId);
        } else if ("COMPANION".equals(info.role)) {
            decrementCompanion(info.zoneId, info.ruleId);
        }

        checkUniqueEncounterCleared(info.zoneId, info.ruleId, info.forced, false);
    }

    private static void decrementAny(LivingEntity entity) {
        Optional<ManagedMobInfo> infoOpt = MobTagger.getInfo(entity);
        if (infoOpt.isEmpty()) return;
        ManagedMobInfo info = infoOpt.get();
        if ("PRIMARY".equals(info.role)) decrementPrimary(info.zoneId, info.ruleId);
        else if ("COMPANION".equals(info.role)) decrementCompanion(info.zoneId, info.ruleId);
    }

    public static void checkUniqueEncounterCleared(String zoneId, String ruleId, boolean forced, boolean cleanup) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return;

        Optional<MobRule> ruleOpt = zoneOpt.get().mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty() || ruleOpt.get().spawnType != SpawnType.UNIQUE) return;

        RuleRuntimeState state = RuntimeStateStorage.getRuleState(zoneId, ruleId);
        if (!state.encounterActive) return;

        int pAlive = getPrimaryAliveCount(zoneId, ruleId);
        int cAlive = getCompanionAliveCount(zoneId, ruleId);

        state.encounterPrimaryAlive = pAlive;
        state.encounterCompanionsAlive = cAlive;

        if (pAlive == 0 && cAlive == 0) {
            state.encounterActive = false;
            state.encounterClearedAt = System.currentTimeMillis();
            state.lastDeathAt = state.encounterClearedAt;

            if (forced) {
                state.lastAttemptReason = "Forced unique encounter cleared, cooldown unchanged";
                RuntimeStateStorage.addEvent(zoneId, ruleId, "UNIQUE_ENCOUNTER_CLEARED", "Forced unique encounter cleared");
            } else if (cleanup) {
                state.nextAttemptAt = state.encounterClearedAt + ruleOpt.get().failedSpawnRetrySeconds * 1000L;
                state.lastAttemptReason = "Unique encounter cleaned up, retry scheduled";
                RuntimeStateStorage.addEvent(zoneId, ruleId, "UNIQUE_ENCOUNTER_CLEARED", "Unique encounter cleaned up");
            } else {
                state.nextAvailableAt = state.encounterClearedAt + (long) ruleOpt.get().respawnSeconds * 1000L;
                state.nextAttemptAt = state.nextAvailableAt;
                state.lastAttemptReason = "Unique encounter cleared, next available after cooldown";
                RuntimeStateStorage.addEvent(zoneId, ruleId, "UNIQUE_ENCOUNTER_CLEARED", "Unique encounter cleared, cooldown started");
            }
        }
        RuntimeStateStorage.markDirty(zoneId);
    }

    public static int getPrimaryAliveCount(String zoneId, String ruleId) {
        return primaryCounts.getOrDefault(zoneId + ":" + ruleId, 0);
    }

    public static int getCompanionAliveCount(String zoneId, String ruleId) {
        return companionCounts.getOrDefault(zoneId + ":" + ruleId, 0);
    }

    public static void incrementPrimary(String zoneId, String ruleId) {
        primaryCounts.merge(zoneId + ":" + ruleId, 1, Integer::sum);
    }

    public static void decrementPrimary(String zoneId, String ruleId) {
        primaryCounts.computeIfPresent(zoneId + ":" + ruleId, (k, v) -> Math.max(0, v - 1));
    }

    public static void incrementCompanion(String zoneId, String ruleId) {
        companionCounts.merge(zoneId + ":" + ruleId, 1, Integer::sum);
    }

    public static void decrementCompanion(String zoneId, String ruleId) {
        companionCounts.computeIfPresent(zoneId + ":" + ruleId, (k, v) -> Math.max(0, v - 1));
    }

    public static void resyncZone(MinecraftServer server, Zone zone) {
        String prefix = zone.id + ":";
        primaryCounts.keySet().removeIf(key -> key.startsWith(prefix));
        companionCounts.keySet().removeIf(key -> key.startsWith(prefix));

        ServerWorld world = server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new net.minecraft.util.Identifier(zone.dimension)));
        if (world == null) return;

        Box box = getZoneBox(zone);

        for (net.minecraft.entity.Entity entity : world.getOtherEntities(null, box)) {
            Optional<ManagedMobInfo> info = MobTagger.getInfo(entity);
            if (info.isPresent() && info.get().zoneId.equals(zone.id)) {
                if ("PRIMARY".equals(info.get().role)) incrementPrimary(zone.id, info.get().ruleId);
                else if ("COMPANION".equals(info.get().role)) incrementCompanion(zone.id, info.get().ruleId);
            }
        }
        
        // After resync, check if any unique encounters should be marked cleared
        for (MobRule rule : zone.mobs) {
            if (rule.spawnType == SpawnType.UNIQUE) {
                checkUniqueEncounterCleared(zone.id, rule.id, false, false);
            }
        }
    }

    private static Box getZoneBox(Zone zone) {
        return new Box(
                Math.min(zone.min.x, zone.max.x), Math.min(zone.min.y, zone.max.y), Math.min(zone.min.z, zone.max.z),
                Math.max(zone.min.x, zone.max.x) + 1, Math.max(zone.min.y, zone.max.y) + 1, Math.max(zone.min.z, zone.max.z) + 1
        );
    }

    public static void resyncActiveZones(MinecraftServer server) {
        primaryCounts.clear();

        for (Zone zone : ZoneRepository.getEnabledZones()) {
            ServerWorld world = server.getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new net.minecraft.util.Identifier(zone.dimension)));
            if (world == null) continue;

            Box box = getZoneBox(zone);

            for (net.minecraft.entity.Entity entity : world.getOtherEntities(null, box)) {
                Optional<ManagedMobInfo> info = MobTagger.getInfo(entity);
                if (info.isPresent() && info.get().zoneId.equals(zone.id) && info.get().role.equals("PRIMARY")) {
                    incrementPrimary(zone.id, info.get().ruleId);
                }
            }
        }
    }
}