package com.fishclient.core;

import com.fishclient.modules.Module;
import com.fishclient.modules.hud.ArmorStatus;
import com.fishclient.modules.hud.CoordinatesHUD;
import com.fishclient.modules.hud.CPSCounter;
import com.fishclient.modules.hud.FPSDisplay;
import com.fishclient.modules.hud.KeystrokesDisplay;
import com.fishclient.modules.hud.PingDisplay;
import com.fishclient.modules.hud.PotionEffectsHUD;
import com.fishclient.modules.misc.FastChat;
import com.fishclient.modules.misc.MemoryFix;
import com.fishclient.modules.misc.ServerIPHider;
import com.fishclient.modules.player.AutoRespawn;
import com.fishclient.modules.player.ChatTimestamps;
import com.fishclient.modules.player.ItemPhysics;
import com.fishclient.modules.visual.Animations;
import com.fishclient.modules.visual.ChunkAnimator;
import com.fishclient.modules.visual.CustomSky;
import com.fishclient.modules.visual.FullBright;
import com.fishclient.modules.visual.HitColor;
import com.fishclient.modules.visual.NameTags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModuleManager {

    private final List<Module> modules = new ArrayList<Module>();

    public void registerAll() {
        register(new FullBright());
        register(new ChunkAnimator());
        register(new HitColor());
        register(new CustomSky());
        register(new Animations());
        register(new NameTags());

        register(new CPSCounter());
        register(new FPSDisplay());
        register(new CoordinatesHUD());
        register(new ArmorStatus());
        register(new PotionEffectsHUD());
        register(new KeystrokesDisplay());
        register(new PingDisplay());

        register(new AutoRespawn());
        register(new ItemPhysics());
        register(new ChatTimestamps());

        register(new FastChat());
        register(new MemoryFix());
        register(new ServerIPHider());
    }

    public void register(Module module) {
        modules.add(module);
    }

    public List<Module> getModules() {
        return Collections.unmodifiableList(modules);
    }

    public Module getModuleByName(String name) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }
}

