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
    protected @NotNull @org.jetbrains.annotations.NotNull FlowLayout createAdapter() {
        return Containers.verticalFlow(Sizing.content(), Sizing.content());
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.PANEL)
                .padding(Insets.of(10))
                .alignment(VerticalAlignment.CENTER, HorizontalAlignment.CENTER);

        rootComponent.child(Components.label(titleText).fontWeight(700).margins(Insets.bottom(5)));
        rootComponent.child(Components.label(descriptionText).margins(Insets.bottom(10)));

        FlowLayout buttons = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        buttons.child(Components.button(Text.literal("Confirm"), button -> {
            onConfirm.run();
            this.close();
        }).margins(Insets.horizontal(5)));
        
        buttons.child(Components.button(Text.literal("Cancel"), button -> {
            this.close();
        }).margins(Insets.horizontal(5)));

        rootComponent.child(buttons);
    }
}