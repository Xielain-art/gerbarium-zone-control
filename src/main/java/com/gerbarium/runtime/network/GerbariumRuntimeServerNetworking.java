package com.gerbarium.runtime.network;

import com.gerbarium.runtime.admin.ActionResultDto;
import com.gerbarium.runtime.admin.RuntimeAdminService;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.RefillMode;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.permission.PermissionUtil;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.state.ZoneRuntimePersistentState;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import com.gerbarium.runtime.state.ZoneStateFile;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tick.ZoneActivationManager;
import com.gerbarium.runtime.tracking.MobTracker;
import com.gerbarium.runtime.util.TimeUtil;
import com.gerbarium.runtime.util.RuntimeRuleValidationUtil;
import com.gerbarium.runtime.util.RuntimeWorldUtil;
import com.gerbarium.runtime.client.dto.RuntimeEventDto;
import com.gerbarium.runtime.client.dto.RuleSummaryDto;
import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GerbariumRuntimeServerNetworking {
    private static final Gson GSON = new Gson();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(GerbariumRuntimePackets.REQUEST_RUNTIME_SNAPSHOT, (server, player, handler, buf, responseSender) -> {
            if (!PermissionUtil.hasAdminPermission(player.getCommandSource())) return;

            server.execute(() -> {
                RuntimeSnapshotDto snapshot = createSnapshot(server);
                PacketByteBuf responseBuf = PacketByteBufs.create();
                responseBuf.writeString(GSON.toJson(snapshot));
                ServerPlayNetworking.send(player, GerbariumRuntimePackets.SYNC_RUNTIME_SNAPSHOT, responseBuf);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(GerbariumRuntimePackets.RUN_GLOBAL_ACTION, (server, player, handler, buf, responseSender) -> {
            if (!PermissionUtil.hasAdminPermission(player.getCommandSource())) return;

            String action = buf.readString();
            String[] parts = action.split(":", 3);
            String actionCode = parts[0];
            String param1 = parts.length > 1 ? parts[1] : (buf.isReadable() ? buf.readString() : null);
            String param2 = parts.length > 2 ? parts[2] : (buf.isReadable() ? buf.readString() : null);
            
            server.execute(() -> {
                ActionResultDto result;
                if ("RELOAD".equals(actionCode)) {
                    result = RuntimeAdminService.reload(player.getName().getString());
                } else if ("CLEANUP_ORPHANS".equals(actionCode)) {
                    result = RuntimeAdminService.cleanupOrphans(server, player.getName().getString());
                } else if ("STATE_SAVE".equals(actionCode)) {
                    com.gerbarium.runtime.storage.RuntimeStateStorage.saveAllDirty();
                    result = new ActionResultDto(true, "Runtime state saved to disk.");
                } else if ("DEBUG_ON".equals(actionCode)) {
                    result = RuntimeAdminService.setDebugEnabled(true, player.getName().getString());
                } else if ("DEBUG_OFF".equals(actionCode)) {
                    result = RuntimeAdminService.setDebugEnabled(false, player.getName().getString());
                } else if ("FORCE_ACTIVATE".equals(actionCode)) {
                    result = RuntimeAdminService.forceActivateZone(param1, player.getName().getString());
                } else if ("FORCE_DEACTIVATE".equals(actionCode)) {
                    result = RuntimeAdminService.forceDeactivateZone(param1, server, player.getName().getString());
                } else if ("FORCE_SPAWN".equals(actionCode)) {
                    result = RuntimeAdminService.forceSpawnZone(param1, server, player.getName().getString());
                } else if ("CLEAR_ZONE".equals(actionCode)) {
                    result = RuntimeAdminService.clearZoneMobs(param1, server, player.getName().getString());
                } else if ("CLEAR_ZONE_STATE".equals(actionCode)) {
                    result = RuntimeAdminService.clearZoneState(param1, player.getName().getString());
                } else if ("FORCE_RULE_SPAWN".equals(actionCode)) {
                    result = RuntimeAdminService.forceSpawnRule(param1, param2, server, player.getName().getString());
                } else if ("FORCE_RULE_PRIMARY".equals(actionCode)) {
                    result = RuntimeAdminService.forceSpawnPrimary(param1, param2, server, player.getName().getString());
                } else if ("FORCE_RULE_COMPANIONS".equals(actionCode)) {
                    result = RuntimeAdminService.forceSpawnCompanions(param1, param2, server, player, player.getName().getString());
                } else if ("RESET_RULE_COOLDOWN".equals(actionCode)) {
                    result = RuntimeAdminService.resetRuleCooldown(param1, param2, player.getName().getString());
                } else if ("KILL_MANAGED".equals(actionCode)) {
                    result = RuntimeAdminService.killManagedMobs(param1, param2, server, player.getName().getString());
                } else if ("CLEAR_RULE_STATE".equals(actionCode)) {
                    result = RuntimeAdminService.clearRuleState(param1, param2, player.getName().getString());
                } else {
                    result = new ActionResultDto(false, "Unknown action: " + actionCode);
                }

                PacketByteBuf responseBuf = PacketByteBufs.create();
                responseBuf.writeString(GSON.toJson(result));
                ServerPlayNetworking.send(player, GerbariumRuntimePackets.ACTION_RESULT, responseBuf);
            });
        });
    }

    private static RuntimeSnapshotDto createSnapshot(MinecraftServer server) {
        RuntimeSnapshotDto dto = new RuntimeSnapshotDto();
        dto.debug = RuntimeConfigStorage.getConfig().debug;
        dto.boundaryControlEnabled = RuntimeConfigStorage.getConfig().boundaryControlEnabled;
        dto.boundaryGlobalCheckIntervalTicks = RuntimeConfigStorage.getConfig().boundaryGlobalCheckIntervalTicks;
        dto.boundaryScanPadding = RuntimeConfigStorage.getConfig().boundaryScanPadding;
        dto.boundaryMaxEntitiesPerTick = RuntimeConfigStorage.getConfig().boundaryMaxEntitiesPerTick;
        Collection<Zone> all = ZoneRepository.getAll();
        dto.totalZones = all.size();
        dto.enabledZones = ZoneRepository.getEnabledZones().size();
        
        dto.zones = new ArrayList<>();
        List<RuntimeEventDto> allEvents = new ArrayList<>();
        int activeZones = 0;
        int managedPrimary = 0;
        int managedCompanions = 0;
        for (Zone zone : all) {
            ZoneSummaryDto z = new ZoneSummaryDto();
            z.id = zone.id;
            z.name = zone.name != null && !zone.name.isBlank() ? zone.name : zone.id;
            z.enabled = zone.enabled;
            z.dimension = zone.dimension;
            z.boundaryControlEnabled = RuntimeConfigStorage.getConfig().boundaryControlEnabled;
            z.boundaryScanPadding = RuntimeConfigStorage.getConfig().boundaryScanPadding;
            
            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            z.active = zState.active;
            z.pendingActivation = zState.pendingActivation;
            z.nearbyPlayers = zState.nearbyPlayers.size();
            if (z.active) {
                activeZones++;
            }
            
            // Calculate totals across all rules
            int totalPrimaryAlive = 0;
            int totalCompanionsAlive = 0;
            for (MobRule rule : zone.mobs) {
                int primaryAlive = MobTracker.getPrimaryAliveCount(zone.id, rule.id);
                int companionAlive = MobTracker.getCompanionAliveCount(zone.id, rule.id);
                totalPrimaryAlive += primaryAlive;
                totalCompanionsAlive += companionAlive;
                managedPrimary += primaryAlive;
                managedCompanions += companionAlive;
            }
            z.primaryAliveTotal = totalPrimaryAlive;
            z.companionsAliveTotal = totalCompanionsAlive;
            z.boundaryOutsideCount = zone.mobs.stream()
                    .mapToInt(rule -> {
                        RuleRuntimeState state = zf.rules.get(rule.id);
                        return state == null ? 0 : state.boundaryOutsideCount;
                    })
                    .sum();
            z.totalRules = zone.mobs.size();
            
            z.minX = zone.min.x;
            z.minY = zone.min.y;
            z.minZ = zone.min.z;
            z.maxX = zone.max.x;
            z.maxY = zone.max.y;
            z.maxZ = zone.max.z;
            z.priority = zone.priority;
            z.activationId = zState.activationId;

            ZoneStateFile zf = RuntimeStateStorage.getZoneState(zone.id);
            ZoneRuntimePersistentState pState = zf.zone;
            z.lastActivatedAt = pState.lastActivatedAt;
            z.lastDeactivatedAt = pState.lastDeactivatedAt;
            z.lastPlayerSeenAt = pState.lastPlayerSeenAt;
            z.dirty = zf.dirty;
            z.attentionStatus = computeZoneAttention(zone, zState, zf, totalPrimaryAlive, totalCompanionsAlive);
            z.currentStatus = computeZoneCurrentStatus(server, zone, zState, zf);
            z.warningText = computeZoneWarning(server, zone, zState, zf);
            z.hintText = computeZoneHint(server, zone, zState, zf);
            z.nextActionText = computeZoneNextAction(server, zone, zState, zf);
            z.statusText = z.currentStatus;

            for (var event : zf.recentEvents) {
                RuntimeEventDto ed = new RuntimeEventDto();
                ed.time = event.time;
                ed.zoneId = event.zoneId;
                ed.ruleId = event.ruleId;
                ed.type = event.type;
                ed.message = event.message;
                ed.entityType = event.entityType;
                ed.role = event.role;
                ed.forced = event.forced;
                ed.action = event.action;
                ed.x = event.x;
                ed.y = event.y;
                ed.z = event.z;
                allEvents.add(ed);
            }

            for (MobRule rule : zone.mobs) {
                RuleSummaryDto rs = new RuleSummaryDto();
                rs.id = rule.id;
                rs.zoneId = zone.id;
                rs.name = rule.name != null && !rule.name.isBlank() ? rule.name : rule.id;
                rs.entity = rule.entity;
                rs.spawnType = rule.spawnType == null ? null : rule.spawnType.name();
                rs.maxAlive = rule.maxAlive;
                rs.aliveCount = MobTracker.getNormalPrimaryAliveCount(zone.id, rule.id);
                rs.enabled = rule.enabled;
                
                rs.refillMode = rule.refillMode == null ? null : rule.refillMode.name();
                rs.boundaryMode = rule.boundaryMode;
                rs.boundaryMaxOutsideSeconds = rule.boundaryMaxOutsideSeconds;
                rs.boundaryCheckIntervalTicks = rule.boundaryCheckIntervalTicks;
                rs.boundaryTeleportBack = rule.boundaryTeleportBack;
                rs.spawnCount = rule.spawnCount;
                rs.respawnSeconds = rule.respawnSeconds;
                rs.chance = rule.chance;
                rs.timedMaxSpawnsPerActivation = rule.timedMaxSpawnsPerActivation;
                rs.cooldownStart = rule.cooldownStart == null ? null : rule.cooldownStart.name();
                rs.spawnWhenReady = rule.spawnWhenReady;
                rs.failedSpawnRetrySeconds = rule.failedSpawnRetrySeconds;
                rs.despawnWhenZoneInactive = rule.despawnWhenZoneInactive;
                rs.announceOnSpawn = rule.announceOnSpawn;

                RuleRuntimeState ruleState = zf.rules.get(rule.id);
                if (ruleState != null) {
                    rs.active = zState.active;
                    rs.timedSpawnedThisActivation = ruleState.timedSpawnedThisActivation;
                    rs.timedProgressMillis = ruleState.timedProgressMillis;
                    rs.nextTimedSpawnInMillis = ruleState.nextTimedSpawnInMillis;
                    rs.timedBudgetExhausted = ruleState.timedBudgetExhausted;
                    rs.lastTimedBudgetResetAt = ruleState.lastTimedBudgetResetAt;
                    rs.lastOnActivationAttemptActivationId = ruleState.lastOnActivationAttemptActivationId;
                    rs.lastActivationSpawnAt = ruleState.lastActivationSpawnAt;
                    rs.lastAttemptAt = ruleState.lastAttemptAt;
                    rs.lastAttemptResult = ruleState.lastAttemptResult;
                    rs.lastAttemptReason = ruleState.lastAttemptReason;
                    rs.lastSuccessAt = ruleState.lastSuccessAt;
                    rs.lastSuccessfulPrimaryCount = ruleState.lastSuccessfulPrimaryCount;
                    rs.lastSuccessfulCompanionCount = ruleState.lastSuccessfulCompanionCount;
                    rs.nextAvailableAt = ruleState.nextAvailableAt;
                    rs.nextAttemptAt = ruleState.nextAttemptAt;
                    rs.lastDeathAt = ruleState.lastDeathAt;
                    rs.encounterActive = ruleState.encounterActive;
                    rs.encounterStartedAt = ruleState.encounterStartedAt;
                    rs.encounterClearedAt = ruleState.encounterClearedAt;
                    rs.encounterPrimaryAlive = ruleState.encounterPrimaryAlive;
                    rs.encounterCompanionsAlive = ruleState.encounterCompanionsAlive;
                    rs.lastEncounterPrimarySpawned = ruleState.lastEncounterPrimarySpawned;
                    rs.lastEncounterCompanionsSpawned = ruleState.lastEncounterCompanionsSpawned;
                    rs.totalAttempts = ruleState.totalAttempts;
                    rs.totalSuccesses = ruleState.totalSuccesses;
                    rs.totalPrimarySpawned = ruleState.totalPrimarySpawned;
                    rs.totalCompanionsSpawned = ruleState.totalCompanionsSpawned;
                    rs.lastBoundaryActionAt = ruleState.lastBoundaryActionAt;
                    rs.lastBoundaryActionType = ruleState.lastBoundaryActionType;
                    rs.boundaryOutsideCount = ruleState.boundaryOutsideCount;
                    rs.boundaryLastScanAt = ruleState.boundaryLastScanAt;
                    rs.boundaryLastHint = ruleState.boundaryLastHint;
                }

                rs.knownAlive = rs.aliveCount + rs.encounterCompanionsAlive;
                rs.currentStatus = computeRuleCurrentStatus(server, zone, zState, zf, rule, rs);
                rs.statusText = rs.currentStatus;
                rs.nextActionText = computeRuleNextAction(server, zone, zState, zf, rule, rs);
                rs.warningText = computeRuleWarning(server, zone, zState, zf, rule, rs);
                rs.hintText = computeRuleHint(server, zone, zState, zf, rule, rs);
                rs.boundaryStatus = computeRuleBoundaryStatus(server, zone, zState, zf, rule, rs);
                rs.boundaryHint = computeRuleBoundaryHint(server, zone, zState, zf, rule, rs);
                
                z.rules.add(rs);
            }
            
            dto.zones.add(z);
        }

        dto.activeZones = activeZones;
        dto.managedPrimaryCount = managedPrimary;
        dto.managedCompanionCount = managedCompanions;
        allEvents.sort((a, b) -> Long.compare(b.time, a.time));
        dto.recentEvents = allEvents.stream().limit(100).toList();
        dto.recentEventsCount = allEvents.size();

        return dto;
    }

    private static String computeZoneCurrentStatus(MinecraftServer server, Zone zone, ZoneRuntimeState zState, ZoneStateFile zf) {
        if (!zone.enabled) return "DISABLED";
        if (zf.zone == null) return "STATE_MISSING";
        ServerWorld world = RuntimeWorldUtil.getWorld(server, zone.dimension).orElse(null);
        if (world == null) return "FAILED_WORLD_UNAVAILABLE";
        if (zState.active) return "ACTIVE";
        return "INACTIVE";
    }

    private static String computeZoneAttention(Zone zone, ZoneRuntimeState zState, ZoneStateFile zf, int primaryAlive, int companionsAlive) {
        if (!zone.enabled) return "disabled";
        if (zf.dirty) return "dirty";
        if (zState.active && (primaryAlive > 0 || companionsAlive > 0)) return "active";
        return "";
    }

    private static String computeZoneWarning(MinecraftServer server, Zone zone, ZoneRuntimeState zState, ZoneStateFile zf) {
        if (!zone.enabled) return "Zone disabled.";
        if (zf.zone == null) return "State file missing zone payload.";
        if (RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty()) return "World unavailable.";
        return "";
    }

    private static String computeZoneHint(MinecraftServer server, Zone zone, ZoneRuntimeState zState, ZoneStateFile zf) {
        if (!zone.enabled) return "Enable zone JSON to resume runtime.";
        if (RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty()) return "Fix dimension id or load target world.";
        if (!zState.active) return "Wait for a player in range or force activate.";
        return "";
    }

    private static String computeZoneNextAction(MinecraftServer server, Zone zone, ZoneRuntimeState zState, ZoneStateFile zf) {
        if (!zone.enabled) return "No runtime actions while disabled.";
        if (RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty()) return "Restore world availability.";
        if (!zState.active) return "Activate when players enter range.";
        return "Watch spawn rules and events.";
    }

    private static String computeRuleCurrentStatus(MinecraftServer server, Zone zone, ZoneRuntimeState zState, ZoneStateFile zf, MobRule rule, RuleSummaryDto rs) {
        if (!zone.enabled || !rule.enabled) return "DISABLED";
        if (RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty()) return "FAILED_WORLD_UNAVAILABLE";
        String configStatus = RuntimeRuleValidationUtil.getConfigStatus(rule);
        if (configStatus != null) return configStatus;
        String entityStatus = RuntimeRuleValidationUtil.getEntityStatus(rule);
        if (entityStatus != null) return entityStatus;
        if (!zState.active) return "INACTIVE";
        if (rule.spawnType == SpawnType.UNIQUE && rs.encounterActive) {
            if (rs.aliveCount > 0) return "ALIVE";
            if (rs.encounterCompanionsAlive > 0) return "WAITING_FOR_COMPANIONS_CLEAR";
            return "ENCOUNTER_ACTIVE";
        }
        if (rule.refillMode == RefillMode.TIMED) {
            if (rs.timedBudgetExhausted) return "TIMED_BUDGET_EXHAUSTED";
            if (rs.nextTimedSpawnInMillis > 0) return "TIMED_WAIT";
        }
        if (rs.nextAvailableAt > System.currentTimeMillis()) return "COOLDOWN";
        if (rule.spawnType == SpawnType.PACK && rs.lastOnActivationAttemptActivationId == zState.activationId) return "ALREADY_ATTEMPTED_THIS_ACTIVATION";
        return "READY";
    }

    private static String computeRuleBoundaryStatus(MinecraftServer server, Zone zone, ZoneRuntimeState zState, ZoneStateFile zf, MobRule rule, RuleSummaryDto rs) {
        String configStatus = RuntimeRuleValidationUtil.getConfigStatus(rule);
        if ("FAILED_INVALID_BOUNDARY_MODE".equals(configStatus)) {
            return configStatus;
        }
        if (!zone.enabled || !rule.enabled) return "DISABLED";
        if (RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty()) return "FAILED_WORLD_UNAVAILABLE";
        if (!zState.active) return "INACTIVE";
        if (rs.boundaryOutsideCount > 0) return "TRACKING_OUTSIDE";
        return "OK";
    }

    private static String computeRuleNextAction(MinecraftServer server, Zone zone, ZoneRuntimeState zState, ZoneStateFile zf, MobRule rule, RuleSummaryDto rs) {
        if (!zone.enabled || !rule.enabled) return "No action while disabled.";
        if (RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty()) return "World unavailable.";
        if (RuntimeRuleValidationUtil.getConfigStatus(rule) != null) return "Fix rule config before spawning.";
        if (RuntimeRuleValidationUtil.getEntityStatus(rule) != null) return "Fix entity id before spawning.";
        if (!zState.active) return "Wait for zone activation.";
        if (rule.refillMode == RefillMode.TIMED) {
            if (rs.timedBudgetExhausted) return "Budget exhausted until reactivation cooldown passes.";
            if (rs.nextTimedSpawnInMillis > 0) return "Next timed spawn in " + TimeUtil.formatDuration(rs.nextTimedSpawnInMillis);
        }
        if (rs.nextAvailableAt > System.currentTimeMillis()) return "Next available " + TimeUtil.formatRelative(rs.nextAvailableAt);
        return "Ready to spawn.";
    }

    private static String computeRuleWarning(MinecraftServer server, Zone zone, ZoneRuntimeState zState, ZoneStateFile zf, MobRule rule, RuleSummaryDto rs) {
        if (!rule.enabled) return "Rule disabled.";
        if (RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty()) return "Zone world unavailable.";
        if (rule.spawnType == null) return "Invalid spawn type.";
        if (rule.spawnType == SpawnType.UNIQUE && rule.maxAlive > 1) return "Unique rules usually use maxAlive = 1.";
        if (rule.refillMode == RefillMode.TIMED && rule.timedMaxSpawnsPerActivation != null && rule.timedMaxSpawnsPerActivation == -1) return "Unlimited timed budget can create farm risk.";
        if (rule.spawnType == SpawnType.PACK && rs.lastOnActivationAttemptActivationId == zState.activationId) return "Already attempted this activation.";
        String boundaryIntervalWarning = RuntimeRuleValidationUtil.getBoundaryIntervalWarning(rule);
        if (boundaryIntervalWarning != null) return boundaryIntervalWarning;
        return "";
    }

    private static String computeRuleHint(MinecraftServer server, Zone zone, ZoneRuntimeState zState, ZoneStateFile zf, MobRule rule, RuleSummaryDto rs) {
        if (!zState.active) return "Zone inactive.";
        if (RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty()) return "Load the target world.";
        if (RuntimeRuleValidationUtil.getEntityStatus(rule) != null) return "Fix invalid entity id.";
        if (rule.spawnType == SpawnType.UNIQUE && rs.encounterCompanionsAlive > 0 && rs.encounterPrimaryAlive == 0) return "Waiting for companions to clear before cooldown starts.";
        if (rule.refillMode == RefillMode.TIMED && rs.timedBudgetExhausted) return "Leave and re-enter after reactivation cooldown to refresh budget.";
        return "";
    }

    private static String computeRuleBoundaryHint(MinecraftServer server, Zone zone, ZoneRuntimeState zState, ZoneStateFile zf, MobRule rule, RuleSummaryDto rs) {
        String boundaryHint = RuntimeRuleValidationUtil.getBoundaryModeHint(rule);
        if (boundaryHint != null) {
            return boundaryHint;
        }
        String intervalWarning = RuntimeRuleValidationUtil.getBoundaryIntervalWarning(rule);
        if (intervalWarning != null) {
            return intervalWarning;
        }
        if (rs.boundaryLastHint != null && !rs.boundaryLastHint.isBlank()) {
            return rs.boundaryLastHint;
        }
        if (!zState.active) return "Zone inactive.";
        if (RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty()) return "Load the target world.";
        if (rs.boundaryOutsideCount > 0) return "Managed mob outside zone bounds.";
        return "";
    }
}
