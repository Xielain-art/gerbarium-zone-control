# Specification: Deep GUI (owo-lib) Implementation

## Overview
Implement the missing administrative GUI screens for Gerbarium Regions Runtime using `owo-lib`. The focus is on providing server administrators with deep visibility into zone states, rules, and historical events. The implemented screens include ZoneDetails, RuleDetails, and EventsScreen, utilizing previously prepared DTOs.

## Functional Requirements
1.  **ZoneDetails Screen:**
    *   Display Runtime State: Current status, player count, active timers.
    *   Display Config Details: Bounds, priorities, dimensions.
    *   Display Applied Rules: A list of rules applied to the selected zone.
2.  **RuleDetails Screen:**
    *   Display Config Details: Rule type, conditions, weights, and mob types.
    *   Display Cooldown Status: Current anti-farm status and active cooldowns.
    *   Display Trigger Statistics: Recent trigger history and relevant stats.
3.  **EventsScreen (Event Log):**
    *   Display a chronological list of all zone events.
    *   Implement filtering by event type (e.g., spawns, entries, errors).
    *   Implement filtering by specific zone.

## UI/UX Requirements
1.  **Layout & Style:**
    *   Use a vanilla-like `owo-lib` layout.
    *   Ensure the interface is clean, clear, readable, and not overloaded. Avoid dense spreadsheet-like layouts.
    *   Utilize pagination, scrollable lists, collapsible sections, and filters where data volume is high.
2.  **Color Coding:**
    *   **Green:** Active / Ready / Success.
    *   **Yellow:** Cooldown / Warning / Waiting.
    *   **Red:** Errors / Failures.
    *   **Gray:** Inactive / Disabled.
3.  **Safety:**
    *   Visually separate dangerous administrative actions.
    *   Always require confirmation for dangerous actions.

## Out of Scope
*   Creating new underlying runtime logic or DTOs (this task is purely UI implementation).