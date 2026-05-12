package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import com.gerbarium.runtime.network.GerbariumRuntimePackets;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class RuntimeZonesScreen extends BaseOwoScreen<FlowLayout> {
    private RuntimeSnapshotDto snapshot;
    private FlowLayout zonesList;

    public void updateSnapshot(RuntimeSnapshotDto snapshot) {
        this.snapshot = snapshot;
        if (zonesList != null) {
            rebuildZonesList();
        }
    }

    @Override
    protected @NotNull io.wispforest.owo.ui.core.OwoUIAdapter<FlowLayout> createAdapter() {
        return io.wispforest.owo.ui.core.OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT)
                .padding(Insets.of(20))
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP);

        // Header
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.child(Components.label(Text.literal("Gerbarium Regions Runtime")).margins(Insets.right(10)));
        
        if (snapshot != null) {
            header.child(Components.label(Text.literal("Zones: " + snapshot.totalZones + " | Enabled: " + snapshot.enabledZones))
                    .color(Color.ofRgb(0xAAAAAA)).margins(Insets.right(10)));
        }
        
        header.child(Components.button(Text.literal("Refresh"), button -> {
            ClientPlayNetworking.send(GerbariumRuntimePackets.REQUEST_RUNTIME_SNAPSHOT, PacketByteBufs.create());
        }).margins(Insets.left(10)));
        
        header.child(Components.button(Text.literal("Reload Zones"), button -> {
            MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(
                Text.literal("Reload Zones"),
                Text.literal("Re-read zone JSON files. Cooldowns will be kept."),
                () -> {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeString("RELOAD");
                    ClientPlayNetworking.send(GerbariumRuntimePackets.RUN_GLOBAL_ACTION, buf);
                }
            ));
        }).margins(Insets.left(5)));

        header.child(Components.button(Text.literal("Events"), button -> {
            if (snapshot != null) {
                MinecraftClient.getInstance().setScreen(new RuntimeEventsScreen(this, snapshot));
            }
        }).margins(Insets.left(10)));
        
        rootComponent.child(header.margins(Insets.bottom(10)));

        // Zones List Scrollable
        zonesList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rootComponent.child(Containers.verticalScroll(Sizing.fill(90), Sizing.fill(80), zonesList));

        if (snapshot == null) {
            ClientPlayNetworking.send(GerbariumRuntimePackets.REQUEST_RUNTIME_SNAPSHOT, PacketByteBufs.create());
        } else {
            rebuildZonesList();
        }
    }

    private void rebuildZonesList() {
        zonesList.clearChildren();
        
        if (snapshot.zones.isEmpty()) {
            zonesList.child(Components.label(Text.literal("No zones loaded. Add zone JSON files to config/gerbarium/zones/"))
                    .color(Color.ofRgb(0xFFFF00)));
            return;
        }
        
        for (ZoneSummaryDto zone : snapshot.zones) {
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.surface(Surface.PANEL).padding(Insets.of(5)).margins(Insets.vertical(2));
            row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

            // Zone ID
            row.child(Components.label(Text.literal(zone.id)).sizing(Sizing.fixed(120)));
            
            // Status
            String statusText = zone.enabled ? (zone.active ? "ACTIVE" : "INACTIVE") : "DISABLED";
            int statusColor = zone.enabled ? (zone.active ? 0x00FF00 : 0xAAAAAA) : 0xFF0000;
            row.child(Components.label(Text.literal(statusText))
                    .color(Color.ofRgb(statusColor))
                    .sizing(Sizing.fixed(70)));
            
            // Players nearby
            row.child(Components.label(Text.literal("Players: " + zone.nearbyPlayers))
                    .color(Color.ofRgb(zone.nearbyPlayers > 0 ? 0xFFFFFF : 0x888888))
                    .sizing(Sizing.fixed(80)));
            
            // Alive mobs
            row.child(Components.label(Text.literal("Alive: " + zone.primaryAliveTotal))
                    .color(Color.ofRgb(zone.primaryAliveTotal > 0 ? 0x00FFFF : 0x888888))
                    .sizing(Sizing.fixed(70)));

            // Details button
            row.child(Components.button(Text.literal("Details"), button -> {
                MinecraftClient.getInstance().setScreen(new RuntimeZoneDetailsScreen(this, zone, snapshot));
            }).margins(Insets.left(5)));

            // Force Spawn button
            row.child(Components.button(Text.literal("Force Spawn"), button -> {
                MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(
                    Text.literal("Force Spawn Zone"),
                    Text.literal("Force spawn mobs for all rules in zone: " + zone.id),
                    () -> {
                        PacketByteBuf buf = PacketByteBufs.create();
                        buf.writeString("FORCE_SPAWN");
                        buf.writeString(zone.id);
                        ClientPlayNetworking.send(GerbariumRuntimePackets.RUN_GLOBAL_ACTION, buf);
                    }
                ));
            }).margins(Insets.left(5)));

            zonesList.child(row);
        }
    }
}
