# Implementation Plan: TIMED Pack Logic

## Phase 1: State Persistence for TIMED Rules [checkpoint: 702c717]
- [x] Task: Update `RuleRuntimeState` model
    - [x] Write failing test for serialization/deserialization of new fields (`timedProgressMillis` or equivalent).
    - [x] Add fields to `RuleRuntimeState` to support timer pause/resume.
    - [x] Update state serialization/deserialization logic.
    - [x] Verify tests pass.
- [x] Task: Conductor - User Manual Verification 'Phase 1: State Persistence for TIMED Rules' (Protocol in workflow.md)

## Phase 2: Active Zone Timer Logic
- [~] Task: Implement Timer Tick Logic
    - [ ] Write failing tests verifying timer progresses only when zone is active.
    - [ ] Write failing tests verifying timer pauses on deactivation and resumes without catch-up.
    - [ ] Implement `refillMode: "TIMED"` tick logic in the zone/spawner ticking system.
    - [ ] Verify tests pass.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Active Zone Timer Logic' (Protocol in workflow.md)

## Phase 3: Spawn Limits and Execution
- [ ] Task: Implement `maxAlive` check for Primary Mobs
    - [ ] Write failing tests ensuring `TIMED` spawns are skipped if primary alive mobs >= `maxAlive`.
    - [ ] Write failing tests ensuring companions are excluded from the `maxAlive` count for `TIMED` rules.
    - [ ] Implement the count check before executing the `TIMED` spawn event.
    - [ ] Verify tests pass.
- [ ] Task: Integrate `respawnSeconds` config
    - [ ] Write failing test to ensure `respawnSeconds` is read correctly for `TIMED` pack intervals.
    - [ ] Connect the configured `respawnSeconds` to the timer logic.
    - [ ] Verify tests pass.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Spawn Limits and Execution' (Protocol in workflow.md)