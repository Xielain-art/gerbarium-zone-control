package com.gerbarium.runtime.client.gui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

public class RuntimeConfirmActionScreen extends BaseOwoScreen<FlowLayout> {
    private final Screen parent;
    private final Text titleText;
    private final Text descriptionText;
    private final Runnable onConfirm;

    public RuntimeConfirmActionScreen(Screen parent, Text titleText, Text descriptionText, Runnable onConfirm) {
        this.parent = parent;
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

        FlowLayout card = RuntimeUi.card(420);
        card.child(RuntimeUi.title(titleText.getString()));
        card.child(RuntimeUi.text(descriptionText.getString()).margins(Insets.top(RuntimeUi.GAP_ITEM)));
        card.child(RuntimeUi.warn("This action is irreversible for current runtime state.").margins(Insets.top(RuntimeUi.GAP_ITEM)));

        FlowLayout buttons = RuntimeUi.row();
        buttons.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        buttons.child(RuntimeUi.button("Confirm", () -> {
            onConfirm.run();
            this.client.setScreen(parent);
        }));
        buttons.child(RuntimeUi.button("Cancel", () -> this.client.setScreen(parent)).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        card.child(buttons.margins(Insets.top(RuntimeUi.GAP_SECTION)));

        rootComponent.child(card);
    }
}
