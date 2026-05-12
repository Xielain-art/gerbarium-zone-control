package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.RuntimeEventDto;
import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class RuntimeEventsScreen extends BaseOwoScreen<FlowLayout> {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    
    private final Screen parent;
    private final RuntimeSnapshotDto snapshot;
    private FlowLayout eventsList;
    
    private String typeFilter = "ALL";
    private String zoneFilter = "ALL";

    public RuntimeEventsScreen(Screen parent, RuntimeSnapshotDto snapshot) {
        this.parent = parent;
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
        header.child(Components.label(Text.literal("Event Log")));
        header.child(Components.button(Text.literal("Back"), button -> {
            this.client.setScreen(parent);
        }).margins(Insets.left(20)));
        
        rootComponent.child(header.margins(Insets.bottom(10)));

        // Filters
        FlowLayout filters = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        filters.child(Components.label(Text.literal("Type: ")));
        filters.child(Components.button(Text.literal(typeFilter), button -> {
            cycleTypeFilter();
            button.setMessage(Text.literal(typeFilter));
            rebuildEventsList();
        }).margins(Insets.right(10)));

        filters.child(Components.label(Text.literal("Zone: ")));
        filters.child(Components.button(Text.literal(zoneFilter), button -> {
            cycleZoneFilter();
            button.setMessage(Text.literal(zoneFilter));
            rebuildEventsList();
        }));
        
        rootComponent.child(filters.margins(Insets.bottom(10)));

        // Events List
        eventsList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rootComponent.child(Containers.verticalScroll(Sizing.fill(90), Sizing.fill(75), eventsList));

        rebuildEventsList();
    }

    private void cycleTypeFilter() {
        if ("ALL".equals(typeFilter)) typeFilter = "SPAWN";
        else if ("SPAWN".equals(typeFilter)) typeFilter = "ERROR";
        else if ("ERROR".equals(typeFilter)) typeFilter = "ACTIVATION";
        else typeFilter = "ALL";
    }

    private void cycleZoneFilter() {
        if ("ALL".equals(zoneFilter)) {
            if (!snapshot.zones.isEmpty()) zoneFilter = snapshot.zones.get(0).id;
        } else {
            int idx = -1;
            for (int i = 0; i < snapshot.zones.size(); i++) {
                if (snapshot.zones.get(i).id.equals(zoneFilter)) {
                    idx = i;
                    break;
                }
            }
            if (idx == -1 || idx == snapshot.zones.size() - 1) zoneFilter = "ALL";
            else zoneFilter = snapshot.zones.get(idx + 1).id;
        }
    }

    private void rebuildEventsList() {
        eventsList.clearChildren();
        
        List<RuntimeEventDto> filtered = snapshot.recentEvents.stream()
                .filter(e -> "ALL".equals(typeFilter) || e.type.contains(typeFilter))
                .filter(e -> "ALL".equals(zoneFilter) || e.zoneId.equals(zoneFilter))
                .collect(Collectors.toList());

        for (var event : filtered) {
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.surface(Surface.PANEL).padding(Insets.of(5)).margins(Insets.vertical(1));
            row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

            String timeStr = TIME_FORMAT.format(new Date(event.time));
            row.child(Components.label(Text.literal("[" + timeStr + "] ")).color(Color.ofRgb(0xAAAAAA)).sizing(Sizing.fixed(60)));
            row.child(Components.label(Text.literal(event.type)).color(getEventColor(event.type)).sizing(Sizing.fixed(80)));
            row.child(Components.label(Text.literal(event.zoneId + ": ")).color(Color.ofRgb(0xAAAAAA)).sizing(Sizing.fixed(80)));
            row.child(Components.label(Text.literal(event.message)));

            eventsList.child(row);
        }
    }

    private Color getEventColor(String type) {
        if (type.contains("ERROR")) return Color.ofRgb(0xFF0000);
        if (type.contains("SPAWN")) return Color.ofRgb(0x00FF00);
        if (type.contains("ACTIVATION")) return Color.ofRgb(0x00FFFF);
        return Color.ofRgb(0xFFFFFF);
    }
}