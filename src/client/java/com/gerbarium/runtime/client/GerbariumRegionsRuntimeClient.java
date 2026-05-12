package com.gerbarium.runtime.client;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.client.network.GerbariumRuntimeClientNetworking;
import net.fabricmc.api.ClientModInitializer;

public class GerbariumRegionsRuntimeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GerbariumRegionsRuntime.LOGGER.info("Gerbarium Regions Runtime client initializing...");
        GerbariumRuntimeClientNetworking.register();
        GerbariumRegionsRuntime.LOGGER.info("Gerbarium Regions Runtime client initialized.");
    }
}