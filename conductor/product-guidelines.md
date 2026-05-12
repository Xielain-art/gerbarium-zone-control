# Product Guidelines

## Communication Style
- **Concise & Actionable:** Command feedback and server logs must be direct and free of fluff. Prioritize delivering the intent and required actionable data immediately.

## GUI Design Philosophy
- **Clean Diagnostics:** The `owo-lib` interfaces should focus on clarity. Use color-coded statuses (e.g., Active vs. Inactive) and logical layouts to ensure server administrators can parse information at a glance without being overwhelmed by data.

## Error Handling & Feedback
- **In-Game Hints:** When a command or action fails, provide friendly, context-aware suggestions directly in the chat (e.g., "Hint: Zone may be too small or minDistanceFromPlayer too high").
- **Semantic Reasons:** Track internal failures using strict semantic reasoning strings (e.g., `SKIPPED_MAX_ALIVE`, `FAILED_NO_POSITION`) so diagnostics are immediately understandable without diving into raw stack traces.

## Code & Architecture
- **Domain Driven:** Organize the codebase by clear, separated responsibilities (e.g., `ZoneActivationManager`, `EntitySpawnService`). Naming should reflect the domain logic and ensure modular boundaries remain clean.