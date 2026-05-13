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
    protected void build(FlowLayout root) {
        root.surface(Surface.VANILLA_TRANSLUCENT)
                .padding(Insets.of(18))
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP);

        root.child(buildHeader());

        this.eventsList = RuntimeUi.col();
        root.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(78), eventsList).margins(Insets.top(RuntimeUi.GAP_SECTION)));

        rebuildEventsList();
    }

    private FlowLayout buildHeader() {
        FlowLayout header = RuntimeUi.card();

        FlowLayout titleRow = RuntimeUi.row();
        titleRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        titleRow.child(RuntimeUi.title("Runtime Events"));
        titleRow.child(RuntimeUi.muted(summaryText()).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        header.child(titleRow);

        header.child(buildFilters().margins(Insets.top(RuntimeUi.GAP_ITEM)));
        header.child(buildPaging().margins(Insets.top(RuntimeUi.GAP_SECTION)));
        return header;
    }

    private FlowLayout buildFilters() {
        FlowLayout col = RuntimeUi.col();

        FlowLayout btnRow = RuntimeUi.row();
        btnRow.child(RuntimeUi.button("Back", () -> client.setScreen(parent)));
        btnRow.child(RuntimeUi.button("Refresh", this::requestSnapshot).margins(Insets.left(RuntimeUi.GAP_TINY)));
        btnRow.child(RuntimeUi.button("Clear filters", this::clearFilters).margins(Insets.left(RuntimeUi.GAP_TINY)));
        btnRow.child(RuntimeUi.button("Type: " + typeFilter, this::cycleTypeFilter).margins(Insets.left(RuntimeUi.GAP_TINY)));
        btnRow.child(RuntimeUi.button("Zone: " + zoneLabel(), this::cycleZoneFilter).margins(Insets.left(RuntimeUi.GAP_TINY)));
        col.child(btnRow);

        FlowLayout searchRow = RuntimeUi.row();
        searchRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        searchRow.child(RuntimeUi.label("Search:", RuntimeUi.COLOR_LABEL));
        this.searchBox = Components.textBox(Sizing.fill(100), searchQuery);
        this.searchBox.onChanged().subscribe(value -> {
            this.searchQuery = value == null ? "" : value.trim();
            this.page = 0;
            rebuildEventsList();
        });
        searchRow.child(searchBox.margins(Insets.left(RuntimeUi.GAP_TINY)));
        col.child(searchRow.margins(Insets.top(RuntimeUi.GAP_TINY)));

        return col;
    }

    private FlowLayout buildPaging() {
        FlowLayout row = RuntimeUi.row();
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.child(RuntimeUi.muted(pageText()));
        row.child(RuntimeUi.button("<", this::prevPage).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        row.child(RuntimeUi.button(">", this::nextPage).margins(Insets.left(RuntimeUi.GAP_TINY)));
        return row;
    }

    private void rebuildEventsList() {
        eventsList.clearChildren();
        List<RuntimeEventDto> events = filteredEvents();
        int totalPages = totalPages(events.size());

        if (page >= totalPages) {
            page = Math.max(0, totalPages - 1);
        }

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, events.size());

        if (events.isEmpty()) {
            eventsList.child(RuntimeUi.muted("No events match current filters.").margins(Insets.top(RuntimeUi.GAP_ITEM)));
            return;
        }

        for (int i = start; i < end; i++) {
            eventsList.child(buildEventCard(events.get(i)).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
    }

    private List<RuntimeEventDto> filteredEvents() {
        List<RuntimeEventDto> all = snapshot == null || snapshot.recentEvents == null ? List.of() : snapshot.recentEvents;
        List<RuntimeEventDto> result = new ArrayList<>();
        for (RuntimeEventDto event : all) {
            if (!"ALL".equals(typeFilter) && (event.type == null || !event.type.toUpperCase(Locale.ROOT).contains(typeFilter))) {
                continue;
            }
            if (!"ALL".equals(zoneFilter) && !zoneFilter.equals(event.zoneId)) {
                continue;
            }
            if (fixedRuleId != null && !fixedRuleId.isBlank() && !fixedRuleId.equals(event.ruleId)) {
                continue;
            }
            if (searchQuery != null && !searchQuery.isBlank()) {
                String q = searchQuery.toLowerCase(Locale.ROOT);
                String blob = ((event.message == null ? "" : event.message) + " " + (event.type == null ? "" : event.type) + " " + (event.entityType == null ? "" : event.entityType) + " " + (event.zoneId == null ? "" : event.zoneId) + " " + (event.ruleId == null ? "" : event.ruleId)).toLowerCase(Locale.ROOT);
                if (!blob.contains(q)) {
                    continue;
                }
            }
            result.add(event);
        }
        result.sort(Comparator.comparingLong((RuntimeEventDto e) -> e.time).reversed());
        return result;
    }

    private FlowLayout buildEventCard(RuntimeEventDto event) {
        FlowLayout card = RuntimeUi.card();

        FlowLayout top = RuntimeUi.row();
        top.child(RuntimeUi.label(TimeUtil.formatRelative(event.time), RuntimeUi.COLOR_SUBTLE));
        top.child(RuntimeUi.label(RuntimeUi.valueOrDash(event.type), typeColor(event.type)).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        top.child(RuntimeUi.label(RuntimeUi.valueOrDash(event.zoneId), RuntimeUi.COLOR_ZONE_TAG).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        if (event.ruleId != null && !event.ruleId.isBlank()) {
            top.child(RuntimeUi.label(event.ruleId, RuntimeUi.COLOR_DIM).margins(Insets.left(RuntimeUi.GAP_ITEM)));
        }
        card.child(top);

        card.child(RuntimeUi.text(RuntimeUi.valueOrDash(event.message)).margins(Insets.top(RuntimeUi.GAP_TINY)));
        if (event.entityType != null && !event.entityType.isBlank()) {
            String detail = "Entity: " + event.entityType;
            if (event.role != null && !event.role.isBlank()) detail += " / " + event.role;
            if (event.forced) detail += " / forced";
            card.child(RuntimeUi.dim(detail).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
        if (event.action != null && !event.action.isBlank()) {
            card.child(RuntimeUi.dim("Action: " + event.action + " @ " + event.x + "," + event.y + "," + event.z).margins(Insets.top(RuntimeUi.GAP_TINY)));
        }
        return card;
    }

    private int typeColor(String type) {
        if (type == null) return RuntimeUi.COLOR_TEXT;
        if (type.contains("FAILED") || type.contains("ERROR")) return RuntimeUi.COLOR_WARN;
        if (type.contains("BOUNDARY")) return RuntimeUi.COLOR_HIGHLIGHT;
        if (type.contains("SUCCESS")) return RuntimeUi.COLOR_OK;
        if (type.contains("CLEARED") || type.contains("DEACTIVATED")) return RuntimeUi.COLOR_HIGHLIGHT;
        if (type.contains("ACTIVATED") || type.contains("SPAWN")) return RuntimeUi.COLOR_OK_LIGHT;
        return RuntimeUi.COLOR_TEXT;
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
        if (count == 0) return "No events to show";
        return "Page " + (page + 1) + " / " + totalPages;
    }

    private int totalPages(int size) {
        return Math.max(1, (int) Math.ceil(size / (double) PAGE_SIZE));
    }

    private String zoneLabel() {
        if (fixedZoneId != null && !fixedZoneId.isBlank()) return fixedZoneId;
        return zoneFilter == null ? "ALL" : zoneFilter;
    }

    private void cycleTypeFilter() {
        String[] filters = {"ALL", "ZONE", "PACK", "UNIQUE", "FORCE", "BOUNDARY", "RULE", "STATE", "SYSTEM"};
        typeFilter = nextValue(filters, typeFilter);
        page = 0;
        rebuildEventsList();
    }

    private void cycleZoneFilter() {
        if (fixedZoneId != null && !fixedZoneId.isBlank()) return;
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
            if (values[i].equals(current)) return values[(i + 1) % values.length];
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

    private void requestSnapshot() {
        ClientPlayNetworking.send(GerbariumRuntimePackets.REQUEST_RUNTIME_SNAPSHOT, PacketByteBufs.create());
    }
}
