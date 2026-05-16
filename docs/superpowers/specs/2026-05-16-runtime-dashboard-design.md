# Gerbarium Runtime Dashboard — Design Spec

**Date:** 2026-05-16
**Scope:** Replace multi-screen owo-lib GUI with single-screen sidebar dashboard for Gerbarium Regions Runtime.

---

## 1. Overview

Replace the current screen stack (`RuntimeZonesScreen` → `RuntimeZoneDetailsScreen` → `RuntimeRuleDetailsScreen` → `RuntimeEventsScreen`) with a single persistent `RuntimeDashboardScreen`. A left sidebar switches panels. The screen never closes until the user presses Escape.

---

## 2. Goals

- **Modern visual style:** dark glassmorphism (blur background, translucent cards, accent glow).
- **No vanilla Minecraft screens:** custom `Screen` subclass with full owo-ui layout, no vanilla widgets or translucent dirt background.
- **Better logs:** events shown as compact table rows with color-coded type badges, zebra striping, inline expandable details.
- **Fix debug toggle:** header toggle switch with optimistic UI that does not get stuck "always ON".
- **Accessibility:** WCAG AA contrast, icon+text status indicators, clear focus states.

---

## 3. Architecture

```
RuntimeDashboardScreen (Screen)
├── SidebarNav (FlowLayout, fixed 140px)
│   └── NavButton[] (Zones, Events, Details)
├── HeaderBar (FlowLayout)
│   ├── Title + Summary
│   ├── Global actions (Refresh, Reload, Cleanup, Save)
│   └── DebugToggle (custom switch component)
└── ContentArea (FlowLayout, fill remaining)
    └── ActivePanel (interface Panel)
        ├── ZonesPanel
        ├── EventsPanel
        ├── ZoneDetailsPanel
        ├── RuleDetailsPanel
        └── ConfirmModal (overlay)
```

- `Panel` interface: `build(FlowLayout target)`, `refresh()`.
- `RuntimeDashboardScreen` holds `RuntimeSnapshotDto snapshot` and routes it to the active panel.
- No `parent` screen references. Back navigation becomes sidebar selection.

---

## 4. Components

### 4.1 RuntimeDashboardScreen
- Extends `BaseOwoScreen<FlowLayout>` but overrides `renderBackground` to draw custom dark translucent overlay instead of vanilla dirt.
- Surface on root layout: transparent; background drawn manually via `fillGradient` or solid fill with low opacity + optional blur.
- Padding: 12px.

### 4.2 SidebarNav
- Vertical flow, fixed width 140px.
- Buttons: Zones, Events.
- When a zone is selected, sidebar shows "Zone: <name>" and "Rule: <name>" as sub-items, replacing the generic "Details" button.
- Long names truncated with ellipsis.
- Active item has cyan left border + glow.

### 4.3 HeaderBar
- Horizontal flow, full width.
- Left: title "Regions Runtime" + chip summary (active/total zones).
- Center: global action buttons (compact icon+text).
- Right: DebugToggle switch + status chip.

### 4.4 DebugToggle
- Custom toggle component (not vanilla button).
- Click flips local state immediately (optimistic UI), disables itself for 500ms, and sends `DEBUG_ON` / `DEBUG_OFF` packet.
- On next snapshot arrival, reconcile label. If snapshot still differs after 2s, revert local state to server value.
- Never reads stale `snapshot.debug` for its own enabled state.

### 4.5 ZonesPanel
- Scrollable vertical list of zone cards.
- Card: status dot + name + dimension + player count + alive counts.
- Actions per card: Select (opens ZoneDetailsPanel), Activate, Deactivate, Spawn, Clear.
- Confirm modal overlays content area instead of pushing new screen.

### 4.6 EventsPanel
- Top filter bar: search text box, type chip filters (toggleable pills), zone dropdown.
- Table header: Time | Type | Zone | Rule | Message. Simulated with horizontal flows; owo-ui has no native table.
- Rows: compact, zebra striping, type badge colored.
- Click row expands inline details (entity, position, action).
- Pagination: simple `< 1 / 5 >` text + arrows.

