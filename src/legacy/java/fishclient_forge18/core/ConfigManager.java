package com.fishclient.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.fishclient.FishClientMod;
import com.fishclient.modules.Module;
import com.fishclient.modules.radio.RadioManager;
import com.fishclient.modules.radio.RadioStation;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {

    private static final String CONFIG_DIR_NAME = "fishclient";
    private static final String CONFIG_FILE = "config.json";

    private final Path configDir;
    private final Path configFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ConfigManager() {
        File mcDir = Minecraft.getMinecraft().mcDataDir;
        configDir = new File(mcDir, CONFIG_DIR_NAME).toPath();
        configFile = configDir.resolve(CONFIG_FILE);
    }

    public void save() {
        try {
            Files.createDirectories(configDir);

            JsonObject root = new JsonObject();

            JsonObject modules = new JsonObject();
            for (Module module : FishClientMod.moduleManager.getModules()) {
                JsonObject mod = new JsonObject();
                mod.addProperty("enabled", module.isEnabled());
                mod.addProperty("keybind", module.getKeybind());
                modules.add(module.getName(), mod);
            }
            root.add("modules", modules);

            JsonArray customStations = new JsonArray();
            for (RadioStation station : RadioManager.getInstance().getAllStations()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", station.getName());
                obj.addProperty("genre", station.getGenre());
                obj.addProperty("url", station.getStreamUrl());
                customStations.add(obj);
            }
            root.add("customStations", customStations);
            root.addProperty("radioVolume", RadioManager.getInstance().getVolume());

            Files.write(configFile, gson.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("[FishClient Config] Save failed: " + e.getMessage());
        }
    }

    public void load() {
        if (!Files.exists(configFile)) {
            return;
        }

        try {
            String content = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
            JsonObject root = new JsonParser().parse(content).getAsJsonObject();

            if (root.has("modules")) {
                JsonObject modules = root.getAsJsonObject("modules");
                for (Module module : FishClientMod.moduleManager.getModules()) {
                    if (modules.has(module.getName())) {
                        JsonObject mod = modules.getAsJsonObject(module.getName());
                        if (mod.has("enabled")) {
                            module.setEnabled(mod.get("enabled").getAsBoolean());
                        }
                        if (mod.has("keybind")) {
                            module.setKeybind(mod.get("keybind").getAsInt());
                        }
                    }
                }
            }

            RadioManager radioManager = RadioManager.getInstance();
            if (root.has("customStations") && root.get("customStations").isJsonArray()) {
                radioManager.clearCustomStations();
                JsonArray stations = root.getAsJsonArray("customStations");
                for (int i = 0; i < stations.size(); i++) {
                    JsonObject obj = stations.get(i).getAsJsonObject();
                    if (obj.has("name") && obj.has("genre") && obj.has("url")) {
                        radioManager.addStation(new RadioStation(
                            obj.get("name").getAsString(),
                            obj.get("genre").getAsString(),
                            obj.get("url").getAsString()
                        ));
                    }
                }
            }

            if (root.has("radioVolume")) {
                radioManager.setVolume(root.get("radioVolume").getAsFloat());
            }
        } catch (Exception e) {
            System.err.println("[FishClient Config] Load failed: " + e.getMessage());
        }
    }
}

