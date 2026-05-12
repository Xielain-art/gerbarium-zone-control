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
        header.child(Components.label(Text.literal("Rule: " + rule.name)));
        header.child(Components.button(Text.literal("Back"), button -> {
            this.client.setScreen(parent);
        }).margins(Insets.left(20)));

        header.child(Components.button(Text.literal("Reset Cooldown"), button -> {
            MinecraftClient.getInstance().setScreen(new RuntimeConfirmActionScreen(
                Text.literal("Reset Cooldown"),
                Text.literal("Reset cooldown for rule: " + rule.name + "?"),
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

        // Basic Info
        details.child(Components.label(Text.literal("Basic Info")).margins(Insets.bottom(5)));
        details.child(createDetailRow("ID", rule.id, 0xAAAAAA));
        details.child(createDetailRow("Name", rule.name, 0xFFFFFF));
        details.child(createDetailRow("Entity", rule.entity, 0xFFFFFF));
        details.child(createDetailRow("Spawn Type", rule.spawnType, 0xFFFF00));
        details.child(createDetailRow("Refill Mode", rule.refillMode, 0xFFFF00));

        // Spawn Config
        details.child(Components.label(Text.literal("Spawn Configuration")).margins(Insets.vertical(5)));
        details.child(createDetailRow("Max Alive", String.valueOf(rule.maxAlive), 0xFFFFFF));
        details.child(createDetailRow("Spawn Count", String.valueOf(rule.spawnCount), 0xFFFFFF));
        details.child(createDetailRow("Respawn Seconds", String.valueOf(rule.respawnSeconds), 0xFFFFFF));
        details.child(createDetailRow("Chance", String.format("%.1f%%", rule.chance * 100), 0xFFFFFF));
        details.child(createDetailRow("Cooldown Start", rule.cooldownStart, 0xAAAAAA));
        
        if (rule.timedMaxSpawnsPerActivation != null) {
            String budgetText = rule.timedMaxSpawnsPerActivation == -1 ? "UNLIMITED" : String.valueOf(rule.timedMaxSpawnsPerActivation);
            details.child(createDetailRow("Timed Budget", budgetText, 0xFFFF00));
            details.child(createDetailRow("Spawned This Activation", String.valueOf(rule.timedSpawnedThisActivation), 0x00FFFF));
        }

        // Current Status
        details.child(Components.label(Text.literal("Current Status")).margins(Insets.vertical(5)));
        
        int aliveColor = rule.aliveCount >= rule.maxAlive ? 0xFF0000 : (rule.aliveCount > 0 ? 0x00FF00 : 0x888888);
        details.child(createDetailRow("Alive Count", rule.aliveCount + "/" + rule.maxAlive, aliveColor));
        
        long now = System.currentTimeMillis();
        boolean onCooldown = rule.nextAvailableAt > now;
        String cooldownText = onCooldown ? "WAITING (" + TimeUtil.formatRelative(rule.nextAvailableAt, now) + ")" : "READY";
        details.child(createDetailRow("Cooldown Status", cooldownText, onCooldown ? 0xFFFF00 : 0x00FF00));
        
        // Last Attempt
        details.child(Components.label(Text.literal("Last Attempt")).margins(Insets.vertical(5)));
        
        if (rule.lastAttemptAt > 0) {
            details.child(createDetailRow("Time", TimeUtil.formatRelative(rule.lastAttemptAt), 0xAAAAAA));
        } else {
            details.child(createDetailRow("Time", "Never", 0x888888));
        }
        
        int resultColor = "SUCCESS".equals(rule.lastAttemptResult) ? 0x00FF00 : 
                         ("NONE".equals(rule.lastAttemptResult) ? 0xAAAAAA : 0xFF0000);
        details.child(createDetailRow("Result", rule.lastAttemptResult, resultColor));
        
        if (rule.lastAttemptReason != null && !rule.lastAttemptReason.isEmpty()) {
            details.child(createDetailRow("Reason", rule.lastAttemptReason, 0xAAAAAA));
        }

        // Statistics
        details.child(Components.label(Text.literal("Statistics")).margins(Insets.vertical(5)));
        details.child(createDetailRow("Total Attempts", String.valueOf(rule.totalAttempts), 0xFFFFFF));
        details.child(createDetailRow("Total Successes", String.valueOf(rule.totalSuccesses), 0x00FF00));
        
        if (rule.totalAttempts > 0) {
            double successRate = (double) rule.totalSuccesses / rule.totalAttempts * 100;
            int rateColor = successRate >= 75 ? 0x00FF00 : (successRate >= 50 ? 0xFFFF00 : 0xFF0000);
            details.child(createDetailRow("Success Rate", String.format("%.1f%%", successRate), rateColor));
        } else {
            details.child(createDetailRow("Success Rate", "N/A", 0x888888));
        }
        
        if (rule.lastSuccessAt > 0) {
            details.child(createDetailRow("Last Success", TimeUtil.formatRelative(rule.lastSuccessAt), 0x00FF00));
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