### 4.7 ZoneDetailsPanel
- Accordion sections: Runtime, Config, Rules.
- Rules shown as compact cards with spawn countdowns.
- Actions: Force Activate/Deactivate/Spawn, Clear.
- Selecting a rule opens RuleDetailsPanel.

### 4.8 RuleDetailsPanel
- Back button returns to ZoneDetailsPanel.
- Sections: Config, Boundary Control, Counters, Last Attempt, Last Success, Current Status, Timer State, After-Death Respawn, UNIQUE, Companions.
- Uses same card style as ZonesPanel.

### 4.9 ConfirmModal
- Centered glass card overlay with title, description, warning label.
- Buttons: Confirm (danger/red accent), Cancel.
- Animates opacity in/out (simple 2-step).

---

## 5. Data Flow

1. `RuntimeDashboardScreen` opens → requests snapshot via networking.
2. Server responds `RuntimeSnapshotDto` → `updateSnapshot()` called.
3. Snapshot distributed to active panel via `Panel.refresh(snapshot)`.
4. User clicks zone → `SidebarNav` adds "Zone: <id>" entry, switches to `ZoneDetailsPanel`.
5. ZoneDetailsPanel requests detailed zone data via `REQUEST_ZONE_DETAILS`.
6. Server responds → `ZoneDetailsPanel` rebuilds with full data.
7. User clicks rule → sidebar adds "Rule: <id>", switches to `RuleDetailsPanel`.
8. RuleDetailsPanel requests `REQUEST_RULE_DETAILS`.
9. Events panel requests `REQUEST_RUNTIME_EVENTS` on open.

All previous `Screen` pushing (`setScreen(new ...)`) is removed. Only `RuntimeDashboardScreen` exists.

---

## 6. Visual Design

| Element | Value |
|---------|-------|
| Background | `#0F172A` @ 92% opacity + subtle blur |
| Sidebar bg | `#1E293B` @ 95% opacity |
| Card bg | `#1E293B` @ 90% opacity |
| Card border | `#334155` 1px |
| Border radius | 6px |
| Accent glow | `#22D3EE` shadow 0 0 8px |
| Text primary | `#F1F5F9` |
| Text muted | `#94A3B8` |
| Success | `#4ADE80` |
| Warning | `#FB923C` |
| Danger | `#F87171` |
| Info | `#38BDF8` |

- No vanilla dirt background.
- No vanilla button textures. Custom flat buttons with hover brightness shift.
- Scrollbars: thin 4px, thumb `#475569`, track transparent.

---

## 7. Accessibility

- All status indicators use icon + text + color (not color alone).
- Contrast ratios ≥ 4.5:1 for body text, ≥ 3:1 for large text.
- Buttons minimum height 20px, padding 6px 10px.
- Focus ring 2px cyan outline on keyboard-navigated elements.
- Event type badges use distinct shapes (pill, dot) + text + color.

---

## 8. Testing

- Unit: `GuiFormattingTest` updated for new color utilities.
- Manual: verify all 5 panels render without crash; verify debug toggle flips; verify confirm modal blocks interactions.
- Build: `./gradlew clean build` passes.

---

## 9. Migration

Files to modify:
- `RuntimeUi.java` — expand with new colors, components, glass surfaces.
- `RuntimeZonesScreen.java` → convert to `ZonesPanel`.
- `RuntimeEventsScreen.java` → convert to `EventsPanel`.
- `RuntimeZoneDetailsScreen.java` → convert to `ZoneDetailsPanel`.
- `RuntimeRuleDetailsScreen.java` → convert to `RuleDetailsPanel`.
- `RuntimeConfirmActionScreen.java` → convert to `ConfirmModal`.
- `GerbariumRegionsRuntimeClient.java` — open `RuntimeDashboardScreen` instead of `RuntimeZonesScreen`.
- Add `RuntimeDashboardScreen.java`.
- Add `Panel.java` interface.

No server-side networking changes required. Packet handlers remain identical.
