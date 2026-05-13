package com.gerbarium.runtime.storage;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.state.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RuntimeStateStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_ROOT = FabricLoader.getInstance().getConfigDir().resolve("gerbarium/zones-control");
    private static final Path STATES_DIR = STORAGE_ROOT.resolve("states");
    private static final Path STATES_ARCHIVE_DIR = STORAGE_ROOT.resolve("states-archive");
    private static final Path ZONES_DIR = FabricLoader.getInstance().getConfigDir().resolve("gerbarium/zones");
    private static final Path OLD_STATE_FILE = STORAGE_ROOT.resolve("runtime-state.json");
    private static final Path MIGRATION_DONE_MARKER = STORAGE_ROOT.resolve("runtime-state.migration-done");

    private static final long SAVE_DEBOUNCE_MILLIS = 5000;

    private static final Map<String, ZoneStateFile> states = new ConcurrentHashMap<>();
    private static final Set<String> dirtyZoneIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static long lastSaveTime = 0;

    // Event rate limiting: key = zoneId:ruleId:type, value = last event time
    private static final Map<String, Long> lastEventTimes = new ConcurrentHashMap<>();
    private static final long EVENT_RATE_LIMIT_MILLIS = 60000; // 60 seconds
    private static final long EVENT_RATE_LIMIT_CLEANUP_INTERVAL = 300000; // 5 minutes
    private static long lastRateLimitCleanup = 0;

    public static ZoneStateFile getZoneState(String zoneId) {
        return states.computeIfAbsent(zoneId, id -> {
            ZoneStateFile f = loadZone(id);
            if (f == null) {
                return new ZoneStateFile(id);
            }
            ensureStateShape(f, id);
            return f;
        });
    }

    public static RuleRuntimeState getRuleState(String zoneId, String ruleId) {
        ZoneStateFile zf = getZoneState(zoneId);
        return zf.rules.computeIfAbsent(ruleId, id -> {
            RuleRuntimeState rs = new RuleRuntimeState();
            rs.zoneId = zoneId;
            rs.ruleId = id;
            return rs;
        });
    }

    public static void markDirty(String zoneId) {
        dirtyZoneIds.add(zoneId);
        ZoneStateFile zf = states.get(zoneId);
        if (zf != null) {
            zf.dirty = true;
        }
    }

    public static boolean isDirty(String zoneId) {
        return dirtyZoneIds.contains(zoneId);
    }

    public static Set<String> getDirtyZoneIds() {
        return Collections.unmodifiableSet(dirtyZoneIds);
    }

    public static void loadAll(Collection<Zone> loadedZones) {
        try {
            ensureStorageDirectories();
            touchMigrationMarkerIfNeeded();

            for (Zone zone : loadedZones) {
                ZoneStateFile zf = loadZone(zone.id);
                if (zf != null) {
                    ensureStateShape(zf, zone.id);
                    states.put(zone.id, zf);
                }
            }
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to initialize states directory", e);
        }
    }

    private static void touchMigrationMarkerIfNeeded() {
        try {
            if (Files.exists(OLD_STATE_FILE) && !Files.exists(MIGRATION_DONE_MARKER)) {
                Files.createFile(MIGRATION_DONE_MARKER);
                GerbariumRegionsRuntime.LOGGER.info("Old monolithic runtime-state.json ignored; per-zone state is authoritative now.");
            }
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to create migration marker", e);
        }
    }

    private static ZoneStateFile loadZone(String zoneId) {
        Path path = STATES_DIR.resolve(zoneId + ".runtime-state.json");
        if (!Files.exists(path)) return null;

        try (FileReader reader = new FileReader(path.toFile())) {
            return GSON.fromJson(reader, ZoneStateFile.class);
        } catch (Exception e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to load zone state: " + zoneId, e);
            return null;
        }
    }

    public static Path getZoneStatePath(String zoneId) {
        return STATES_DIR.resolve(zoneId + ".runtime-state.json");
    }

    public static Path getZonesDir() {
        return ZONES_DIR;
    }

    public static Path getStorageRoot() {
        return STORAGE_ROOT;
    }

    public static Path getStatesArchiveDir() {
        return STATES_ARCHIVE_DIR;
    }

    private static void ensureStorageDirectories() throws IOException {
        Files.createDirectories(ZONES_DIR);
        Files.createDirectories(STORAGE_ROOT);
        Files.createDirectories(STATES_DIR);
    }

    private static void ensureStateShape(ZoneStateFile zf, String zoneId) {
        if (zf.zone == null) {
            zf.zone = new ZoneRuntimePersistentState();
        }
        if (zf.zone.zoneId == null || zf.zone.zoneId.isBlank()) {
            zf.zone.zoneId = zoneId;
        }
        zf.zoneId = zoneId;
        if (zf.rules == null) {
            zf.rules = new HashMap<>();
        }
        if (zf.recentEvents == null) {
            zf.recentEvents = new ArrayList<>();
        }
    }

    public static void saveIfDirty() {
        if (dirtyZoneIds.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (now - lastSaveTime < SAVE_DEBOUNCE_MILLIS) return;

        saveAllDirty();
    }

    public static void saveAllDirty() {
        List<String> toSave = new ArrayList<>(dirtyZoneIds);
        for (String id : toSave) {
            if (saveZone(id)) {
                dirtyZoneIds.remove(id);
            }
        }
        lastSaveTime = System.currentTimeMillis();
    }

    public static boolean saveZone(String zoneId) {
        ZoneStateFile zf = states.get(zoneId);
        if (zf == null) return true;

        try {
            ensureStorageDirectories();
            Path path = getZoneStatePath(zoneId);
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            try (FileWriter writer = new FileWriter(tempPath.toFile())) {
                GSON.toJson(zf, writer);
            }
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            zf.dirty = false;
            return true;
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to save zone state: " + zoneId, e);
            try {
                Files.deleteIfExists(getZoneStatePath(zoneId).resolveSibling(getZoneStatePath(zoneId).getFileName() + ".tmp"));
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    public static void addEvent(String zoneId, String ruleId, String type, String message) {
        // Rate limit repeated skip events
        boolean isSkipEvent = type.startsWith("SKIPPED_");
        if (isSkipEvent) {
            String key = zoneId + ":" + (ruleId != null ? ruleId : "null") + ":" + type;
            Long lastTime = lastEventTimes.get(key);
            long now = System.currentTimeMillis();

            if (lastTime != null && (now - lastTime) < EVENT_RATE_LIMIT_MILLIS) {
                return;
            }

            lastEventTimes.put(key, now);
        }

        maybeCleanupRateLimit();

        ZoneStateFile zf = getZoneState(zoneId);
        zf.recentEvents.add(0, new RuntimeEvent(System.currentTimeMillis(), zoneId, ruleId, type, message));
        if (zf.recentEvents.size() > 200) {
            zf.recentEvents.remove(zf.recentEvents.size() - 1);
        }
        markDirty(zoneId);
    }

    public static void addBoundaryEvent(String zoneId, String ruleId, UUID entityUuid, String type, String message,
                                        String entityType, String role, boolean forced, int x, int y, int z,
                                        String action) {
        String key = zoneId + ":" + (ruleId != null ? ruleId : "null") + ":" + (entityUuid == null ? "null" : entityUuid) + ":" + type;
        long now = System.currentTimeMillis();
        Long lastTime = lastEventTimes.get(key);
        if (lastTime != null && (now - lastTime) < EVENT_RATE_LIMIT_MILLIS) {
            return;
        }
        lastEventTimes.put(key, now);

        maybeCleanupRateLimit();

        RuntimeEvent event = new RuntimeEvent(now, zoneId, ruleId, type, message);
        event.entityType = entityType;
        event.role = role;
        event.forced = forced;
        event.x = x;
        event.y = y;
        event.z = z;
        event.action = action;
        ZoneStateFile zf = getZoneState(zoneId);
        zf.recentEvents.add(0, event);
        if (zf.recentEvents.size() > 200) {
            zf.recentEvents.remove(zf.recentEvents.size() - 1);
        }
        markDirty(zoneId);
    }

    private static void maybeCleanupRateLimit() {
        long now = System.currentTimeMillis();
        if (now - lastRateLimitCleanup < EVENT_RATE_LIMIT_CLEANUP_INTERVAL) {
            return;
        }
        lastRateLimitCleanup = now;
        long cutoff = now - EVENT_RATE_LIMIT_MILLIS * 2;
        lastEventTimes.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    public static void clearZoneState(String zoneId) {
        states.remove(zoneId);
        dirtyZoneIds.remove(zoneId);
        try {
            Files.deleteIfExists(getZoneStatePath(zoneId));
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to delete zone state file: " + zoneId, e);
        }
    }

    public static void clearRuleState(String zoneId, String ruleId) {
        ZoneStateFile zf = states.get(zoneId);
        if (zf != null) {
            zf.rules.remove(ruleId);
            markDirty(zoneId);
            saveZone(zoneId);
        }
    }
}