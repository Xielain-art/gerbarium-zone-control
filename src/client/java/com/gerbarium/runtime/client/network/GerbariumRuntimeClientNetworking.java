package com.gerbarium.runtime.client.network;

import com.gerbarium.runtime.client.gui.RuntimeZonesScreen;
import com.gerbarium.runtime.network.GerbariumRuntimePackets;
import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

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
                if (client.currentScreen instanceof RuntimeZonesScreen screen) {
                    screen.updateSnapshot(snapshot);
                }
            });
        });
    }
}