# Implementation Plan: Deep GUI (owo-lib)

## Phase 1: ZoneDetails Screen Implementation [checkpoint: 50fdd49]
- [x] Task: Implement ZoneDetails screen layout and DTO integration
    - [x] Setup `owo-lib` UI model layout for ZoneDetails.
    - [x] Bind Zone DTO Runtime State data (status, player count, timers).
    - [x] Bind Zone DTO Config Details (bounds, priorities, dimensions).
    - [x] Integrate color coding logic for status indicators.
- [x] Task: Implement Applied Rules list within ZoneDetails
    - [x] Create a scrollable list component for applied rules.
    - [x] Add navigation buttons from applied rules to the RuleDetails screen.
- [x] Task: Write verification checks/tests for ZoneDetails UI components
- [x] Task: Conductor - User Manual Verification 'Phase 1: ZoneDetails Screen Implementation' (Protocol in workflow.md)

## Phase 2: RuleDetails Screen Implementation
- [x] Task: Implement RuleDetails screen layout and DTO integration
    - [x] Setup `owo-lib` UI model layout for RuleDetails.
    - [x] Bind Rule Config Details (type, conditions, weights, mob types).
    - [x] Bind Cooldown Status and Trigger Statistics.
    - [x] Apply color coding for cooldowns and stats.
- [x] Task: Implement navigation components
    - [x] Add a functional "Back" button to return to ZoneDetails.
- [x] Task: Write verification checks/tests for RuleDetails UI components
- [ ] Task: Conductor - User Manual Verification 'Phase 2: RuleDetails Screen Implementation' (Protocol in workflow.md)

## Phase 3: EventsScreen (Event Log) Implementation
- [ ] Task: Implement EventsScreen basic layout and event list
    - [ ] Setup `owo-lib` UI model layout for EventsScreen.
    - [ ] Create a scrollable, paginated list component for chronological events.
    - [ ] Apply color coding based on event types (errors in red, success in green, etc.).
- [ ] Task: Implement filtering UI and logic
    - [ ] Add UI controls for event type filtering.
    - [ ] Add UI controls for zone filtering.
    - [ ] Connect filters to dynamically update the event list.
- [ ] Task: Write verification checks/tests for EventsScreen rendering and filtering
- [ ] Task: Conductor - User Manual Verification 'Phase 3: EventsScreen (Event Log) Implementation' (Protocol in workflow.md)

## Phase 4: Integration and Final Polish
- [ ] Task: Integrate new screens with existing main list
    - [ ] Hook up existing buttons in the main zone list to open ZoneDetails.
    - [ ] Hook up global or per-zone "Events Log" buttons to open EventsScreen.
- [ ] Task: Verify safety constraints
    - [ ] Ensure all dangerous actions within these new screens trigger the existing confirmation window.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: Integration and Final Polish' (Protocol in workflow.md)