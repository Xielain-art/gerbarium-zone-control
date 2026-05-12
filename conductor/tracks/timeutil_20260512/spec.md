# Specification: Smart Time Formatting (TimeUtil)

## Overview
Currently, the Gerbarium Regions Runtime outputs time values as raw milliseconds or basic `toString()` representations in both CLI commands and GUI screens. This track implements a new utility class, `TimeUtil`, to provide smart, human-readable time formatting and integrates it across the mod's interfaces to improve the user experience for server administrators.

## Functional Requirements
1. **TimeUtil Implementation:** Create a new `TimeUtil` class responsible for formatting timestamps and durations.
2. **Format Style (Mixed):**
   - For durations less than 24 hours (relative to the current time), the output should be relative (e.g., "5 hours ago", "in 2 hours").
   - For timestamps older than 24 hours, the output should fall back to an absolute readable date-time format (e.g., "yyyy-MM-dd HH:mm:ss").
3. **Precision:**
   - Relative formatted strings must display up to two significant units of time for accuracy (e.g., "2 hours and 15 minutes ago", "in 1 day and 3 hours").
4. **Localization:**
   - The formatted strings will be hardcoded in English. Translation keys (`Text.translatable`) are not required for these specific time strings.
5. **Integration Scope:**
   - **CLI Commands:** Update the `/gerbzone status` command (and any other relevant query commands) to use `TimeUtil` for displaying last attempt times, next available times, event histories, etc.
   - **GUI Screens:** Update the `owo-lib` based GUI screens (e.g., Zone Details, Event Logs) to use `TimeUtil` for all timestamp displays.

## Non-Functional Requirements
- **Efficiency:** The formatting methods should be lightweight and not cause performance issues when repeatedly called during GUI renders or command executions.
- **Maintainability:** `TimeUtil` should provide clear, reusable static methods (e.g., `formatRelative(long timestamp)`, `formatDuration(long millis)`).

## Acceptance Criteria
- [ ] A `TimeUtil` class exists with methods for mixed, two-unit precision time formatting in English.
- [ ] CLI commands (like `/gerbzone status`) display human-readable times instead of raw milliseconds.
- [ ] GUI screens display human-readable times instead of raw milliseconds.
- [ ] Times < 24h are formatted relatively (e.g., "X ago", "in Y").
- [ ] Times >= 24h are formatted as absolute dates.

## Out of Scope
- Implementing Minecraft localization/translation support for the time strings.
- Changing the underlying data structures; timestamps remain as `long` milliseconds internally.