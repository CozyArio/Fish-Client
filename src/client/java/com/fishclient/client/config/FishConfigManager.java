package com.fishclient.client.config;

import com.fishclient.client.ui.FishClickScreen;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FishConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_MODULE = "Config Manager";
    private static final String DEFAULT_CONFIG = "default";

    private static final Path CONFIG_DIR = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("fishclient")
        .resolve("configs");
    private static final Path LAST_FILE = CONFIG_DIR.resolve("last_config.txt");

    private static boolean initialized;

    private FishConfigManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ensureDirectories();
        if (listConfigNames().isEmpty()) {
            saveConfig(DEFAULT_CONFIG);
            writeLastConfig(DEFAULT_CONFIG);
        }
        String toLoad = readLastConfig();
        if (toLoad.isBlank()) {
            toLoad = DEFAULT_CONFIG;
        }
        if (!loadConfig(toLoad)) {
            if (!DEFAULT_CONFIG.equals(toLoad)) {
                loadConfig(DEFAULT_CONFIG);
                writeLastConfig(DEFAULT_CONFIG);
            }
        }
        syncUiSettings();
    }

    public static synchronized void syncUiSettings() {
        FishClickScreen.ModuleEntry module = findConfigModule();
        if (module == null) {
            return;
        }
        List<String> names = listConfigNames();
        if (names.isEmpty()) {
            names = new ArrayList<>();
            names.add(DEFAULT_CONFIG);
        }
        FishClickScreen.SettingEntry configSelect = findSetting(module, "Config");
        if (configSelect != null && configSelect.type == FishClickScreen.SettingEntry.Type.SELECT) {
            String current = selectedOption(configSelect);
            setOptions(configSelect, names, current);
        }
    }

    public static synchronized boolean handleSelectAction(FishClickScreen.ModuleEntry module, FishClickScreen.SettingEntry setting) {
        if (module == null || setting == null) {
            return false;
        }
        if (!CONFIG_MODULE.equalsIgnoreCase(module.name)) {
            return false;
        }
        if (setting.type != FishClickScreen.SettingEntry.Type.SELECT) {
            return false;
        }
        String label = safe(setting.label);
        if ("Config".equalsIgnoreCase(label)) {
            return false;
        }
        if (setting.selectIndex != 1) {
            return false;
        }

        boolean changed = false;
        String selected = getSelectedConfigName(module);
        String configName = getText(module, "Config Name").trim();
        if (configName.isBlank()) {
            configName = selected;
        }

        if ("Save Selected".equalsIgnoreCase(label)) {
            String saved = saveConfig(selected);
            changed = true;
            setStatus(module, saved.isBlank() ? "Save failed." : "Saved: " + saved);
            if (!saved.isBlank()) {
                writeLastConfig(saved);
            }
        } else if ("Save As Name".equalsIgnoreCase(label)) {
            String saved = saveConfig(configName);
            changed = true;
            if (saved.isBlank()) {
                setStatus(module, "Invalid name.");
            } else {
                writeLastConfig(saved);
                setText(module, "Config Name", saved);
                setStatus(module, "Saved new: " + saved);
                selected = saved;
            }
        } else if ("Load Selected".equalsIgnoreCase(label)) {
            changed = loadConfig(selected) || changed;
            if (changed) {
                writeLastConfig(selected);
                setStatus(module, "Loaded: " + selected);
            } else {
                setStatus(module, "Load failed: " + selected);
            }
        } else if ("Delete Selected".equalsIgnoreCase(label)) {
            if (DEFAULT_CONFIG.equalsIgnoreCase(selected)) {
                setStatus(module, "Can't delete default.");
            } else if (deleteConfig(selected)) {
                changed = true;
                String fallback = DEFAULT_CONFIG;
                if (!loadConfig(fallback)) {
                    saveConfig(fallback);
                    loadConfig(fallback);
                }
                writeLastConfig(fallback);
                setStatus(module, "Deleted: " + selected);
                selected = fallback;
            } else {
                setStatus(module, "Delete failed: " + selected);
            }
        } else if ("Refresh List".equalsIgnoreCase(label)) {
            setStatus(module, "Refreshed.");
            changed = true;
        }

        setting.selectIndex = 0;
        refreshOptions(module, selected);
        return changed;
    }

    public static synchronized String saveConfig(String rawName) {
        ensureDirectories();
        String name = sanitizeName(rawName);
        if (name.isBlank()) {
            return "";
        }
        Path file = configFile(name);
        JsonObject root = new JsonObject();
        JsonArray modules = new JsonArray();

        for (FishClickScreen.ModuleEntry module : FishClickScreen.SHARED_MODULES) {
            if (CONFIG_MODULE.equalsIgnoreCase(module.name)) {
                continue;
            }
            JsonObject m = new JsonObject();
            m.addProperty("name", module.name);
            m.addProperty("enabled", module.enabled);
            m.addProperty("keybind", module.keybind);

            JsonArray settings = new JsonArray();
            for (FishClickScreen.SettingEntry setting : module.settings) {
                JsonObject s = new JsonObject();
                s.addProperty("label", setting.label);
                s.addProperty("type", setting.type.name());
                switch (setting.type) {
                    case TOGGLE -> s.addProperty("value", setting.boolValue);
                    case SLIDER -> s.addProperty("value", setting.sliderValue);
                    case SELECT -> s.addProperty("value", setting.selectIndex);
                    case TEXT -> s.addProperty("value", safe(setting.textValue));
                }
                settings.add(s);
            }
            m.add("settings", settings);
            modules.add(m);
        }
        root.add("modules", modules);

        try {
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            return name;
        } catch (IOException ignored) {
            return "";
        }
    }

    public static synchronized boolean loadConfig(String rawName) {
        String name = sanitizeName(rawName);
        if (name.isBlank()) {
            return false;
        }
        Path file = configFile(name);
        if (!Files.exists(file)) {
            return false;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonArray modules = root.getAsJsonArray("modules");
            if (modules == null) {
                return false;
            }
            for (JsonElement moduleElement : modules) {
                if (!moduleElement.isJsonObject()) {
                    continue;
                }
                JsonObject moduleJson = moduleElement.getAsJsonObject();
                String moduleName = safe(moduleJson.get("name"));
                FishClickScreen.ModuleEntry module = findModule(moduleName);
                if (module == null || CONFIG_MODULE.equalsIgnoreCase(module.name)) {
                    continue;
                }
                if (moduleJson.has("enabled")) {
                    module.enabled = moduleJson.get("enabled").getAsBoolean();
                    module.toggleAnim = module.enabled ? 1.0f : 0.0f;
                    module.toggleAnimMs = System.currentTimeMillis();
                }
                if (moduleJson.has("keybind")) {
                    module.keybind = moduleJson.get("keybind").getAsInt();
                }
                JsonArray settings = moduleJson.getAsJsonArray("settings");
                if (settings == null) {
                    continue;
                }
                for (JsonElement settingElement : settings) {
                    if (!settingElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject settingJson = settingElement.getAsJsonObject();
                    String label = safe(settingJson.get("label"));
                    FishClickScreen.SettingEntry setting = findSetting(module, label);
                    if (setting == null || !settingJson.has("value")) {
                        continue;
                    }
                    switch (setting.type) {
                        case TOGGLE -> setting.boolValue = settingJson.get("value").getAsBoolean();
                        case SLIDER -> setting.sliderValue = settingJson.get("value").getAsDouble();
                        case SELECT -> setting.selectIndex = clampIndex(setting, settingJson.get("value").getAsInt());
                        case TEXT -> setting.textValue = safe(settingJson.get("value"));
                    }
                }
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static synchronized boolean deleteConfig(String rawName) {
        String name = sanitizeName(rawName);
        if (name.isBlank()) {
            return false;
        }
        Path file = configFile(name);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void refreshOptions(FishClickScreen.ModuleEntry module, String preferred) {
        List<String> names = listConfigNames();
        if (names.isEmpty()) {
            names.add(DEFAULT_CONFIG);
        }
        FishClickScreen.SettingEntry select = findSetting(module, "Config");
        if (select == null) {
            return;
        }
        setOptions(select, names, preferred);
    }

    private static void setOptions(FishClickScreen.SettingEntry select, List<String> options, String preferred) {
        if (select.type != FishClickScreen.SettingEntry.Type.SELECT || options.isEmpty()) {
            return;
        }
        select.selectOptions = options.toArray(new String[0]);
        int idx = options.indexOf(preferred);
        if (idx < 0) {
            idx = 0;
        }
        select.selectIndex = idx;
    }

    private static String getSelectedConfigName(FishClickScreen.ModuleEntry module) {
        FishClickScreen.SettingEntry select = findSetting(module, "Config");
        if (select == null || select.type != FishClickScreen.SettingEntry.Type.SELECT || select.selectOptions == null || select.selectOptions.length == 0) {
            return DEFAULT_CONFIG;
        }
        int idx = clampIndex(select, select.selectIndex);
        return safe(select.selectOptions[idx]);
    }

    private static int clampIndex(FishClickScreen.SettingEntry select, int idx) {
        if (select.selectOptions == null || select.selectOptions.length == 0) {
            return 0;
        }
        return Math.max(0, Math.min(select.selectOptions.length - 1, idx));
    }

    private static String getText(FishClickScreen.ModuleEntry module, String label) {
        FishClickScreen.SettingEntry setting = findSetting(module, label);
        if (setting == null || setting.type != FishClickScreen.SettingEntry.Type.TEXT) {
            return "";
        }
        return safe(setting.textValue);
    }

    private static void setText(FishClickScreen.ModuleEntry module, String label, String value) {
        FishClickScreen.SettingEntry setting = findSetting(module, label);
        if (setting == null || setting.type != FishClickScreen.SettingEntry.Type.TEXT) {
            return;
        }
        setting.textValue = safe(value);
    }

    private static void setStatus(FishClickScreen.ModuleEntry module, String status) {
        setText(module, "Status", status);
    }

    private static FishClickScreen.ModuleEntry findConfigModule() {
        return findModule(CONFIG_MODULE);
    }

    private static FishClickScreen.ModuleEntry findModule(String name) {
        for (FishClickScreen.ModuleEntry module : FishClickScreen.SHARED_MODULES) {
            if (module.name.equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    private static FishClickScreen.SettingEntry findSetting(FishClickScreen.ModuleEntry module, String label) {
        if (module == null) {
            return null;
        }
        for (FishClickScreen.SettingEntry setting : module.settings) {
            if (setting.label.equalsIgnoreCase(label)) {
                return setting;
            }
        }
        return null;
    }

    private static List<String> listConfigNames() {
        ensureDirectories();
        List<String> names = new ArrayList<>();
        try {
            Files.list(CONFIG_DIR)
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                .forEach(path -> {
                    String fileName = path.getFileName().toString();
                    String base = fileName.substring(0, fileName.length() - 5);
                    if (!base.isBlank()) {
                        names.add(base);
                    }
                });
        } catch (IOException ignored) {
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    private static Path configFile(String name) {
        return CONFIG_DIR.resolve(name + ".json");
    }

    private static void ensureDirectories() {
        try {
            Files.createDirectories(CONFIG_DIR);
        } catch (IOException ignored) {
        }
    }

    private static String sanitizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static String readLastConfig() {
        try {
            if (!Files.exists(LAST_FILE)) {
                return "";
            }
            return sanitizeName(Files.readString(LAST_FILE, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return "";
        }
    }

    private static void writeLastConfig(String name) {
        String safeName = sanitizeName(name);
        if (safeName.isBlank()) {
            return;
        }
        ensureDirectories();
        try {
            Files.writeString(LAST_FILE, safeName, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String safe(JsonElement element) {
        return element == null ? "" : safe(element.getAsString());
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static String selectedOption(FishClickScreen.SettingEntry select) {
        if (select.selectOptions == null || select.selectOptions.length == 0) {
            return "";
        }
        return select.selectOptions[clampIndex(select, select.selectIndex)];
    }
}
