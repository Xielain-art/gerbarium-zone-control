package com.gerbarium.runtime.storage;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.model.ZoneSpawnSettings;
import com.gerbarium.runtime.storage.modular.MobRulesFile;
import com.gerbarium.runtime.storage.modular.ZoneBase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ZoneLoader {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path ZONES_DIR = FabricLoader.getInstance().getConfigDir().resolve("gerbarium/zones");
    private static final Pattern SAFE_ZONE_ID = Pattern.compile("^[a-z0-9_-]+$");

    public static void loadAll() {
        try {
            Files.createDirectories(ZONES_DIR);
            File rootDir = ZONES_DIR.toFile();

            if (!rootDir.isDirectory()) {
                ZoneRepository.replaceAll(new ArrayList<>());
                return;
            }

            // Warn about legacy flat .json files under root
            File[] rootFiles = rootDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (rootFiles != null) {
                for (File legacyFile : rootFiles) {
                    GerbariumRegionsRuntime.LOGGER.warn(
                            "Ignoring legacy flat zone file " + legacyFile.getName()
                                    + ". Modular folder format is required.");
                }
            }

            // Load modular zone folders
            File[] subdirs = rootDir.listFiles(File::isDirectory);
            if (subdirs == null) {
                ZoneRepository.replaceAll(new ArrayList<>());
                return;
            }

            List<Zone> loadedZones = new ArrayList<>();
            for (File dir : subdirs) {
                try {
                    Zone zone = loadModularZone(dir);
                    if (zone != null) {
                        loadedZones.add(zone);
                    }
                } catch (Exception e) {
                    GerbariumRegionsRuntime.LOGGER.error(
                            "Failed to load modular zone: " + dir.getName(), e);
                }
            }
            ZoneRepository.replaceAll(loadedZones);
            int activeZones = (int) loadedZones.stream()
                    .filter(zone -> zone.enabled)
                    .count();
            int totalRules = loadedZones.stream()
                    .mapToInt(zone -> zone.mobs == null ? 0 : zone.mobs.size())
                    .sum();
            int activeRules = loadedZones.stream()
                    .mapToInt(zone -> zone.mobs == null ? 0 : (int) zone.mobs.stream()
                            .filter(rule -> rule != null && rule.enabled && rule.spawnWhenReady)
                            .count())
                    .sum();
            GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Loaded zones: total={} active={}", loadedZones.size(), activeZones);
            GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Loaded mob rules: total={} active={}", totalRules, activeRules);
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to ensure zones directory exists", e);
        }
    }

    private static Zone loadModularZone(File dir) {
        String folderName = dir.getName();
        File zoneJson = new File(dir, "zone.json");

        if (!zoneJson.exists()) {
            GerbariumRegionsRuntime.LOGGER.error(
                    "Zone folder " + folderName + " missing zone.json, skipping.");
            return null;
        }

        // Load zone.json
        ZoneBase base;
        try (FileReader reader = new FileReader(zoneJson)) {
            base = GSON.fromJson(reader, ZoneBase.class);
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error(
                    "Error reading zone.json for folder " + folderName, e);
            return null;
        }

        if (base == null) {
            GerbariumRegionsRuntime.LOGGER.error(
                    "zone.json is empty in folder " + folderName);
            return null;
        }

        // Validate zone id
        if (base.id == null || base.id.isBlank()) {
            GerbariumRegionsRuntime.LOGGER.error(
                    "zone.json in folder " + folderName + " has missing id, skipping.");
            return null;
        }

        if (!folderName.equals(base.id)) {
            GerbariumRegionsRuntime.LOGGER.error(
                    "Zone ID mismatch in folder " + folderName
                            + ": zone.json id is '" + base.id + "', expected '" + folderName + "'. Skipping.");
            return null;
        }

        if (!SAFE_ZONE_ID.matcher(base.id).matches()) {
            GerbariumRegionsRuntime.LOGGER.error(
                    "Zone ID has invalid characters in folder " + folderName + ": " + base.id);
            return null;
        }

        if (base.min == null || base.max == null) {
            GerbariumRegionsRuntime.LOGGER.error(
                    "Zone " + base.id + " has missing min/max coordinates");
            return null;
        }

        if (base.dimension == null || base.dimension.isBlank()) {
            GerbariumRegionsRuntime.LOGGER.error(
                    "Zone " + base.id + " has missing dimension");
            return null;
        }

        // Build runtime Zone from zone.json base
        Zone zone = new Zone();
        zone.id = base.id;
        zone.name = base.name;
        zone.enabled = base.enabled;
        zone.dimension = base.dimension;
        zone.min = base.min;
        zone.max = base.max;
        if (base.activation != null) {
            zone.activation = base.activation;
        }

        // Warn about inverted bounds before normalization.
        if (ZoneBoundsUtil.hasInvertedBounds(base)) {
            GerbariumRegionsRuntime.LOGGER.warn(
                    "Zone " + zone.id + " has inverted min/max bounds. This may cause unexpected behavior.");
        }

        // Load mobs.json if present
        File mobsJson = new File(dir, "mobs.json");
        if (mobsJson.exists()) {
            loadMobsModule(mobsJson, folderName, zone);
        } else {
            zone.spawn = new ZoneSpawnSettings();
            zone.mobs = new ArrayList<>();
            GerbariumRegionsRuntime.LOGGER.info(
                    "No mobs.json for zone " + zone.id + "; loading empty mob rules.");
        }

        // Normalize
        zone.normalize();

        return zone;
    }

    private static void loadMobsModule(File mobsJson, String folderName, Zone zone) {
        MobRulesFile mobsFile;
        try (FileReader reader = new FileReader(mobsJson)) {
            mobsFile = GSON.fromJson(reader, MobRulesFile.class);
        } catch (Exception e) {
            GerbariumRegionsRuntime.LOGGER.error(
                    "Error reading mobs.json for folder " + folderName + ", using empty mob rules.", e);
            zone.spawn = new ZoneSpawnSettings();
            zone.mobs = new ArrayList<>();
            return;
        }

        if (mobsFile == null) {
            GerbariumRegionsRuntime.LOGGER.warn(
                    "mobs.json is empty for zone " + zone.id + "; using empty mob rules.");
            zone.spawn = new ZoneSpawnSettings();
            zone.mobs = new ArrayList<>();
            return;
        }

        // Validate mobs.json zoneId matches
        if (mobsFile.zoneId == null || !folderName.equals(mobsFile.zoneId)) {
            GerbariumRegionsRuntime.LOGGER.error(
                    "mobs.json zoneId mismatch in folder " + folderName
                            + ": mobs.json zoneId is '" + mobsFile.zoneId + "', expected '" + folderName
                            + "'. Loading zone with empty mob rules.");
            zone.spawn = new ZoneSpawnSettings();
            zone.mobs = new ArrayList<>();
            return;
        }

        // Apply spawn settings
        if (mobsFile.spawn != null) {
            zone.spawn = mobsFile.spawn;
        } else {
            zone.spawn = new ZoneSpawnSettings();
        }

        // Apply mob rules
        if (mobsFile.rules == null) {
            zone.mobs = new ArrayList<>();
        } else {
            zone.mobs = new ArrayList<>(mobsFile.rules);
        }

        // Validate mob rules
        validateMobRules(zone);
    }

    private static void validateMobRules(Zone zone) {
        for (int i = 0; i < zone.mobs.size(); i++) {
            var rule = zone.mobs.get(i);
            if (rule == null) {
                continue;
            }
            if (rule.id == null || rule.id.isBlank()) {
                GerbariumRegionsRuntime.LOGGER.error(
                        "Zone " + zone.id + " mob rule at index " + i + " has missing id, skipping rule");
                zone.mobs.set(i, null);
                continue;
            }
            if (rule.entity == null || rule.entity.isBlank()) {
                GerbariumRegionsRuntime.LOGGER.warn(
                        "Zone " + zone.id + " rule " + rule.id + " has missing entity");
            }
            if (rule.spawnType == null) {
                GerbariumRegionsRuntime.LOGGER.warn(
                        "Zone " + zone.id + " rule " + rule.id + " has invalid spawnType, defaulting to PACK");
                rule.spawnType = com.gerbarium.runtime.model.SpawnType.PACK;
            }
            if (rule.refillMode == null) {
                GerbariumRegionsRuntime.LOGGER.warn(
                        "Zone " + zone.id + " rule " + rule.id + " has invalid refillMode, defaulting to ON_ACTIVATION");
                rule.refillMode = com.gerbarium.runtime.model.RefillMode.ON_ACTIVATION;
            }
            if (com.gerbarium.runtime.model.BoundaryMode.from(rule.boundaryMode) == null) {
                GerbariumRegionsRuntime.LOGGER.warn(
                        "Zone " + zone.id + " rule " + rule.id + " has invalid boundaryMode: " + rule.boundaryMode);
            }
        }

        // Remove null entries from failed validations
        zone.mobs.removeIf(rule -> rule == null);
    }
}
