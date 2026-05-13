package com.gerbarium.runtime.client.gui;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.text.Text;

final class RuntimeUi {
    private RuntimeUi() {}

    // ── Color Palette ──────────────────────────────────────────────
    static final int COLOR_TEXT      = 0xE5E7EB;
    static final int COLOR_MUTED     = 0x9CA3AF;
    static final int COLOR_LABEL     = 0xAAB2C6;
    static final int COLOR_ACCENT    = 0x93C5FD;
    static final int COLOR_WARN      = 0xFCA5A5;
    static final int COLOR_OK        = 0x86EFAC;
    static final int COLOR_OK_LIGHT  = 0xA7F3D0;
    static final int COLOR_HIGHLIGHT = 0xFDE68A;
    static final int COLOR_SUBTLE    = 0xCBD5E1;
    static final int COLOR_DIM       = 0xC7D2FE;
    static final int COLOR_ZONE_TAG  = 0x7DD3FC;

    // ── Spacing ─────────────────────────────────────────────────────
    static final int GAP_SECTION = 10;
    static final int GAP_ITEM    = 6;
    static final int GAP_TINY    = 3;
    static final int PAD_CARD    = 10;

    // ── Layout Builders ────────────────────────────────────────────

    static FlowLayout card() {
        return card(-1);
    }

    static FlowLayout card(int maxWidth) {
        FlowLayout c = maxWidth > 0 ? Containers.verticalFlow(Sizing.fixed(maxWidth), Sizing.content()) : Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        c.surface(Surface.PANEL).padding(Insets.of(PAD_CARD));
        return c;
    }

    static FlowLayout section(String title) {
        FlowLayout sec = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        sec.surface(Surface.PANEL).padding(Insets.of(8));
        sec.child(label(title, COLOR_HIGHLIGHT));
        return sec;
    }

    static FlowLayout row() {
        return Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
    }

    static FlowLayout col() {
        return Containers.verticalFlow(Sizing.fill(100), Sizing.content());
    }

    // ── Typography ─────────────────────────────────────────────────

    static LabelComponent title(String text) {
        return Components.label(Text.literal(text)).color(Color.ofRgb(COLOR_ACCENT));
    }

    static LabelComponent label(String text, int color) {
        return Components.label(Text.literal(text)).color(Color.ofRgb(color));
    }

    static LabelComponent text(String text) {
        return label(text, COLOR_TEXT);
    }

    static LabelComponent muted(String text) {
        return label(text, COLOR_MUTED);
    }

    static LabelComponent dim(String text) {
        return label(text, COLOR_SUBTLE);
    }

    static LabelComponent warn(String text) {
        return label(text, COLOR_WARN);
    }

    static LabelComponent ok(String text) {
        return label(text, COLOR_OK);
    }

    // ── Components ─────────────────────────────────────────────────

    static Component button(String text, Runnable action) {
        return Components.button(Text.literal(text), btn -> action.run())
                .sizing(Sizing.content(), Sizing.content());
    }

    static Component chip(String label, String value, int color) {
        return Components.label(Text.literal(label + ": " + value)).color(Color.ofRgb(color));
    }

    static FlowLayout kv(String key, String value) {
        FlowLayout r = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        r.child(label(key + ":", COLOR_LABEL).horizontalSizing(Sizing.content()));
        r.child(label(valueOrDash(value), COLOR_TEXT).margins(Insets.left(6)));
        return r;
    }

    static FlowLayout kv(String key, String value, int labelWidth) {
        FlowLayout r = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        r.child(label(key + ":", COLOR_LABEL).sizing(Sizing.fixed(labelWidth), Sizing.content()));
        r.child(label(valueOrDash(value), COLOR_TEXT));
        return r;
    }

    // ── Helpers ────────────────────────────────────────────────────

    static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    static String boolText(boolean value) {
        return value ? "yes" : "no";
    }
}

