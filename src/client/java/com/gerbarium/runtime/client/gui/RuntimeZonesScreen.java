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

    public RuntimeZonesScreen() {}

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
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT)
                .padding(Insets.of(18))
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP);
        root.child(buildHeader());
        this.zonesList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        root.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(78), zonesList)
                .margins(Insets.top(RuntimeUi.GAP_SECTION)));
        if (snapshot == null) {
            requestSnapshot();
        } else {
            rebuildZonesList();
        }
    }

    private FlowLayout buildHeader() {
        FlowLayout header = RuntimeUi.card();
        FlowLayout titleRow = RuntimeUi.row();
        titleRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        titleRow.child(RuntimeUi.title("Gerbarium Regions Runtime"));
        titleRow.child(RuntimeUi.muted(summaryText()).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        header.child(titleRow);
        header.child(statsRow().margins(Insets.top(RuntimeUi.GAP_ITEM)));
        header.child(primaryActions().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        header.child(secondaryActions().margins(Insets.top(RuntimeUi.GAP_ITEM)));
        return header;
    }

    private String summaryText() {
        if (snapshot == null) return "";
        return snapshot.activeZones + "/" + snapshot.totalZones + " active";
    }

    private FlowLayout statsRow() {
        FlowLayout row = RuntimeUi.row();
        if (snapshot == null) {
            row.child(RuntimeUi.muted("Loading..."));
            return row;
        }
        row.child(RuntimeUi.chip("Zones", str(snapshot.totalZones), RuntimeUi.COLOR_TEXT));
        row.child(RuntimeUi.chip("Enabled", str(snapshot.enabledZones), RuntimeUi.COLOR_OK_LIGHT).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        row.child(RuntimeUi.chip("Active", str(snapshot.activeZones), RuntimeUi.COLOR_OK).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        row.child(RuntimeUi.chip("Primary", str(snapshot.managedPrimaryCount), RuntimeUi.COLOR_DIM).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        row.child(RuntimeUi.chip("Companions", str(snapshot.managedCompanionCount), RuntimeUi.COLOR_DIM).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        row.child(RuntimeUi.chip("Events", str(snapshot.recentEventsCount), RuntimeUi.COLOR_ZONE_TAG).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        row.child(RuntimeUi.chip("Debug", snapshot.debug ? "ON" : "OFF", snapshot.debug ? RuntimeUi.COLOR_HIGHLIGHT : RuntimeUi.COLOR_MUTED).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        return row;
    }

    private FlowLayout primaryActions() {
        FlowLayout row = RuntimeUi.row();
        row.child(RuntimeUi.button("Refresh", this::requestSnapshot));
        row.child(RuntimeUi.button("Reload Zones", () -> openConfirm("Reload Zones", "Re-read zone JSON files. Runtime state stays intact.", "RELOAD")).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        row.child(RuntimeUi.button("Cleanup Orphans", () -> openConfirm("Cleanup Orphans", "Remove orphan managed mobs from loaded worlds.", "CLEANUP_ORPHANS")).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        row.child(RuntimeUi.button("State Save", () -> sendAction("STATE_SAVE")).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        return row;
    }

    private FlowLayout secondaryActions() {
        FlowLayout row = RuntimeUi.row();
        row.child(RuntimeUi.button("Events", () -> {
            if (snapshot != null) {
                MinecraftClient.getInstance().setScreen(new RuntimeEventsScreen(this, snapshot));
            }
        }));
        row.child(RuntimeUi.button(snapshot != null && snapshot.debug ? "Debug Off" : "Debug On", () -> sendAction(snapshot != null && snapshot.debug ? "DEBUG_OFF" : "DEBUG_ON")).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        return row;
    }

    private void rebuildZonesList() {
        zonesList.clearChildren();
        if (snapshot == null || snapshot.zones == null || snapshot.zones.isEmpty()) {
            zonesList.child(RuntimeUi.muted("No zones loaded.").margins(Insets.top(RuntimeUi.GAP_ITEM)));
            return;
        }
        for (ZoneSummaryDto zone : snapshot.zones) {
            zonesList.child(zoneCard(zone).margins(Insets.top(RuntimeUi.GAP_ITEM)));
        }
    }

    private FlowLayout zoneCard(ZoneSummaryDto zone) {
        FlowLayout card = RuntimeUi.card();
        FlowLayout header = RuntimeUi.row();
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.child(RuntimeUi.label(displayName(zone), RuntimeUi.COLOR_ACCENT));
        header.child(zoneStatus(zone).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        header.child(zoneEnabled(zone).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        header.child(zoneActive(zone).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        card.child(header);

        FlowLayout meta = RuntimeUi.row();
        meta.child(RuntimeUi.label("Dim: " + RuntimeUi.valueOrDash(zone.dimension), RuntimeUi.COLOR_SUBTLE));
        meta.child(RuntimeUi.label("Rules: " + zone.totalRules, RuntimeUi.COLOR_SUBTLE).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        meta.child(RuntimeUi.label("Primary: " + zone.primaryAliveTotal, RuntimeUi.COLOR_OK_LIGHT).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        meta.child(RuntimeUi.label("Companions: " + zone.companionsAliveTotal, RuntimeUi.COLOR_DIM).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        if (zone.nearbyPlayers > 0) {
            meta.child(RuntimeUi.label("Players: " + zone.nearbyPlayers, RuntimeUi.COLOR_ZONE_TAG).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        }
        if (zone.activationId > 0) {
            meta.child(RuntimeUi.muted("Act#" + zone.activationId + " " + TimeUtil.formatRelative(zone.lastActivatedAt)).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        }
        card.child(meta.margins(Insets.top(RuntimeUi.GAP_TINY)));

        if (zone.warningText != null && !zone.warningText.isBlank()) {
            card.child(RuntimeUi.warn(zone.warningText).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
        if (zone.hintText != null && !zone.hintText.isBlank()) {
            card.child(RuntimeUi.label("Hint: " + zone.hintText, RuntimeUi.COLOR_LABEL).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
        if (zone.nextActionText != null && !zone.nextActionText.isBlank()) {
            card.child(RuntimeUi.label("Next: " + zone.nextActionText, RuntimeUi.COLOR_DIM).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
        card.child(zoneActions(zone).margins(Insets.top(RuntimeUi.GAP_ITEM)));
        return card;
    }

    private FlowLayout zoneActions(ZoneSummaryDto zone) {
        FlowLayout row = RuntimeUi.row();
        row.child(RuntimeUi.button("Details", "View zone details and rules", () -> MinecraftClient.getInstance().setScreen(new RuntimeZoneDetailsScreen(this, zone, snapshot))));
        row.child(RuntimeUi.button("Events", "View zone event history", () -> MinecraftClient.getInstance().setScreen(new RuntimeEventsScreen(this, snapshot, zone.id, null))).margins(Insets.left(RuntimeUi.GAP_TINY)));
        row.child(RuntimeUi.button("Activate", "Force activate this zone", () -> openConfirm("Force Activate", "Force activate zone " + zone.id + "?", "FORCE_ACTIVATE:" + zone.id)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        row.child(RuntimeUi.button("Deactivate", "Force deactivate this zone", () -> openConfirm("Force Deactivate", "Force deactivate zone " + zone.id + "?", "FORCE_DEACTIVATE:" + zone.id)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        row.child(RuntimeUi.button("Spawn", "Force spawn all rules in zone", () -> openConfirm("Force Spawn", "Force spawn all rules in zone " + zone.id + "?", "FORCE_SPAWN:" + zone.id)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        row.child(RuntimeUi.button("Clear", "Remove managed mobs", () -> openConfirm("Clear Mobs", "Remove managed mobs in zone " + zone.id + "?", "CLEAR_ZONE:" + zone.id)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        return row;
    }

    private Component zoneStatus(ZoneSummaryDto zone) {
        String text = zone.currentStatus == null || zone.currentStatus.isBlank() ? fallbackStatus(zone) : zone.currentStatus;
        return RuntimeUi.label(text, statusColor(zone));
    }

    private Component zoneEnabled(ZoneSummaryDto zone) {
        return RuntimeUi.label(zone.enabled ? "ENABLED" : "DISABLED", zone.enabled ? RuntimeUi.COLOR_OK_LIGHT : RuntimeUi.COLOR_WARN);
    }

    private Component zoneActive(ZoneSummaryDto zone) {
        return RuntimeUi.label(zone.active ? "ACTIVE" : "INACTIVE", zone.active ? RuntimeUi.COLOR_OK : RuntimeUi.COLOR_MUTED);
    }

    private String fallbackStatus(ZoneSummaryDto zone) {
        if (!zone.enabled) return "DISABLED";
        return zone.active ? "ACTIVE" : "INACTIVE";
    }

    private int statusColor(ZoneSummaryDto zone) {
        if (!zone.enabled) return RuntimeUi.COLOR_WARN;
        if (zone.warningText != null && !zone.warningText.isBlank()) return RuntimeUi.COLOR_HIGHLIGHT;
        return zone.active ? RuntimeUi.COLOR_OK : RuntimeUi.COLOR_SUBTLE;
    }

    private String displayName(ZoneSummaryDto zone) {
        return zone.name == null || zone.name.isBlank() ? zone.id : zone.name;
    }

    private String str(int value) {
        return String.valueOf(value);
    }

    private void requestSnapshot() {
        ClientPlayNetworking.send(GerbariumRuntimePackets.REQUEST_RUNTIME_SNAPSHOT, PacketByteBufs.create());
    }

    private void openConfirm(String title, String description, String action) {
        MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(this,
                Text.literal(title), Text.literal(description), () -> sendAction(action)));
    }

    private void sendAction(String action) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(action);
        ClientPlayNetworking.send(GerbariumRuntimePackets.RUN_GLOBAL_ACTION, buf);
    }
}
