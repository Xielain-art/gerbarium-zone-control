package com.gerbarium.runtime.tick;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.config.RuntimeConfig;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.tracking.MobTracker;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class RuntimeTickHandler {
    private static long tickCounter = 0;
    private static boolean registered = false;

    public static void register() {
        if (registered) {
            return;
        }
        ServerTickEvents.END_SERVER_TICK.register(RuntimeTickHandler::onTick);
        registered = true;
        GerbariumRegionsRuntime.LOGGER.info("[GerbariumRuntime] Tick registered successfully");
    }

    public static boolean isRegistered() {
        return registered;
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
            BoundaryControlManager.tick(server, tickCounter);
        }

        if (tickCounter % config.resyncIntervalTicks == 0) {
            MobTracker.resyncActiveZones(server);
        }

        if (tickCounter % config.stateSaveIntervalTicks == 0) {
            RuntimeStateStorage.saveIfDirty();
        }
    }
}
