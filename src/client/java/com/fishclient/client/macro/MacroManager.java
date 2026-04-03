package com.fishclient.client.macro;

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

public final class MacroManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path MACRO_FILE = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("fishclient")
        .resolve("macros.json");

    private static boolean loaded;
    private static List<MacroEntry> macros = new ArrayList<>();

    private MacroManager() {
    }

    public static synchronized void initialize() {
        if (loaded) {
            return;
        }
        loaded = true;
        macros = loadFromDisk();
        if (macros.isEmpty()) {
            macros.add(new MacroEntry("macro-home", "Home", "/home", 0, true));
            saveInternal();
        }
    }

    public static synchronized List<MacroEntry> getMacros() {
        initialize();
        return copyList(macros);
    }

    public static synchronized void replaceAll(List<MacroEntry> entries) {
        initialize();
        macros = sanitize(entries);
        saveInternal();
    }

    private static List<MacroEntry> sanitize(List<MacroEntry> input) {
        List<MacroEntry> out = new ArrayList<>();
        if (input == null) {
            return out;
        }
        for (MacroEntry entry : input) {
            if (entry == null) {
                continue;
            }
            String name = safe(entry.name).trim();
            String command = safe(entry.command).trim();
            if (name.isBlank() || command.isBlank()) {
                continue;
            }
            String id = safe(entry.id).isBlank() ? "macro-" + System.currentTimeMillis() : entry.id;
            int keybind = Math.max(0, entry.keybind);
            out.add(new MacroEntry(id, name, command, keybind, entry.enabled));
        }
        out.sort(Comparator.comparing(value -> value.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    private static List<MacroEntry> copyList(List<MacroEntry> source) {
        List<MacroEntry> copy = new ArrayList<>();
        for (MacroEntry entry : source) {
            copy.add(new MacroEntry(entry.id, entry.name, entry.command, entry.keybind, entry.enabled));
        }
        return copy;
    }

    private static List<MacroEntry> loadFromDisk() {
        ensureParent();
        if (!Files.exists(MACRO_FILE)) {
            return new ArrayList<>();
        }
        try {
            String raw = Files.readString(MACRO_FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("macros");
            if (array == null) {
                return new ArrayList<>();
            }
            List<MacroEntry> list = new ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject obj = element.getAsJsonObject();
                String id = safe(obj.get("id"));
                String name = safe(obj.get("name"));
                String command = safe(obj.get("command"));
                int keybind = obj.has("keybind") ? obj.get("keybind").getAsInt() : 0;
                boolean enabled = !obj.has("enabled") || obj.get("enabled").getAsBoolean();
                list.add(new MacroEntry(id, name, command, keybind, enabled));
            }
            return sanitize(list);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    private static void saveInternal() {
        ensureParent();
        JsonObject root = new JsonObject();
        JsonArray array = new JsonArray();
        for (MacroEntry entry : macros) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", entry.id);
            obj.addProperty("name", entry.name);
            obj.addProperty("command", entry.command);
            obj.addProperty("keybind", entry.keybind);
            obj.addProperty("enabled", entry.enabled);
            array.add(obj);
        }
        root.add("macros", array);
        try {
            Files.writeString(MACRO_FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void ensureParent() {
        try {
            Files.createDirectories(MACRO_FILE.getParent());
        } catch (IOException ignored) {
        }
    }

    private static String safe(JsonElement element) {
        return element == null ? "" : safe(element.getAsString());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static final class MacroEntry {
        public String id;
        public String name;
        public String command;
        public int keybind;
        public boolean enabled;

        public MacroEntry(String id, String name, String command, int keybind, boolean enabled) {
            this.id = id;
            this.name = name;
            this.command = command;
            this.keybind = keybind;
            this.enabled = enabled;
        }
    }
}

