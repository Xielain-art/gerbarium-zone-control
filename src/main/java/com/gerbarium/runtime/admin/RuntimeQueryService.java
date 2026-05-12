package com.gerbarium.runtime.admin;

import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.SpawnType;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.state.RuleRuntimeState;
import com.gerbarium.runtime.state.ZoneRuntimePersistentState;
import com.gerbarium.runtime.state.ZoneRuntimeState;
import com.gerbarium.runtime.state.ZoneStateFile;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tick.ZoneActivationManager;
import com.gerbarium.runtime.tracking.MobTracker;

import java.util.Optional;

public class RuntimeQueryService {
    public static String getZoneStatusString(String zoneId) {
        Optional<Zone> zoneOpt = ZoneRepository.getById(zoneId);
        if (zoneOpt.isEmpty()) return "Zone not found: " + zoneId;

        Zone zone = zoneOpt.get();
        ZoneRuntimeState zState = ZoneActivationManager.getZoneState(zoneId);
        ZoneStateFile zf = RuntimeStateStorage.getZoneState(zoneId);
        ZoneRuntimePersistentState pState = zf.zone;

        StringBuilder sb = new StringBuilder();
        sb.append("Zone: ").append(zone.id).append("\n");
        sb.append("Enabled: ").append(zone.enabled).append("\n");
        sb.append("Dimension: ").append(zone.dimension).append("\n");
        sb.append("Active: ").append(zState.active).append("\n");
        sb.append("Nearby Players: ").append(zState.nearbyPlayers.size()).append("\n");

        if (pState != null) {
            sb.append("Total Activations: ").append(pState.totalActivations).append("\n");
            sb.append("Total Successful Spawns: ").append(pState.totalSuccessfulSpawns).append("\n");
        }

        sb.append("\nRules Summary:\n");
        for (MobRule rule : zone.mobs) {
            int pAlive = MobTracker.getPrimaryAliveCount(zoneId, rule.id);
            int cAlive = MobTracker.getCompanionAliveCount(zoneId, rule.id);
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
                sb.append("Last Result: ").append(rs.lastAttemptResult);
                
                if (rule.refillMode == com.gerbarium.runtime.model.RefillMode.TIMED) {
                    int budget = rule.timedMaxSpawnsPerActivation != null ? rule.timedMaxSpawnsPerActivation : rule.maxAlive;
                    if (budget == -1) {
                        sb.append(". TIMED budget: UNLIMITED (Farm risk!)");
                    } else {
                        sb.append(". TIMED budget: ").append(rs.timedSpawnedThisActivation).append("/").append(budget).append(" used");
                    }
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}