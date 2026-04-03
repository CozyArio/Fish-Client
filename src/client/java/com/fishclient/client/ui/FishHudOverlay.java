package com.fishclient.client.ui;

import com.fishclient.client.FishClientClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FishHudOverlay {

    private static final int COL_ACCENT_BASE = 0xFFAA55FF;
    private static final int COL_ACCENT_DIM = 0xFF7C36CC;
    private static final int COL_TEXT = 0xFFFFFFFF;
    private static final int COL_MUTED = 0xFFB6B8C9;
    private static final int COL_BG = 0xCC0A0C14;
    private static final int COL_BG_DARK = 0xD0080A11;
    private static final int COL_BORDER = 0xFF2A2A38;
    private static final int COL_SHADOW = 0x70000000;
    private static final int COL_LOGO_BG = 0xFF141726;
    private static final int COL_ARRAY_BG = 0xB0121522;
    private static final int COL_TAG_BG = 0xC0101321;
    private static final int COL_TAG_BG_ACTIVE = 0xC01E1330;

    private FishHudOverlay() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.options.hideGui) {
            return;
        }

        if (!isModuleEnabled("HUD")) {
            return;
        }

        Font font = mc.font;
        long now = System.currentTimeMillis();
        float pulse = 0.65f + (float) (Math.sin(now / 450.0) * 0.20f);
        int accent = lerpColor(COL_ACCENT_DIM, COL_ACCENT_BASE, pulse);

        int left = 6;
        int top = 6;
        int width = 144;
        int height = 30;
        int logoX = left + 6;
        int logoY = top + 7;
        int logoSize = 16;

        graphics.fill(left + 2, top + 2, left + width + 2, top + height + 2, COL_SHADOW);
        graphics.fill(left, top, left + width, top + height, COL_BG);
        graphics.fill(left, top, left + width, top + 1, COL_BORDER);
        graphics.fill(left, top + height - 1, left + width, top + height, COL_BORDER);
        graphics.fill(left, top, left + 1, top + height, COL_BORDER);
        graphics.fill(left + width - 1, top, left + width, top + height, COL_BORDER);
        graphics.fill(left, top, left + 2, top + height, accent);

        graphics.fill(logoX, logoY, logoX + logoSize, logoY + logoSize, COL_LOGO_BG);
        graphics.fill(logoX, logoY, logoX + logoSize, logoY + 1, COL_BORDER);
        graphics.fill(logoX, logoY + logoSize - 1, logoX + logoSize, logoY + logoSize, COL_BORDER);
        graphics.fill(logoX, logoY, logoX + 1, logoY + logoSize, COL_BORDER);
        graphics.fill(logoX + logoSize - 1, logoY, logoX + logoSize, logoY + logoSize, COL_BORDER);
        drawFishLogo(graphics, logoX + 2, logoY + 4, 1, 0xFFF5F7FF, accent);

        graphics.drawString(font, "Fish Client", left + 28, top + 7, COL_TEXT);
        graphics.drawString(font, "HUD Overlay", left + 28, top + 17, COL_MUTED);

        int rebreakSelected = FishClientClient.getRebreakSelectionCount();
        boolean rebreakEnabled = isModuleEnabled("Rebreak");
        drawRebreakTag(graphics, font, left, top + height + 4, rebreakSelected, rebreakEnabled, accent);

        if (!isModuleEnabled("ArrayList")) {
            return;
        }

        List<String> enabledNames = collectEnabledModuleNames();
        int y = top + height + 6;
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        for (int i = 0; i < enabledNames.size(); i++) {
            String name = enabledNames.get(i);
            int nameW = font.width(name);
            int rowH = 12;
            int rowW = nameW + 14;
            int x = screenWidth - rowW - 6;
            int rowY = y + i * (rowH + 3);
            float shadeMix = 0.45f + (i % 5) * 0.11f;
            int rowAccent = lerpColor(COL_ACCENT_DIM, accent, Math.min(1.0f, shadeMix));

            graphics.fill(x + 1, rowY + 1, x + rowW + 1, rowY + rowH + 1, COL_SHADOW);
            graphics.fill(x, rowY, x + rowW, rowY + rowH, COL_ARRAY_BG);
            graphics.fill(x, rowY, x + rowW, rowY + 1, COL_BORDER);
            graphics.fill(x + rowW - 2, rowY, x + rowW, rowY + rowH, rowAccent);
            graphics.drawString(font, name, x + 6, rowY + 2, COL_TEXT);
        }
    }

    private static boolean isModuleEnabled(String name) {
        for (FishClickScreen.ModuleEntry entry : FishClickScreen.SHARED_MODULES) {
            if (entry.name.equalsIgnoreCase(name)) {
                return entry.enabled;
            }
        }
        return false;
    }

    private static List<String> collectEnabledModuleNames() {
        List<String> names = new ArrayList<>();
        for (FishClickScreen.ModuleEntry entry : FishClickScreen.SHARED_MODULES) {
            if (!entry.enabled) {
                continue;
            }
            if (entry.name.equalsIgnoreCase("HUD") || entry.name.equalsIgnoreCase("ArrayList")) {
                continue;
            }
            if (entry.name.equalsIgnoreCase("ClickGUI Bind")) {
                continue;
            }
            if (entry.name.equalsIgnoreCase("ClickGUI Style")) {
                continue;
            }
            names.add(entry.name);
        }
        names.sort(Comparator.comparingInt((String value) -> Minecraft.getInstance().font.width(value)).reversed());
        return names;
    }

    private static void drawRebreakTag(GuiGraphics graphics, Font font, int x, int y, int selected, boolean rebreakEnabled, int accent) {
        String text = "Rebreak: " + selected + " selected";
        int w = font.width(text) + 12;
        int h = 12;
        int bg = selected > 0 ? COL_TAG_BG_ACTIVE : COL_TAG_BG;
        int stripe = rebreakEnabled ? accent : COL_BORDER;
        int textColor = selected > 0 ? COL_TEXT : COL_MUTED;

        graphics.fill(x + 1, y + 1, x + w + 1, y + h + 1, COL_SHADOW);
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.fill(x, y, x + w, y + 1, COL_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COL_BORDER);
        graphics.fill(x, y, x + 1, y + h, stripe);
        graphics.fill(x + w - 1, y, x + w, y + h, COL_BORDER);
        graphics.drawString(font, text, x + 6, y + 2, textColor);
    }

    private static void drawFishLogo(GuiGraphics graphics, int x, int y, int scale, int bodyColor, int finColor) {
        String[] pixels = new String[]{
            "001111100000",
            "011111111000",
            "111111111100",
            "111011111111",
            "111111111100",
            "011111111000",
            "001111100000"
        };
        for (int py = 0; py < pixels.length; py++) {
            String row = pixels[py];
            for (int px = 0; px < row.length(); px++) {
                char ch = row.charAt(px);
                if (ch != '1') {
                    continue;
                }
                int color = px >= row.length() - 2 ? finColor : bodyColor;
                int rx = x + px * scale;
                int ry = y + py * scale;
                graphics.fill(rx, ry, rx + scale, ry + scale, color);
            }
        }
        graphics.fill(x + 3 * scale, y + 2 * scale, x + 4 * scale, y + 3 * scale, 0xFF1A1C2A);
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int fa = (from >> 24) & 0xFF;
        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;
        int ta = (to >> 24) & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >> 8) & 0xFF;
        int tb = to & 0xFF;
        int a = (int) (fa + (ta - fa) * clamped);
        int r = (int) (fr + (tr - fr) * clamped);
        int g = (int) (fg + (tg - fg) * clamped);
        int b = (int) (fb + (tb - fb) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
