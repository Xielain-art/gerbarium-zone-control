package com.gerbarium.runtime;

import com.gerbarium.runtime.command.RuntimeCommands;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.network.GerbariumRuntimeServerNetworking;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneLoader;
import com.gerbarium.runtime.tick.RuntimeTickHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GerbariumRegionsRuntime implements ModInitializer {
    public static final String MOD_ID = "gerbarium_regions_runtime";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Gerbarium Regions Runtime initializing...");

        // Load Config and State
        RuntimeConfigStorage.load();
        RuntimeStateStorage.load();

        // Register Commands and Networking
        RuntimeCommands.register();
        GerbariumRuntimeServerNetworking.register();
        MobTracker.register();

        // Lifecycle Events
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ZoneLoader.loadAll();
            RuntimeStateStorage.loadAll(ZoneRepository.getAll());
            RuntimeTickHandler.register();
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RuntimeStateStorage.save();
        });

        LOGGER.info("Gerbarium Regions Runtime initialized.");
    }
}