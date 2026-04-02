package com.fishclient.module.core;

import com.fishclient.FishClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleStateStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Boolean>>() {}.getType();

    private final Path baseDir = FabricLoader.getInstance().getConfigDir().resolve("fishclient");
    private final Path stateFile = baseDir.resolve("module-states.json");
    private final Path externalDir = baseDir.resolve("modules");

    public Map<String, Boolean> loadStates() {
        ensureLayout();
        if (!Files.exists(stateFile)) {
            return Collections.emptyMap();
        }

        try {
            String raw = new String(Files.readAllBytes(stateFile), StandardCharsets.UTF_8);
            Map<String, Boolean> parsed = GSON.fromJson(raw, MAP_TYPE);
            if (parsed == null) {
                return Collections.emptyMap();
            }
            return parsed;
        } catch (Exception ex) {
            FishClient.LOGGER.error("Failed to load module state file: {}", stateFile, ex);
            return Collections.emptyMap();
        }
    }

    public void saveStates(Map<String, Boolean> states) {
        ensureLayout();
        try {
            Files.write(stateFile, GSON.toJson(states).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            FishClient.LOGGER.error("Failed to save module state file: {}", stateFile, ex);
        }
    }

    public List<ExternalModuleSpec> loadExternalSpecs() {
        ensureLayout();
        List<ExternalModuleSpec> specs = new ArrayList<ExternalModuleSpec>();

        try {
            Files.list(externalDir)
                .filter(path -> path.toString().toLowerCase().endsWith(".json"))
                .sorted()
                .forEach(path -> specs.addAll(readSpecFile(path)));
        } catch (IOException ex) {
            FishClient.LOGGER.error("Failed reading external module dir: {}", externalDir, ex);
        }

        return specs;
    }

    private List<ExternalModuleSpec> readSpecFile(Path path) {
        try {
            String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            JsonElement element = new JsonParser().parse(raw);
            List<ExternalModuleSpec> specs = new ArrayList<ExternalModuleSpec>();
            if (element.isJsonArray()) {
                JsonArray array = element.getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    ExternalModuleSpec spec = GSON.fromJson(array.get(i), ExternalModuleSpec.class);
                    if (spec != null) {
                        specs.add(spec);
                    }
                }
            } else {
                ExternalModuleSpec spec = GSON.fromJson(element, ExternalModuleSpec.class);
                if (spec != null) {
                    specs.add(spec);
                }
            }
            return specs;
        } catch (Exception ex) {
            FishClient.LOGGER.error("Failed reading external module file: {}", path, ex);
            return Collections.emptyList();
        }
    }

    private void ensureLayout() {
        try {
            Files.createDirectories(baseDir);
            Files.createDirectories(externalDir);
            ensureExampleModuleFile();
            ensureStateFile();
        } catch (IOException ex) {
            FishClient.LOGGER.error("Failed creating Fish Client config layout", ex);
        }
    }

    private void ensureStateFile() throws IOException {
        if (!Files.exists(stateFile)) {
            Files.write(stateFile, "{}\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private void ensureExampleModuleFile() throws IOException {
        Path example = externalDir.resolve("_example_module.json");
        if (Files.exists(example)) {
            return;
        }

        Map<String, Object> content = new LinkedHashMap<String, Object>();
        content.put("id", "example_external_module");
        content.put("name", "Example External Module");
        content.put("description", "Loaded from config/fishclient/modules/*.json");
        content.put("category", "MISC");
        content.put("enabledByDefault", false);
        Files.write(example, GSON.toJson(content).getBytes(StandardCharsets.UTF_8));
    }
}
