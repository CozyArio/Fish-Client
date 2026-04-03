package com.fishclient.client;

import com.fishclient.FishClient;
import com.fishclient.client.cloud.CloudBridge;
import com.fishclient.client.config.FishConfigManager;
import com.fishclient.client.discord.DiscordRpcManager;
import com.fishclient.client.macro.MacroManager;
import com.fishclient.client.render.FishEspRenderer;
import com.fishclient.client.runtime.GameplayModulesRuntime;
import com.fishclient.client.ui.AltManagerScreen;
import com.fishclient.client.ui.FishHudOverlay;
import com.fishclient.client.ui.FishClickScreen;
import com.fishclient.client.ui.MacroGuiScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public final class FishClientClient implements ClientModInitializer {

    private static KeyMapping openMenuKey;
    private static KeyMapping reloadModulesKey;
    private static final GameplayModulesRuntime GAMEPLAY_RUNTIME = new GameplayModulesRuntime();
    private static final Set<Integer> HELD_MODULE_KEYS = new HashSet<>();
    private static final Set<Integer> HELD_MACRO_KEYS = new HashSet<>();
    private static boolean discordRpcReady;

    public static int getRebreakSelectionCount() {
        return GAMEPLAY_RUNTIME.getRebreakSelectionCount();
    }

    @Override
    public void onInitializeClient() {
        FishConfigManager.initialize();
        MacroManager.initialize();
        try {
            DiscordRpcManager.initialize();
            discordRpcReady = true;
        } catch (Throwable t) {
            discordRpcReady = false;
            System.err.println("[FishClient] Discord RPC disabled: " + t.getMessage());
        }

        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.fishclient.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_SHIFT,
            KeyMapping.Category.MISC
        ));

        reloadModulesKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.fishclient.reload_modules",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            KeyMapping.Category.MISC
        ));

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) {
                return;
            }

            boolean alreadyAdded = Screens.getButtons(screen).stream()
                .anyMatch(button -> button != null && button.getMessage() != null
                    && "Alt Manager".equals(button.getMessage().getString()));
            if (alreadyAdded) {
                return;
            }

            int buttonWidth = 98;
            int buttonHeight = 20;
            int x = 8;
            int y = screen.height - buttonHeight - 8;

            Button altButton = Button.builder(
                Component.literal("Alt Manager"),
                btn -> client.setScreen(new AltManagerScreen(screen))
            ).bounds(x, y, buttonWidth, buttonHeight).build();

            Screens.getButtons(screen).add(altButton);
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (message.startsWith(".macrogui")) {
                Minecraft client = Minecraft.getInstance();
                if (client != null) {
                    client.setScreen(new MacroGuiScreen(client.screen));
                }
                return false;
            }

            if (!message.startsWith(".cloud")) {
                return true;
            }

            boolean started = CloudBridge.startIfNeeded();

            java.net.URI panelUri = null;
            try {
                java.nio.file.Path[] localCandidates = new java.nio.file.Path[] {
                    java.nio.file.Path.of("C:/Users/motyl/Documents/AI CODING PEHNIS/Fish Client/fish-client-panel/index.html"),
                    java.nio.file.Path.of(System.getProperty("user.dir"), "fish-client-panel", "index.html"),
                    java.nio.file.Path.of(System.getProperty("user.home"), "Documents", "AI CODING PEHNIS", "Fish Client", "fish-client-panel", "index.html")
                };

                for (java.nio.file.Path candidate : localCandidates) {
                    java.nio.file.Path normalized = candidate.toAbsolutePath().normalize();
                    if (java.nio.file.Files.exists(normalized)) {
                        panelUri = normalized.toUri();
                        break;
                    }
                }

                if (panelUri == null) {
                    panelUri = new java.net.URI("http://cozyario.github.io/Fish-Client/fish-client-panel/index.html");
                }
                java.awt.Desktop.getDesktop().browse(panelUri);
            } catch (Exception exception) {
                System.err.println("[FishClient] Could not open browser: " + exception.getMessage());
            }

            Minecraft client = Minecraft.getInstance();
            if (client != null && client.player != null) {
                if (started) {
                    String panelText = panelUri == null ? "panel" : panelUri.toString();
                    client.player.displayClientMessage(
                        Component.literal("[FishClient] Cloud ready. " + CloudBridge.endpoint() + " | " + panelText),
                        false
                    );
                } else {
                    client.player.displayClientMessage(
                        Component.literal("[FishClient] Cloud server failed to start. Check logs."),
                        false
                    );
                }
            }

            return false;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (discordRpcReady) {
                try {
                    DiscordRpcManager.tick(client);
                } catch (Throwable t) {
                    discordRpcReady = false;
                    System.err.println("[FishClient] Discord RPC tick disabled: " + t.getMessage());
                }
            }

            while (openMenuKey.consumeClick()) {
                client.setScreen(new FishClickScreen());
            }

            while (reloadModulesKey.consumeClick()) {
                FishClient.modules().reloadExternalModules();
            }

            handleModuleKeybinds(client);
            handleMacroKeybinds(client);
            FishClient.modules().tickEnabledModules();
            GAMEPLAY_RUNTIME.tick(client);
        });

        HudRenderCallback.EVENT.register((graphics, tickCounter) -> FishHudOverlay.render(graphics));
        WorldRenderEvents.END_MAIN.register(FishEspRenderer::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (!discordRpcReady) {
                return;
            }
            try {
                DiscordRpcManager.shutdown();
            } catch (Throwable ignored) {
            }
        });
    }

    private static void handleModuleKeybinds(Minecraft client) {
        if (client == null || client.getWindow() == null) {
            return;
        }
        if (client.screen != null) {
            HELD_MODULE_KEYS.clear();
            return;
        }

        com.mojang.blaze3d.platform.Window window = client.getWindow();
        Set<Integer> currentlyHeld = new HashSet<>();

        for (FishClickScreen.ModuleEntry module : FishClickScreen.SHARED_MODULES) {
            int key = module.keybind;
            if (key <= 0 || module.capturingKey) {
                continue;
            }
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_INSERT) {
                continue;
            }
            if ("ClickGUI Bind".equalsIgnoreCase(module.name)) {
                continue;
            }

            boolean down = InputConstants.isKeyDown(window, key);
            if (!down) {
                continue;
            }
            currentlyHeld.add(key);
            if (HELD_MODULE_KEYS.contains(key)) {
                continue;
            }

            module.enabled = !module.enabled;
            module.toggleAnimMs = System.currentTimeMillis();
            CloudBridge.broadcastState();
        }

        HELD_MODULE_KEYS.clear();
        HELD_MODULE_KEYS.addAll(currentlyHeld);
    }

    private static void handleMacroKeybinds(Minecraft client) {
        if (client == null || client.getWindow() == null) {
            return;
        }
        if (client.screen != null) {
            HELD_MACRO_KEYS.clear();
            return;
        }

        com.mojang.blaze3d.platform.Window window = client.getWindow();
        Set<Integer> currentlyHeld = new HashSet<>();
        for (MacroManager.MacroEntry macro : MacroManager.getMacros()) {
            if (macro == null || !macro.enabled || macro.keybind <= 0) {
                continue;
            }
            boolean down = InputConstants.isKeyDown(window, macro.keybind);
            if (!down) {
                continue;
            }
            currentlyHeld.add(macro.keybind);
            if (HELD_MACRO_KEYS.contains(macro.keybind)) {
                continue;
            }
            executeMacro(client, macro.command);
        }

        HELD_MACRO_KEYS.clear();
        HELD_MACRO_KEYS.addAll(currentlyHeld);
    }

    private static void executeMacro(Minecraft client, String rawCommand) {
        if (client == null || client.player == null || client.player.connection == null) {
            return;
        }
        String commandText = rawCommand == null ? "" : rawCommand.trim();
        if (commandText.isBlank()) {
            return;
        }
        String[] lines = commandText.split("\\r?\\n");
        for (String line : lines) {
            String cmd = line.trim();
            if (cmd.isBlank()) {
                continue;
            }
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1).trim();
            }
            if (cmd.isBlank()) {
                continue;
            }
            try {
                client.player.connection.sendCommand(cmd);
            } catch (Throwable ignored) {
                try {
                    client.player.connection.sendChat("/" + cmd);
                } catch (Throwable ignoredAgain) {
                }
            }
        }
    }
}

