package com.gerbarium.runtime.config;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class RuntimeConfigStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("gerbarium/zones-control");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("runtime.json");

    private static RuntimeConfig config = new RuntimeConfig();

    public static RuntimeConfig getConfig() {
        return config;
    }

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Path path = CONFIG_FILE;

            if (Files.exists(path)) {
                try (FileReader reader = new FileReader(path.toFile())) {
                    config = GSON.fromJson(reader, RuntimeConfig.class);
                    if (config == null) {
                        config = new RuntimeConfig();
                    }
                    normalizeConfig();
                }
            } else {
                normalizeConfig();
                save();
            }
        } catch (Exception e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to load runtime config", e);
            config = new RuntimeConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Path tempPath = CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp");
            try (FileWriter writer = new FileWriter(tempPath.toFile())) {
                GSON.toJson(config, writer);
            }
            Files.move(tempPath, CONFIG_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to save runtime config", e);
            try {
                Files.deleteIfExists(CONFIG_FILE.resolveSibling(CONFIG_FILE.getFileName() + ".tmp"));
            } catch (IOException ignored) {
            }
        }
    }

    private static void normalizeConfig() {
        RuntimeConfig defaults = new RuntimeConfig();
        config.activationCheckIntervalTicks = positive(config.activationCheckIntervalTicks, defaults.activationCheckIntervalTicks);
        config.spawnCheckIntervalTicks = positive(config.spawnCheckIntervalTicks, defaults.spawnCheckIntervalTicks);
        config.resyncIntervalTicks = positive(config.resyncIntervalTicks, defaults.resyncIntervalTicks);
        config.stateSaveIntervalTicks = positive(config.stateSaveIntervalTicks, defaults.stateSaveIntervalTicks);
        config.maxSpawnsPerTickCycle = positive(config.maxSpawnsPerTickCycle, defaults.maxSpawnsPerTickCycle);
        config.maxZonesProcessedPerSpawnTick = positive(config.maxZonesProcessedPerSpawnTick, defaults.maxZonesProcessedPerSpawnTick);
        config.autoReloadCheckIntervalTicks = positive(config.autoReloadCheckIntervalTicks, defaults.autoReloadCheckIntervalTicks);
        config.boundaryGlobalCheckIntervalTicks = positive(config.boundaryGlobalCheckIntervalTicks, defaults.boundaryGlobalCheckIntervalTicks);
        config.boundaryScanPadding = Math.max(0, config.boundaryScanPadding);
        if (config.boundaryMaxEntitiesPerTick <= 0) {
            config.boundaryMaxEntitiesPerTick = defaults.boundaryMaxEntitiesPerTick;
        }
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
