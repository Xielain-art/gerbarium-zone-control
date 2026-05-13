package com.gerbarium.runtime.admin;

import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.config.RuntimeConfig;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.RefillMode;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.state.RuntimeEvent;
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
import net.minecraft.server.MinecraftServer;

import java.util.*;
import java.util.stream.Collectors;

public class RuntimeQueryService {
    
    public static String getRuntimeStatusString() {
        Collection<Zone> allZones = ZoneRepository.getAll();
        int loadedZones = allZones.size();
        int enabledZones = (int) allZones.stream().filter(z -> z.enabled).count();
        int activeZones = 0;
        int totalRules = 0;
        int totalPrimaryAlive = 0;
        int totalCompanionsAlive = 0;
        int dirtyStates = 0;
        
        for (Zone zone : allZones) {
            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            if (zState.active) activeZones++;
            
            totalRules += zone.mobs.size();
            
            for (MobRule rule : zone.mobs) {
                totalPrimaryAlive += MobTracker.getPrimaryAliveCount(zone.id, rule.id);
                totalCompanionsAlive += MobTracker.getCompanionAliveCount(zone.id, rule.id);
            }
            
            ZoneStateFile zf = RuntimeStateStorage.getZoneState(zone.id);
            if ((RuntimeStateStorage.isDirty(zone.id))) dirtyStates++;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== Gerbarium Zones Runtime Status ===\n");
        sb.append("Loaded zones: ").append(loadedZones).append("\n");
        sb.append("Enabled zones: ").append(enabledZones).append("\n");
        sb.append("Active zones: ").append(activeZones).append("\n");
        sb.append("Total rules: ").append(totalRules).append("\n");
        sb.append("Managed primary alive: ").append(totalPrimaryAlive).append("\n");
        sb.append("Managed companions alive: ").append(totalCompanionsAlive).append("\n");
        sb.append("Dirty states: ").append(dirtyStates).append("\n");
        sb.append("Debug: ").append(RuntimeConfigStorage.getConfig().debug ? "ON" : "OFF").append("\n");
        sb.append("Boundary control: ").append(RuntimeConfigStorage.getConfig().boundaryControlEnabled ? "ON" : "OFF").append("\n");
        sb.append("Config path: ").append(com.gerbarium.runtime.storage.RuntimeStateStorage.getZonesDir()).append("\n");
        sb.append("States path: ").append(com.gerbarium.runtime.storage.RuntimeStateStorage.getStorageRoot().resolve("states")).append("\n");
        sb.append("Archive path: ").append(com.gerbarium.runtime.storage.RuntimeStateStorage.getStatesArchiveDir()).append("\n");
        
        return sb.toString();
    }
    
    public static String getStateFilesString() {
        Collection<Zone> zones = ZoneRepository.getAll();
        if (zones.isEmpty()) return "No zones loaded.";
        
        StringBuilder sb = new StringBuilder();
        sb.append("=== Zone State Files ===\n");
        
        for (Zone zone : zones) {
            ZoneStateFile zf = RuntimeStateStorage.getZoneState(zone.id);
            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            
            sb.append("- ").append(zone.id);
            sb.append(" | name: ").append(zone.name != null && !zone.name.isBlank() ? zone.name : zone.id);
            sb.append(" | file: yes");
            sb.append(" | dirty: ").append((RuntimeStateStorage.isDirty(zone.id)) ? "yes" : "no");
            sb.append(" | rules: ").append(zf.rules.size());
            sb.append(" | events: ").append(zf.recentEvents.size());
            sb.append(" | last activated: ").append(TimeUtil.formatRelative(zf.zone.lastActivatedAt));
            sb.append(" | last deactivated: ").append(TimeUtil.formatRelative(zf.zone.lastDeactivatedAt));
            sb.append(" | path: ").append(RuntimeStateStorage.getZoneStatePath(zone.id));
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    public static String getZoneStatusString(String zoneId) {
        return getZoneStatusString(zoneId, null);
    }

    public static String getZoneStatusString(String zoneId, MinecraftServer server) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return "Zone not found: " + zoneId;

        Zone zone = zoneOpt.get();
        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        ZoneStateFile zf = RuntimeStateStorage.getZoneState(zoneId);
        ZoneRuntimePersistentState pState = zf.zone;

        StringBuilder sb = new StringBuilder();
        sb.append("Zone: ").append(zone.id).append("\n");
        sb.append("Name: ").append(zone.name != null && !zone.name.isBlank() ? zone.name : zone.id).append("\n");
        sb.append("Enabled: ").append(zone.enabled).append("\n");
        sb.append("Dimension: ").append(zone.dimension).append("\n");
        boolean worldMissing = server != null && RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty();
        if (worldMissing) {
            sb.append("World Status: FAILED_WORLD_UNAVAILABLE\n");
        }
        sb.append("Active: ").append(zState.active).append("\n");
        sb.append("Nearby Players: ").append(zState.nearbyPlayers.size()).append("\n");
        RuntimeConfig config = RuntimeConfigStorage.getConfig();
        int boundaryOutsideCount = zone.mobs.stream()
                .mapToInt(rule -> {
                    RuleRuntimeState state = zf.rules.get(rule.id);
                    return state == null ? 0 : state.boundaryOutsideCount;
                })
                .sum();
        sb.append("Boundary Control Enabled: ").append(config.boundaryControlEnabled).append("\n");
        sb.append("Boundary Scan Padding: ").append(config.boundaryScanPadding).append("\n");
        sb.append("Boundary Outside Mobs: ").append(boundaryOutsideCount).append("\n");
        sb.append("Current Status: ").append(worldMissing ? "FAILED_WORLD_UNAVAILABLE" : (zone.enabled ? (zState.active ? "ACTIVE" : "INACTIVE") : "DISABLED")).append("\n");

        if (pState != null) {
            sb.append("Last Player Seen: ").append(TimeUtil.formatRelative(pState.lastPlayerSeenAt)).append("\n");
            sb.append("Last Activated: ").append(TimeUtil.formatRelative(pState.lastActivatedAt)).append(" (").append(pState.lastActivationReason).append(")\n");
            sb.append("Last Deactivated: ").append(TimeUtil.formatRelative(pState.lastDeactivatedAt)).append(" (").append(pState.lastDeactivationReason).append(")\n");
            sb.append("Total Activations: ").append(pState.totalActivations).append("\n");
            sb.append("Total Successful Spawns: ").append(pState.totalSuccessfulSpawns).append("\n");
        }

        sb.append("\nRules Summary:\n");
        for (MobRule rule : zone.mobs) {
            int pAlive = MobTracker.getNormalPrimaryAliveCount(zoneId, rule.id);
            int cAlive = MobTracker.getNormalCompanionAliveCount(zoneId, rule.id);
            RuleRuntimeState rs = zf.rules.get(rule.id);
            
            sb.append("- ").append(rule.id).append(" (").append(rule.name).append("): ");
            if (rule.spawnType == SpawnType.UNIQUE && rs != null && rs.encounterActive) {
                if (pAlive > 0) sb.append("ALIVE. ");
                else sb.append("WAITING_FOR_COMPANIONS. ");
                sb.append("Encounter: ").append(pAlive).append("P/").append(cAlive).append("C. ");
            } else {
                sb.append(pAlive).append("/").append(rule.maxAlive).append(" alive. ");
            }
            
            if (rs != null) {
                sb.append("Result: ").append(rs.lastAttemptResult).append(". ");
                
                if (rule.refillMode == RefillMode.TIMED) {
                    int budget = rule.timedMaxSpawnsPerActivation != null ? rule.timedMaxSpawnsPerActivation : rule.maxAlive;
                    if (budget == -1) {
                        sb.append("Budget: UNLIMITED. ");
                    } else {
                        sb.append("Budget: ").append(rs.timedSpawnedThisActivation).append("/").append(budget).append(" used. ");
                    }
                    sb.append("Next Timed Spawn: ").append(TimeUtil.formatDuration(Math.max(0, (rule.respawnSeconds * 1000L) - rs.timedProgressMillis))).append(". ");
                } else {
                    if (rs.nextAvailableAt > System.currentTimeMillis()) {
                        sb.append("Next Available: ").append(TimeUtil.formatRelative(rs.nextAvailableAt)).append(". ");
                    }
                }
                
                if (rs.lastAttemptAt > 0) {
                    sb.append("Last Attempt: ").append(TimeUtil.formatRelative(rs.lastAttemptAt)).append(". ");
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public static String getZonesListString() {
        Collection<Zone> zones = ZoneRepository.getAll();
        if (zones.isEmpty()) return "No zones loaded.";

        StringBuilder sb = new StringBuilder();
        sb.append("Loaded zones: ").append(zones.size()).append("\n");

        for (Zone zone : zones) {
            ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zone.id);
            ZoneStateFile zf = RuntimeStateStorage.getZoneState(zone.id);

            int totalPrimary = 0;
            int totalCompanions = 0;
            for (MobRule rule : zone.mobs) {
                totalPrimary += MobTracker.getPrimaryAliveCount(zone.id, rule.id);
                totalCompanions += MobTracker.getCompanionAliveCount(zone.id, rule.id);
            }

            sb.append("- ").append(zone.id);
            sb.append(" | ").append(zone.enabled ? "enabled" : "disabled");
            sb.append(" | ").append(zone.dimension);
            sb.append(" | ").append(zState.active ? "active" : "inactive");
            sb.append(" | rules ").append(zone.mobs.size());
            sb.append(" | primary ").append(totalPrimary);
            sb.append(" | companions ").append(totalCompanions);
            sb.append(" | last activated: ").append(TimeUtil.formatRelative(zf.zone.lastActivatedAt));
            sb.append("\n");
        }

        return sb.toString();
    }

    public static String getZoneScheduleString(String zoneId) {
        return getZoneScheduleString(zoneId, null);
    }

    public static String getZoneScheduleString(String zoneId, MinecraftServer server) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return "Zone not found: " + zoneId;

        Zone zone = zoneOpt.get();
        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        ZoneStateFile zf = RuntimeStateStorage.getZoneState(zoneId);
        long now = System.currentTimeMillis();
        boolean worldMissing = server != null && RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("Schedule for zone: ").append(zoneId).append("\n");
        sb.append("Zone active: ").append(zState.active).append("\n\n");
        if (worldMissing) {
            sb.append("World status: FAILED_WORLD_UNAVAILABLE\n\n");
        }

        for (MobRule rule : zone.mobs) {
            RuleRuntimeState rs = zf.rules.get(rule.id);
            if (rs == null) rs = new RuleRuntimeState();

            int pAlive = MobTracker.getNormalPrimaryAliveCount(zoneId, rule.id);
            int cAlive = MobTracker.getNormalCompanionAliveCount(zoneId, rule.id);

            sb.append("Rule: ").append(rule.id).append(" (").append(rule.name).append(")\n");
            sb.append("  Type: ").append(rule.spawnType).append(" / ").append(rule.refillMode).append("\n");
            sb.append("  Alive: ").append(pAlive).append("/").append(rule.maxAlive).append(" primary, ").append(cAlive).append(" companions\n");

            if (RuntimeRuleValidationUtil.getConfigStatus(rule) != null) {
                sb.append("  Status: FAILED_INVALID_RULE_CONFIG\n");
            } else if (RuntimeRuleValidationUtil.getEntityStatus(rule) != null) {
                sb.append("  Status: FAILED_INVALID_ENTITY\n");
            } else if (rule.spawnType == SpawnType.PACK) {
                if (rule.refillMode == RefillMode.TIMED) {
                    int budget = rule.timedMaxSpawnsPerActivation != null ? rule.timedMaxSpawnsPerActivation : rule.maxAlive;
                    sb.append("  TIMED interval: ").append(rule.respawnSeconds).append("s\n");
                    sb.append("  Progress: ").append(TimeUtil.formatDuration(rs.timedProgressMillis)).append(" / ").append(TimeUtil.formatDuration(rule.respawnSeconds * 1000L)).append("\n");
                    sb.append("  Budget: ").append(rs.timedSpawnedThisActivation).append("/");
                    if (budget == -1) {
                        sb.append("UNLIMITED (farm risk!)\n");
                    } else {
                        sb.append(budget).append("\n");
                    }

                    if (pAlive >= rule.maxAlive) {
                        sb.append("  Status: blocked_max_alive\n");
                    } else if (budget != -1 && rs.timedSpawnedThisActivation >= budget) {
                        sb.append("  Status: timed_budget_exhausted\n");
                    } else if (!zState.active) {
                        sb.append("  Status: inactive\n");
                    } else if (!zState.firstSpawnDelayPassed) {
                        sb.append("  Status: pending_first_spawn_delay\n");
                    } else {
                        long timeLeft = (rule.respawnSeconds * 1000L) - rs.timedProgressMillis;
                        if (timeLeft > 0) {
                            sb.append("  Status: timed_wait (next in ").append(TimeUtil.formatDuration(timeLeft)).append(")\n");
                        } else {
                            sb.append("  Status: ready\n");
                        }
                    }
                } else {
                    sb.append("  Cooldown: ").append(rule.respawnSeconds).append("s\n");
                    sb.append("  Last activation spawn: ").append(TimeUtil.formatRelative(rs.lastActivationSpawnAt)).append("\n");

                    if (pAlive >= rule.maxAlive) {
                        sb.append("  Status: blocked_max_alive\n");
                    } else if (!zState.active) {
                        sb.append("  Status: inactive\n");
                    } else if (!zState.firstSpawnDelayPassed) {
                        sb.append("  Status: pending_first_spawn_delay\n");
                    } else {
                        long cooldown = Math.max(zone.activation.reactivationCooldownSeconds, rule.respawnSeconds) * 1000L;
                        if (now - rs.lastActivationSpawnAt < cooldown) {
                            sb.append("  Status: cooldown (next ").append(TimeUtil.formatRelative(rs.lastActivationSpawnAt + cooldown)).append(")\n");
                        } else {
                            sb.append("  Status: ready\n");
                        }
                    }
                }
            } else if (rule.spawnType == SpawnType.UNIQUE) {
                sb.append("  Encounter active: ").append(rs.encounterActive).append("\n");
                sb.append("  Primary alive: ").append(pAlive).append("\n");
                sb.append("  Companions alive: ").append(cAlive).append("\n");

                if (rs.encounterActive) {
                    if (pAlive > 0) {
                        sb.append("  Status: alive\n");
                        sb.append("  Started: ").append(TimeUtil.formatRelative(rs.encounterStartedAt)).append("\n");
                    } else {
                        sb.append("  Status: waiting_for_companions_clear\n");
                        sb.append("  Cooldown: not started yet\n");
                        sb.append("  Reason: Primary is dead, but companions are still alive.\n");
                    }
                } else {
                    if (rs.nextAvailableAt > now) {
                        sb.append("  Status: cooldown\n");
                        sb.append("  Next available: ").append(TimeUtil.formatRelative(rs.nextAvailableAt)).append("\n");
                    } else if (rs.nextAttemptAt > now) {
                        sb.append("  Status: retry_wait\n");
                        sb.append("  Next attempt: ").append(TimeUtil.formatRelative(rs.nextAttemptAt)).append("\n");
                    } else {
                        sb.append("  Status: ready\n");
                    }
                    if (rs.encounterClearedAt > 0) {
                        sb.append("  Last cleared: ").append(TimeUtil.formatRelative(rs.encounterClearedAt)).append("\n");
                    }
                }
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    public static String getRuleStatusString(String zoneId, String ruleId) {
        return getRuleStatusString(zoneId, ruleId, null);
    }

    public static String getRuleStatusString(String zoneId, String ruleId, MinecraftServer server) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return "Zone not found: " + zoneId;

        Zone zone = zoneOpt.get();
        Optional<MobRule> ruleOpt = zone.mobs.stream().filter(m -> m.id.equals(ruleId)).findFirst();
        if (ruleOpt.isEmpty()) return "Rule not found: " + ruleId;

        MobRule rule = ruleOpt.get();
        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        ZoneStateFile zf = RuntimeStateStorage.getZoneState(zoneId);
        RuleRuntimeState rs = zf.rules.get(ruleId);
        if (rs == null) rs = new RuleRuntimeState();
        boolean worldMissing = server != null && RuntimeWorldUtil.getWorld(server, zone.dimension).isEmpty();

        int pAlive = MobTracker.getNormalPrimaryAliveCount(zoneId, ruleId);
        int cAlive = MobTracker.getNormalCompanionAliveCount(zoneId, ruleId);

        StringBuilder sb = new StringBuilder();
        sb.append("Rule: ").append(ruleId).append("\n");
        sb.append("Name: ").append(rule.name).append("\n");
        sb.append("Entity: ").append(rule.entity).append("\n");
        sb.append("Enabled: ").append(rule.enabled).append("\n");
        sb.append("Spawn Type: ").append(rule.spawnType).append("\n");
        sb.append("Refill Mode: ").append(rule.refillMode).append("\n");
        sb.append("Max Alive: ").append(rule.maxAlive).append("\n");
        sb.append("Spawn Count: ").append(rule.spawnCount).append("\n");
        sb.append("Respawn Seconds: ").append(rule.respawnSeconds).append("\n");
        sb.append("Chance: ").append(rule.chance).append("\n");
        sb.append("Companions: ").append(rule.companions.size()).append(" configured\n");
        sb.append("Boundary Mode: ").append(rule.boundaryMode).append("\n");
        sb.append("Boundary Max Outside Seconds: ").append(rule.boundaryMaxOutsideSeconds).append("\n");
        sb.append("Boundary Check Interval Ticks: ").append(rule.boundaryCheckIntervalTicks).append("\n");
        sb.append("Boundary Teleport Back: ").append(rule.boundaryTeleportBack).append("\n");
        if (worldMissing) {
            sb.append("World Status: FAILED_WORLD_UNAVAILABLE\n");
        }
        sb.append("\n");

        sb.append("Current State:\n");
        sb.append("  Primary alive: ").append(pAlive).append("\n");
        sb.append("  Companions alive: ").append(cAlive).append("\n");
        sb.append("  Last attempt: ").append(TimeUtil.formatRelative(rs.lastAttemptAt)).append("\n");
        sb.append("  Last attempt result: ").append(rs.lastAttemptResult).append("\n");
        sb.append("  Last attempt reason: ").append(rs.lastAttemptReason).append("\n");
        sb.append("  Last success: ").append(TimeUtil.formatRelative(rs.lastSuccessAt)).append("\n");
        sb.append("  Total attempts: ").append(rs.totalAttempts).append("\n");
        sb.append("  Total successes: ").append(rs.totalSuccesses).append("\n");
        sb.append("  Total primary spawned: ").append(rs.totalPrimarySpawned).append("\n");
        sb.append("  Total companions spawned: ").append(rs.totalCompanionsSpawned).append("\n");
        String currentStatus;
        if (worldMissing) {
            currentStatus = "FAILED_WORLD_UNAVAILABLE";
        } else if (RuntimeRuleValidationUtil.getConfigStatus(rule) != null) {
            currentStatus = "FAILED_INVALID_RULE_CONFIG";
        } else if (RuntimeRuleValidationUtil.getEntityStatus(rule) != null) {
            currentStatus = "FAILED_INVALID_ENTITY";
        } else {
            currentStatus = rule.enabled ? (zone.enabled ? (zState.active ? "READY_OR_COOLDOWN" : "INACTIVE") : "DISABLED") : "DISABLED";
        }
        sb.append("  Boundary mode: ").append(rule.boundaryMode).append("\n");
        sb.append("  Boundary status: ").append(currentStatus).append("\n");
        sb.append("  Boundary outside count: ").append(rs.boundaryOutsideCount).append("\n");
        sb.append("  Boundary last action: ").append(TimeUtil.formatRelative(rs.lastBoundaryActionAt)).append(" (").append(valueOrDash(rs.lastBoundaryActionType)).append(")\n");
        sb.append("  Current status: ").append(currentStatus).append("\n");
        sb.append("\n");

        if (rule.refillMode == RefillMode.TIMED) {
            int budget = rule.timedMaxSpawnsPerActivation != null ? rule.timedMaxSpawnsPerActivation : rule.maxAlive;
            sb.append("TIMED Details:\n");
            sb.append("  Progress: ").append(TimeUtil.formatDuration(rs.timedProgressMillis)).append(" / ").append(TimeUtil.formatDuration(rule.respawnSeconds * 1000L)).append("\n");
            sb.append("  Spawned this activation: ").append(rs.timedSpawnedThisActivation).append("\n");
            sb.append("  Budget per activation: ");
            if (budget == -1) {
                sb.append("UNLIMITED\n");
                sb.append("  WARNING: Farm risk - unlimited TIMED spawning while active!\n");
            } else {
                sb.append(budget).append("\n");
            }
        }

        if (rule.spawnType == SpawnType.UNIQUE) {
            sb.append("\nUNIQUE Details:\n");
            sb.append("  Encounter active: ").append(rs.encounterActive).append("\n");
            sb.append("  Encounter started: ").append(TimeUtil.formatRelative(rs.encounterStartedAt)).append("\n");
            sb.append("  Encounter cleared: ").append(TimeUtil.formatRelative(rs.encounterClearedAt)).append("\n");
            sb.append("  Encounter primary alive: ").append(rs.encounterPrimaryAlive).append("\n");
            sb.append("  Encounter companions alive: ").append(rs.encounterCompanionsAlive).append("\n");
            sb.append("  Next available: ").append(TimeUtil.formatRelative(rs.nextAvailableAt)).append("\n");
            sb.append("  Next attempt: ").append(TimeUtil.formatRelative(rs.nextAttemptAt)).append("\n");
        }

        sb.append("\nBoundary Control:\n");
        sb.append("  Mode: ").append(rule.boundaryMode).append("\n");
        sb.append("  Max outside seconds: ").append(rule.boundaryMaxOutsideSeconds).append("\n");
        sb.append("  Check interval ticks: ").append(rule.boundaryCheckIntervalTicks).append("\n");
        sb.append("  Teleport back: ").append(rule.boundaryTeleportBack).append("\n");
        String boundaryStatus = RuntimeRuleValidationUtil.getConfigStatus(rule);
        if (boundaryStatus != null && boundaryStatus.equals("FAILED_INVALID_BOUNDARY_MODE")) {
            sb.append("  Status: ").append(boundaryStatus).append("\n");
            sb.append("  Hint: ").append(valueOrDash(RuntimeRuleValidationUtil.getBoundaryModeHint(rule))).append("\n");
        } else {
            sb.append("  Status: ").append(rs.boundaryOutsideCount > 0 ? "TRACKING_OUTSIDE" : "OK").append("\n");
            if (rs.lastBoundaryActionAt > 0) {
                sb.append("  Last boundary action: ").append(TimeUtil.formatRelative(rs.lastBoundaryActionAt)).append(" (").append(valueOrDash(rs.lastBoundaryActionType)).append(")\n");
            }
            if (rs.boundaryLastHint != null && !rs.boundaryLastHint.isBlank()) {
                sb.append("  Hint: ").append(rs.boundaryLastHint).append("\n");
            }
        }

        return sb.toString();
    }

    public static String getEventsString(int limit) {
        List<RuntimeEvent> allEvents = new ArrayList<>();
        for (Zone zone : ZoneRepository.getAll()) {
            ZoneStateFile zf = RuntimeStateStorage.getZoneState(zone.id);
            allEvents.addAll(zf.recentEvents);
        }

        allEvents.sort((a, b) -> Long.compare(b.time, a.time));
        List<RuntimeEvent> limited = allEvents.stream().limit(limit).collect(Collectors.toList());

        if (limited.isEmpty()) return "No events recorded.";

        StringBuilder sb = new StringBuilder();
        sb.append("Recent events (showing ").append(limited.size()).append(" of ").append(allEvents.size()).append("):\n");

        long now = System.currentTimeMillis();
        for (RuntimeEvent event : limited) {
            sb.append("[").append(TimeUtil.formatRelative(event.time, now)).append("] ");
            sb.append(event.zoneId);
            if (event.ruleId != null && !event.ruleId.isEmpty()) {
                sb.append(" / ").append(event.ruleId);
            }
            sb.append(" / ").append(event.type).append(": ").append(event.message);
            if (event.entityType != null && !event.entityType.isBlank()) {
                sb.append(" | entity=").append(event.entityType);
            }
            if (event.role != null && !event.role.isBlank()) {
                sb.append(" | role=").append(event.role);
            }
            if (event.forced) {
                sb.append(" | forced=true");
            }
            if (event.action != null && !event.action.isBlank()) {
                sb.append(" | action=").append(event.action);
            }
            if (event.action != null && !event.action.isBlank()) {
                sb.append(" | pos=").append(event.x).append(",").append(event.y).append(",").append(event.z);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public static String getZoneEventsString(String zoneId, int limit) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return "Zone not found: " + zoneId;

        ZoneStateFile zf = RuntimeStateStorage.getZoneState(zoneId);
        List<RuntimeEvent> events = zf.recentEvents.stream().limit(limit).collect(Collectors.toList());

        if (events.isEmpty()) return "No events for zone: " + zoneId;

        StringBuilder sb = new StringBuilder();
        sb.append("Events for zone ").append(zoneId).append(" (showing ").append(events.size()).append("):\n");

        long now = System.currentTimeMillis();
        for (RuntimeEvent event : events) {
            sb.append("[").append(TimeUtil.formatRelative(event.time, now)).append("] ");
            if (event.ruleId != null && !event.ruleId.isEmpty()) {
                sb.append(event.ruleId).append(" / ");
            }
            sb.append(event.type).append(": ").append(event.message);
            if (event.entityType != null && !event.entityType.isBlank()) {
                sb.append(" | entity=").append(event.entityType);
            }
            if (event.role != null && !event.role.isBlank()) {
                sb.append(" | role=").append(event.role);
            }
            if (event.action != null && !event.action.isBlank()) {
                sb.append(" | action=").append(event.action);
                sb.append(" | pos=").append(event.x).append(",").append(event.y).append(",").append(event.z);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public static String getRuleEventsString(String zoneId, String ruleId, int limit) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return "Zone not found: " + zoneId;

        ZoneStateFile zf = RuntimeStateStorage.getZoneState(zoneId);
        List<RuntimeEvent> events = zf.recentEvents.stream()
                .filter(e -> ruleId.equals(e.ruleId))
                .limit(limit)
                .collect(Collectors.toList());

        if (events.isEmpty()) return "No events for rule: " + zoneId + ":" + ruleId;

        StringBuilder sb = new StringBuilder();
        sb.append("Events for rule ").append(zoneId).append(":").append(ruleId).append(" (showing ").append(events.size()).append("):\n");

        long now = System.currentTimeMillis();
        for (RuntimeEvent event : events) {
            sb.append("[").append(TimeUtil.formatRelative(event.time, now)).append("] ");
            sb.append(event.type).append(": ").append(event.message);
            if (event.action != null && !event.action.isBlank()) {
                sb.append(" | action=").append(event.action);
                sb.append(" | pos=").append(event.x).append(",").append(event.y).append(",").append(event.z);
            }
            sb.append("\n");
        }

        return sb.toString();
    }


    private static String valueOrDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }
}
