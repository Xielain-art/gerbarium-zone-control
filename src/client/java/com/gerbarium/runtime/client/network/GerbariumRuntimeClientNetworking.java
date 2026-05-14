package com.gerbarium.runtime.client.network;

import com.gerbarium.runtime.client.gui.RuntimeZonesScreen;
import com.gerbarium.runtime.client.gui.RuntimeEventsView;
import com.gerbarium.runtime.client.gui.RuntimeRuleDetailsView;
import com.gerbarium.runtime.client.gui.RuntimeSnapshotView;
import com.gerbarium.runtime.client.gui.RuntimeZoneDetailsView;
import com.gerbarium.runtime.client.dto.RuntimeEventsDto;
import com.gerbarium.runtime.network.GerbariumRuntimePackets;
import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import com.gerbarium.runtime.client.dto.RuleSummaryDto;
import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import com.gerbarium.runtime.admin.ActionResultDto;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class GerbariumRuntimeClientNetworking {
    private static final Gson GSON = new Gson();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(GerbariumRuntimePackets.OPEN_RUNTIME_GUI, (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                client.setScreen(new RuntimeZonesScreen());
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GerbariumRuntimePackets.SYNC_RUNTIME_SNAPSHOT, (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> {
                RuntimeSnapshotDto snapshot = GSON.fromJson(json, RuntimeSnapshotDto.class);
                if (client.currentScreen instanceof RuntimeSnapshotView screen) {
                    screen.updateSnapshot(snapshot);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GerbariumRuntimePackets.SYNC_ZONE_DETAILS, (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> {
                ZoneSummaryDto zone = GSON.fromJson(json, ZoneSummaryDto.class);
                if (client.currentScreen instanceof RuntimeZoneDetailsView screen) {
                    screen.updateZoneDetails(zone);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GerbariumRuntimePackets.SYNC_RULE_DETAILS, (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> {
                RuleSummaryDto rule = GSON.fromJson(json, RuleSummaryDto.class);
                if (client.currentScreen instanceof RuntimeRuleDetailsView screen) {
                    screen.updateRuleDetails(rule);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GerbariumRuntimePackets.SYNC_RUNTIME_EVENTS, (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> {
                RuntimeEventsDto events = GSON.fromJson(json, RuntimeEventsDto.class);
                if (client.currentScreen instanceof RuntimeEventsView screen) {
                    screen.updateEvents(events);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(GerbariumRuntimePackets.ACTION_RESULT, (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> {
                ActionResultDto result = GSON.fromJson(json, ActionResultDto.class);
                if (client.player != null) {
                    if (result.success) {
                        client.player.sendMessage(Text.literal("[Runtime] " + result.message), false);
                    } else {
                        client.player.sendMessage(Text.literal("[Runtime Error] " + result.message), false);
                    }
                }
            });
        });
    }
}
