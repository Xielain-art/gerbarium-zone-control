package com.gerbarium.runtime.config;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
            File file = CONFIG_FILE.toFile();

            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    config = GSON.fromJson(reader, RuntimeConfig.class);
                    if (config == null) {
                        config = new RuntimeConfig();
                    }
                }
            } else {
                save();
            }
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to load runtime config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (FileWriter writer = new FileWriter(CONFIG_FILE.toFile())) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            GerbariumRegionsRuntime.LOGGER.error("Failed to save runtime config", e);
        }
    }
}