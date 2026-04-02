package com.fishclient.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;

import java.awt.Font;
import java.io.InputStream;

public final class FontLoader {

    private FontLoader() {
    }

    public static Font loadAwtFont(String path, float size) {
        try {
            ResourceLocation location = new ResourceLocation("fishclient", path);
            InputStream stream = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();
            Font font = Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(size);
            stream.close();
            return font;
        } catch (Exception ignored) {
            return new Font("SansSerif", Font.PLAIN, (int) size);
        }
    }

    public static FontRenderer getMinecraftFont() {
        return Minecraft.getMinecraft().fontRendererObj;
    }
}

