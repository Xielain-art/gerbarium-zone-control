package com.gerbarium.runtime.network;

import com.gerbarium.runtime.admin.ActionResultDto;
import com.gerbarium.runtime.admin.RuntimeAdminService;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.permission.PermissionUtil;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.gerbarium.runtime.tick.ZoneActivationManager;
import com.gerbarium.runtime.tracking.MobTracker;
import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;

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
        for (Zone zone : all) {
            ZoneSummaryDto z = new ZoneSummaryDto();
            z.id = zone.id;
            z.enabled = zone.enabled;
            z.dimension = zone.dimension;
            
            var zState = ZoneActivationManager.getZoneState(zone.id);
            z.active = zState.active;
            z.nearbyPlayers = zState.nearbyPlayers.size();
            z.primaryAliveTotal = 0; // Simplified for MVP
            
            dto.zones.add(z);
        }

        return dto;
    }
}