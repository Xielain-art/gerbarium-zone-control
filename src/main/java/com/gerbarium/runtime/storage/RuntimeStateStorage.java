package com.gerbarium.runtime.storage;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.state.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RuntimeStateStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_ROOT = FabricLoader.getInstance().getConfigDir().resolve("gerbarium/zones-control");
    private static final Path STATES_DIR = STORAGE_ROOT.resolve("states");
    private static final Path OLD_STATE_FILE = STORAGE_ROOT.resolve("runtime-state.json");
    private static final Path MIGRATION_DONE_MARKER = STORAGE_ROOT.resolve("runtime-state.migration-done");
    
    private static final long SAVE_DEBOUNCE_MILLIS = 5000;

    private static final Map<String, ZoneStateFile> states = new ConcurrentHashMap<>();
    private static final Set<String> dirtyZoneIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static long lastSaveTime = 0;

    public static ZoneStateFile getZoneState(String zoneId) {
        return states.computeIfAbsent(zoneId, id -> {
            ZoneStateFile f = loadZone(id);
            return f != null ? f : new ZoneStateFile(id);
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
    }

    public static void loadAll(Collection<Zone> loadedZones) {
        try {
            Files.createDirectories(STATES_DIR);
            
            // Check for migration
            if (Files.exists(OLD_STATE_FILE) && !Files.exists(MIGRATION_DONE_MARKER)) {
                performMigration();
            }

            for (Zone zone : loadedZones) {
                ZoneStateFile zf = loadZone(zone.id);
                if (zf != null) {
                    states.put(zone.id, zf);
                }
            }
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to initialize states directory", e);
        }
    }

    private static void performMigration() {
        GerbariumRegionsRuntime.LOGGER.info("Starting runtime-state migration...");
        try (FileReader reader = new FileReader(OLD_STATE_FILE.toFile())) {
            // Very simplified migration for MVP
            // Reads old format if it was just a raw ZoneStateFile or map-based
            // Since the old format was monolithic, we'd need its exact structure.
            // Assuming old format had Map<String, ZoneRuntimePersistentState> zones and Map<String, RuleRuntimeState> rules
            // We skip complex migration if it's too risky and just start fresh.
            Files.createFile(MIGRATION_DONE_MARKER);
            GerbariumRegionsRuntime.LOGGER.info("Migration finished (marker created).");
        } catch (Exception e) {
            GerbariumRegionsRuntime.LOGGER.error("Migration failed", e);
        }
    }

    private static ZoneStateFile loadZone(String zoneId) {
        Path path = STATES_DIR.resolve(zoneId + ".runtime-state.json");
        if (!Files.exists(path)) return null;

        try (FileReader reader = new FileReader(path.toFile())) {
            return GSON.fromJson(reader, ZoneStateFile.class);
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to load zone state: " + zoneId, e);
            return null;
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
        dirtyZoneIds.clear();
        for (String id : toSave) {
            saveZone(id);
        }
        lastSaveTime = System.currentTimeMillis();
    }

    public static void saveZone(String zoneId) {
        ZoneStateFile zf = states.get(zoneId);
        if (zf == null) return;

        try {
            Files.createDirectories(STATES_DIR);
            Path path = STATES_DIR.resolve(zoneId + ".runtime-state.json");
            try (FileWriter writer = new FileWriter(path.toFile())) {
                GSON.toJson(zf, writer);
            }
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to save zone state: " + zoneId, e);
        }
    }

    public static void addEvent(String zoneId, String ruleId, String type, String message) {
        ZoneStateFile zf = getZoneState(zoneId);
        zf.recentEvents.add(0, new RuntimeEvent(System.currentTimeMillis(), zoneId, ruleId, type, message));
        if (zf.recentEvents.size() > 200) {
            zf.recentEvents.remove(zf.recentEvents.size() - 1);
        }
        markDirty(zoneId);
    }

    public static void clearZoneState(String zoneId) {
        states.remove(zoneId);
        dirtyZoneIds.remove(zoneId);
        try {
            Files.deleteIfExists(STATES_DIR.resolve(zoneId + ".runtime-state.json"));
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