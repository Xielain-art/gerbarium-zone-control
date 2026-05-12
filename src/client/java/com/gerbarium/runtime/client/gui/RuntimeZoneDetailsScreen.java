package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
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
        header.child(Components.label(Text.literal("Zone Details: " + zone.id)));
        header.child(Components.button(Text.literal("Back"), button -> {
            this.client.setScreen(parent);
        }).margins(Insets.left(20)));

        header.child(Components.button(Text.literal("Zone Events"), button -> {
            this.client.setScreen(new RuntimeEventsScreen(this, snapshot));
        }).margins(Insets.left(10)));
        
        rootComponent.child(header.margins(Insets.bottom(15)));

        // Details Container
        FlowLayout details = Containers.verticalFlow(Sizing.fill(90), Sizing.content());
        details.surface(Surface.PANEL).padding(Insets.of(10));

        // Runtime State Section
        details.child(Components.label(Text.literal("Runtime State")).margins(Insets.bottom(5)));
        details.child(createDetailRow("Status", zone.active ? "ACTIVE" : "INACTIVE", zone.active ? 0x00FF00 : 0xAAAAAA));
        details.child(createDetailRow("Nearby Players", String.valueOf(zone.nearbyPlayers), 0xFFFFFF));
        details.child(createDetailRow("Primary Alive", String.valueOf(zone.primaryAliveTotal), 0xFFFFFF));
        
        // Config Details Section
        details.child(Components.label(Text.literal("Configuration")).margins(Insets.vertical(5)));
        details.child(createDetailRow("Dimension", zone.dimension, 0xFFFFFF));
        details.child(createDetailRow("Bounds Min", String.format("%d, %d, %d", zone.minX, zone.minY, zone.minZ), 0xFFFFFF));
        details.child(createDetailRow("Bounds Max", String.format("%d, %d, %d", zone.maxX, zone.maxY, zone.maxZ), 0xFFFFFF));
        details.child(createDetailRow("Priority", String.valueOf(zone.priority), 0xFFFFFF));

        // Applied Rules Section
        details.child(Components.label(Text.literal("Applied Rules")).margins(Insets.vertical(5)));
        for (var rule : zone.rules) {
            FlowLayout ruleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            ruleRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            ruleRow.child(Components.label(Text.literal("- " + rule.name)).sizing(Sizing.fixed(120)));
            ruleRow.child(Components.label(Text.literal(rule.aliveCount + "/" + rule.maxAlive + " alive")));
            
            ruleRow.child(Components.button(Text.literal("Rule Details"), button -> {
                this.client.setScreen(new RuntimeRuleDetailsScreen(this, rule));
            }).margins(Insets.left(10)).sizing(Sizing.content()));
            
            details.child(ruleRow.margins(Insets.vertical(1)));
        }

        rootComponent.child(details);
    }

    private FlowLayout createDetailRow(String label, String value, int color) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(Components.label(Text.literal(label + ": ")).sizing(Sizing.fixed(120)));
        row.child(Components.label(Text.literal(value)).color(Color.ofRgb(color)));
        row.margins(Insets.vertical(2));
        return row;
    }
}