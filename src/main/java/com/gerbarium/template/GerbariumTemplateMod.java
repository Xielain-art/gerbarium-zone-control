package com.gerbarium.template;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GerbariumTemplateMod implements ModInitializer {
    public static final String MOD_ID = "gerbarium_template";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Gerbarium Template initialized");
    }
}