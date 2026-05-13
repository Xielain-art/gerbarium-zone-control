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

public class RuntimeRuleDetailsScreen extends BaseOwoScreen<FlowLayout> implements RuntimeSnapshotView {
    private final Screen parent;
    private final String zoneId;
    private final String ruleId;
    private RuleSummaryDto rule;
    private RuntimeSnapshotDto snapshot;
    private FlowLayout body;

    public RuntimeRuleDetailsScreen(Screen parent, RuleSummaryDto rule) {
        this.parent = parent;
        this.zoneId = rule.zoneId;
        this.ruleId = rule.id;
        this.rule = rule;
    }

    @Override
    public void updateSnapshot(RuntimeSnapshotDto snapshot) {
        this.snapshot = snapshot;
        this.rule = findRule(snapshot);
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
        title.child(Components.label(Text.literal("Rule Details")).sizing(Sizing.fixed(180)));
        title.child(Components.label(Text.literal(rule == null ? ruleId : displayName(rule) + " (" + ruleId + ")")).color(Color.ofRgb(0xAAB2C6)));
        header.child(title);
        header.child(Components.label(Text.literal("Snapshot-backed diagnostics for one mob rule.")).color(Color.ofRgb(0xCBD5E1)).margins(Insets.top(2)));
        header.child(headerActions().margins(Insets.top(8)));
        return header;
    }

    private FlowLayout headerActions() {
        FlowLayout actions = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        actions.child(actionRow(
                actionButton("Back", () -> this.client.setScreen(parent)),
                actionButton("Refresh", this::requestSnapshot),
                actionButton("Rule History", () -> this.client.setScreen(new RuntimeEventsScreen(this, snapshot, zoneId, ruleId)))
        ));
        actions.child(actionRow(
                actionButton("Force Spawn", () -> confirm("Force Spawn", "Force spawn rule " + ruleId + "?", "FORCE_RULE_SPAWN:" + zoneId + ":" + ruleId)),
                actionButton("Force Primary", () -> confirm("Force Primary", "Force spawn only the primary entity for rule " + ruleId + "?", "FORCE_RULE_PRIMARY:" + zoneId + ":" + ruleId)),
                actionButton("Force Companions", () -> confirm("Force Companions", "Force spawn companions for rule " + ruleId + "?", "FORCE_RULE_COMPANIONS:" + zoneId + ":" + ruleId))
        ).margins(Insets.top(6)));
        actions.child(actionRow(
                actionButton("Reset Cooldown", () -> confirm("Reset Cooldown", "Reset cooldown state for rule " + ruleId + "?", "RESET_RULE_COOLDOWN:" + zoneId + ":" + ruleId)),
                actionButton("Kill Managed", () -> confirm("Kill Managed", "Discard managed mobs for rule " + ruleId + "?", "KILL_MANAGED:" + zoneId + ":" + ruleId)),
                actionButton("Clear Rule State", () -> confirm("Clear Rule State", "Remove runtime state for rule " + ruleId + "?", "CLEAR_RULE_STATE:" + zoneId + ":" + ruleId))
        ).margins(Insets.top(6)));
        return actions;
    }

    private void rebuildBody() {
        body.clearChildren();

        if (rule == null) {
            body.child(Components.label(Text.literal("Rule not present in the latest snapshot.")).color(Color.ofRgb(0xFCA5A5)));
            return;
        }

        body.child(sectionCard("Config", configRows()));
        body.child(sectionCard("Counters", counterRows()).margins(Insets.top(8)));
        body.child(sectionCard("Last Attempt", attemptRows()).margins(Insets.top(8)));
        body.child(sectionCard("Last Success", successRows()).margins(Insets.top(8)));
        body.child(sectionCard("Current Status", statusRows()).margins(Insets.top(8)));
        body.child(sectionCard("PACK ON_ACTIVATION", onActivationRows()).margins(Insets.top(8)));
        body.child(sectionCard("PACK TIMED", timedRows()).margins(Insets.top(8)));
        body.child(sectionCard("UNIQUE", uniqueRows()).margins(Insets.top(8)));
        body.child(sectionCard("Companions", companionRows()).margins(Insets.top(8)));
    }

