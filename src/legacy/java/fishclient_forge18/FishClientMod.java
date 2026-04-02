package com.fishclient;

import com.fishclient.core.ConfigManager;
import com.fishclient.core.EventBus;
import com.fishclient.core.ModuleManager;
import com.fishclient.modules.radio.RadioManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;

@Mod(modid = FishClientMod.MOD_ID, name = FishClientMod.NAME, version = FishClientMod.VERSION)
public class FishClientMod {

    public static final String MOD_ID = "fishclient";
    public static final String NAME = "Fish Client";
    public static final String VERSION = "1.0.0";

    public static FishClientMod INSTANCE;
    public static ModuleManager moduleManager;
    public static ConfigManager configManager;
    public static RadioManager radioManager;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        INSTANCE = this;
        configManager = new ConfigManager();
        moduleManager = new ModuleManager();
        radioManager = RadioManager.getInstance();

        moduleManager.registerAll();
        configManager.load();
        EventBus.register();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Reserved for post-init hooks.
    }
}

