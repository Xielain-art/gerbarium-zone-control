package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.RuleSummaryDto;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import com.gerbarium.runtime.util.TimeUtil;

public class RuntimeRuleDetailsScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private final RuleSummaryDto rule;

    public RuntimeRuleDetailsScreen(Screen parent, RuleSummaryDto rule) {
        this.parent = parent;
        this.rule = rule;
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
        header.child(Components.label(Text.literal("Rule Details: " + rule.name)));
        header.child(Components.button(Text.literal("Back"), button -> {
            this.client.setScreen(parent);
        }).margins(Insets.left(20)));

        header.child(Components.button(Text.literal("Reset Cooldown"), button -> {
            MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(
                Text.literal("Reset Cooldown"),
                Text.literal("This will reset the cooldown for this rule. Proceed?"),
                () -> {
                    net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                    buf.writeString("RESET_RULE_COOLDOWN");
                    buf.writeString(rule.zoneId);
                    buf.writeString(rule.id);
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(com.gerbarium.runtime.network.GerbariumRuntimePackets.RUN_GLOBAL_ACTION, buf);
                }
            ));
        }).margins(Insets.left(10)));
        
        rootComponent.child(header.margins(Insets.bottom(15)));

        // Details Container
        FlowLayout details = Containers.verticalFlow(Sizing.fill(90), Sizing.content());
        details.surface(Surface.PANEL).padding(Insets.of(10));

        // Config Details Section
        details.child(Components.label(Text.literal("Configuration")).margins(Insets.bottom(5)));
        details.child(createDetailRow("ID", rule.id, 0xAAAAAA));
        details.child(createDetailRow("Entity", rule.entity, 0xFFFFFF));
        details.child(createDetailRow("Spawn Type", rule.spawnType, 0xFFFFFF));
        details.child(createDetailRow("Max Alive", String.valueOf(rule.maxAlive), 0xFFFFFF));
        details.child(createDetailRow("Spawn Count", String.valueOf(rule.spawnCount), 0xFFFFFF));
        details.child(createDetailRow("Respawn Seconds", String.valueOf(rule.respawnSeconds), 0xFFFFFF));
        details.child(createDetailRow("Chance", String.format("%.2f", rule.chance), 0xFFFFFF));

        // Cooldown Status Section
        details.child(Components.label(Text.literal("Cooldown Status")).margins(Insets.vertical(5)));
        long now = System.currentTimeMillis();
        boolean onCooldown = rule.nextAvailableAt > now;
        String cooldownText = onCooldown ? "WAITING (" + TimeUtil.formatRelative(rule.nextAvailableAt, now) + ")" : "READY";
        details.child(createDetailRow("Status", cooldownText, onCooldown ? 0xFFFF00 : 0x00FF00));
        details.child(createDetailRow("Last Result", rule.lastAttemptResult, "SUCCESS".equals(rule.lastAttemptResult) ? 0x00FF00 : ("NONE".equals(rule.lastAttemptResult) ? 0xAAAAAA : 0xFF0000)));
        details.child(createDetailRow("Last Reason", rule.lastAttemptReason, 0xAAAAAA));

        // Statistics Section
        details.child(Components.label(Text.literal("Statistics")).margins(Insets.vertical(5)));
        details.child(createDetailRow("Total Attempts", String.valueOf(rule.totalAttempts), 0xFFFFFF));
        details.child(createDetailRow("Total Successes", String.valueOf(rule.totalSuccesses), 0xFFFFFF));
        double successRate = rule.totalAttempts > 0 ? (double) rule.totalSuccesses / rule.totalAttempts * 100 : 0;
        details.child(createDetailRow("Success Rate", String.format("%.1f%%", successRate), 0xFFFFFF));

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