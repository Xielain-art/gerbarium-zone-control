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
   - Add necessary fields to `RuleRuntimeState` (e.g., `timedProgressMillis` or `nextTimedSpawnInMillis`) to support pause/resume and survive server restarts.
4. **Spawn Limits (Max Alive):** 
   - If the number of alive **primary mobs** for this rule is `>= maxAlive`, the spawn is skipped.
   - **Companion mobs must not be counted** towards the `maxAlive` limit.
   - Do not spawn extra mobs later to compensate for skipped spawns.
   - Do not create accumulated/backlogged spawn events.
   - The timer should continue/pause in a safe way, but *never* burst-spawn missed intervals.
5. **Configuration Schema:** 
   - Do not add new fields for intervals.
   - Use the existing `respawnSeconds` field. For `PACK` + `TIMED`, `respawnSeconds` represents the timed spawn interval in seconds.
   - Keep the JSON schema `camelCase` and compatible with the current bridge format. Example:
     ```json
     {
       "spawnType": "PACK",
       "refillMode": "TIMED",
       "maxAlive": 10,
       "spawnCount": 2,
       "respawnSeconds": 300,
       "chance": 1.0
     }
     ```

## Non-Functional Requirements
- **Performance:** Tracking timer state and alive mob counts per zone/pack must be efficient and not degrade server performance during active zone ticking.

## Acceptance Criteria
- [ ] A rule configured with `spawnType: "PACK"` and `refillMode: "TIMED"` spawns `spawnCount` mobs repeatedly every `respawnSeconds` while players are in the zone.
- [ ] The timer pauses when the zone is deactivated and resumes correctly upon reactivation without catching up.
- [ ] Spawns are skipped (and not burst-spawned later) if the number of alive **primary mobs** from the rule reaches `maxAlive`. Companion mobs are correctly excluded from this count.
- [ ] The zone state file (`<zoneId>.runtime-state.json`) correctly stores and restores the elapsed time/progress for `TIMED` rules.

## Out of Scope
- Modifying `UNIQUE` encounter logic.
- Modifying `ON_ACTIVATION` behavior.
- Adding auto-reload for zones.