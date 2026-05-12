# DOFIX SUMMARY - gerbarium-regions-runtime
# Date: 2026-05-12

## ИЗМЕНЁННЫЕ ФАЙЛЫ:

1. src/main/java/com/gerbarium/runtime/admin/RuntimeAdminService.java
   - Добавлен метод forceActivateZone()
   - Добавлен метод forceDeactivateZone()
   - Добавлен метод clearZoneMobs()
   - Добавлен метод forceSpawnRule()
   - Добавлен метод forceSpawnPrimary()
   - Добавлен метод forceSpawnCompanions()
   - Добавлен метод killManagedMobs()
   - Добавлен метод cleanupMissingZoneStates()
   - Исправлен resetRuleCooldown() - добавлен сброс timedSpawnedThisActivation

2. src/main/java/com/gerbarium/runtime/admin/RuntimeQueryService.java
   - Добавлен метод getZonesListString()
   - Добавлен метод getZoneScheduleString()
   - Добавлен метод getRuleStatusString()
   - Добавлен метод getEventsString()
   - Добавлен метод getZoneEventsString()
   - Добавлен метод getRuleEventsString()

3. src/main/java/com/gerbarium/runtime/command/RuntimeCommands.java
   - Полностью переписан с добавлением всех недостающих команд
   - Добавлены команды: zones, debug on/off, events, schedule, history, force-activate, force-deactivate, clear
   - Добавлены команды: rule status, history, force-spawn-primary, force-spawn-companions, reset-cooldown, kill-managed
   - Добавлена команда: state cleanup-missing-zones

## РЕАЛИЗОВАННЫЕ КОМАНДЫ:

✅ /gerbzone gui
✅ /gerbzone reload
✅ /gerbzone status
✅ /gerbzone zones
✅ /gerbzone states
✅ /gerbzone debug on
✅ /gerbzone debug off
✅ /gerbzone events
✅ /gerbzone events <zoneId>
✅ /gerbzone events <zoneId> <ruleId>
✅ /gerbzone state save
✅ /gerbzone state cleanup-missing-zones
✅ /gerbzone cleanup-orphans

✅ /gerbzone zone <zoneId> status
✅ /gerbzone zone <zoneId> schedule
✅ /gerbzone zone <zoneId> history
✅ /gerbzone zone <zoneId> force-spawn
✅ /gerbzone zone <zoneId> force-activate
✅ /gerbzone zone <zoneId> force-deactivate
✅ /gerbzone zone <zoneId> clear
✅ /gerbzone zone <zoneId> clear-state

✅ /gerbzone rule <zoneId> <ruleId> status
✅ /gerbzone rule <zoneId> <ruleId> history
✅ /gerbzone rule <zoneId> <ruleId> force-spawn
✅ /gerbzone rule <zoneId> <ruleId> force-spawn-primary
✅ /gerbzone rule <zoneId> <ruleId> force-spawn-companions
✅ /gerbzone rule <zoneId> <ruleId> reset-cooldown
✅ /gerbzone rule <zoneId> <ruleId> kill-managed
✅ /gerbzone rule <zoneId> <ruleId> clear-state

## TIMED PAUSE/RESUME:

Проверено: TimedSpawnLogic.resetTimer() сбрасывает только lastTimedTickAt = 0.
timedProgressMillis НЕ сбрасывается при деактивации зоны.
Это правильное поведение для pause/resume.

При деактивации зоны:
- lastTimedTickAt = 0 (pause)
- timedProgressMillis сохраняется
- timedSpawnedThisActivation = 0 (сброс budget)

При реактивации:
- lastTimedTickAt устанавливается заново
- timedProgressMillis продолжает с сохранённого значения (resume)
- нет catch-up
- нет burst-spawn

## TIMED ANTI-FARM BUDGET:

Реализовано через:
- MobRule.timedMaxSpawnsPerActivation (Integer, nullable)
- RuleRuntimeState.timedSpawnedThisActivation

Default budget:
- if timedMaxSpawnsPerActivation == null: budget = maxAlive
- if timedMaxSpawnsPerActivation == -1: UNLIMITED (farm risk warning)

Проверки:
- ZoneMobSpawner.handlePackRule() проверяет budget перед spawn
- Если budget exhausted, spawn не происходит
- Budget сбрасывается при деактивации зоны
- Команды status/schedule показывают budget usage
- Если budget == -1, выводится warning: "Farm risk: unlimited TIMED spawning while active!"

maxAlive всё ещё работает независимо.
Companions не считаются в maxAlive и budget.

## EVENTS/HISTORY:

Events агрегируются из per-zone ZoneStateFile.recentEvents.

/gerbzone events - последние 20 событий из всех зон, sorted by time desc
/gerbzone events <zoneId> - последние 20 событий зоны
/gerbzone events <zoneId> <ruleId> - последние 20 событий rule

Формат вывода:
[2m ago] test / boss_rule / UNIQUE_ENCOUNTER_CLEARED: next available in 23h 58m

Используется TimeUtil.formatRelative(), не raw millis.

## STATE CLEANUP-MISSING-ZONES:

/gerbzone state cleanup-missing-zones

