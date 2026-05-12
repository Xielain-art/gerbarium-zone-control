package com.gerbarium.runtime.network;

import com.gerbarium.runtime.admin.ActionResultDto;
import com.gerbarium.runtime.admin.RuntimeAdminService;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.permission.PermissionUtil;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tick.ZoneActivationManager;
import com.gerbarium.runtime.tracking.MobTracker;
import com.gerbarium.runtime.client.dto.RuntimeEventDto;
import com.gerbarium.runtime.client.dto.RuleSummaryDto;
import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GerbariumRuntimeServerNetworking {
    private static final Gson GSON = new Gson();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(GerbariumRuntimePackets.REQUEST_RUNTIME_SNAPSHOT, (server, player, handler, buf, responseSender) -> {
            if (!PermissionUtil.hasAdminPermission(player.getCommandSource())) return;

            server.execute(() -> {
                RuntimeSnapshotDto snapshot = createSnapshot();
                PacketByteBuf responseBuf = PacketByteBufs.create();
                responseBuf.writeString(GSON.toJson(snapshot));
                ServerPlayNetworking.send(player, GerbariumRuntimePackets.SYNC_RUNTIME_SNAPSHOT, responseBuf);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(GerbariumRuntimePackets.RUN_GLOBAL_ACTION, (server, player, handler, buf, responseSender) -> {
            if (!PermissionUtil.hasAdminPermission(player.getCommandSource())) return;

            String action = buf.readString();
            server.execute(() -> {
                ActionResultDto result;
                if ("RELOAD".equals(action)) {
                    result = RuntimeAdminService.reload(player.getName().getString());
                } else if ("CLEANUP_ORPHANS".equals(action)) {
                    result = RuntimeAdminService.cleanupOrphans(server, player.getName().getString());
                } else if ("FORCE_SPAWN".equals(action)) {
                    String zoneId = buf.readString();
                    result = RuntimeAdminService.forceSpawnZone(zoneId, server, player.getName().getString());
                } else if ("RESET_RULE_COOLDOWN".equals(action)) {
                    String zoneId = buf.readString();
                    String ruleId = buf.readString();
                    result = RuntimeAdminService.resetRuleCooldown(zoneId, ruleId, player.getName().getString());
                } else {
                    result = new ActionResultDto(false, "Unknown action: " + action);
                }

                PacketByteBuf responseBuf = PacketByteBufs.create();
                responseBuf.writeString(GSON.toJson(result));
                ServerPlayNetworking.send(player, GerbariumRuntimePackets.ACTION_RESULT, responseBuf);
            });
        });
    }

    private static RuntimeSnapshotDto createSnapshot() {
        RuntimeSnapshotDto dto = new RuntimeSnapshotDto();
        Collection<Zone> all = ZoneRepository.getAll();
        dto.totalZones = all.size();
        dto.enabledZones = ZoneRepository.getEnabledZones().size();
        
        dto.zones = new ArrayList<>();
        List<RuntimeEventDto> allEvents = new ArrayList<>();
        for (Zone zone : all) {
            ZoneSummaryDto z = new ZoneSummaryDto();
            z.id = zone.id;
            z.enabled = zone.enabled;
            z.dimension = zone.dimension;
            
            var zState = ZoneActivationManager.getZoneState(zone.id);
            z.active = zState.active;
            z.nearbyPlayers = zState.nearbyPlayers.size();
            z.primaryAliveTotal = MobTracker.getPrimaryAliveCount(zone.id, ""); // Placeholder for total primary alive
            
            z.minX = zone.min.x;
            z.minY = zone.min.y;
            z.minZ = zone.min.z;
            z.maxX = zone.max.x;
            z.maxY = zone.max.y;
            z.maxZ = zone.max.z;

            var zf = com.gerbarium.runtime.storage.RuntimeStateStorage.getZoneState(zone.id);
            for (var event : zf.recentEvents) {
                RuntimeEventDto ed = new RuntimeEventDto();
                ed.time = event.time;
                ed.zoneId = event.zoneId;
                ed.ruleId = event.ruleId;
                ed.type = event.type;
                ed.message = event.message;
                allEvents.add(ed);
            }

            for (MobRule rule : zone.mobs) {
                RuleSummaryDto rs = new RuleSummaryDto();
                rs.id = rule.id;
                rs.zoneId = zone.id;
                rs.name = rule.name;
                rs.entity = rule.entity;
                rs.spawnType = rule.spawnType.name();
                rs.maxAlive = rule.maxAlive;
                rs.aliveCount = MobTracker.getPrimaryAliveCount(zone.id, rule.id);
                
                rs.refillMode = rule.refillMode.name();
                rs.spawnCount = rule.spawnCount;
                rs.respawnSeconds = rule.respawnSeconds;
                rs.chance = rule.chance;
                rs.timedMaxSpawnsPerActivation = rule.timedMaxSpawnsPerActivation;
                rs.cooldownStart = rule.cooldownStart.name();

                var ruleState = zf.rules.get(rule.id);
                if (ruleState != null) {
                    rs.active = rule.enabled;
                    rs.timedSpawnedThisActivation = ruleState.timedSpawnedThisActivation;
                    rs.lastAttemptAt = ruleState.lastAttemptAt;
                    rs.lastAttemptResult = ruleState.lastAttemptResult;
                    rs.lastAttemptReason = ruleState.lastAttemptReason;
                    rs.lastSuccessAt = ruleState.lastSuccessAt;
                    rs.nextAvailableAt = ruleState.nextAvailableAt;
                    rs.totalAttempts = ruleState.totalAttempts;
                    rs.totalSuccesses = ruleState.totalSuccesses;
                }
                
                z.rules.add(rs);
            }
            
            dto.zones.add(z);
        }

        allEvents.sort((a, b) -> Long.compare(b.time, a.time));
        dto.recentEvents = allEvents.stream().limit(100).toList();
        dto.recentEventsCount = allEvents.size();

        return dto;
    }
}