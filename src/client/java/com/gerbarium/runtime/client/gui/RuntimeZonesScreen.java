package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import com.gerbarium.runtime.network.GerbariumRuntimePackets;
import com.gerbarium.runtime.util.TimeUtil;
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

public class RuntimeZonesScreen extends BaseOwoScreen<FlowLayout> implements RuntimeSnapshotView {
    private RuntimeSnapshotDto snapshot;
    private FlowLayout zonesList;
    private String filterMode = "ALL";

    public RuntimeZonesScreen() {
    }

    public RuntimeZonesScreen(RuntimeSnapshotDto snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public void updateSnapshot(RuntimeSnapshotDto snapshot) {
        this.snapshot = snapshot;
        if (this.zonesList != null) {
            rebuildZonesList();
        }
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT)
                .padding(Insets.of(18))
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP);

        rootComponent.child(buildHeader());

        this.zonesList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rootComponent.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(78), zonesList).margins(Insets.top(10)));

        if (snapshot == null) {
            requestSnapshot();
        } else {
            rebuildZonesList();
        }
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        header.surface(Surface.PANEL).padding(Insets.of(10));

        FlowLayout titleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        titleRow.child(Components.label(Text.literal("Gerbarium Regions Runtime")).sizing(Sizing.fixed(260)));
        titleRow.child(Components.label(Text.literal(summaryText())).color(Color.ofRgb(0xAAB2C6)));
        header.child(titleRow);

        header.child(summaryRow().margins(Insets.top(6)));
        header.child(primaryActions().margins(Insets.top(8)));
        header.child(secondaryActions().margins(Insets.top(6)));
        header.child(filtersRow().margins(Insets.top(8)));
        return header;
    }

    private FlowLayout summaryRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(statChip("Zones", value(snapshot == null ? 0 : snapshot.totalZones)));
        row.child(statChip("Enabled", value(snapshot == null ? 0 : snapshot.enabledZones)).margins(Insets.left(6)));
        row.child(statChip("Active", value(snapshot == null ? 0 : snapshot.activeZones)).margins(Insets.left(6)));
        row.child(statChip("Primary", value(snapshot == null ? 0 : snapshot.managedPrimaryCount)).margins(Insets.left(6)));
        row.child(statChip("Companions", value(snapshot == null ? 0 : snapshot.managedCompanionCount)).margins(Insets.left(6)));
        row.child(statChip("Events", value(snapshot == null ? 0 : snapshot.recentEventsCount)).margins(Insets.left(6)));
        row.child(statChip("Debug", snapshot != null && snapshot.debug ? "ON" : "OFF").margins(Insets.left(6)));
        return row;
    }

    private FlowLayout primaryActions() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(actionButton("Refresh", this::requestSnapshot));
        row.child(actionButton("Reload Zones", () -> openConfirm("Reload Zones", "Re-read zone JSON files. Runtime state stays intact.", "RELOAD")).margins(Insets.left(6)));
        row.child(actionButton("Cleanup Orphans", () -> openConfirm("Cleanup Orphans", "Remove orphan managed mobs from loaded worlds.", "CLEANUP_ORPHANS")).margins(Insets.left(6)));
        row.child(actionButton("State Save", () -> sendAction("STATE_SAVE", null)).margins(Insets.left(6)));
        return row;
    }

    private FlowLayout secondaryActions() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(actionButton("Events", () -> {
            if (snapshot != null) {
                MinecraftClient.getInstance().setScreen(new RuntimeEventsScreen(this, snapshot));
            }
        }));
        row.child(actionButton(snapshot != null && snapshot.debug ? "Debug Off" : "Debug On", () -> sendAction(snapshot != null && snapshot.debug ? "DEBUG_OFF" : "DEBUG_ON", null)).margins(Insets.left(6)));
        return row;
    }

    private FlowLayout filtersRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.child(Components.label(Text.literal("Filter")).color(Color.ofRgb(0xAAB2C6)));
        row.child(filterButton("ALL").margins(Insets.left(8)));
        row.child(filterButton("ACTIVE").margins(Insets.left(4)));
        row.child(filterButton("INACTIVE").margins(Insets.left(4)));
        row.child(filterButton("ENABLED").margins(Insets.left(4)));
        row.child(filterButton("DISABLED").margins(Insets.left(4)));
        row.child(filterButton("ATTENTION").margins(Insets.left(4)));
        return row;
    }

    private Component statChip(String title, String value) {
        FlowLayout chip = Containers.verticalFlow(Sizing.content(), Sizing.content());
        chip.surface(Surface.DARK_PANEL).padding(Insets.of(6));
        chip.child(Components.label(Text.literal(title)).color(Color.ofRgb(0xAAB2C6)));
        chip.child(Components.label(Text.literal(value)).color(Color.ofRgb(0xFFFFFF)));
        return chip;
    }

    private Component actionButton(String title, Runnable action) {
        return Components.button(Text.literal(title), button -> action.run()).sizing(Sizing.fixed(132), Sizing.content());
    }

    private Component filterButton(String mode) {
        boolean selected = filterMode.equals(mode);
        return Components.button(Text.literal(selected ? "[" + mode + "]" : mode), button -> {
            filterMode = mode;
            rebuildZonesList();
        }).sizing(Sizing.fixed(mode.equals("ATTENTION") ? 108 : 96), Sizing.content());
    }

    private String summaryText() {
        if (snapshot == null) {
            return "Loading snapshot...";
        }
        return "loaded " + snapshot.totalZones + " zones, " + snapshot.activeZones + " active, " + snapshot.recentEventsCount + " recent events";
    }

    private boolean matchesFilter(ZoneSummaryDto zone) {
        return switch (filterMode) {
            case "ACTIVE" -> zone.active;
            case "INACTIVE" -> !zone.active && zone.enabled;
            case "ENABLED" -> zone.enabled;
            case "DISABLED" -> !zone.enabled;
            case "ATTENTION" -> zone.dirty || (zone.warningText != null && !zone.warningText.isBlank()) || (zone.hintText != null && !zone.hintText.isBlank());
            default -> true;
        };
    }

    private void rebuildZonesList() {
        zonesList.clearChildren();

        if (snapshot == null) {
            zonesList.child(Components.label(Text.literal("Loading snapshot...")).color(Color.ofRgb(0xAAB2C6)));
            return;
        }

        if (snapshot.zones.isEmpty()) {
            zonesList.child(Components.label(Text.literal("No zones loaded. Add JSON files to config/gerbarium/zones/")).color(Color.ofRgb(0xFFE08A)));
            return;
        }

        int shown = 0;
        for (ZoneSummaryDto zone : snapshot.zones) {
            if (!matchesFilter(zone)) {
                continue;
            }

            zonesList.child(buildZoneCard(zone));
            shown++;
        }

        if (shown == 0) {
            zonesList.child(Components.label(Text.literal("No zones match the current filter.")).color(Color.ofRgb(0xFFE08A)));
        }
    }

    private FlowLayout buildZoneCard(ZoneSummaryDto zone) {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(Surface.PANEL).padding(Insets.of(10)).margins(Insets.vertical(4));

        FlowLayout titleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        titleRow.child(Components.label(Text.literal(displayName(zone))).sizing(Sizing.fixed(240)));
        titleRow.child(statusLabel(zone).margins(Insets.left(6)));
        titleRow.child(enabledLabel(zone).margins(Insets.left(6)));
        titleRow.child(activeLabel(zone).margins(Insets.left(6)));
        if (zone.dirty) {
            titleRow.child(Components.label(Text.literal("DIRTY")).color(Color.ofRgb(0xFFB347)).margins(Insets.left(6)));
        }
        card.child(titleRow);

        FlowLayout meta = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        meta.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        meta.child(metaLabel("ID", zone.id));
        meta.child(metaLabel("Dimension", zone.dimension).margins(Insets.left(12)));
        meta.child(metaLabel("Players", String.valueOf(zone.nearbyPlayers)).margins(Insets.left(12)));
        meta.child(metaLabel("Rules", String.valueOf(zone.totalRules)).margins(Insets.left(12)));
        meta.child(metaLabel("Primary", String.valueOf(zone.primaryAliveTotal)).margins(Insets.left(12)));
        meta.child(metaLabel("Companions", String.valueOf(zone.companionsAliveTotal)).margins(Insets.left(12)));
        card.child(meta.margins(Insets.top(4)));

        FlowLayout timing = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        timing.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        timing.child(metaLabel("Last activated", TimeUtil.formatRelative(zone.lastActivatedAt)));
        timing.child(metaLabel("Last deactivated", TimeUtil.formatRelative(zone.lastDeactivatedAt)).margins(Insets.left(12)));
        timing.child(metaLabel("Last player seen", TimeUtil.formatRelative(zone.lastPlayerSeenAt)).margins(Insets.left(12)));
        card.child(timing.margins(Insets.top(4)));

        if (zone.currentStatus != null && !zone.currentStatus.isBlank()) {
            card.child(Components.label(Text.literal("Status: " + zone.currentStatus)).color(Color.ofRgb(0xE3E8F6)).margins(Insets.top(6)));
        }
        if (zone.warningText != null && !zone.warningText.isBlank()) {
            card.child(Components.label(Text.literal("Warning: " + zone.warningText)).color(Color.ofRgb(0xFF8A80)).margins(Insets.top(2)));
        }
        if (zone.hintText != null && !zone.hintText.isBlank()) {
            card.child(Components.label(Text.literal("Hint: " + zone.hintText)).color(Color.ofRgb(0xAAB2C6)).margins(Insets.top(2)));
        }
        if (zone.nextActionText != null && !zone.nextActionText.isBlank()) {
            card.child(Components.label(Text.literal("Next: " + zone.nextActionText)).color(Color.ofRgb(0xC7D2FE)).margins(Insets.top(2)));
        }

        card.child(zoneActionRowOne(zone).margins(Insets.top(8)));
        card.child(zoneActionRowTwo(zone).margins(Insets.top(4)));
        return card;
    }

    private FlowLayout zoneActionRowOne(ZoneSummaryDto zone) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(actionButton("Details", () -> MinecraftClient.getInstance().setScreen(new RuntimeZoneDetailsScreen(this, zone, snapshot))));
        row.child(actionButton("Events", () -> MinecraftClient.getInstance().setScreen(new RuntimeEventsScreen(this, snapshot, zone.id, null))).margins(Insets.left(6)));
        row.child(actionButton("Force Activate", () -> openConfirm("Force Activate", "Force activate zone " + zone.id + "?", "FORCE_ACTIVATE:" + zone.id)).margins(Insets.left(6)));
        row.child(actionButton("Force Deactivate", () -> openConfirm("Force Deactivate", "Force deactivate zone " + zone.id + "?", "FORCE_DEACTIVATE:" + zone.id)).margins(Insets.left(6)));
        return row;
    }

    private FlowLayout zoneActionRowTwo(ZoneSummaryDto zone) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(actionButton("Force Spawn", () -> openConfirm("Force Spawn", "Force spawn all rules in zone " + zone.id + "?", "FORCE_SPAWN:" + zone.id)));
        row.child(actionButton("Clear Mobs", () -> openConfirm("Clear Mobs", "Remove managed mobs in zone " + zone.id + "?", "CLEAR_ZONE:" + zone.id)).margins(Insets.left(6)));
        row.child(actionButton("Clear State", () -> openConfirm("Clear State", "Delete runtime state for zone " + zone.id + "?", "CLEAR_ZONE_STATE:" + zone.id)).margins(Insets.left(6)));
        return row;
    }

    private Component statusLabel(ZoneSummaryDto zone) {
        return Components.label(Text.literal(zone.currentStatus == null || zone.currentStatus.isBlank() ? fallbackZoneStatus(zone) : zone.currentStatus))
                .color(Color.ofRgb(statusColor(zone)))
                .sizing(Sizing.fixed(180), Sizing.content());
    }

    private Component enabledLabel(ZoneSummaryDto zone) {
        return Components.label(Text.literal(zone.enabled ? "ENABLED" : "DISABLED"))
                .color(Color.ofRgb(zone.enabled ? 0xA7F3D0 : 0xFCA5A5))
                .sizing(Sizing.fixed(88), Sizing.content());
    }

    private Component activeLabel(ZoneSummaryDto zone) {
        return Components.label(Text.literal(zone.active ? "ACTIVE" : "INACTIVE"))
                .color(Color.ofRgb(zone.active ? 0x86EFAC : 0xCBD5E1))
                .sizing(Sizing.fixed(88), Sizing.content());
    }

    private Component metaLabel(String label, String value) {
        return Components.label(Text.literal(label + ": " + valueOrDash(value))).color(Color.ofRgb(0xD1D5DB));
    }

    private String fallbackZoneStatus(ZoneSummaryDto zone) {
        if (!zone.enabled) {
            return "DISABLED";
        }
        return zone.active ? "ACTIVE" : "INACTIVE";
    }

    private int statusColor(ZoneSummaryDto zone) {
        if (!zone.enabled) {
            return 0xFCA5A5;
        }
        if (zone.warningText != null && !zone.warningText.isBlank()) {
            return 0xFCD34D;
        }
        return zone.active ? 0x86EFAC : 0xCBD5E1;
    }

    private String displayName(ZoneSummaryDto zone) {
        return zone.name == null || zone.name.isBlank() ? zone.id : zone.name;
    }

    private String value(int value) {
        return String.valueOf(value);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void requestSnapshot() {
        ClientPlayNetworking.send(GerbariumRuntimePackets.REQUEST_RUNTIME_SNAPSHOT, PacketByteBufs.create());
    }

    private void openConfirm(String title, String description, String action) {
        MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(
                Text.literal(title),
                Text.literal(description),
                () -> sendAction(action, null)
        ));
    }

    private void sendAction(String action, String zoneId) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(action);
        if (zoneId != null) {
            buf.writeString(zoneId);
        }
        ClientPlayNetworking.send(GerbariumRuntimePackets.RUN_GLOBAL_ACTION, buf);
        requestSnapshot();
    }
}
