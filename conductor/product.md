# Product Definition: Gerbarium Regions Runtime

## Overview
The Gerbarium Regions Runtime is a high-performance Fabric 1.20.1 Minecraft mod designed to manage dynamic spawning and region activation logic. It acts as the execution engine for zones defined via JSON configurations (typically authored by an external bridge/editor).

## Target Audience
**Server Administrators:** The primary users of this runtime are server operators who need robust tools to monitor, manage, and force-trigger region mechanics in real-time.

## Core Features
- **Dynamic Spawning:** Advanced mechanics for spawning PACKs of mobs with anti-farm logic and UNIQUE boss encounters where cooldown begins only after the boss and its companions are fully cleared.
- **Admin GUI:** A comprehensive, read-only interface built with `owo-lib` for monitoring zone states, schedules, and active encounters.
- **Proximity Logic:** Intelligent proximity-based zone activation to drastically save server resources by only ticking logic for areas with active players.

## Performance & Scale
**High Performance:** Built for demanding environments. The runtime features strict anti-farm logic, decoupled in-memory mob tracking, debounced saving, and lazy per-zone runtime state files stored under `/config/gerbarium/zones-control/states/` to keep diagnostics and cooldown data isolated per zone.

## Interaction Methods
- **CLI Commands:** Extensive management via the `/gerbzone` command tree.
- **GUI Screens:** Visual diagnostics and administrative actions through `owo-lib` interfaces.
- **Config Files:** Ingestion of zone and rule configurations via JSON files stored in `/config/gerbarium/zones/`, with manual reload support through `/gerbzone reload` or the Runtime GUI Reload Zones button.