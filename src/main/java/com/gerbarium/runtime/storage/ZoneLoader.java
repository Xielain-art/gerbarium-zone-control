package com.gerbarium.runtime.storage;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.model.Zone;
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
            File[] files = ZONES_DIR.toFile().listFiles((dir, name) -> name.endsWith(".json"));

            if (files == null) {
                ZoneRepository.replaceAll(new ArrayList<>());
                return;
            }

            List<Zone> loadedZones = new ArrayList<>();
            for (File file : files) {
                try {
                    Zone zone = loadZone(file);
                    if (zone != null) {
                        loadedZones.add(zone);
                    }
                } catch (Exception e) {
                    GerbariumRegionsRuntime.LOGGER.error("Failed to load zone file: " + file.getName(), e);
                }
            }
            ZoneRepository.replaceAll(loadedZones);
            GerbariumRegionsRuntime.LOGGER.info("Loaded " + loadedZones.size() + " zones.");
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to ensure zones directory exists", e);
        }
    }

    private static Zone loadZone(File file) {
        String filename = file.getName();
        String expectedId = filename.substring(0, filename.length() - 5);

        try (FileReader reader = new FileReader(file)) {
            Zone zone = GSON.fromJson(reader, Zone.class);
            if (zone == null) {
                GerbariumRegionsRuntime.LOGGER.error("Zone file is empty: " + filename);
                return null;
            }

            // Normalize null collections and enums before validation
            zone.normalize();

            if (zone.id == null || zone.id.isBlank()) {
                GerbariumRegionsRuntime.LOGGER.error("Zone file has missing id: " + filename);
                return null;
            }

            if (!expectedId.equals(zone.id)) {
                GerbariumRegionsRuntime.LOGGER.error("Zone ID mismatch in " + filename + ": expected '" + expectedId + "', found '" + zone.id + "'");
                return null;
            }

            if (!SAFE_ZONE_ID.matcher(zone.id).matches()) {
                GerbariumRegionsRuntime.LOGGER.error("Zone ID has invalid characters in " + filename + ": " + zone.id);
                return null;
            }

            if (zone.min == null || zone.max == null) {
                GerbariumRegionsRuntime.LOGGER.error("Zone " + zone.id + " has missing min/max coordinates");
                return null;
            }

            if (zone.dimension == null || zone.dimension.isBlank()) {
                GerbariumRegionsRuntime.LOGGER.error("Zone " + zone.id + " has missing dimension");
                return null;
            }

            // Warn about inverted bounds (not fatal, code uses Math.min/max defensively)
            if (zone.getMinX() > zone.getMaxX() || zone.getMinY() > zone.getMaxY() || zone.getMinZ() > zone.getMaxZ()) {
                GerbariumRegionsRuntime.LOGGER.warn("Zone " + zone.id + " has inverted min/max bounds. This may cause unexpected behavior.");
            }

            // Validate mob rules
            for (int i = 0; i < zone.mobs.size(); i++) {
                var rule = zone.mobs.get(i);
                if (rule.id == null || rule.id.isBlank()) {
                    GerbariumRegionsRuntime.LOGGER.error("Zone " + zone.id + " mob rule at index " + i + " has missing id, skipping rule");
                    zone.mobs.set(i, null);
                    continue;
                }
                if (rule.entity == null || rule.entity.isBlank()) {
                    GerbariumRegionsRuntime.LOGGER.warn("Zone " + zone.id + " rule " + rule.id + " has missing entity");
                }
                if (rule.spawnType == null) {
                    GerbariumRegionsRuntime.LOGGER.warn("Zone " + zone.id + " rule " + rule.id + " has invalid spawnType, defaulting to PACK");
                    rule.spawnType = com.gerbarium.runtime.model.SpawnType.PACK;
                }
                if (rule.refillMode == null) {
                    GerbariumRegionsRuntime.LOGGER.warn("Zone " + zone.id + " rule " + rule.id + " has invalid refillMode, defaulting to ON_ACTIVATION");
                    rule.refillMode = com.gerbarium.runtime.model.RefillMode.ON_ACTIVATION;
                }
                if (com.gerbarium.runtime.model.BoundaryMode.from(rule.boundaryMode) == null) {
                    GerbariumRegionsRuntime.LOGGER.warn("Zone " + zone.id + " rule " + rule.id + " has invalid boundaryMode: " + rule.boundaryMode);
                }
            }

            // Remove null entries from failed validations
            zone.mobs.removeIf(rule -> rule == null);

            return zone;
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Error reading zone file: " + filename, e);
            return null;
        }
    }
}