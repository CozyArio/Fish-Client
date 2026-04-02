package com.fishclient.module.core;

import com.fishclient.FishClient;
import com.fishclient.module.Category;
import com.fishclient.module.api.ClientModule;
import com.fishclient.module.api.ModuleProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

public final class ModuleRegistry {

    private final List<ClientModule> modules = new ArrayList<ClientModule>();
    private final Map<String, ClientModule> byId = new LinkedHashMap<String, ClientModule>();
    private final ModuleStateStore stateStore = new ModuleStateStore();

    public synchronized void bootstrap() {
        modules.clear();
        byId.clear();

        registerBuiltinDefaults();
        loadProviderModules();
        loadExternalModules();
        applyPersistedState();
    }

    public synchronized void reloadExternalModules() {
        List<ClientModule> kept = new ArrayList<ClientModule>();
        for (ClientModule module : modules) {
            if (!module.isExternal()) {
                kept.add(module);
            }
        }

        modules.clear();
        modules.addAll(kept);
        byId.clear();
        for (ClientModule module : modules) {
            byId.put(module.id(), module);
        }

        loadExternalModules();
        applyPersistedState();
    }

    public synchronized void register(ClientModule module) {
        if (module == null || module.id() == null || module.id().isEmpty()) {
            return;
        }
        if (byId.containsKey(module.id())) {
            FishClient.LOGGER.warn("Skipping duplicate module id: {}", module.id());
            return;
        }

        modules.add(module);
        byId.put(module.id(), module);
    }

    public synchronized List<ClientModule> all() {
        return Collections.unmodifiableList(new ArrayList<ClientModule>(modules));
    }

    public synchronized List<ClientModule> byCategory(Category category) {
        List<ClientModule> out = new ArrayList<ClientModule>();
        for (ClientModule module : modules) {
            if (module.category() == category) {
                out.add(module);
            }
        }
        return out;
    }

    public synchronized ClientModule byId(String id) {
        return byId.get(id);
    }

    public synchronized void setEnabled(String id, boolean enabled) {
        ClientModule module = byId.get(id);
        if (module == null) {
            return;
        }
        module.setEnabled(enabled);
        persistState();
    }

    public synchronized void tickEnabledModules() {
        for (ClientModule module : modules) {
            if (module.enabled()) {
                module.onClientTick();
            }
        }
    }

    public synchronized void persistState() {
        Map<String, Boolean> states = new LinkedHashMap<String, Boolean>();
        for (ClientModule module : modules) {
            states.put(module.id(), module.enabled());
        }
        stateStore.saveStates(states);
    }

    private void registerBuiltinDefaults() {
        register(new BaseModule("killaura", "KillAura", "Automatically attacks nearby targets", Category.COMBAT, false, false));
        register(new BaseModule("velocity", "Velocity", "Reduce knockback from hits", Category.COMBAT, false, false));
        register(new BaseModule("target_strafe", "TargetStrafe", "Circle around your current target", Category.COMBAT, false, false));
        register(new BaseModule("triggerbot", "TriggerBot", "Attack when crosshair is on target", Category.COMBAT, false, false));
        register(new BaseModule("autocriticals", "AutoCriticals", "Auto-jump for critical strikes", Category.COMBAT, false, false));

        register(new BaseModule("sprint", "AutoSprint", "Keeps sprint active while moving", Category.MOVEMENT, true, false));
        register(new BaseModule("speed", "Speed", "Increase movement speed", Category.MOVEMENT, false, false));
        register(new BaseModule("flight", "Flight", "Allows creative-like flight", Category.MOVEMENT, false, false));
        register(new BaseModule("longjump", "LongJump", "Boost jump distance", Category.MOVEMENT, false, false));
        register(new BaseModule("step", "Step", "Step up blocks without jumping", Category.MOVEMENT, false, false));

        register(new BaseModule("autoeat", "AutoEat", "Automatically eat at low hunger", Category.PLAYER, false, false));
        register(new BaseModule("autototem", "AutoTotem", "Auto place totem in offhand", Category.PLAYER, false, false));
        register(new BaseModule("fastplace", "FastPlace", "Remove placement delay", Category.PLAYER, false, false));
        register(new BaseModule("inventory_move", "InventoryMove", "Move while inventory is open", Category.PLAYER, false, false));

        register(new BaseModule("chat_timestamps", "Chat Timestamps", "Prefix chat with current time", Category.MISC, false, false));
        register(new BaseModule("antikick", "AntiKick", "Minor anti-kick motion correction", Category.MISC, false, false));
        register(new BaseModule("autorespawn", "AutoRespawn", "Respawn instantly on death", Category.MISC, false, false));
        register(new BaseModule("fastbreak", "FastBreak", "Break blocks faster", Category.MISC, false, false));

        register(new BaseModule("fullbright", "FullBright", "Brighten dark areas", Category.VISUAL, true, false));
        register(new BaseModule("nametags", "NameTags", "Enhanced player nametags", Category.VISUAL, false, false));
        register(new BaseModule("particles", "Particles", "Customize particles amount", Category.VISUAL, false, false));
        register(new BaseModule("animations", "Animations", "Client-side animation styles", Category.VISUAL, false, false));

        register(new BaseModule("coordinates", "Coordinates HUD", "Show XYZ on-screen", Category.OVERLAY, true, false));
        register(new BaseModule("fps", "FPS Display", "Show FPS counter", Category.OVERLAY, true, false));
        register(new BaseModule("keystrokes", "Keystrokes", "Render WASD and mouse inputs", Category.OVERLAY, false, false));
        register(new BaseModule("potions", "Potion Effects", "Display active potion effects", Category.OVERLAY, false, false));

        register(new BaseModule("theme_purple", "Purple Neon Theme", "Classic Fish UI palette", Category.THEME, true, false));
        register(new BaseModule("theme_ocean", "Ocean Theme", "Cool blue accents", Category.THEME, false, false));
        register(new BaseModule("theme_crimson", "Crimson Theme", "High-contrast red style", Category.THEME, false, false));

        register(new BaseModule("music_widget", "Music Widget", "Top-left stream widget", Category.RADIO, false, false));
        register(new BaseModule("radio_autoplay", "AutoPlay Radio", "Automatically start station", Category.RADIO, false, false));
        register(new BaseModule("radio_sync", "Beat Sync", "Pulse accents with music beat", Category.RADIO, false, false));
    }

    private void loadProviderModules() {
        ServiceLoader<ModuleProvider> loader = ServiceLoader.load(ModuleProvider.class);
        for (ModuleProvider provider : loader) {
            try {
                provider.registerModules(this);
            } catch (Exception ex) {
                FishClient.LOGGER.error("Module provider {} failed", provider.getClass().getName(), ex);
            }
        }
    }

    private void loadExternalModules() {
        List<ExternalModuleSpec> specs = stateStore.loadExternalSpecs();
        for (ExternalModuleSpec spec : specs) {
            String id = spec.normalizedId();
            if (id == null) {
                continue;
            }
            register(new BaseModule(
                id,
                spec.resolvedName(),
                spec.resolvedDescription(),
                spec.resolvedCategory(),
                spec.enabledByDefault(),
                true
            ));
        }
    }

    private void applyPersistedState() {
        Map<String, Boolean> states = stateStore.loadStates();
        for (Map.Entry<String, Boolean> entry : states.entrySet()) {
            ClientModule module = byId.get(entry.getKey());
            if (module != null) {
                module.setEnabled(Boolean.TRUE.equals(entry.getValue()));
            }
        }
        persistState();
    }
}
