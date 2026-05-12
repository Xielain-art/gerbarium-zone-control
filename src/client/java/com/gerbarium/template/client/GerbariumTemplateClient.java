package com.gerbarium.template.client;

import com.gerbarium.template.GerbariumTemplateMod;
import net.fabricmc.api.ClientModInitializer;

public class GerbariumTemplateClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        GerbariumTemplateMod.LOGGER.info("Gerbarium Template client initialized");
    }
}