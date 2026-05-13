package com.gerbarium.runtime.client.gui;

import com.gerbarium.runtime.client.dto.RuntimeEventDto;
import com.gerbarium.runtime.client.dto.RuntimeSnapshotDto;
import com.gerbarium.runtime.network.GerbariumRuntimePackets;
import com.gerbarium.runtime.util.TimeUtil;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RuntimeEventsScreen extends BaseOwoScreen<FlowLayout> implements RuntimeSnapshotView {
    private static final int PAGE_SIZE = 12;

    private final Screen parent;
    private RuntimeSnapshotDto snapshot;
    private final String fixedZoneId;
    private final String fixedRuleId;

    private FlowLayout eventsList;
    private TextBoxComponent searchBox;
    private String searchQuery = "";
    private String typeFilter = "ALL";
    private String zoneFilter = "ALL";
    private int page = 0;

    public RuntimeEventsScreen(Screen parent, RuntimeSnapshotDto snapshot) {
        this(parent, snapshot, null, null);
    }

    public RuntimeEventsScreen(Screen parent, RuntimeSnapshotDto snapshot, String zoneId, String ruleId) {
        this.parent = parent;
        this.snapshot = snapshot;
        this.fixedZoneId = zoneId;
        this.fixedRuleId = ruleId;
        if (zoneId != null && !zoneId.isBlank()) {
            this.zoneFilter = zoneId;
        }
    }

    @Override
    public void updateSnapshot(RuntimeSnapshotDto snapshot) {
        this.snapshot = snapshot;
        if (this.fixedZoneId != null && !this.fixedZoneId.isBlank()) {
            this.zoneFilter = this.fixedZoneId;
        }
        if (this.eventsList != null) {
            rebuildEventsList();
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

        this.eventsList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rootComponent.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(78), eventsList).margins(Insets.top(10)));

        rebuildEventsList();
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        header.surface(Surface.PANEL).padding(Insets.of(10));

        FlowLayout title = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        title.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        title.child(Components.label(Text.literal("Runtime Events")).sizing(Sizing.fixed(180)));
        title.child(Components.label(Text.literal(summaryText())).color(Color.ofRgb(0xAAB2C6)));
        header.child(title);

        header.child(filtersRow().margins(Insets.top(6)));
        header.child(pagingRow().margins(Insets.top(8)));
        return header;
    }

    private FlowLayout filtersRow() {
        FlowLayout row = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        FlowLayout first = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        first.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        first.child(actionButton("Back", () -> this.client.setScreen(parent)));
        first.child(actionButton("Refresh", this::requestSnapshot).margins(Insets.left(6)));
        first.child(actionButton("Clear filters", this::clearFilters).margins(Insets.left(6)));
        first.child(actionButton("Type: " + typeFilter, this::cycleTypeFilter).margins(Insets.left(6)));
        first.child(actionButton("Zone: " + zoneLabel(), this::cycleZoneFilter).margins(Insets.left(6)));
        row.child(first);

        FlowLayout second = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        second.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        second.child(Components.label(Text.literal("Search")).color(Color.ofRgb(0xAAB2C6)));
        this.searchBox = Components.textBox(Sizing.fixed(260), searchQuery);
        this.searchBox.onChanged().subscribe(value -> {
            this.searchQuery = value == null ? "" : value.trim();
            this.page = 0;
            rebuildEventsList();
        });
        second.child(searchBox.sizing(Sizing.fixed(260), Sizing.content()).margins(Insets.left(8)));
        if (fixedRuleId != null && !fixedRuleId.isBlank()) {
            second.child(Components.label(Text.literal("Rule filter: " + fixedRuleId)).color(Color.ofRgb(0xC7D2FE)).margins(Insets.left(12)));
        }
        row.child(second.margins(Insets.top(8)));
        return row;
    }

    private FlowLayout pagingRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.child(Components.label(Text.literal(pageText())).color(Color.ofRgb(0xAAB2C6)));
        row.child(actionButton("Prev", this::prevPage).margins(Insets.left(12)));
        row.child(actionButton("Next", this::nextPage).margins(Insets.left(6)));
        return row;
    }

    private void rebuildEventsList() {
        eventsList.clearChildren();

        List<RuntimeEventDto> filtered = filteredEvents();
        if (filtered.isEmpty()) {
            eventsList.child(Components.label(Text.literal("No events match the current filters.")).color(Color.ofRgb(0xFDE68A)));
            return;
        }

        int totalPages = totalPages(filtered.size());
        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(filtered.size(), from + PAGE_SIZE);
        List<RuntimeEventDto> pageEvents = filtered.subList(from, to);

        for (RuntimeEventDto event : pageEvents) {
            eventsList.child(eventCard(event).margins(Insets.vertical(3)));
        }

        if (totalPages > 1) {
            eventsList.child(Components.label(Text.literal("Page " + (page + 1) + " of " + totalPages + " - showing " + pageEvents.size() + " of " + filtered.size() + " matched events")).color(Color.ofRgb(0xAAB2C6)).margins(Insets.top(8)));
        } else {
            eventsList.child(Components.label(Text.literal("Showing " + filtered.size() + " matched events")).color(Color.ofRgb(0xAAB2C6)).margins(Insets.top(8)));
        }
    }

    private List<RuntimeEventDto> filteredEvents() {
        if (snapshot == null || snapshot.recentEvents == null) {
            return List.of();
        }

        String query = searchQuery == null ? "" : searchQuery.toLowerCase(Locale.ROOT);
        List<RuntimeEventDto> filtered = new ArrayList<>();
        for (RuntimeEventDto event : snapshot.recentEvents) {
            if (!matchesZone(event)) {
                continue;
            }
            if (!matchesRule(event)) {
                continue;
            }
            if (!matchesType(event)) {
                continue;
            }
            if (!query.isBlank() && !matchesSearch(event, query)) {
                continue;
            }
            filtered.add(event);
        }
        filtered.sort(Comparator.comparingLong((RuntimeEventDto event) -> event.time).reversed());
        return filtered;
    }

    private boolean matchesZone(RuntimeEventDto event) {
        return zoneFilter == null || zoneFilter.isBlank() || "ALL".equals(zoneFilter) || zoneFilter.equals(event.zoneId);
    }

    private boolean matchesRule(RuntimeEventDto event) {
        return fixedRuleId == null || fixedRuleId.isBlank() || fixedRuleId.equals(event.ruleId);
    }

    private boolean matchesType(RuntimeEventDto event) {
        if ("ALL".equals(typeFilter)) {
            return true;
        }
        return switch (typeFilter) {
            case "ZONE" -> event.type.startsWith("ZONE_");
            case "PACK" -> event.type.startsWith("PACK_");
            case "UNIQUE" -> event.type.startsWith("UNIQUE_");
            case "FORCE" -> event.type.startsWith("FORCE_");
            case "RULE" -> event.type.startsWith("RULE_");
            case "STATE" -> event.type.startsWith("STATE_") || event.type.startsWith("COOLDOWN_");
            case "SYSTEM" -> event.type.equals("RELOAD") || event.type.equals("ORPHANS_CLEANED");
            default -> event.type.equals(typeFilter);
        };
    }

    private boolean matchesSearch(RuntimeEventDto event, String query) {
        return contains(TimeUtil.formatRelative(event.time), query)
                || contains(event.zoneId, query)
                || contains(event.ruleId, query)
                || contains(event.type, query)
                || contains(event.message, query);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private FlowLayout eventCard(RuntimeEventDto event) {
        FlowLayout card = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        card.surface(Surface.PANEL).padding(Insets.of(8));

        FlowLayout top = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        top.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        top.child(Components.label(Text.literal(TimeUtil.formatRelative(event.time))).color(Color.ofRgb(0xAAB2C6)).sizing(Sizing.fixed(160)));
        top.child(Components.label(Text.literal(event.type)).color(Color.ofRgb(typeColor(event.type))).sizing(Sizing.fixed(170)));
        top.child(Components.label(Text.literal(event.zoneId == null ? "-" : event.zoneId)).color(Color.ofRgb(0x7DD3FC)).sizing(Sizing.fixed(160)));
        if (event.ruleId != null && !event.ruleId.isBlank()) {
            top.child(Components.label(Text.literal(event.ruleId)).color(Color.ofRgb(0xC7D2FE)).sizing(Sizing.fixed(160)));
        }
        card.child(top);

        card.child(Components.label(Text.literal(event.message == null || event.message.isBlank() ? "-" : event.message)).color(Color.ofRgb(0xFFFFFF)).margins(Insets.top(4)));
        return card;
    }

    private int typeColor(String type) {
        if (type == null) {
            return 0xFFFFFF;
        }
        if (type.contains("FAILED") || type.contains("ERROR")) {
            return 0xFCA5A5;
        }
        if (type.contains("SUCCESS")) {
            return 0x86EFAC;
        }
        if (type.contains("CLEARED") || type.contains("DEACTIVATED")) {
            return 0xFDE68A;
        }
        if (type.contains("ACTIVATED") || type.contains("SPAWN")) {
            return 0xA7F3D0;
        }
        return 0xFFFFFF;
    }

    private String summaryText() {
        int size = snapshot == null || snapshot.recentEvents == null ? 0 : snapshot.recentEvents.size();
        return size + " loaded / " + filteredCount() + " matched";
    }

    private int filteredCount() {
        return filteredEvents().size();
    }

    private String pageText() {
        int count = filteredCount();
        int totalPages = totalPages(count);
        if (count == 0) {
            return "No events to show";
        }
        return "Page " + (page + 1) + " / " + totalPages;
    }

    private int totalPages(int size) {
        return Math.max(1, (int) Math.ceil(size / (double) PAGE_SIZE));
    }

    private String zoneLabel() {
        if (fixedZoneId != null && !fixedZoneId.isBlank()) {
            return fixedZoneId;
        }
        return zoneFilter == null ? "ALL" : zoneFilter;
    }

    private void cycleTypeFilter() {
        String[] filters = {"ALL", "ZONE", "PACK", "UNIQUE", "FORCE", "RULE", "STATE", "SYSTEM"};
        typeFilter = nextValue(filters, typeFilter);
        page = 0;
        rebuildEventsList();
    }

    private void cycleZoneFilter() {
        if (fixedZoneId != null && !fixedZoneId.isBlank()) {
            return;
        }
        if (snapshot == null || snapshot.zones == null || snapshot.zones.isEmpty()) {
            zoneFilter = "ALL";
            return;
        }

        if ("ALL".equals(zoneFilter)) {
            zoneFilter = snapshot.zones.get(0).id;
        } else {
            int index = -1;
            for (int i = 0; i < snapshot.zones.size(); i++) {
                if (snapshot.zones.get(i).id.equals(zoneFilter)) {
                    index = i;
                    break;
                }
            }
            zoneFilter = index < 0 || index >= snapshot.zones.size() - 1 ? "ALL" : snapshot.zones.get(index + 1).id;
        }
        page = 0;
        rebuildEventsList();
    }

    private String nextValue(String[] values, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private void clearFilters() {
        searchQuery = "";
        typeFilter = "ALL";
        if (fixedZoneId != null && !fixedZoneId.isBlank()) {
            zoneFilter = fixedZoneId;
        } else {
            zoneFilter = "ALL";
        }
        page = 0;
        if (searchBox != null) {
            searchBox.text("");
        }
        rebuildEventsList();
    }

    private void prevPage() {
        page = Math.max(0, page - 1);
        rebuildEventsList();
    }

    private void nextPage() {
        int totalPages = totalPages(filteredCount());
        page = Math.min(totalPages - 1, page + 1);
        rebuildEventsList();
    }

    private Component actionButton(String title, Runnable action) {
        return Components.button(Text.literal(title), button -> action.run()).sizing(Sizing.fixed(132), Sizing.content());
    }

    private void requestSnapshot() {
        ClientPlayNetworking.send(GerbariumRuntimePackets.REQUEST_RUNTIME_SNAPSHOT, PacketByteBufs.create());
    }
}
