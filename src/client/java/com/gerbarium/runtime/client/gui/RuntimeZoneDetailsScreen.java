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

public class RuntimeZoneDetailsScreen extends BaseOwoScreen<FlowLayout> implements RuntimeSnapshotView, RuntimeZoneDetailsView {
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
    public void updateZoneDetails(ZoneSummaryDto zone) {
        this.zone = zone;
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
        root.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(78), body).margins(Insets.top(RuntimeUi.GAP_SECTION)));
        requestZoneDetails();
        rebuildBody();
    }

    private FlowLayout buildHeader() {
        FlowLayout header = RuntimeUi.card();
        FlowLayout titleRow = RuntimeUi.row();
        titleRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        titleRow.child(RuntimeUi.title("Zone Details"));
        titleRow.child(RuntimeUi.label(zone == null ? zoneId : displayName(zone) + " (" + zoneId + ")", RuntimeUi.COLOR_LABEL).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        header.child(titleRow);
        header.child(RuntimeUi.dim("Live runtime overview for a single zone.").margins(Insets.top(RuntimeUi.GAP_TINY)));
        header.child(buildActions().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        return header;
    }

    private FlowLayout buildActions() {
        FlowLayout col = RuntimeUi.col();
        FlowLayout row1 = RuntimeUi.row();
        row1.child(RuntimeUi.button("Back", () -> client.setScreen(parent)));
        row1.child(RuntimeUi.button("Refresh", this::requestZoneDetails).margins(Insets.left(RuntimeUi.GAP_TINY)));
        row1.child(RuntimeUi.button("Events", () -> client.setScreen(new RuntimeEventsScreen(this, snapshot, zoneId, null))).margins(Insets.left(RuntimeUi.GAP_TINY)));
        col.child(row1);

        FlowLayout row2 = RuntimeUi.row();
        row2.child(RuntimeUi.button("Force Activate", () -> confirm("Force Activate", "Force activate zone " + zoneId + "?", "FORCE_ACTIVATE:" + zoneId)));
        row2.child(RuntimeUi.button("Force Deactivate", () -> confirm("Force Deactivate", "Force deactivate zone " + zoneId + "?", "FORCE_DEACTIVATE:" + zoneId)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        row2.child(RuntimeUi.button("Force Spawn", () -> confirm("Force Spawn", "Force spawn all rules in zone " + zoneId + "?", "FORCE_SPAWN:" + zoneId)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        col.child(row2.margins(Insets.top(RuntimeUi.GAP_TINY)));
        return col;
    }

    private void rebuildBody() {
        body.clearChildren();
        if (zone == null) {
            body.child(RuntimeUi.warn("Zone not present in the latest snapshot."));
            return;
        }
        body.child(buildRuntimeSection());
        body.child(buildConfigSection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        body.child(buildRulesSection().margins(Insets.top(RuntimeUi.GAP_SECTION)));
    }

    private FlowLayout buildRuntimeSection() {
        FlowLayout sec = RuntimeUi.section("Runtime");
        sec.child(RuntimeUi.kv("Enabled", RuntimeUi.boolText(zone.enabled)));
        sec.child(RuntimeUi.kv("Active", RuntimeUi.boolText(zone.active)));
        sec.child(RuntimeUi.kv("Status", RuntimeUi.valueOrDash(zone.currentStatus)));
        sec.child(RuntimeUi.kv("Pending activation", RuntimeUi.boolText(zone.pendingActivation)));
        sec.child(RuntimeUi.kv("Activation ID", String.valueOf(zone.activationId)));
        sec.child(RuntimeUi.kv("Last activated", TimeUtil.formatRelative(zone.lastActivatedAt)));
        sec.child(RuntimeUi.kv("Last deactivated", TimeUtil.formatRelative(zone.lastDeactivatedAt)));
        sec.child(RuntimeUi.kv("Last player seen", TimeUtil.formatRelative(zone.lastPlayerSeenAt)));
        sec.child(RuntimeUi.kv("Nearby players", String.valueOf(zone.nearbyPlayers)));
        sec.child(RuntimeUi.kv("Mobs count", String.valueOf(zone.mobsCount)));
        sec.child(RuntimeUi.kv("Primary alive", String.valueOf(zone.primaryAliveTotal)));
        sec.child(RuntimeUi.kv("Companions alive", String.valueOf(zone.companionsAliveTotal)));
        if (zone.warningText != null && !zone.warningText.isBlank()) {
            sec.child(RuntimeUi.warn(zone.warningText).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
        if (zone.hintText != null && !zone.hintText.isBlank()) {
            sec.child(RuntimeUi.label("Hint: " + zone.hintText, RuntimeUi.COLOR_LABEL).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
        return sec;
    }

    private FlowLayout buildConfigSection() {
        FlowLayout sec = RuntimeUi.section("Config");
        sec.child(RuntimeUi.kv("Zone ID", RuntimeUi.valueOrDash(zone.id)));
        sec.child(RuntimeUi.kv("Name", RuntimeUi.valueOrDash(zone.name)));
        sec.child(RuntimeUi.kv("Dimension", RuntimeUi.valueOrDash(zone.dimension)));
        sec.child(RuntimeUi.kv("Priority", String.valueOf(zone.priority)));
        sec.child(RuntimeUi.kv("Boundary control", RuntimeUi.boolText(zone.boundaryControlEnabled)));
        sec.child(RuntimeUi.kv("Boundary scan padding", String.valueOf(zone.boundaryScanPadding)));
        sec.child(RuntimeUi.kv("Activation range", String.valueOf(zone.activationRange)));
        sec.child(RuntimeUi.kv("Deactivate after", zone.deactivateAfterSeconds + "s"));
        sec.child(RuntimeUi.kv("First spawn delay", zone.firstSpawnDelaySeconds + "s"));
        sec.child(RuntimeUi.kv("Reactivation cooldown", zone.reactivationCooldownSeconds + "s"));
        sec.child(RuntimeUi.kv("Spawn distance", zone.spawnMinDistanceFromPlayer + "-" + zone.spawnMaxDistanceFromPlayer));
        sec.child(RuntimeUi.kv("Position attempts", String.valueOf(zone.spawnMaxPositionAttempts)));
        sec.child(RuntimeUi.kv("Require loaded chunk", RuntimeUi.boolText(zone.spawnRequireLoadedChunk)));
        sec.child(RuntimeUi.kv("Respect spawn rules", RuntimeUi.boolText(zone.spawnRespectVanillaSpawnRules)));
        sec.child(RuntimeUi.kv("Allow non-solid ground", RuntimeUi.boolText(zone.spawnAllowNonSolidGround)));
        sec.child(RuntimeUi.kv("Bounds min", zone.minX + ", " + zone.minY + ", " + zone.minZ));
        sec.child(RuntimeUi.kv("Bounds max", zone.maxX + ", " + zone.maxY + ", " + zone.maxZ));
        sec.child(RuntimeUi.kv("State file exists", RuntimeUi.boolText(zone.stateFileExists)));
        sec.child(RuntimeUi.kv("Dirty", RuntimeUi.boolText(zone.dirty)));
        sec.child(RuntimeUi.kv("Rules", String.valueOf(zone.totalRules)));
        return sec;
    }

    private FlowLayout buildRulesSection() {
        FlowLayout sec = RuntimeUi.section("Rules");
        if (zone.rules == null || zone.rules.isEmpty()) {
            sec.child(RuntimeUi.muted("No rules configured.").margins(Insets.top(RuntimeUi.GAP_TINY)));
            return sec;
        }
        for (RuleSummaryDto rule : zone.rules) {
            sec.child(buildRuleCard(rule).margins(Insets.top(RuntimeUi.GAP_ITEM)));
        }
        return sec;
    }

    private FlowLayout buildRuleCard(RuleSummaryDto rule) {
        FlowLayout card = RuntimeUi.card();
        FlowLayout header = RuntimeUi.row();
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.child(RuntimeUi.label(RuntimeUi.valueOrDash(rule.name != null && !rule.name.isBlank() ? rule.name : rule.id), RuntimeUi.COLOR_TEXT));
        header.child(RuntimeUi.label(" [" + RuntimeUi.valueOrDash(rule.currentStatus) + "]", statusColor(rule)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        header.child(RuntimeUi.label(rule.enabled ? " ENABLED" : " DISABLED", rule.enabled ? RuntimeUi.COLOR_OK_LIGHT : RuntimeUi.COLOR_WARN).margins(Insets.left(RuntimeUi.GAP_TINY)));
        card.child(header);

        FlowLayout meta = RuntimeUi.row();
        meta.child(RuntimeUi.label("Entity: " + RuntimeUi.valueOrDash(rule.entity), RuntimeUi.COLOR_SUBTLE));
        meta.child(RuntimeUi.label("Type: " + RuntimeUi.valueOrDash(rule.spawnType), RuntimeUi.COLOR_DIM).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        meta.child(RuntimeUi.label("Alive: " + rule.aliveCount + "/" + rule.maxAlive, RuntimeUi.COLOR_OK_LIGHT).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        card.child(meta.margins(Insets.top(RuntimeUi.GAP_TINY)));

        if (rule.warningText != null && !rule.warningText.isBlank()) {
            card.child(RuntimeUi.warn(rule.warningText).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }

        FlowLayout btns = RuntimeUi.row();
        btns.child(RuntimeUi.button("Details", () -> client.setScreen(new RuntimeRuleDetailsScreen(this, rule))));
        btns.child(RuntimeUi.button("History", () -> client.setScreen(new RuntimeEventsScreen(this, snapshot, zoneId, rule.id))).margins(Insets.left(RuntimeUi.GAP_TINY)));
        btns.child(RuntimeUi.button("Spawn", () -> confirm("Force Spawn", "Force spawn rule " + rule.id + "?", "FORCE_RULE_SPAWN:" + rule.zoneId + ":" + rule.id)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        btns.child(RuntimeUi.button("Primary", () -> confirm("Force Primary", "Spawn primary for rule " + rule.id + "?", "FORCE_RULE_PRIMARY:" + rule.zoneId + ":" + rule.id)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        btns.child(RuntimeUi.button("Companions", () -> confirm("Force Companions", "Spawn companions for rule " + rule.id + "?", "FORCE_RULE_COMPANIONS:" + rule.zoneId + ":" + rule.id)).margins(Insets.left(RuntimeUi.GAP_TINY)));
        card.child(btns.margins(Insets.top(RuntimeUi.GAP_ITEM)));
        return card;
    }

    private int statusColor(RuleSummaryDto rule) {
        if (!rule.enabled) return RuntimeUi.COLOR_WARN;
        if (rule.warningText != null && !rule.warningText.isBlank()) return RuntimeUi.COLOR_HIGHLIGHT;
        return switch (RuntimeUi.valueOrDash(rule.currentStatus)) {
            case "READY" -> RuntimeUi.COLOR_OK_LIGHT;
            case "INACTIVE", "DISABLED" -> RuntimeUi.COLOR_SUBTLE;
            case "COOLDOWN", "TIMED_WAIT", "WAITING_FOR_COMPANIONS_CLEAR" -> RuntimeUi.COLOR_HIGHLIGHT;
            default -> RuntimeUi.COLOR_DIM;
        };
    }

    private ZoneSummaryDto findZone(RuntimeSnapshotDto snapshot) {
        if (snapshot == null || snapshot.zones == null) return null;
        for (ZoneSummaryDto candidate : snapshot.zones) {
            if (zoneId.equals(candidate.id)) return candidate;
        }
        return null;
    }

    private String displayName(ZoneSummaryDto zone) {
        return zone.name == null || zone.name.isBlank() ? zone.id : zone.name;
    }

    private void confirm(String title, String description, String action) {
        MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(this, Text.literal(title), Text.literal(description), () -> sendAction(action)));
    }

    private void sendAction(String action) {
        String[] parts = action.split(":", 2);
        var buf = PacketByteBufs.create();
        buf.writeString(parts[0]);
        buf.writeString(zoneId);
        ClientPlayNetworking.send(GerbariumRuntimePackets.RUN_ZONE_ACTION, buf);
    }

    private void requestZoneDetails() {
        var buf = PacketByteBufs.create();
        buf.writeString(zoneId);
        ClientPlayNetworking.send(GerbariumRuntimePackets.REQUEST_ZONE_DETAILS, buf);
    }
}
