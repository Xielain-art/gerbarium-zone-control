# Implementation Plan: Smart Time Formatting (TimeUtil)

## Phase 1: Implement TimeUtil Logic
- [ ] Task: Create `TimeUtil` class
    - [ ] Write failing unit tests for relative time formatting (< 24 hours, two-unit precision).
    - [ ] Write failing unit tests for absolute time formatting (>= 24 hours).
    - [ ] Write failing unit tests for edge cases (e.g., exactly now, negative durations if applicable).
    - [ ] Implement `TimeUtil` formatting logic to pass the tests.
    - [ ] Verify all tests pass.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Implement TimeUtil Logic' (Protocol in workflow.md)

## Phase 2: Integrate TimeUtil into CLI Commands
- [ ] Task: Update `/gerbzone status` output
    - [ ] Identify all timestamp usages in `RuntimeQueryService` (e.g., last attempt, next available).
    - [ ] Refactor formatting to use `TimeUtil`.
    - [ ] Verify changes via automated or manual testing (as appropriate).
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Integrate TimeUtil into CLI Commands' (Protocol in workflow.md)

## Phase 3: Integrate TimeUtil into GUI Screens
- [ ] Task: Update Client DTOs / GUI Rendering
    - [ ] Identify all timestamp usages in GUI screens (e.g., `ZoneDetailsScreen`, event logs).
    - [ ] Refactor client-side rendering or server-side DTO generation to use `TimeUtil`.
    - [ ] Verify changes via automated or manual testing.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Integrate TimeUtil into GUI Screens' (Protocol in workflow.md)