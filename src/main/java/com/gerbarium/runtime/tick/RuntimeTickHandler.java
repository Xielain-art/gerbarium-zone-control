package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.config.RuntimeConfig;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.tracking.MobTracker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class RuntimeTickHandler {
    private static long tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RuntimeTickHandler::onTick);
    }

    private static void onTick(MinecraftServer server) {
        tickCounter++;
        RuntimeConfig config = RuntimeConfigStorage.getConfig();

        if (tickCounter % config.activationCheckIntervalTicks == 0) {
            ZoneActivationManager.update(server);
        }

        if (tickCounter % config.spawnCheckIntervalTicks == 0) {
            ZoneMobSpawner.tick(server);
        }

        if (tickCounter % Math.max(1, config.boundaryGlobalCheckIntervalTicks) == 0) {
            BoundaryControlManager.tick(server);
        }

        if (tickCounter % config.resyncIntervalTicks == 0) {
            MobTracker.resyncActiveZones(server);
        }

        if (tickCounter % config.stateSaveIntervalTicks == 0) {
            RuntimeStateStorage.saveIfDirty();
        }
    }
}