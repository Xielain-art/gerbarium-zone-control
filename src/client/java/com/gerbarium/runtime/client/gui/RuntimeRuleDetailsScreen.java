package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.RuleSummaryDto;
import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import com.gerbarium.runtime.client.dto.ZoneSummaryDto;
import com.gerbarium.runtime.network.GerbariumRuntimePackets;
import com.gerbarium.runtime.util.TimeUtil;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class RuntimeRuleDetailsScreen extends BaseOwoScreen<FlowLayout> implements RuntimeSnapshotView, RuntimeRuleDetailsView {
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
    public void updateRuleDetails(RuleSummaryDto rule) {
        this.rule = rule;
        if (this.body != null) {
            rebuildBody();
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

        this.body = RuntimeUi.col();
        this.body.surface(Surface.PANEL).padding(Insets.of(RuntimeUi.PAD_CARD));
        root.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(78), body).margins(Insets.top(RuntimeUi.GAP_SECTION)));

        requestRuleDetails();
        rebuildBody();
    }

    private FlowLayout buildHeader() {
        FlowLayout header = RuntimeUi.card();

        FlowLayout titleRow = RuntimeUi.row();
        titleRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        titleRow.child(RuntimeUi.title("Rule Details"));
        titleRow.child(RuntimeUi.label(rule == null ? ruleId : displayName(rule) + " (" + ruleId + ")", RuntimeUi.COLOR_LABEL).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        header.child(titleRow);

        header.child(RuntimeUi.dim("Live diagnostics for one mob rule.").margins(Insets.top(RuntimeUi.GAP_TINY)));
        header.child(buildActions().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        return header;
    }

    private FlowLayout buildActions() {
        FlowLayout col = RuntimeUi.col();

        // Navigation row
        FlowLayout navRow = RuntimeUi.row();
        navRow.child(RuntimeUi.button("Back", "Return to previous screen", () -> client.setScreen(parent)));
        navRow.child(RuntimeUi.button("Refresh", "Reload rule details from server", this::requestRuleDetails).margins(Insets.left(RuntimeUi.GAP_TINY)));
        navRow.child(RuntimeUi.button("Rule History", "View event history for this rule", () -> client.setScreen(new RuntimeEventsScreen(this, snapshot, zoneId, ruleId))).margins(Insets.left(RuntimeUi.GAP_TINY)));
        col.child(navRow);

        // Spawn actions row
        FlowLayout spawnRow = RuntimeUi.row();
        spawnRow.child(RuntimeUi.button("Force Spawn", "Spawn primary + companions", () -> confirm("Force Spawn", "Force spawn rule " + ruleId + "?", "FORCE_RULE_SPAWN:" + zoneId + ":" + ruleId)));
        spawnRow.child(RuntimeUi.button("Force Primary", "Spawn only primary entity", () -> confirm("Force Primary", "Spawn only the primary entity for rule " + ruleId + "?", "FORCE_RULE_PRIMARY:" + zoneId + ":" + ruleId)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        spawnRow.child(RuntimeUi.button("Force Companions", "Spawn only companions", () -> confirm("Force Companions", "Spawn companions for rule " + ruleId + "?", "FORCE_RULE_COMPANIONS:" + zoneId + ":" + ruleId)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        col.child(spawnRow.margins(Insets.top(RuntimeUi.GAP_TINY)));

        // State management row
        FlowLayout stateRow = RuntimeUi.row();
        stateRow.child(RuntimeUi.button("Reset Timer", "Clear cooldown and allow immediate spawn attempt", () -> confirm("Reset Timer", "Reset timer for rule " + ruleId + "?", "RESET_RULE_TIMER:" + zoneId + ":" + ruleId)));
        stateRow.child(RuntimeUi.button("Reset Cooldown", "Reset cooldown state", () -> confirm("Reset Cooldown", "Reset cooldown state for rule " + ruleId + "?", "RESET_RULE_COOLDOWN:" + zoneId + ":" + ruleId)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        stateRow.child(RuntimeUi.button("Kill Managed", "Remove all mobs tracked by this rule", () -> confirm("Kill Managed", "Discard managed mobs for rule " + ruleId + "?", "KILL_MANAGED:" + zoneId + ":" + ruleId)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        stateRow.child(RuntimeUi.button("Clear Rule State", "Delete runtime state for this rule", () -> confirm("Clear Rule State", "Remove runtime state for rule " + ruleId + "?", "CLEAR_RULE_STATE:" + zoneId + ":" + ruleId)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        col.child(stateRow.margins(Insets.top(RuntimeUi.GAP_TINY)));

        return col;
    }

    private void rebuildBody() {
        body.clearChildren();
        if (rule == null) {
            body.child(RuntimeUi.warn("Rule not present in the latest snapshot."));
            return;
        }

        body.child(buildConfigSection());
        body.child(buildBoundarySection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        body.child(buildCountersSection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        body.child(buildAttemptSection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        body.child(buildSuccessSection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        body.child(buildStatusSection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        body.child(buildTimerSection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        body.child(buildAfterDeathSection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        body.child(buildUniqueSection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        body.child(buildCompanionSection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
    }

    private FlowLayout buildConfigSection() {
        FlowLayout sec = RuntimeUi.section("Config");
        sec.child(RuntimeUi.kv("Zone", zoneId));
        sec.child(RuntimeUi.kv("Rule", ruleId));
        sec.child(RuntimeUi.kv("Name", displayName(rule)));
        sec.child(RuntimeUi.kv("Entity", RuntimeUi.valueOrDash(rule.entity)));
        sec.child(RuntimeUi.kv("Enabled", RuntimeUi.boolText(rule.enabled)));
        sec.child(RuntimeUi.kv("Spawn trigger", RuntimeUi.valueOrDash(rule.spawnTrigger)));
        sec.child(RuntimeUi.kv("Spawn mode", RuntimeUi.valueOrDash(rule.spawnMode)));
        sec.child(RuntimeUi.kv("Boundary mode", RuntimeUi.valueOrDash(rule.boundaryMode)));
        sec.child(RuntimeUi.kv("Boundary max outside seconds", String.valueOf(rule.boundaryMaxOutsideSeconds)));
        sec.child(RuntimeUi.kv("Boundary check interval ticks", String.valueOf(rule.boundaryCheckIntervalTicks)));
        sec.child(RuntimeUi.kv("Boundary teleport back", RuntimeUi.boolText(rule.boundaryTeleportBack)));
        sec.child(RuntimeUi.kv("Max alive", String.valueOf(rule.maxAlive)));
        sec.child(RuntimeUi.kv("Spawn count", String.valueOf(rule.spawnCount)));
        sec.child(RuntimeUi.kv("Respawn seconds", String.valueOf(rule.respawnSeconds)));
        sec.child(RuntimeUi.kv("Retry seconds", String.valueOf(rule.retrySeconds)));
        sec.child(RuntimeUi.kv("After death delay seconds", String.valueOf(rule.afterDeathDelaySeconds)));
        sec.child(RuntimeUi.kv("Chance", String.format("%.2f", rule.chance)));
        sec.child(RuntimeUi.kv("Cooldown start", RuntimeUi.valueOrDash(rule.cooldownStart)));
        sec.child(RuntimeUi.kv("Spawn when ready", RuntimeUi.boolText(rule.spawnWhenReady)));
        sec.child(RuntimeUi.kv("Despawn when inactive", RuntimeUi.boolText(rule.despawnWhenZoneInactive)));
        sec.child(RuntimeUi.kv("Announce on spawn", RuntimeUi.boolText(rule.announceOnSpawn)));
        sec.child(RuntimeUi.kv("Respawn after death", RuntimeUi.boolText(rule.respawnAfterDeath)));
        sec.child(RuntimeUi.kv("Respawn after despawn", RuntimeUi.boolText(rule.respawnAfterDespawn)));
        sec.child(RuntimeUi.kv("Fixed X", rule.fixedX == null ? "-" : String.valueOf(rule.fixedX)));
        sec.child(RuntimeUi.kv("Fixed Y", rule.fixedY == null ? "-" : String.valueOf(rule.fixedY)));
        sec.child(RuntimeUi.kv("Fixed Z", rule.fixedZ == null ? "-" : String.valueOf(rule.fixedZ)));
        sec.child(RuntimeUi.kv("Allow small room", RuntimeUi.boolText(rule.allowSmallRoom)));
        sec.child(RuntimeUi.kv("Spread spawns", RuntimeUi.boolText(rule.spreadSpawns)));
        sec.child(RuntimeUi.kv("Min spawn distance", String.valueOf(rule.minDistanceBetweenSpawns)));
        sec.child(RuntimeUi.kv("Position attempts", String.valueOf(rule.positionAttempts)));
        sec.child(RuntimeUi.kv("Require player nearby", RuntimeUi.boolText(rule.requirePlayerNearby)));
        sec.child(RuntimeUi.kv("Player activation range", String.valueOf(rule.playerActivationRange)));
        sec.child(RuntimeUi.kv("Require chunk loaded", RuntimeUi.boolText(rule.requireChunkLoaded)));
        sec.child(RuntimeUi.kv("Allow force load", RuntimeUi.boolText(rule.allowForceLoad)));
        return sec;
    }

    private FlowLayout buildBoundarySection() {
        FlowLayout sec = RuntimeUi.section("Boundary Control");
        sec.child(RuntimeUi.kv("Mode", RuntimeUi.valueOrDash(rule.boundaryMode)));
        sec.child(RuntimeUi.kv("Status", RuntimeUi.valueOrDash(rule.boundaryStatus)));
        sec.child(RuntimeUi.kv("Outside tracked", String.valueOf(rule.boundaryOutsideCount)));
        sec.child(RuntimeUi.kv("Last boundary action", TimeUtil.formatRelative(rule.lastBoundaryActionAt)));
        sec.child(RuntimeUi.kv("Last boundary action type", RuntimeUi.valueOrDash(rule.lastBoundaryActionType)));
        sec.child(RuntimeUi.kv("Boundary hint", RuntimeUi.valueOrDash(rule.boundaryHint)));
        sec.child(RuntimeUi.dim(boundaryExplanation()).margins(Insets.top(RuntimeUi.GAP_TINY)));
        if (rule.boundaryHint != null && !rule.boundaryHint.isBlank()) {
            sec.child(RuntimeUi.label(rule.boundaryHint, RuntimeUi.COLOR_HIGHLIGHT).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
        if ("REMOVE_OUTSIDE".equalsIgnoreCase(rule.boundaryMode)) {
            sec.child(RuntimeUi.label("Removed mobs do not count as normal deaths.", RuntimeUi.COLOR_HIGHLIGHT).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
        return sec;
    }

    private FlowLayout buildCountersSection() {
        FlowLayout sec = RuntimeUi.section("Counters");
        sec.child(RuntimeUi.kv("Normal primary alive", String.valueOf(rule.normalPrimaryAlive)));
        sec.child(RuntimeUi.kv("Forced primary alive", String.valueOf(rule.forcedPrimaryAlive)));
        sec.child(RuntimeUi.kv("Normal companions alive", String.valueOf(rule.normalCompanionAlive)));
        sec.child(RuntimeUi.kv("Forced companions alive", String.valueOf(rule.forcedCompanionAlive)));
        sec.child(RuntimeUi.kv("Known alive", String.valueOf(rule.knownAlive)));
        sec.child(RuntimeUi.kv("Total attempts", String.valueOf(rule.totalAttempts)));
        sec.child(RuntimeUi.kv("Total successes", String.valueOf(rule.totalSuccesses)));
        sec.child(RuntimeUi.kv("Total primary spawned", String.valueOf(rule.totalPrimarySpawned)));
        sec.child(RuntimeUi.kv("Total companions spawned", String.valueOf(rule.totalCompanionsSpawned)));
        return sec;
    }

    private FlowLayout buildAttemptSection() {
        FlowLayout sec = RuntimeUi.section("Last Attempt");
        sec.child(RuntimeUi.kv("Last attempted at", TimeUtil.formatRelative(rule.lastAttemptAt)));
        sec.child(RuntimeUi.kv("Last attempt result", RuntimeUi.valueOrDash(rule.lastAttemptResult)));
        sec.child(RuntimeUi.kv("Last attempt reason", RuntimeUi.valueOrDash(rule.lastAttemptReason)));
        return sec;
    }

    private FlowLayout buildSuccessSection() {
        FlowLayout sec = RuntimeUi.section("Last Success");
        sec.child(RuntimeUi.kv("Last success at", TimeUtil.formatRelative(rule.lastSuccessAt)));
        sec.child(RuntimeUi.kv("Last successful primary count", String.valueOf(rule.lastSuccessfulPrimaryCount)));
        sec.child(RuntimeUi.kv("Last successful companion count", String.valueOf(rule.lastSuccessfulCompanionCount)));
        return sec;
    }

    private FlowLayout buildStatusSection() {
        FlowLayout sec = RuntimeUi.section("Current Status");
        sec.child(RuntimeUi.kv("Current status", RuntimeUi.valueOrDash(rule.currentStatus)));
        sec.child(RuntimeUi.kv("Next action", RuntimeUi.valueOrDash(rule.nextActionText)));

        // Show countdown prominently if applicable
        long now = System.currentTimeMillis();
        long nextAttempt = rule.nextAllowedAttemptTimeMillis;
        if (nextAttempt > now) {
            long remainingSeconds = (nextAttempt - now) / 1000L;
            sec.child(RuntimeUi.kv("Next attempt in", remainingSeconds + "s"));
        }
        if (rule.hasPendingAfterDeathRespawn && rule.pendingAfterDeathRespawnTimeMillis > now) {
            long remainingSeconds = (rule.pendingAfterDeathRespawnTimeMillis - now) / 1000L;
            sec.child(RuntimeUi.kv("After-death respawn in", remainingSeconds + "s"));
        }

        sec.child(RuntimeUi.kv("Hint", RuntimeUi.valueOrDash(rule.hintText)));
        sec.child(RuntimeUi.kv("Warning", RuntimeUi.valueOrDash(rule.warningText)));
        sec.child(RuntimeUi.kv("Boundary status", RuntimeUi.valueOrDash(rule.boundaryStatus)));
        return sec;
    }

    private FlowLayout buildTimerSection() {
        FlowLayout sec = RuntimeUi.section("Timer State");
        sec.child(RuntimeUi.kv("Next allowed attempt", TimeUtil.formatRelative(rule.nextAllowedAttemptTimeMillis)));
        sec.child(RuntimeUi.kv("Cooldown remaining", TimeUtil.formatDuration(Math.max(0, rule.nextAllowedAttemptTimeMillis - System.currentTimeMillis()))));
        sec.child(RuntimeUi.kv("Last activation spawn", TimeUtil.formatRelative(rule.lastActivationSpawnAt)));
        sec.child(RuntimeUi.kv("Last attempt", TimeUtil.formatRelative(rule.lastAttemptAt)));
        sec.child(RuntimeUi.kv("Last attempt result", RuntimeUi.valueOrDash(rule.lastAttemptResult)));
        sec.child(RuntimeUi.kv("Last attempt reason", RuntimeUi.valueOrDash(rule.lastAttemptReason)));
        sec.child(RuntimeUi.kv("Last position search stats", RuntimeUi.valueOrDash(rule.lastPositionSearchStats)));
        return sec;
    }

    private FlowLayout buildAfterDeathSection() {
        FlowLayout sec = RuntimeUi.section("After-Death Respawn");
        sec.child(RuntimeUi.kv("Has pending respawn", RuntimeUi.boolText(rule.hasPendingAfterDeathRespawn)));
        sec.child(RuntimeUi.kv("Pending respawn time", TimeUtil.formatRelative(rule.pendingAfterDeathRespawnTimeMillis)));
        sec.child(RuntimeUi.kv("Death count", String.valueOf(rule.deathCount)));
        sec.child(RuntimeUi.kv("Last death", TimeUtil.formatRelative(rule.lastDeathAt)));
        return sec;
    }

    private FlowLayout buildUniqueSection() {
        FlowLayout sec = RuntimeUi.section("UNIQUE");
        sec.child(RuntimeUi.kv("Encounter active", RuntimeUi.boolText(rule.encounterActive)));
        sec.child(RuntimeUi.kv("Encounter started", TimeUtil.formatRelative(rule.encounterStartedAt)));
        sec.child(RuntimeUi.kv("Encounter cleared", TimeUtil.formatRelative(rule.encounterClearedAt)));
        sec.child(RuntimeUi.kv("Primary alive", String.valueOf(rule.encounterPrimaryAlive)));
        sec.child(RuntimeUi.kv("Companions alive", String.valueOf(rule.encounterCompanionsAlive)));
        sec.child(RuntimeUi.kv("Last spawned primary", String.valueOf(rule.lastEncounterPrimarySpawned)));
        sec.child(RuntimeUi.kv("Last spawned companions", String.valueOf(rule.lastEncounterCompanionsSpawned)));
        sec.child(RuntimeUi.kv("Next available", TimeUtil.formatRelative(rule.nextAvailableAt)));
        sec.child(RuntimeUi.kv("Next attempt", TimeUtil.formatRelative(rule.nextAttemptAt)));
        sec.child(RuntimeUi.kv("Last death", TimeUtil.formatRelative(rule.lastDeathAt)));
        if (rule.encounterCompanionsAlive > 0 && rule.encounterPrimaryAlive == 0) {
            sec.child(RuntimeUi.muted("Hint: waiting for companions to clear before cooldown starts.").margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
        return sec;
    }

    private FlowLayout buildCompanionSection() {
        FlowLayout sec = RuntimeUi.section("Companions");
        sec.child(RuntimeUi.kv("Companion count", String.valueOf(rule.lastSuccessfulCompanionCount)));
        sec.child(RuntimeUi.dim("Companions are tracked on the parent rule and spawn around the primary or encounter center.").margins(Insets.top(RuntimeUi.GAP_TINY)));
        return sec;
    }

    private long currentActivationId() {
        ZoneSummaryDto zone = snapshot == null ? null : findZone(snapshot);
        return zone == null ? 0L : zone.activationId;
    }

    private String displayName(RuleSummaryDto rule) {
        return rule.name == null || rule.name.isBlank() ? rule.id : rule.name;
    }

    private String boundaryExplanation() {
        return switch (RuntimeUi.valueOrDash(rule.boundaryMode)) {
            case "NONE" -> "Mobs may leave the zone.";
            case "LEASH" -> "Mobs are returned after staying outside too long.";
            case "TELEPORT_BACK" -> "Mobs are teleported back inside the zone.";
            case "REMOVE_OUTSIDE" -> "Mobs are removed if they stay outside too long.";
            default -> RuntimeUi.valueOrDash(rule.boundaryHint);
        };
    }

    private RuleSummaryDto findRule(RuntimeSnapshotDto snapshot) {
        if (snapshot == null || snapshot.zones == null) return null;
        for (ZoneSummaryDto zone : snapshot.zones) {
            if (!zoneId.equals(zone.id)) continue;
            for (RuleSummaryDto candidate : zone.rules) {
                if (ruleId.equals(candidate.id)) return candidate;
            }
        }
        return null;
    }

    private ZoneSummaryDto findZone(RuntimeSnapshotDto snapshot) {
        if (snapshot == null || snapshot.zones == null) return null;
        for (ZoneSummaryDto candidate : snapshot.zones) {
            if (zoneId.equals(candidate.id)) return candidate;
        }
        return null;
    }

    private void confirm(String title, String description, String action) {
        MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(this, Text.literal(title), Text.literal(description), () -> sendAction(action)));
    }

    private void sendAction(String action) {
        String[] parts = action.split(":", 3);
        var buf = PacketByteBufs.create();
        buf.writeString(parts[0]);
        buf.writeString(zoneId);
        buf.writeString(ruleId);
        ClientPlayNetworking.send(GerbariumRuntimePackets.RUN_RULE_ACTION, buf);
    }

    private void requestRuleDetails() {
        var buf = PacketByteBufs.create();
        buf.writeString(zoneId);
        buf.writeString(ruleId);
        ClientPlayNetworking.send(GerbariumRuntimePackets.REQUEST_RULE_DETAILS, buf);
    }
}
