package com.gerbarium.runtime.client.gui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class RuntimeConfirmActionScreen extends BaseOwoScreen<FlowLayout> {
    private final Text titleText;
    private final Text descriptionText;
    private final Runnable onConfirm;

    public RuntimeConfirmActionScreen(Text titleText, Text descriptionText, Runnable onConfirm) {
        this.titleText = titleText;
        this.descriptionText = descriptionText;
        this.onConfirm = onConfirm;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.VANILLA_TRANSLUCENT)
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER)
                .padding(Insets.of(18));

        FlowLayout card = Containers.verticalFlow(Sizing.fixed(380), Sizing.content());
        card.surface(Surface.PANEL).padding(Insets.of(14));

        card.child(Components.label(titleText).color(Color.ofRgb(0xFDE68A)));
        card.child(Components.label(descriptionText).color(Color.ofRgb(0xE5E7EB)).margins(Insets.top(6)));
        card.child(Components.label(Text.literal("This action is irreversible for the current runtime state.")).color(Color.ofRgb(0xFCA5A5)).margins(Insets.top(6)));

        FlowLayout buttons = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        buttons.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        buttons.child(Components.button(Text.literal("Confirm"), button -> {
            onConfirm.run();
            this.close();
        }).sizing(Sizing.fixed(120), Sizing.content()));
        buttons.child(Components.button(Text.literal("Cancel"), button -> this.close()).sizing(Sizing.fixed(120), Sizing.content()).margins(Insets.left(8)));
        card.child(buttons.margins(Insets.top(12)));

        rootComponent.child(card);
    }
}
