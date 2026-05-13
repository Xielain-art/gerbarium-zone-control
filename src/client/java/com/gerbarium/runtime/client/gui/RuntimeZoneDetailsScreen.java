package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.RuleSummaryDto;
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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class RuntimeZoneDetailsScreen extends BaseOwoScreen<FlowLayout> implements RuntimeSnapshotView {
    private final Screen parent;
    private final String zoneId;
    private ZoneSummaryDto zone;
    private RuntimeSnapshotDto snapshot;
    private FlowLayout body;

    public RuntimeZoneDetailsScreen(Screen parent, ZoneSummaryDto zone, RuntimeSnapshotDto snapshot) {
        this.parent = parent;
        this.zoneId = zone.id;
        this.zone = zone;
        this.snapshot = snapshot;
    }

    @Override
    public void updateSnapshot(RuntimeSnapshotDto snapshot) {
        this.snapshot = snapshot;
        this.zone = findZone(snapshot);
        if (this.body != null) {
            rebuildBody();
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

        this.body = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.body.surface(Surface.PANEL).padding(Insets.of(10));
        rootComponent.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(78), body).margins(Insets.top(10)));

        rebuildBody();
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        header.surface(Surface.PANEL).padding(Insets.of(10));

        FlowLayout title = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        title.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        title.child(Components.label(Text.literal("Zone Details")).sizing(Sizing.fixed(180)));
        title.child(Components.label(Text.literal(zone == null ? zoneId : displayName(zone) + " (" + zoneId + ")")).color(Color.ofRgb(0xAAB2C6)));
        header.child(title);
        header.child(Components.label(Text.literal("Snapshot-backed runtime overview for a single zone.")).color(Color.ofRgb(0xCBD5E1)).margins(Insets.top(2)));
        header.child(headerActions().margins(Insets.top(8)));
        return header;
    }

    private FlowLayout headerActions() {
        FlowLayout actions = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        actions.child(actionRow(
                actionButton("Back", () -> this.client.setScreen(parent)),
                actionButton("Refresh", this::requestSnapshot),
                actionButton("Events", () -> this.client.setScreen(new RuntimeEventsScreen(this, snapshot, zoneId, null)))
        ));
        actions.child(actionRow(
                actionButton("Force Activate", () -> confirm("Force Activate", "Force activate zone " + zoneId + "?", "FORCE_ACTIVATE:" + zoneId)),
                actionButton("Force Deactivate", () -> confirm("Force Deactivate", "Force deactivate zone " + zoneId + "?", "FORCE_DEACTIVATE:" + zoneId)),
                actionButton("Force Spawn Zone", () -> confirm("Force Spawn", "Force spawn all rules in zone " + zoneId + "?", "FORCE_SPAWN:" + zoneId))
        ).margins(Insets.top(6)));
        return actions;
    }

    private void rebuildBody() {
        body.clearChildren();

        if (zone == null) {
            body.child(Components.label(Text.literal("Zone not present in the latest snapshot.")).color(Color.ofRgb(0xFCA5A5)));
            return;
        }

        body.child(sectionCard("Runtime", runtimeRows()));
        body.child(sectionCard("Config", configRows()).margins(Insets.top(8)));
        body.child(sectionCard("Rules", rulesRows()).margins(Insets.top(8)));
    }

    private FlowLayout runtimeRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Enabled", boolText(zone.enabled)));
        rows.child(kv("Active", boolText(zone.active)));
        rows.child(kv("Nearby players", String.valueOf(zone.nearbyPlayers)));
        rows.child(kv("Last player seen", TimeUtil.formatRelative(zone.lastPlayerSeenAt)));
        rows.child(kv("Last activated", TimeUtil.formatRelative(zone.lastActivatedAt)));
        rows.child(kv("Last deactivated", TimeUtil.formatRelative(zone.lastDeactivatedAt)));
        rows.child(kv("Total rules", String.valueOf(zone.totalRules)));
        rows.child(kv("Primary alive", String.valueOf(zone.primaryAliveTotal)));
        rows.child(kv("Companions alive", String.valueOf(zone.companionsAliveTotal)));
        rows.child(kv("Current status", valueOrDash(zone.currentStatus)));
        rows.child(kv("Next action", valueOrDash(zone.nextActionText)));
        rows.child(kv("Hint", valueOrDash(zone.hintText)));
        rows.child(kv("Warning", valueOrDash(zone.warningText)));
        return rows;
    }

    private FlowLayout configRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Dimension", valueOrDash(zone.dimension)));
        rows.child(kv("Bounds min", zone.minX + ", " + zone.minY + ", " + zone.minZ));
        rows.child(kv("Bounds max", zone.maxX + ", " + zone.maxY + ", " + zone.maxZ));
        rows.child(kv("Dirty", boolText(zone.dirty)));
        rows.child(kv("State file", boolText(zone.stateFileExists)));
        rows.child(kv("Pending activation", boolText(zone.pendingActivation)));
        rows.child(kv("Priority", String.valueOf(zone.priority)));
        return rows;
    }

    private FlowLayout rulesRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        if (zone.rules.isEmpty()) {
            rows.child(Components.label(Text.literal("No rules configured.")).color(Color.ofRgb(0xCBD5E1)));
            return rows;
        }

        for (RuleSummaryDto rule : zone.rules) {
            rows.child(ruleCard(rule).margins(Insets.vertical(4)));
        }
        return rows;
    }

    private FlowLayout ruleCard(RuleSummaryDto rule) {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(Surface.DARK_PANEL).padding(Insets.of(8));

        FlowLayout title = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        title.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        title.child(Components.label(Text.literal(rule.name == null || rule.name.isBlank() ? rule.id : rule.name)).sizing(Sizing.fixed(220)));
        title.child(ruleStatus(rule).margins(Insets.left(6)));
        title.child(ruleEnabled(rule).margins(Insets.left(6)));
        title.child(ruleSpawnType(rule).margins(Insets.left(6)));
        card.child(title);

        card.child(kv("Entity", valueOrDash(rule.entity)).margins(Insets.top(4)));
        card.child(kv("Mode", valueOrDash(rule.spawnType) + " / " + valueOrDash(rule.refillMode)));
        card.child(kv("Primary alive", rule.aliveCount + " / " + rule.maxAlive));
        card.child(kv("Companions alive", String.valueOf(rule.encounterCompanionsAlive)));
        card.child(kv("Next action", valueOrDash(rule.nextActionText)));
        card.child(kv("Hint", valueOrDash(rule.hintText)));
        if (rule.warningText != null && !rule.warningText.isBlank()) {
            card.child(Components.label(Text.literal("Warning: " + rule.warningText)).color(Color.ofRgb(0xFCA5A5)).margins(Insets.top(2)));
        }

        FlowLayout buttons = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        buttons.child(actionRow(
                actionButton("Rule Details", () -> this.client.setScreen(new RuntimeRuleDetailsScreen(this, rule))),
                actionButton("History", () -> this.client.setScreen(new RuntimeEventsScreen(this, snapshot, zoneId, rule.id)))
        ));
        buttons.child(actionRow(
                actionButton("Force Spawn", () -> confirm("Force Spawn", "Force spawn rule " + rule.id + "?", "FORCE_RULE_SPAWN:" + rule.zoneId + ":" + rule.id)),
                actionButton("Force Primary", () -> confirm("Force Primary", "Spawn only the primary entity for rule " + rule.id + "?", "FORCE_RULE_PRIMARY:" + rule.zoneId + ":" + rule.id)),
                actionButton("Force Companions", () -> confirm("Force Companions", "Spawn only companions for rule " + rule.id + "?", "FORCE_RULE_COMPANIONS:" + rule.zoneId + ":" + rule.id))
        ).margins(Insets.top(6)));
        card.child(buttons.margins(Insets.top(6)));
        return card;
    }

    private FlowLayout sectionCard(String title, FlowLayout content) {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(Surface.PANEL).padding(Insets.of(8));
        card.child(Components.label(Text.literal(title)).color(Color.ofRgb(0xFDE68A)));
        card.child(content.margins(Insets.top(6)));
        return card;
    }

    private FlowLayout actionRow(Component... components) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        for (int i = 0; i < components.length; i++) {
            Component component = components[i];
            if (component instanceof FlowLayout flow) {
                row.child(flow);
            } else {
                row.child(component);
            }
        }
        return row;
    }

    private Component actionButton(String title, Runnable action) {
        return Components.button(Text.literal(title), button -> action.run()).sizing(Sizing.fixed(140), Sizing.content());
    }

    private Component kv(String label, String value) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(Components.label(Text.literal(label + ":")).color(Color.ofRgb(0xAAB2C6)).sizing(Sizing.fixed(180)));
        row.child(Components.label(Text.literal(valueOrDash(value))).color(Color.ofRgb(0xFFFFFF)));
        return row;
    }

    private Component ruleStatus(RuleSummaryDto rule) {
        return Components.label(Text.literal(valueOrDash(rule.currentStatus))).color(Color.ofRgb(statusColor(rule))).sizing(Sizing.fixed(170), Sizing.content());
    }

    private Component ruleEnabled(RuleSummaryDto rule) {
        return Components.label(Text.literal(rule.enabled ? "ENABLED" : "DISABLED")).color(Color.ofRgb(rule.enabled ? 0xA7F3D0 : 0xFCA5A5));
    }

    private Component ruleSpawnType(RuleSummaryDto rule) {
        return Components.label(Text.literal(valueOrDash(rule.spawnType))).color(Color.ofRgb(0xC7D2FE));
    }

    private String boolText(boolean value) {
        return value ? "yes" : "no";
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private int statusColor(RuleSummaryDto rule) {
        if (!rule.enabled) {
            return 0xFCA5A5;
        }
        if (rule.warningText != null && !rule.warningText.isBlank()) {
            return 0xFDE68A;
        }
        return switch (valueOrDash(rule.currentStatus)) {
            case "READY" -> 0xA7F3D0;
            case "INACTIVE", "DISABLED" -> 0xCBD5E1;
            case "COOLDOWN", "TIMED_WAIT", "WAITING_FOR_COMPANIONS_CLEAR" -> 0xFDE68A;
            default -> 0xC7D2FE;
        };
    }

    private ZoneSummaryDto findZone(RuntimeSnapshotDto snapshot) {
        if (snapshot == null || snapshot.zones == null) {
            return null;
        }
        for (ZoneSummaryDto candidate : snapshot.zones) {
            if (zoneId.equals(candidate.id)) {
                return candidate;
            }
        }
        return null;
    }

    private String displayName(ZoneSummaryDto zone) {
        return zone.name == null || zone.name.isBlank() ? zone.id : zone.name;
    }

    private void confirm(String title, String description, String action) {
        MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(Text.literal(title), Text.literal(description), () -> sendAction(action)));
    }

    private void sendAction(String action) {
        var buf = PacketByteBufs.create();
        buf.writeString(action);
        ClientPlayNetworking.send(GerbariumRuntimePackets.RUN_GLOBAL_ACTION, buf);
    }

    private void requestSnapshot() {
        ClientPlayNetworking.send(GerbariumRuntimePackets.REQUEST_RUNTIME_SNAPSHOT, PacketByteBufs.create());
    }
}
