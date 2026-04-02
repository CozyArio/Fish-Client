package com.fishclient.client;

import com.fishclient.FishClient;
import com.fishclient.client.cloud.CloudServer;
import com.fishclient.client.ui.FishClickScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class FishClientClient implements ClientModInitializer {

    private static KeyMapping openMenuKey;
    private static KeyMapping reloadModulesKey;

    @Override
    public void onInitializeClient() {
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

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (!message.startsWith(".cloud")) {
                return true;
            }

            boolean started = CloudServer.startIfNeeded();

            java.net.URI panelUri = null;
            try {
                java.nio.file.Path localPanel = java.nio.file.Path.of("fish-client-panel", "index.html")
                    .toAbsolutePath()
                    .normalize();
                if (java.nio.file.Files.exists(localPanel)) {
                    panelUri = localPanel.toUri();
                } else {
                    panelUri = new java.net.URI("http://YOUR-USERNAME.github.io/fish-client-panel/");
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
                        Component.literal("[FishClient] Cloud ready. " + CloudServer.endpoint() + " | " + panelText),
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
            while (openMenuKey.consumeClick()) {
                client.setScreen(new FishClickScreen());
            }

            while (reloadModulesKey.consumeClick()) {
                FishClient.modules().reloadExternalModules();
            }

            FishClient.modules().tickEnabledModules();
        });
    }
}
