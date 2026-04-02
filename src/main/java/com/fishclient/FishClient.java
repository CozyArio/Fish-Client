package com.fishclient;

import com.fishclient.module.core.ModuleRegistry;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FishClient implements ModInitializer {

    public static final String MOD_ID = "fishclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ModuleRegistry MODULES = new ModuleRegistry();

    @Override
    public void onInitialize() {
        MODULES.bootstrap();
        LOGGER.info("Fish Client initialized for Minecraft 1.21.11");
    }

    public static ModuleRegistry modules() {
        return MODULES;
    }
}