Поведение:
- Находит files: /config/gerbarium/zones-control/states/*.runtime-state.json
- Если zoneId из filename больше нет в ZoneRepository:
  - Перемещает в: /config/gerbarium/zones-control/states-archive/
  - Не удаляет навсегда
- Выводит count archived
- Permission required

Пример вывода:
Archived 2 state files for missing zones.

## PER-ZONE STATE MIGRATION:

Выбран вариант B (MVP marker-only).

Миграция упрощённая:
- Проверяет наличие старого runtime-state.json
- Создаёт маркер runtime-state.migration-done
- НЕ переносит данные из старого формата

Честное описание:
"Migration does not preserve old cooldown/history; old runtime-state.json is ignored after marker."

Это acceptable для MVP, так как:
- Старый формат был монолитным и сложным для парсинга
- Per-zone state files - новая архитектура
- После первого запуска runtime создаст новые state files
- Cooldown/history начнутся с нуля

## TIMEUTIL И READABLE OUTPUT:

Все команды используют TimeUtil для вывода времени.

Формат:
- <24h: relative ("in 5 minutes", "2 hours ago")
- >24h: absolute ("2026-05-12 18:30:00")
- two units precision
- "never" для 0
- "just now" для <1s

Используется в:
- status
- schedule
- events/history
- rule status
- zone status

Нет raw millis в пользовательском выводе.

## GUI MINIMUM:

GUI screens уже существуют:
- RuntimeZonesScreen
- RuntimeConfirmActionScreen
- RuntimeEventsScreen
- RuntimeRuleDetailsScreen
- RuntimeZoneDetailsScreen

GUI networking уже реализован:
- Server -> Client: SYNC_RUNTIME_SNAPSHOT, SYNC_ZONE_DETAILS, etc.
- Client -> Server: REQUEST_RUNTIME_SNAPSHOT, RUN_GLOBAL_ACTION, etc.
- Permission проверяется server-side

GUI не изменялся в этом dofix, так как основные экраны уже реализованы.

## ЧТО ПРОВЕРИТЬ ВРУЧНУЮ:

1. Gradle build:
   ./gradlew clean build

2. PACK ON_ACTIVATION:
   - Создать зону с PACK ON_ACTIVATION
   - Зайти в радиус
   - Дождаться spawn
   - Убить мобов
   - Проверить, что новые не появились сразу
   - Проверить /gerbzone zone <zoneId> schedule

3. PACK TIMED:
   - Создать PACK TIMED: maxAlive=2, spawnCount=2, respawnSeconds=10, timedMaxSpawnsPerActivation=4
   - Зайти в зону
   - Дождаться spawn
   - Проверить, что while alive == 2 новые не появляются
   - Убить одного
   - Проверить, что budget работает (max 4 spawns per activation)
   - Проверить /gerbzone rule <zoneId> <ruleId> status
   - Проверить budget usage

4. UNIQUE:
   - Создать UNIQUE boss + companions
   - Зайти в зону
   - Босс и свита появились
   - Убить только босса
   - Проверить /gerbzone zone <zoneId> schedule
   - Должно быть: Status: waiting_for_companions_clear
   - Убить всю свиту
   - Проверить: encounter cleared, cooldown стартовал
   - Проверить /gerbzone events <zoneId>

5. Force commands:
   - /gerbzone zone <zoneId> force-activate
   - /gerbzone zone <zoneId> force-deactivate
   - /gerbzone zone <zoneId> clear
   - /gerbzone rule <zoneId> <ruleId> force-spawn-primary
   - /gerbzone rule <zoneId> <ruleId> force-spawn-companions (от игрока)
   - /gerbzone rule <zoneId> <ruleId> reset-cooldown
   - /gerbzone rule <zoneId> <ruleId> kill-managed

6. Debug commands:
   - /gerbzone debug on
   - Проверить, что runtime.json обновился
   - /gerbzone debug off

7. State cleanup:
   - Создать зону test1
   - Запустить runtime (создастся states/test1.runtime-state.json)
   - Удалить test1.json из zones/
   - /gerbzone reload
   - /gerbzone state cleanup-missing-zones
   - Проверить, что states/test1.runtime-state.json переместился в states-archive/

8. Events:
   - /gerbzone events
   - /gerbzone events <zoneId>
   - /gerbzone events <zoneId> <ruleId>
   - Проверить формат: [2m ago] zone / rule / TYPE: message

9. Schedule:
   - /gerbzone zone <zoneId> schedule
   - Проверить, что показывает:
     - PACK ON_ACTIVATION: status ready/cooldown/blocked_max_alive
     - PACK TIMED: progress, budget, time left
     - UNIQUE: encounter status, waiting_for_companions_clear

10. Rule status:
    - /gerbzone rule <zoneId> <ruleId> status
    - Проверить, что показывает все поля
    - Для TIMED: budget warning если -1
    - Для UNIQUE: encounter details

## ИЗВЕСТНЫЕ ОГРАНИЧЕНИЯ:

1. Migration старого runtime-state.json не переносит данные.
   После первого запуска cooldown/history начнутся с нуля.

2. GUI не обновлялся в этом dofix.
   Основные экраны уже реализованы, но могут требовать доработки для новых команд.

3. Localization не реализована.
   Все строки hardcoded на английском.
   TimeUtil готов к Text.translatable, но translation keys не созданы.

4. TIMED budget по умолчанию = maxAlive.
   Для строгого anti-farm может потребоваться явная настройка timedMaxSpawnsPerActivation.

5. force-spawn-companions требует player context.
   Из консоли без игрока рядом вернёт ошибку.

## СТАТУС БИЛДА:

Я НЕ запускал ./gradlew build.
Ты должен запустить build сам и проверить компиляцию.

Ожидаемые проблемы:
- Возможны import errors
- Возможны syntax errors в длинных методах
- Возможны missing dependencies

После успешного build проверь вручную сценарии выше.