    private FlowLayout configRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Zone", zoneId));
        rows.child(kv("Rule", ruleId));
        rows.child(kv("Name", displayName(rule)));
        rows.child(kv("Entity", valueOrDash(rule.entity)));
        rows.child(kv("Enabled", boolText(rule.enabled)));
        rows.child(kv("Spawn type", valueOrDash(rule.spawnType)));
        rows.child(kv("Refill mode", valueOrDash(rule.refillMode)));
        rows.child(kv("Max alive", String.valueOf(rule.maxAlive)));
        rows.child(kv("Spawn count", String.valueOf(rule.spawnCount)));
        rows.child(kv("Respawn seconds", String.valueOf(rule.respawnSeconds)));
        rows.child(kv("Chance", String.format("%.2f", rule.chance)));
        rows.child(kv("Cooldown start", valueOrDash(rule.cooldownStart)));
        rows.child(kv("Spawn when ready", boolText(rule.spawnWhenReady)));
        rows.child(kv("Failed retry seconds", String.valueOf(rule.failedSpawnRetrySeconds)));
        rows.child(kv("Despawn when inactive", boolText(rule.despawnWhenZoneInactive)));
        rows.child(kv("Announce on spawn", boolText(rule.announceOnSpawn)));
        rows.child(kv("Timed budget", rule.timedMaxSpawnsPerActivation == null ? "null" : String.valueOf(rule.timedMaxSpawnsPerActivation)));
        return rows;
    }

    private FlowLayout counterRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Primary alive", String.valueOf(rule.aliveCount)));
        rows.child(kv("Companions alive", String.valueOf(rule.encounterCompanionsAlive)));
        rows.child(kv("Known alive", String.valueOf(rule.knownAlive)));
        rows.child(kv("Total attempts", String.valueOf(rule.totalAttempts)));
        rows.child(kv("Total successes", String.valueOf(rule.totalSuccesses)));
        rows.child(kv("Total primary spawned", String.valueOf(rule.totalPrimarySpawned)));
        rows.child(kv("Total companions spawned", String.valueOf(rule.totalCompanionsSpawned)));
        return rows;
    }

    private FlowLayout attemptRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Last attempted at", TimeUtil.formatRelative(rule.lastAttemptAt)));
        rows.child(kv("Last attempt result", valueOrDash(rule.lastAttemptResult)));
        rows.child(kv("Last attempt reason", valueOrDash(rule.lastAttemptReason)));
        return rows;
    }

    private FlowLayout successRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Last success at", TimeUtil.formatRelative(rule.lastSuccessAt)));
        rows.child(kv("Last successful primary count", String.valueOf(rule.lastSuccessfulPrimaryCount)));
        rows.child(kv("Last successful companion count", String.valueOf(rule.lastSuccessfulCompanionCount)));
        return rows;
    }

    private FlowLayout statusRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Current status", valueOrDash(rule.currentStatus)));
        rows.child(kv("Next action", valueOrDash(rule.nextActionText)));
        rows.child(kv("Hint", valueOrDash(rule.hintText)));
        rows.child(kv("Warning", valueOrDash(rule.warningText)));
        return rows;
    }

    private FlowLayout onActivationRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Last activation spawn", TimeUtil.formatRelative(rule.lastActivationSpawnAt)));
        rows.child(kv("Last activation attempt id", String.valueOf(rule.lastOnActivationAttemptActivationId)));
        rows.child(kv("Current activation id", String.valueOf(currentActivationId())));
        rows.child(kv("Already attempted this activation", boolText(rule.lastOnActivationAttemptActivationId == currentActivationId())));
        rows.child(kv("Next available", TimeUtil.formatRelative(rule.nextAvailableAt)));
        return rows;
    }

    private FlowLayout timedRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Timed progress", TimeUtil.formatDuration(rule.timedProgressMillis)));
        rows.child(kv("Next timed spawn", TimeUtil.formatDuration(rule.nextTimedSpawnInMillis)));
        rows.child(kv("Spawned this activation", String.valueOf(rule.timedSpawnedThisActivation)));
        rows.child(kv("Budget exhausted", boolText(rule.timedBudgetExhausted)));
        rows.child(kv("Last budget reset", TimeUtil.formatRelative(rule.lastTimedBudgetResetAt)));
        if (rule.timedMaxSpawnsPerActivation != null && rule.timedMaxSpawnsPerActivation == -1) {
            rows.child(Components.label(Text.literal("Warning: unlimited timed budget can create farm risk.")).color(Color.ofRgb(0xFDE68A)).margins(Insets.top(2)));
        }
        return rows;
    }

    private FlowLayout uniqueRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Encounter active", boolText(rule.encounterActive)));
        rows.child(kv("Encounter started", TimeUtil.formatRelative(rule.encounterStartedAt)));
        rows.child(kv("Encounter cleared", TimeUtil.formatRelative(rule.encounterClearedAt)));
        rows.child(kv("Primary alive", String.valueOf(rule.encounterPrimaryAlive)));
        rows.child(kv("Companions alive", String.valueOf(rule.encounterCompanionsAlive)));
        rows.child(kv("Last spawned primary", String.valueOf(rule.lastEncounterPrimarySpawned)));
        rows.child(kv("Last spawned companions", String.valueOf(rule.lastEncounterCompanionsSpawned)));
        rows.child(kv("Next available", TimeUtil.formatRelative(rule.nextAvailableAt)));
        rows.child(kv("Next attempt", TimeUtil.formatRelative(rule.nextAttemptAt)));
        rows.child(kv("Last death", TimeUtil.formatRelative(rule.lastDeathAt)));
        if (rule.encounterCompanionsAlive > 0 && rule.encounterPrimaryAlive == 0) {
            rows.child(Components.label(Text.literal("Hint: waiting for companions to clear before cooldown starts.")).color(Color.ofRgb(0xAAB2C6)).margins(Insets.top(2)));
        }
        return rows;
    }

    private FlowLayout companionRows() {
        FlowLayout rows = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rows.child(kv("Companion count", String.valueOf(rule.lastSuccessfulCompanionCount)));
        rows.child(Components.label(Text.literal("Companions are tracked on the parent rule and spawn around the primary or encounter center.")).color(Color.ofRgb(0xCBD5E1)).margins(Insets.top(2)));
        return rows;
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
        for (Component component : components) {
            row.child(component);
        }
        return row;
    }

    private Component actionButton(String title, Runnable action) {
        return Components.button(Text.literal(title), button -> action.run()).sizing(Sizing.fixed(146), Sizing.content());
    }

    private Component kv(String label, String value) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(Components.label(Text.literal(label + ":")).color(Color.ofRgb(0xAAB2C6)).sizing(Sizing.fixed(200)));
        row.child(Components.label(Text.literal(valueOrDash(value))).color(Color.ofRgb(0xFFFFFF)));
        return row;
    }

    private String boolText(boolean value) {
        return value ? "yes" : "no";
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private long currentActivationId() {
        ZoneSummaryDto zone = snapshot == null ? null : findZone(snapshot);
        return zone == null ? 0L : zone.activationId;
    }

    private String displayName(RuleSummaryDto rule) {
        return rule.name == null || rule.name.isBlank() ? rule.id : rule.name;
    }

    private RuleSummaryDto findRule(RuntimeSnapshotDto snapshot) {
        if (snapshot == null || snapshot.zones == null) {
            return null;
        }
        for (ZoneSummaryDto zone : snapshot.zones) {
            if (!zoneId.equals(zone.id)) {
                continue;
            }
            for (RuleSummaryDto candidate : zone.rules) {
                if (ruleId.equals(candidate.id)) {
                    return candidate;
                }
            }
        }
        return null;
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
