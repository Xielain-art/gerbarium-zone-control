package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class RuntimeZoneDetailsScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private final ZoneSummaryDto zone;
    private final com.gerbarium.runtime.client.dto.RuntimeSnapshotDto snapshot;

    public RuntimeZoneDetailsScreen(Screen parent, ZoneSummaryDto zone, com.gerbarium.runtime.client.dto.RuntimeSnapshotDto snapshot) {
        this.parent = parent;
        this.zone = zone;
        this.snapshot = snapshot;
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
        header.child(Components.label(Text.literal("Zone: " + zone.id)));
        header.child(Components.button(Text.literal("Back"), button -> {
            this.client.setScreen(parent);
        }).margins(Insets.left(20)));

        header.child(Components.button(Text.literal("Events"), button -> {
            this.client.setScreen(new RuntimeEventsScreen(this, snapshot));
        }).margins(Insets.left(10)));

        header.child(Components.button(Text.literal("Force Spawn"), button -> {
            MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(
                Text.literal("Force Spawn Zone"),
                Text.literal("Force spawn mobs for all rules in this zone?"),
                () -> {
                    net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                    buf.writeString("FORCE_SPAWN");
                    buf.writeString(zone.id);
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(com.gerbarium.runtime.network.GerbariumRuntimePackets.RUN_GLOBAL_ACTION, buf);
                }
            ));
        }).margins(Insets.left(10)));
        
        rootComponent.child(header.margins(Insets.bottom(15)));

        // Details Container
        FlowLayout details = Containers.verticalFlow(Sizing.fill(90), Sizing.content());
        details.surface(Surface.PANEL).padding(Insets.of(10));

        // Runtime State Section
        details.child(Components.label(Text.literal("Runtime State")).margins(Insets.bottom(5)));
        
        String statusText = zone.enabled ? (zone.active ? "ACTIVE" : "INACTIVE") : "DISABLED";
        int statusColor = zone.enabled ? (zone.active ? 0x00FF00 : 0xAAAAAA) : 0xFF0000;
        details.child(createDetailRow("Status", statusText, statusColor));
        details.child(createDetailRow("Nearby Players", String.valueOf(zone.nearbyPlayers), zone.nearbyPlayers > 0 ? 0xFFFFFF : 0x888888));
        details.child(createDetailRow("Primary Mobs Alive", String.valueOf(zone.primaryAliveTotal), zone.primaryAliveTotal > 0 ? 0x00FFFF : 0x888888));
        
        // Config Details Section
        details.child(Components.label(Text.literal("Configuration")).margins(Insets.vertical(5)));
        details.child(createDetailRow("Dimension", zone.dimension, 0xFFFFFF));
        details.child(createDetailRow("Bounds Min", String.format("%d, %d, %d", zone.minX, zone.minY, zone.minZ), 0xAAAAAA));
        details.child(createDetailRow("Bounds Max", String.format("%d, %d, %d", zone.maxX, zone.maxY, zone.maxZ), 0xAAAAAA));
        details.child(createDetailRow("Priority", String.valueOf(zone.priority), 0xFFFFFF));

        // Rules Section
        details.child(Components.label(Text.literal("Mob Rules (" + zone.rules.size() + ")")).margins(Insets.vertical(5)));
        
        if (zone.rules.isEmpty()) {
            details.child(Components.label(Text.literal("No rules configured")).color(Color.ofRgb(0xFFFF00)));
        } else {
            for (var rule : zone.rules) {
                FlowLayout ruleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
                ruleRow.surface(Surface.DARK_PANEL).padding(Insets.of(3)).margins(Insets.vertical(1));
                ruleRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

                // Rule name
                ruleRow.child(Components.label(Text.literal(rule.name)).sizing(Sizing.fixed(120)));
                
                // Entity type
                ruleRow.child(Components.label(Text.literal(rule.entity))
                        .color(Color.ofRgb(0xAAAAAA))
                        .sizing(Sizing.fixed(100)));
                
                // Alive count
                int aliveColor = rule.aliveCount >= rule.maxAlive ? 0xFF0000 : (rule.aliveCount > 0 ? 0x00FF00 : 0x888888);
                ruleRow.child(Components.label(Text.literal(rule.aliveCount + "/" + rule.maxAlive))
                        .color(Color.ofRgb(aliveColor))
                        .sizing(Sizing.fixed(50)));
                
                // Spawn type
                ruleRow.child(Components.label(Text.literal(rule.spawnType))
                        .color(Color.ofRgb(0xFFFF00))
                        .sizing(Sizing.fixed(60)));
                
                ruleRow.child(Components.button(Text.literal("Details"), button -> {
                    this.client.setScreen(new RuntimeRuleDetailsScreen(this, rule));
                }).margins(Insets.left(5)).sizing(Sizing.content()));
                
                details.child(ruleRow);
            }
        }

        rootComponent.child(Containers.verticalScroll(Sizing.fill(90), Sizing.fill(75), details));
    }

    private FlowLayout createDetailRow(String label, String value, int color) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(Components.label(Text.literal(label + ": ")).sizing(Sizing.fixed(150)));
        row.child(Components.label(Text.literal(value)).color(Color.ofRgb(color)));
        row.margins(Insets.vertical(2));
        return row;
    }
}
