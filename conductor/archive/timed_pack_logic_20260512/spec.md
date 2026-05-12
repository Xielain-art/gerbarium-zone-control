# Specification: TIMED Pack Logic Implementation

## Overview
Currently, the Gerbarium Regions Runtime only supports `ON_ACTIVATION` logic for spawning packs of mobs when a player enters a zone. The system has placeholders for `TIMED` logic, which is intended to spawn packs periodically while players remain in the active zone. This track implements the `TIMED` spawning mechanics (`refillMode: "TIMED"`), ensuring distinct behavior from `ON_ACTIVATION` packs to prevent uncontrolled farming.

## Functional Requirements
1. **Trigger Type Separation:** Support `refillMode: "TIMED"` for packs (`spawnType: "PACK"`), alongside the existing `ON_ACTIVATION` behavior.
2. **Timer Behavior:**
   - The timer progresses *only* while the zone is active (players are present).
   - Do not run TIMED timers in inactive zones.
   - Do not catch up missed spawns after inactivity.
3. **State Persistence (Pause & Resume):** 
   - When the zone deactivates, the timer progress pauses and is saved in the per-zone runtime state file (`/config/gerbarium/zones-control/states/<zoneId>.runtime-state.json`).
   - When the zone activates again, the timer resumes from the saved progress.
   - Add necessary fields to `RuleRuntimeState` (e.g., `timedProgressMillis`, `lastTimedTickAt`, `timedSpawnedThisActivation`) to support pause/resume and budget tracking.
4. **Anti-Farm Protection (Per-Activation Budget):**
   - During one continuous zone activation, a `TIMED` rule can spawn at most `timedMaxSpawnsPerActivation` primary mobs total.
   - **Default:** `timedMaxSpawnsPerActivation = maxAlive`.
   - If `timedSpawnedThisActivation >= timedMaxSpawnsPerActivation`, no more `TIMED` spawns occur during this activation.
   - The budget resets only when the zone deactivates and is later reactivated.
   - Setting `timedMaxSpawnsPerActivation: -1` allows unlimited spawns while active.
5. **Spawn Limits (Max Alive):** 
   - If the number of alive **primary mobs** for this rule is `>= maxAlive`, the spawn is skipped.
   - **Companion mobs must not be counted** towards the `maxAlive` limit.
   - Do not spawn extra mobs later to compensate for skipped spawns.
   - Do not create accumulated/backlogged spawn events.
   - The timer should continue/pause in a safe way, but *never* burst-spawn missed intervals.
6. **Configuration Schema:** 
   - Use the existing `respawnSeconds` field for the interval.
   - Add optional field `timedMaxSpawnsPerActivation` (defaulting to `maxAlive` if null/not provided).
7. **GUI & Monitoring:**
   - Show a warning in the GUI/commands if `timedMaxSpawnsPerActivation = -1` ("Farm risk: unlimited TIMED spawning").
   - Display the current budget usage in rule status (e.g., "TIMED budget: 2/2 used this activation").

## Non-Functional Requirements
- **Performance:** Tracking timer state and alive mob counts per zone/pack must be efficient and not degrade server performance during active zone ticking.

## Acceptance Criteria
- [ ] A `TIMED` rule spawns mobs at the specified interval *up to* the `timedMaxSpawnsPerActivation` limit per activation.
- [ ] The budget resets upon zone reactivation.
- [ ] If `maxAlive` is reached, spawns are skipped (and not backlogged).
- [ ] GUI displays appropriate warnings and current budget status.

## Out of Scope
- Modifying `UNIQUE` encounter logic.
- Modifying `ON_ACTIVATION` behavior.
- Adding auto-reload for zones.