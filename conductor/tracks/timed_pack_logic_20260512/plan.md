# Implementation Plan: TIMED Pack Logic

## Phase 1: State Persistence for TIMED Rules [checkpoint: 702c717]
- [x] Task: Update `RuleRuntimeState` model
    - [x] Write failing test for serialization/deserialization of new fields (`timedProgressMillis` or equivalent).
    - [x] Add fields to `RuleRuntimeState` to support timer pause/resume.
    - [x] Add `timedSpawnedThisActivation` field to `RuleRuntimeState`.
    - [x] Update state serialization/deserialization logic.
    - [x] Verify tests pass.
- [x] Task: Conductor - User Manual Verification 'Phase 1: State Persistence for TIMED Rules' (Protocol in workflow.md)

## Phase 2: Active Zone Timer Logic [checkpoint: 58f07a0]
- [x] Task: Implement Timer Tick Logic
    - [x] Write failing tests verifying timer progresses only when zone is active.
    - [x] Write failing tests verifying timer pauses on deactivation and resumes without catch-up.
    - [x] Implement `refillMode: "TIMED"` tick logic in the zone/spawner ticking system.
    - [x] Verify tests pass.
- [x] Task: Conductor - User Manual Verification 'Phase 2: Active Zone Timer Logic' (Protocol in workflow.md)

## Phase 3: Spawn Limits and Execution
- [x] Task: Implement `maxAlive` check for Primary Mobs
    - [x] Write failing tests ensuring `TIMED` spawns are skipped if primary alive mobs >= `maxAlive`.
    - [x] Write failing tests ensuring companions are excluded from the `maxAlive` count for `TIMED` rules.
    - [x] Implement the count check before executing the `TIMED` spawn event.
    - [x] Verify tests pass.
- [x] Task: Integrate `respawnSeconds` config
    - [x] Write failing test to ensure `respawnSeconds` is read correctly for `TIMED` pack intervals.
    - [x] Connect the configured `respawnSeconds` to the timer logic.
    - [x] Verify tests pass.
- [x] Task: Conductor - User Manual Verification 'Phase 3: Spawn Limits and Execution' (Protocol in workflow.md)

## Phase 4: Anti-Farm Protection and UI/UX
- [x] Task: Implement Per-Activation Budget
    - [x] Add `timedMaxSpawnsPerActivation` to `MobRule` (defaulting to `maxAlive`).
    - [x] Implement budget check in `ZoneMobSpawner`.
    - [x] Increment `timedSpawnedThisActivation` on successful TIMED spawn.
    - [x] Reset `timedSpawnedThisActivation` in `ZoneActivationManager` on deactivation.
- [x] Task: Update GUI and Commands
    - [x] Display TIMED budget status in rule info.
    - [x] Add farm risk warning if budget is -1.
- [x] Task: Conductor - User Manual Verification 'Phase 4: Anti-Farm Protection and UI/UX' (Protocol in workflow.md)