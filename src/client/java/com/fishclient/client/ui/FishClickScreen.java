package com.fishclient.client.ui;

import com.fishclient.client.cloud.CloudServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class FishClickScreen extends Screen {

    private static final int COL_PANEL_BG = 0xF0101014;
    private static final int COL_SIDEBAR_BG = 0xFF0C0C10;
    private static final int COL_CARD_BG = 0xFF18181E;
    private static final int COL_CARD_HOVER = 0xFF1E1E26;
    private static final int COL_CARD_ACTIVE = 0xFF1A1025;
    private static final int COL_SETTINGS_BG = 0xFF13131A;
    private static final int COL_SEARCH_BG = 0xFF141418;
    private static final int COL_SEPARATOR = 0xFF2A2A38;
    private static final int COL_ACCENT = 0xFFAA55FF;
    private static final int COL_ACCENT_DIM = 0xFF7733CC;
    private static final int COL_TOGGLE_ON = 0xFF9955EE;
    private static final int COL_TOGGLE_OFF = 0xFF333340;
    private static final int COL_TOGGLE_KNOB = 0xFFFFFFFF;
    private static final int COL_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int COL_TEXT_SECONDARY = 0xFFAAAAAA;
    private static final int COL_TEXT_MUTED = 0xFF666677;
    private static final int COL_CHIP_BG = 0xFF222230;
    private static final int COL_CHIP_ACTIVE = 0xFFAA55FF;

    private static final int PANEL_W = 720;
    private static final int PANEL_H = 440;
    private static final int SIDEBAR_W = 120;
    private static final int TOP_BAR_H = 32;
    private static final int CARD_H = 36;
    private static final int CARD_GAP = 4;
    private static final int CARD_PAD = 8;
    private static final int TOGGLE_W = 28;
    private static final int TOGGLE_H = 14;
    private static final int SCROLLBAR_W = 3;

    public static final List<ModuleEntry> SHARED_MODULES = new ArrayList<ModuleEntry>();

    static {
        SHARED_MODULES.add(new ModuleEntry("BlockEater", "Breaks nearby blocks automatically", "World", List.of(
            SettingEntry.ofSlider("Range", 1, 6, 1, 3),
            SettingEntry.ofSlider("Delay ms", 0, 500, 10, 100),
            SettingEntry.ofText("Whitelist", ""),
            SettingEntry.ofToggle("Enabled", false)
        )));
        SHARED_MODULES.add(new ModuleEntry("Nuker", "Breaks blocks in a sphere around you", "World", List.of(
            SettingEntry.ofSlider("Radius", 1, 5, 1, 2),
            SettingEntry.ofSlider("Delay ms", 50, 300, 10, 150)
        )));
        SHARED_MODULES.add(new ModuleEntry("Wings", "Creative-style fly in survival", "Movement", List.of(
            SettingEntry.ofSlider("Speed", 0.1, 5.0, 0.1, 1.0),
            SettingEntry.ofSlider("Vertical", 0.1, 3.0, 0.1, 0.8),
            SettingEntry.ofToggle("Glide", true)
        )));
        SHARED_MODULES.add(new ModuleEntry("Sprint", "Auto sprint always on", "Movement", List.of()));
        SHARED_MODULES.add(new ModuleEntry("NoFall", "Cancels fall damage", "Movement", List.of()));
        SHARED_MODULES.add(new ModuleEntry("AutoClicker", "Auto left click at set CPS", "Combat", List.of(
            SettingEntry.ofSlider("CPS Min", 1, 20, 1, 8),
            SettingEntry.ofSlider("CPS Max", 1, 20, 1, 12),
            SettingEntry.ofToggle("Only Held", true)
        )));
        SHARED_MODULES.add(new ModuleEntry("Reach", "Extended attack reach", "Combat", List.of(
            SettingEntry.ofSlider("Distance", 3.0, 6.0, 0.1, 3.5)
        )));
        SHARED_MODULES.add(new ModuleEntry("Spawn Macro", "Teleports to spawn", "Macros", List.of(SettingEntry.ofText("Command", "/spawn"))));
        SHARED_MODULES.add(new ModuleEntry("Home Macro", "Teleports to home", "Macros", List.of(SettingEntry.ofText("Command", "/home"))));
        SHARED_MODULES.add(new ModuleEntry("ClickGUI Bind", "Key to open this menu", "Keybinds", List.of()));
        SHARED_MODULES.add(new ModuleEntry("Purple Neon", "Default purple theme", "Theme", List.of(
            SettingEntry.ofSlider("Accent R", 0, 255, 1, 170),
            SettingEntry.ofSlider("Accent G", 0, 255, 1, 85),
            SettingEntry.ofSlider("Accent B", 0, 255, 1, 255)
        )));
    }

    private final List<ModuleEntry> modules = SHARED_MODULES;
    private List<ModuleEntry> filteredModules = new ArrayList<ModuleEntry>();
    private String selectedCategory = "World";
    private String searchText = "";
    private boolean searchFocused;
    private int scrollOffset;

    private ModuleEntry openSettingsModule;
    private int settingsScrollOffset;
    private SettingEntry focusedTextSetting;

    private boolean dragging;
    private int dragOffX;
    private int dragOffY;
    private int panelX;
    private int panelY;

    private static final String[] CATEGORIES = {"World", "Movement", "Combat", "Macros", "Keybinds", "Theme"};

    public FishClickScreen() {
        super(Component.literal("Fish Client"));
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        rebuildFiltered();
    }

    private void rebuildFiltered() {
        filteredModules = modules.stream()
            .filter(m -> m.category.equals(selectedCategory))
            .filter(m -> searchText.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(searchText.toLowerCase(Locale.ROOT)))
            .collect(Collectors.toList());
        clampModuleScroll();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x64000000);

        if (dragging) {
            panelX = mouseX - dragOffX;
            panelY = mouseY - dragOffY;
            clampPanel();
        }

        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_PANEL_BG);
        renderSidebar(g, mouseX, mouseY);
        g.fill(panelX + SIDEBAR_W, panelY, panelX + SIDEBAR_W + 1, panelY + PANEL_H, COL_SEPARATOR);
        renderTopBar(g);
        g.fill(panelX + SIDEBAR_W + 1, panelY + TOP_BAR_H, panelX + PANEL_W, panelY + TOP_BAR_H + 1, COL_SEPARATOR);
        renderModuleList(g, mouseX, mouseY);

        if (openSettingsModule != null) {
            renderSettingsPanel(g, mouseX);
        }

        drawPanelBorder(g);
    }

    private void renderSidebar(GuiGraphics g, int mx, int my) {
        g.fill(panelX, panelY, panelX + SIDEBAR_W, panelY + PANEL_H, COL_SIDEBAR_BG);
        drawText(g, "Fish", panelX + 10, panelY + 9, COL_ACCENT);
        drawText(g, "v1.0", panelX + 10, panelY + 19, COL_TEXT_MUTED);
        g.fill(panelX + 6, panelY + 29, panelX + SIDEBAR_W - 6, panelY + 30, COL_SEPARATOR);

        int itemY = panelY + 34;
        for (String category : CATEGORIES) {
            boolean active = category.equals(selectedCategory);
            boolean hovered = inRect(mx, my, panelX, itemY, SIDEBAR_W, 26);
            if (active) {
                g.fill(panelX, itemY, panelX + SIDEBAR_W, itemY + 26, COL_CARD_HOVER);
                g.fill(panelX, itemY, panelX + 2, itemY + 26, COL_ACCENT);
            } else if (hovered) {
                g.fill(panelX, itemY, panelX + SIDEBAR_W, itemY + 26, COL_CARD_BG);
            }
            drawText(g, category, panelX + 10, itemY + 9, active ? COL_ACCENT : (hovered ? COL_TEXT_SECONDARY : COL_TEXT_MUTED));
            itemY += 26;
        }

        String username = "Player";
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null && mc.getUser().getName() != null) {
            username = mc.getUser().getName();
        }
        g.fill(panelX, panelY + PANEL_H - 22, panelX + SIDEBAR_W, panelY + PANEL_H, COL_SIDEBAR_BG);
        g.fill(panelX + 4, panelY + PANEL_H - 23, panelX + SIDEBAR_W - 4, panelY + PANEL_H - 22, COL_SEPARATOR);
        drawText(g, truncate(username, SIDEBAR_W - 16), panelX + 8, panelY + PANEL_H - 14, COL_TEXT_MUTED);
    }

    private void renderTopBar(GuiGraphics g) {
        drawText(g, selectedCategory, panelX + SIDEBAR_W + 10, panelY + 12, COL_ACCENT);
        int sx = searchX();
        int sy = searchY();
        g.fill(sx, sy, sx + 180, sy + 20, COL_SEARCH_BG);
        fakeRoundCorners(g, sx, sy, 180, 20, COL_PANEL_BG);
        String placeholder = (searchText.isEmpty() && !searchFocused) ? "Search..." : searchText;
        drawText(g, placeholder, sx + 6, sy + 6, searchText.isEmpty() ? COL_TEXT_MUTED : COL_TEXT_PRIMARY);
        if (searchFocused && (System.currentTimeMillis() % 1000L) < 500L) {
            int cursorX = sx + 6 + font.width(searchText);
            g.fill(cursorX, sy + 4, cursorX + 1, sy + 16, COL_TEXT_PRIMARY);
        }
    }

    private void renderModuleList(GuiGraphics g, int mx, int my) {
        int lx = listX();
        int ly = listY();
        int lw = listW();
        int lh = listH();
        g.enableScissor(lx, ly, lx + lw, ly + lh);

        for (int i = 0; i < filteredModules.size(); i++) {
            ModuleEntry m = filteredModules.get(i);
            int cx = lx + CARD_PAD;
            int cy = ly + CARD_PAD + i * (CARD_H + CARD_GAP) - scrollOffset;
            int cw = lw - CARD_PAD * 2;
            if (cy + CARD_H < ly || cy > ly + lh) {
                continue;
            }

            boolean hovered = inRect(mx, my, cx, cy, cw, CARD_H);
            int bg = m.enabled ? COL_CARD_ACTIVE : (hovered ? COL_CARD_HOVER : COL_CARD_BG);
            g.fill(cx, cy, cx + cw, cy + CARD_H, bg);
            fakeRoundCorners(g, cx, cy, cw, CARD_H, COL_PANEL_BG);
            if (m.enabled) {
                g.fill(cx, cy, cx + 3, cy + CARD_H, COL_ACCENT);
            }

            int right = cx + cw - CARD_PAD;
            int tx = right - TOGGLE_W;
            int ty = cy + (CARD_H - TOGGLE_H) / 2;
            renderToggle(g, m, tx, ty);
            right = tx - 6;

            if (!m.settings.isEmpty()) {
                int gx = right - font.width("*") - 4;
                drawText(g, "*", gx, cy + (CARD_H - 8) / 2, inRect(mx, my, gx - 2, cy, 14, CARD_H) ? COL_ACCENT : COL_TEXT_MUTED);
                right = gx - 8;
            }

            String keyLabel = m.capturingKey ? "..." : (m.keybind == 0 ? "NONE" : glfwKeyName(m.keybind));
            int chipW = Math.max(32, font.width(keyLabel) + 10);
            int chipX = right - chipW;
            int chipY = cy + (CARD_H - 14) / 2;
            g.fill(chipX, chipY, chipX + chipW, chipY + 14, m.capturingKey ? COL_CHIP_ACTIVE : COL_CHIP_BG);
            drawText(g, keyLabel, chipX + 5, chipY + 3, m.capturingKey ? COL_TEXT_PRIMARY : COL_TEXT_MUTED);
            right = chipX - 6;

            drawText(g, truncate(m.name, right - (cx + 8)), cx + 8, cy + (CARD_H - 8) / 2, m.enabled ? COL_TEXT_PRIMARY : COL_TEXT_SECONDARY);
        }

        g.disableScissor();

        int total = filteredModules.size() * (CARD_H + CARD_GAP) + CARD_PAD * 2;
        if (total > lh) {
            int thumbH = Math.max(20, lh * lh / total);
            int range = total - lh;
            int thumbY = ly + (int) ((float) scrollOffset / (float) range * (float) (lh - thumbH));
            g.fill(lx + lw, ly, lx + lw + SCROLLBAR_W, ly + lh, COL_CARD_BG);
            g.fill(lx + lw, thumbY, lx + lw + SCROLLBAR_W, thumbY + thumbH, COL_ACCENT_DIM);
        }
    }

    private void renderToggle(GuiGraphics g, ModuleEntry m, int x, int y) {
        long now = System.currentTimeMillis();
        float dt = (now - m.toggleAnimMs) / 150.0f;
        m.toggleAnimMs = now;
        m.toggleAnim = m.enabled ? Math.min(1.0f, m.toggleAnim + dt) : Math.max(0.0f, m.toggleAnim - dt);

        g.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, lerpColor(COL_TOGGLE_OFF, COL_TOGGLE_ON, m.toggleAnim));
        int knobX = x + 2 + (int) (m.toggleAnim * (TOGGLE_W - TOGGLE_H));
        g.fill(knobX, y + 2, knobX + TOGGLE_H - 4, y + TOGGLE_H - 2, COL_TOGGLE_KNOB);
    }

    private void renderSettingsPanel(GuiGraphics g, int mouseX) {
        int sx = panelX + SIDEBAR_W + 1;
        int sy = panelY + TOP_BAR_H + 1;
        int sw = PANEL_W - SIDEBAR_W - 1;
        int sh = PANEL_H - TOP_BAR_H - 1;

        g.fill(sx, sy, sx + sw, sy + sh, COL_SETTINGS_BG);
        g.fill(sx, sy, sx + sw, sy + TOP_BAR_H, COL_SIDEBAR_BG);
        drawText(g, "< Back", sx + 10, sy + 12, COL_ACCENT_DIM);
        drawText(g, openSettingsModule.name, sx + (sw - font.width(openSettingsModule.name)) / 2, sy + 12, COL_TEXT_PRIMARY);
        g.fill(sx, sy + TOP_BAR_H, sx + sw, sy + TOP_BAR_H + 1, COL_SEPARATOR);

        int bodyY = sy + TOP_BAR_H + 1;
        int bodyH = sh - TOP_BAR_H - 1;
        g.enableScissor(sx, bodyY, sx + sw, bodyY + bodyH);

        int rowY = bodyY - settingsScrollOffset + 4;
        for (SettingEntry s : openSettingsModule.settings) {
            int rowH = renderSettingRow(g, mouseX, s, sx, rowY, sw);
            g.fill(sx + 8, rowY + rowH - 1, sx + sw - 8, rowY + rowH, COL_SEPARATOR);
            rowY += rowH;
        }

        g.disableScissor();
    }

    private int renderSettingRow(GuiGraphics g, int mouseX, SettingEntry s, int x, int y, int w) {
        if (s.type == SettingEntry.Type.TOGGLE) {
            drawText(g, s.label, x + 10, y + 9, COL_TEXT_SECONDARY);
            int tx = x + w - TOGGLE_W - 10;
            int ty = y + 5;
            g.fill(tx, ty, tx + TOGGLE_W, ty + TOGGLE_H, s.boolValue ? COL_TOGGLE_ON : COL_TOGGLE_OFF);
            int knobX = s.boolValue ? tx + TOGGLE_W - TOGGLE_H + 2 : tx + 2;
            g.fill(knobX, ty + 2, knobX + TOGGLE_H - 4, ty + TOGGLE_H - 2, COL_TOGGLE_KNOB);
            return 28;
        }
        if (s.type == SettingEntry.Type.SLIDER) {
            drawText(g, s.label, x + 10, y + 6, COL_TEXT_SECONDARY);
            drawText(g, formatSlider(s), x + w - 45, y + 6, COL_ACCENT);
            int bx = x + 10;
            int by = y + 20;
            int bw = w - 20;
            g.fill(bx, by, bx + bw, by + 4, COL_TOGGLE_OFF);
            float pct = (float) ((s.sliderValue - s.sliderMin) / (s.sliderMax - s.sliderMin));
            pct = Math.max(0.0f, Math.min(1.0f, pct));
            g.fill(bx, by, bx + (int) (pct * bw), by + 4, COL_ACCENT);
            int thumb = bx + (int) (pct * bw) - 4;
            g.fill(thumb, by - 2, thumb + 8, by + 6, COL_TOGGLE_KNOB);
            if (s.dragging && mouseX >= bx && mouseX <= bx + bw) {
                double np = Math.max(0.0, Math.min(1.0, (double) (mouseX - bx) / (double) bw));
                double raw = s.sliderMin + np * (s.sliderMax - s.sliderMin);
                s.sliderValue = Math.round(raw / s.sliderStep) * s.sliderStep;
                CloudServer.broadcastState();
            }
            return 34;
        }
        if (s.type == SettingEntry.Type.SELECT) {
            drawText(g, s.label, x + 10, y + 9, COL_TEXT_SECONDARY);
            String val = s.selectOptions[s.selectIndex];
            drawText(g, "< " + val + " >", x + (w - font.width("< " + val + " >")) / 2, y + 9, COL_ACCENT);
            return 28;
        }
        drawText(g, s.label, x + 10, y + 6, COL_TEXT_SECONDARY);
        int ix = x + 10;
        int iy = y + 17;
        int iw = w - 20;
        g.fill(ix, iy, ix + iw, iy + 18, COL_SEARCH_BG);
        drawText(g, s.textValue, ix + 4, iy + 5, COL_TEXT_PRIMARY);
        if (focusedTextSetting == s && (System.currentTimeMillis() % 1000L) < 500L) {
            int cx = ix + 4 + font.width(s.textValue);
            g.fill(cx, iy + 3, cx + 1, iy + 15, COL_TEXT_PRIMARY);
        }
        return 40;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean allowDragging) {
        int x = (int) event.x();
        int y = (int) event.y();
        if (event.button() != 0) {
            return super.mouseClicked(event, allowDragging);
        }

        boolean inSearch = inRect(x, y, searchX(), searchY(), 180, 20);
        if (inRect(x, y, panelX, panelY, PANEL_W, TOP_BAR_H) && !inSearch) {
            dragging = true;
            dragOffX = x - panelX;
            dragOffY = y - panelY;
            return true;
        }

        if (inSearch) {
            searchFocused = true;
            focusedTextSetting = null;
            return true;
        }
        searchFocused = false;

        if (openSettingsModule != null) {
            int sx = panelX + SIDEBAR_W + 1;
            int sy = panelY + TOP_BAR_H + 1;
            if (inRect(x, y, sx + 6, sy, 80, TOP_BAR_H)) {
                openSettingsModule = null;
                focusedTextSetting = null;
                return true;
            }

            int rowY = sy + TOP_BAR_H + 1 + 4 - settingsScrollOffset;
            for (SettingEntry s : openSettingsModule.settings) {
                int rowH = settingRowHeight(s);
                if (y >= rowY && y < rowY + rowH) {
                    handleSettingClick(s, x, y, rowY, sx, PANEL_W - SIDEBAR_W - 1);
                }
                rowY += rowH;
            }
            return true;
        }

        if (inRect(x, y, panelX, panelY, SIDEBAR_W, PANEL_H)) {
            int itemY = panelY + 34;
            for (String category : CATEGORIES) {
                if (y >= itemY && y < itemY + 26) {
                    selectedCategory = category;
                    rebuildFiltered();
                    return true;
                }
                itemY += 26;
            }
        }

        int lx = listX();
        int ly = listY();
        int lw = listW();
        for (int i = 0; i < filteredModules.size(); i++) {
            ModuleEntry m = filteredModules.get(i);
            int cx = lx + CARD_PAD;
            int cy = ly + CARD_PAD + i * (CARD_H + CARD_GAP) - scrollOffset;
            int cw = lw - CARD_PAD * 2;
            if (!inRect(x, y, cx, cy, cw, CARD_H)) {
                continue;
            }

            int tx = cx + cw - CARD_PAD - TOGGLE_W;
            int ty = cy + (CARD_H - TOGGLE_H) / 2;
            if (inRect(x, y, tx, ty, TOGGLE_W, TOGGLE_H)) {
                m.enabled = !m.enabled;
                m.toggleAnimMs = System.currentTimeMillis();
                CloudServer.broadcastState();
                return true;
            }

            if (!m.settings.isEmpty()) {
                int gx = cx + cw - CARD_PAD - TOGGLE_W - 6 - font.width("*") - 4;
                if (inRect(x, y, gx - 2, cy, 14, CARD_H)) {
                    openSettingsModule = m;
                    settingsScrollOffset = 0;
                    focusedTextSetting = null;
                    return true;
                }
            }

            String keyLabel = m.capturingKey ? "..." : (m.keybind == 0 ? "NONE" : glfwKeyName(m.keybind));
            int chipW = Math.max(32, font.width(keyLabel) + 10);
            int chipX = cx + cw - CARD_PAD - TOGGLE_W - 6 - (m.settings.isEmpty() ? 0 : font.width("*") + 12) - chipW;
            if (inRect(x, y, chipX, cy, chipW, CARD_H)) {
                for (ModuleEntry e : modules) {
                    e.capturingKey = false;
                }
                m.capturingKey = true;
                return true;
            }

            m.enabled = !m.enabled;
            m.toggleAnimMs = System.currentTimeMillis();
            CloudServer.broadcastState();
            return true;
        }

        focusedTextSetting = null;
        return super.mouseClicked(event, allowDragging);
    }

    private void handleSettingClick(SettingEntry s, int mouseX, int mouseY, int rowY, int sx, int sw) {
        switch (s.type) {
            case TOGGLE -> {
                s.boolValue = !s.boolValue;
                CloudServer.broadcastState();
            }
            case SLIDER -> s.dragging = true;
            case SELECT -> {
                String val = s.selectOptions[s.selectIndex];
                int valX = sx + (sw - font.width("< " + val + " >")) / 2;
                if (mouseX < valX + 8) {
                    s.selectIndex = (s.selectIndex - 1 + s.selectOptions.length) % s.selectOptions.length;
                } else {
                    s.selectIndex = (s.selectIndex + 1) % s.selectOptions.length;
                }
                CloudServer.broadcastState();
            }
            case TEXT -> {
                int ix = sx + 10;
                int iy = rowY + 17;
                int iw = sw - 20;
                int ih = 18;
                if (inRect(mouseX, mouseY, ix, iy, iw, ih)) {
                    focusedTextSetting = s;
                    searchFocused = false;
                }
            }
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        boolean changed = false;
        for (ModuleEntry m : modules) {
            for (SettingEntry s : m.settings) {
                if (s.dragging) {
                    s.dragging = false;
                    changed = true;
                }
            }
        }
        if (changed) {
            CloudServer.broadcastState();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int delta = (int) (-verticalAmount * 12);
        if (openSettingsModule != null) {
            settingsScrollOffset = Math.max(0, settingsScrollOffset + delta);
            clampSettingsScroll();
            return true;
        }
        scrollOffset += delta;
        clampModuleScroll();
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char c = (char) event.codepoint();
        if (c < 32 || c > 126) {
            return super.charTyped(event);
        }
        if (searchFocused) {
            searchText += c;
            rebuildFiltered();
            return true;
        }
        if (focusedTextSetting != null) {
            focusedTextSetting.textValue += c;
            CloudServer.broadcastState();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        for (ModuleEntry m : modules) {
            if (m.capturingKey) {
                m.keybind = keyCode == GLFW.GLFW_KEY_ESCAPE ? 0 : keyCode;
                m.capturingKey = false;
                CloudServer.broadcastState();
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (openSettingsModule != null) {
                openSettingsModule = null;
                focusedTextSetting = null;
                return true;
            }
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT || keyCode == GLFW.GLFW_KEY_INSERT) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (searchFocused && !searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
                rebuildFiltered();
                return true;
            }
            if (focusedTextSetting != null && !focusedTextSetting.textValue.isEmpty()) {
                focusedTextSetting.textValue = focusedTextSetting.textValue.substring(0, focusedTextSetting.textValue.length() - 1);
                CloudServer.broadcastState();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void clampPanel() {
        panelX = Math.max(0, Math.min(width - PANEL_W, panelX));
        panelY = Math.max(0, Math.min(height - PANEL_H, panelY));
    }

    private void clampModuleScroll() {
        int max = Math.max(0, filteredModules.size() * (CARD_H + CARD_GAP) + CARD_PAD * 2 - listH());
        scrollOffset = Math.max(0, Math.min(max, scrollOffset));
    }

    private void clampSettingsScroll() {
        if (openSettingsModule == null) {
            settingsScrollOffset = 0;
            return;
        }
        int visible = PANEL_H - TOP_BAR_H - 1 - TOP_BAR_H - 1;
        int content = 4;
        for (SettingEntry s : openSettingsModule.settings) {
            content += settingRowHeight(s);
        }
        settingsScrollOffset = Math.max(0, Math.min(Math.max(0, content - visible), settingsScrollOffset));
    }

    private int settingRowHeight(SettingEntry s) {
        return switch (s.type) {
            case SLIDER -> 34;
            case TEXT -> 40;
            default -> 28;
        };
    }

    private int searchX() { return panelX + PANEL_W - 188; }
    private int searchY() { return panelY + 6; }
    private int listX() { return panelX + SIDEBAR_W + 1; }
    private int listY() { return panelY + TOP_BAR_H + 1; }
    private int listW() { return PANEL_W - SIDEBAR_W - 1 - SCROLLBAR_W; }
    private int listH() { return PANEL_H - TOP_BAR_H - 1; }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void drawText(GuiGraphics g, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color);
    }

    private void fakeRoundCorners(GuiGraphics g, int x, int y, int w, int h, int bg) {
        g.fill(x, y, x + 1, y + 1, bg);
        g.fill(x + w - 1, y, x + w, y + 1, bg);
        g.fill(x, y + h - 1, x + 1, y + h, bg);
        g.fill(x + w - 1, y + h - 1, x + w, y + h, bg);
    }

    private void drawPanelBorder(GuiGraphics g) {
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, COL_SEPARATOR);
        g.fill(panelX, panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H, COL_SEPARATOR);
        g.fill(panelX, panelY, panelX + 1, panelY + PANEL_H, COL_SEPARATOR);
        g.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_SEPARATOR);
    }

    private int lerpColor(int from, int to, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));
        int fa = (from >> 24) & 0xFF, fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int ta = (to >> 24) & 0xFF, tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        return ((int) (fa + (ta - fa) * t) << 24)
            | ((int) (fr + (tr - fr) * t) << 16)
            | ((int) (fg + (tg - fg) * t) << 8)
            | (int) (fb + (tb - fb) * t);
    }

    private String truncate(String text, int maxPx) {
        if (text == null) return "";
        if (font.width(text) <= maxPx) return text;
        String v = text;
        while (!v.isEmpty() && font.width(v + "...") > maxPx) v = v.substring(0, v.length() - 1);
        return v + "...";
    }

    private String glfwKeyName(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "RSHIFT";
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT) return "LSHIFT";
        if (key == GLFW.GLFW_KEY_INSERT) return "INS";
        String n = GLFW.glfwGetKeyName(key, 0);
        return (n != null && !n.isEmpty()) ? n.toUpperCase(Locale.ROOT) : "[" + key + "]";
    }

    private String formatSlider(SettingEntry s) {
        return s.sliderStep < 1.0 ? String.format(Locale.ROOT, "%.1f", s.sliderValue) : Integer.toString((int) Math.round(s.sliderValue));
    }

    public static final class ModuleEntry {
        public String name;
        public String description;
        public String category;
        public boolean enabled;
        public int keybind;
        public boolean capturingKey;
        public List<SettingEntry> settings;
        public float toggleAnim;
        public long toggleAnimMs;

        public ModuleEntry(String n, String desc, String cat, List<SettingEntry> s) {
            name = n;
            description = desc;
            category = cat;
            settings = new ArrayList<SettingEntry>(s);
            enabled = false;
            keybind = 0;
            toggleAnim = 0.0f;
            toggleAnimMs = System.currentTimeMillis();
        }
    }

    public static final class SettingEntry {
        public enum Type { TOGGLE, SLIDER, SELECT, TEXT }

        public String label;
        public Type type;
        public boolean boolValue;
        public double sliderValue;
        public double sliderMin;
        public double sliderMax;
        public double sliderStep;
        public boolean dragging;
        public int selectIndex;
        public String[] selectOptions;
        public String textValue;

        public static SettingEntry ofToggle(String label, boolean def) {
            SettingEntry s = new SettingEntry();
            s.label = label; s.type = Type.TOGGLE; s.boolValue = def; return s;
        }
        public static SettingEntry ofSlider(String label, double min, double max, double step, double def) {
            SettingEntry s = new SettingEntry();
            s.label = label; s.type = Type.SLIDER; s.sliderMin = min; s.sliderMax = max; s.sliderStep = step; s.sliderValue = def; return s;
        }
        public static SettingEntry ofSelect(String label, String[] opts, int def) {
            SettingEntry s = new SettingEntry();
            s.label = label; s.type = Type.SELECT; s.selectOptions = opts; s.selectIndex = Math.max(0, Math.min(def, opts.length - 1)); return s;
        }
        public static SettingEntry ofText(String label, String def) {
            SettingEntry s = new SettingEntry();
            s.label = label; s.type = Type.TEXT; s.textValue = def; return s;
        }
    }
}
