package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import com.gerbarium.runtime.network.GerbariumRuntimePackets;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
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
        header.child(Components.label(Text.literal("Gerbarium Regions Runtime")));
        header.child(Components.button(Text.literal("Refresh"), button -> {
            ClientPlayNetworking.send(GerbariumRuntimePackets.REQUEST_RUNTIME_SNAPSHOT, PacketByteBufs.create());
        }).margins(Insets.left(20)));
        header.child(Components.button(Text.literal("Reload"), button -> {
            MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(
                Text.literal("Reload Zones"),
                Text.literal("This will re-read zone JSON files. Cooldowns will be kept."),
                () -> {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeString("RELOAD");
                    ClientPlayNetworking.send(GerbariumRuntimePackets.RUN_GLOBAL_ACTION, buf);
                }
            ));
        }).margins(Insets.left(5)));
        
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
        for (ZoneSummaryDto zone : snapshot.zones) {
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.surface(Surface.PANEL).padding(Insets.of(5)).margins(Insets.vertical(2));
            row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

            row.child(Components.label(Text.literal(zone.id)).sizing(Sizing.fixed(100)));
            row.child(Components.label(Text.literal(zone.active ? "ACTIVE" : "INACTIVE"))
                    .color(zone.active ? Color.ofRgb(0x00FF00) : Color.ofRgb(0xAAAAAA))
                    .sizing(Sizing.fixed(60)));
            
            row.child(Components.label(Text.literal("Players: " + zone.nearbyPlayers)).sizing(Sizing.fixed(80)));

            row.child(Components.button(Text.literal("Details"), button -> {
                MinecraftClient.getInstance().setScreen(new RuntimeZoneDetailsScreen(this, zone));
            }).margins(Insets.left(5)));

            row.child(Components.button(Text.literal("Force Spawn"), button -> {
                // To be implemented: SEND RUN_ZONE_ACTION
            }).margins(Insets.left(10)));

            zonesList.child(row);
        }
    }
}