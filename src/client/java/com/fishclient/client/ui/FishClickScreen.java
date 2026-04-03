package com.fishclient.client.ui;

import com.fishclient.client.config.FishConfigManager;
import com.fishclient.client.cloud.CloudBridge;
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

    private static final int COL_PANEL_BG = 0xF00D0F18;
    private static final int COL_SIDEBAR_BG = 0xFF090B12;
    private static final int COL_CARD_BG = 0xFF141A2A;
    private static final int COL_CARD_HOVER = 0xFF1A2134;
    private static final int COL_CARD_ACTIVE = 0xFF201632;
    private static final int COL_SETTINGS_BG = 0xFF13131A;
    private static final int COL_SEARCH_BG = 0xFF141418;
    private static final int COL_SEPARATOR = 0xFF31364B;
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
    private static final int COL_PANEL_GLOW = 0x70331B58;
    private static final int COL_PANEL_INSET = 0xF0080A12;
    private static final int COL_ACCENT_SOFT = 0x554D2A7F;
    private static final int COL_BADGE_BG = 0xCC171D2D;
    private static final int COL_SHADOW = 0x90000000;

    private static final int PANEL_W = 748;
    private static final int PANEL_H = 438;
    private static final int SIDEBAR_W = 132;
    private static final int TOP_BAR_H = 44;
    private static final int CARD_H = 46;
    private static final int CARD_GAP = 8;
    private static final int CARD_PAD = 9;
    private static final int GRID_GAP = 8;
    private static final int MODULE_COLUMNS = 2;
    private static final int SEARCH_W = 196;
    private static final int TOGGLE_W = 28;
    private static final int TOGGLE_H = 14;
    private static final int SCROLLBAR_W = 3;
    private static final String SETTINGS_ICON = "\u2699";

    public static final List<ModuleEntry> SHARED_MODULES = new ArrayList<ModuleEntry>();

    static {
        SHARED_MODULES.add(new ModuleEntry("Nuker", "Breaks lots of blocks around you", "World", List.of(
            SettingEntry.ofSlider("Range", 1, 8, 0.5, 6),
            SettingEntry.ofSlider("Delay ms", 0, 300, 5, 40),
            SettingEntry.ofSlider("Blocks Per Tick", 1, 64, 1, 12),
            SettingEntry.ofSelect("Mine Mode", new String[]{"Packet", "Creative"}, 0),
            SettingEntry.ofSelect("Pattern", new String[]{"Sphere", "Layer"}, 0)
        )));
        SHARED_MODULES.add(new ModuleEntry("Range Extender", "Extends interact and mining distance", "World", List.of(
            SettingEntry.ofSlider("Range", 4, 30, 0.5, 10),
            SettingEntry.ofSelect("Action", new String[]{"Interact + Mine", "Mine Only", "Interact Only"}, 0),
            SettingEntry.ofToggle("Packet Mine", true)
        )));
        SHARED_MODULES.add(new ModuleEntry("Rebreak", "Constantly rebreaks selected blocks", "World", List.of(
            SettingEntry.ofSlider("Range", 1, 30, 0.5, 8),
            SettingEntry.ofSlider("Delay ms", 0, 500, 5, 25),
            SettingEntry.ofSlider("Blocks Per Tick", 1, 48, 1, 8),
            SettingEntry.ofSelect("Mine Mode", new String[]{"Packet", "Creative"}, 0),
            SettingEntry.ofSelect("Select Button", new String[]{"Middle Mouse", "R", "V", "Right Alt"}, 0),
            SettingEntry.ofSelect("Selection Mode", new String[]{"Single", "Cuboid (2 points)"}, 0),
            SettingEntry.ofText("Pos1", "-"),
            SettingEntry.ofText("Pos2", "-"),
            SettingEntry.ofSelect("Clear Selected", new String[]{"Idle", "Run"}, 0),
            SettingEntry.ofText("Selected Blocks", "0"),
            SettingEntry.ofText("Status", "Ready")
        )));
        SHARED_MODULES.add(new ModuleEntry("Prison Layer Miner", "Mines area from pos1 to pos2 top to bottom", "World", List.of(
            SettingEntry.ofText("Pos1", "0 64 0"),
            SettingEntry.ofSelect("Pos1 Action", new String[]{"Manual", "Use Current Block"}, 0),
            SettingEntry.ofText("Pos2", "10 60 10"),
            SettingEntry.ofSelect("Pos2 Action", new String[]{"Manual", "Use Current Block"}, 0),
            SettingEntry.ofText("Target Block", "minecraft:stone"),
            SettingEntry.ofText("Looking At", ""),
            SettingEntry.ofSelect("Target Action", new String[]{"Manual", "Use Looking Block"}, 0),
            SettingEntry.ofSlider("Range", 1, 8, 0.5, 8),
            SettingEntry.ofSlider("Delay ms", 0, 500, 5, 25),
            SettingEntry.ofSlider("Blocks Per Tick", 1, 96, 1, 20),
            SettingEntry.ofSelect("Mine Mode", new String[]{"Packet", "Creative"}, 0),
            SettingEntry.ofSelect("Y Order", new String[]{"TopDown", "BottomUp"}, 0)
        )));
        SHARED_MODULES.add(new ModuleEntry("Wings", "Creative-style fly in survival", "Movement", List.of(
            SettingEntry.ofSlider("Speed", 0.1, 5.0, 0.1, 1.0),
            SettingEntry.ofSlider("Vertical", 0.1, 3.0, 0.1, 0.8),
            SettingEntry.ofToggle("Glide", true)
        )));
        SHARED_MODULES.add(new ModuleEntry("Sprint", "Auto sprint always on", "Movement", List.of()));
        SHARED_MODULES.add(new ModuleEntry("FISH MODE", "Goofy mode: no fall + air jump", "Movement", List.of()));
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
        SHARED_MODULES.add(new ModuleEntry("PayAll", "Pays all online players by amount", "Macros", List.of(
            SettingEntry.ofText("Amount", "1000"),
            SettingEntry.ofSlider("Delay ms", 0, 2000, 25, 250),
            SettingEntry.ofToggle("Include Self", false),
            SettingEntry.ofSelect("Player Source", new String[]{"Tab List", "World Entities"}, 0),
            SettingEntry.ofSelect("Scan Players", new String[]{"Idle", "Run"}, 0),
            SettingEntry.ofText("Targets Cached", "0"),
            SettingEntry.ofText("Status", "Idle")
        )));
        SHARED_MODULES.add(new ModuleEntry("ClickGUI Bind", "Key to open this menu", "Keybinds", List.of()));
        SHARED_MODULES.add(new ModuleEntry("Config Manager", "Save and load full client configs", "Config", List.of(
            SettingEntry.ofSelect("Config", new String[]{"default"}, 0),
            SettingEntry.ofText("Config Name", "myconfig"),
            SettingEntry.ofSelect("Save Selected", new String[]{"Idle", "Run"}, 0),
            SettingEntry.ofSelect("Save As Name", new String[]{"Idle", "Run"}, 0),
            SettingEntry.ofSelect("Load Selected", new String[]{"Idle", "Run"}, 0),
            SettingEntry.ofSelect("Delete Selected", new String[]{"Idle", "Run"}, 0),
            SettingEntry.ofSelect("Refresh List", new String[]{"Idle", "Run"}, 0),
            SettingEntry.ofText("Status", "Ready")
        )));
        ModuleEntry hudModule = new ModuleEntry("HUD", "Shows Fish logo and client name", "Render", List.of());
        hudModule.enabled = true;
        hudModule.toggleAnim = 1.0f;
        SHARED_MODULES.add(hudModule);
        ModuleEntry arrayListModule = new ModuleEntry("ArrayList", "Shows enabled modules on HUD", "Render", List.of());
        arrayListModule.enabled = true;
        arrayListModule.toggleAnim = 1.0f;
        SHARED_MODULES.add(arrayListModule);
        ModuleEntry itemScaleModule = new ModuleEntry("ItemScale", "Scales first-person held item rendering", "Render", List.of(
            SettingEntry.ofSlider("Scale", 0.1, 3.0, 0.1, 1.0)
        ));
        itemScaleModule.enabled = true;
        itemScaleModule.toggleAnim = 1.0f;
        SHARED_MODULES.add(itemScaleModule);
        SHARED_MODULES.add(new ModuleEntry("PlayerESP", "Highlights nearby players", "Render", List.of(
            SettingEntry.ofSlider("Range", 8, 160, 1, 80),
            SettingEntry.ofSelect("Mode", new String[]{"Neon", "Soft", "Health", "Chroma"}, 0),
            SettingEntry.ofSelect("Draw Style", new String[]{"Both", "Outline", "Filled"}, 0),
            SettingEntry.ofToggle("Tracers", true)
        )));
        SHARED_MODULES.add(new ModuleEntry("StorageESP", "Highlights nearby storage blocks", "Render", List.of(
            SettingEntry.ofSlider("Range", 4, 32, 1, 16),
            SettingEntry.ofSelect("Mode", new String[]{"Neon", "Soft", "Chroma"}, 0),
            SettingEntry.ofToggle("Include Ender", true),
            SettingEntry.ofToggle("Include Furnaces", true)
        )));
        SHARED_MODULES.add(new ModuleEntry("ClickGUI Style", "ClickGUI style and palette options", "Theme", List.of(
            SettingEntry.ofSlider("Accent R", 0, 255, 1, 170),
            SettingEntry.ofSlider("Accent G", 0, 255, 1, 85),
            SettingEntry.ofSlider("Accent B", 0, 255, 1, 255),
            SettingEntry.ofSelect("GUI STYLE", new String[]{"Default", "Apple iOS"}, 0),
            SettingEntry.ofSelect("ClickGUI Style", new String[]{"Panel", "Dropdown"}, 0)
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

    private static final String[] CATEGORIES = {"World", "Movement", "Combat", "Render", "Macros", "Keybinds", "Config", "Theme"};

    public FishClickScreen() {
        super(Component.literal("Fish Client"));
    }

    @Override
    protected void init() {
        FishConfigManager.syncUiSettings();
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
        int bgTop = themed(0x3B101A35, 0x45172438);
        int bgBottom = themed(0x35110A20, 0x3A121E2F);
        int fog = themed(0x94040711, 0x9A070B14);
        int panelBg = themed(COL_PANEL_BG, 0xF0141A24);
        int panelInset = themed(COL_PANEL_INSET, 0xF0101620);
        int glow = themed(COL_PANEL_GLOW, 0x503A4F78);
        int shadow = themed(COL_SHADOW, 0x90000000);
        int divider = themed(COL_SEPARATOR, 0xFF314057);

        g.fill(0, 0, width, height, fog);
        int midY = height / 2;
        g.fill(0, 0, width, midY, bgTop);
        g.fill(0, midY, width, height, bgBottom);
        for (int i = 0; i < 18; i++) {
            int stripY = panelY - 120 + i * 14;
            int line = themed(0x221133, 0x5B768F);
            int alpha = (i % 2 == 0) ? (isAppleTheme() ? 0x08 : 0x0A) : (isAppleTheme() ? 0x04 : 0x05);
            g.fill(0, stripY, width, stripY + 1, (alpha << 24) | line);
        }

        if (dragging) {
            panelX = mouseX - dragOffX;
            panelY = mouseY - dragOffY;
            clampPanel();
        }

        g.fill(panelX - 6, panelY - 6, panelX + PANEL_W + 6, panelY + PANEL_H + 6, shadow);
        g.fill(panelX - 3, panelY - 3, panelX + PANEL_W + 3, panelY + PANEL_H + 3, glow);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, panelBg);
        g.fill(panelX + 1, panelY + 1, panelX + PANEL_W - 1, panelY + PANEL_H - 1, panelInset);
        renderSidebar(g, mouseX, mouseY);
        g.fill(panelX + SIDEBAR_W, panelY, panelX + SIDEBAR_W + 1, panelY + PANEL_H, divider);
        renderTopBar(g);
        g.fill(panelX + SIDEBAR_W + 1, panelY + TOP_BAR_H, panelX + PANEL_W, panelY + TOP_BAR_H + 1, divider);
        renderModuleList(g, mouseX, mouseY);

        if (openSettingsModule != null) {
            renderSettingsPanel(g, mouseX);
        }

        drawPanelBorder(g);
    }

    private void renderSidebar(GuiGraphics g, int mx, int my) {
        int sideBg = themed(COL_SIDEBAR_BG, 0xFF131A26);
        int badgeBg = themed(COL_BADGE_BG, 0xFF1C2536);
        int accent = themed(COL_ACCENT, 0xFF6E9CFF);
        int accentSoft = themed(COL_ACCENT_SOFT, 0x553F6DBA);
        int textPrimary = themed(COL_TEXT_PRIMARY, 0xFFE7EEF9);
        int textMuted = themed(COL_TEXT_MUTED, 0xFF96A6C4);
        int separator = themed(COL_SEPARATOR, 0xFF2B3850);
        int cardBg = themed(COL_CARD_BG, 0xFF1A2233);
        int cardHover = themed(COL_CARD_HOVER, 0xFF202A3F);
        int textSecondary = themed(COL_TEXT_SECONDARY, 0xFFB9C7E0);

        g.fill(panelX, panelY, panelX + SIDEBAR_W, panelY + PANEL_H, sideBg);
        g.fill(panelX + 10, panelY + 12, panelX + SIDEBAR_W - 10, panelY + 48, badgeBg);
        g.fill(panelX + 10, panelY + 12, panelX + 12, panelY + 48, accent);
        g.fill(panelX + 14, panelY + 18, panelX + 34, panelY + 38, accentSoft);
        drawText(g, "F", panelX + 22, panelY + 25, textPrimary);
        drawText(g, "Fish Client", panelX + 40, panelY + 20, textPrimary);
        drawText(g, "dev 1.21.11", panelX + 40, panelY + 31, textMuted);
        g.fill(panelX + 10, panelY + 56, panelX + SIDEBAR_W - 10, panelY + 57, separator);

        int itemY = panelY + 64;
        for (String category : CATEGORIES) {
            boolean active = category.equals(selectedCategory);
            boolean hovered = inRect(mx, my, panelX + 8, itemY, SIDEBAR_W - 16, 28);
            if (active) {
                g.fill(panelX + 8, itemY, panelX + SIDEBAR_W - 8, itemY + 28, cardHover);
                g.fill(panelX + 8, itemY, panelX + 11, itemY + 28, accent);
                g.fill(panelX + 11, itemY, panelX + 22, itemY + 28, accentSoft);
            } else if (hovered) {
                g.fill(panelX + 8, itemY, panelX + SIDEBAR_W - 8, itemY + 28, cardBg);
            }
            drawText(g, active ? "> " + category : "  " + category, panelX + 16, itemY + 10, active ? accent : (hovered ? textSecondary : textMuted));
            itemY += 30;
        }

        String username = "Player";
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null && mc.getUser().getName() != null) {
            username = mc.getUser().getName();
        }
        g.fill(panelX + 8, panelY + PANEL_H - 42, panelX + SIDEBAR_W - 8, panelY + PANEL_H - 10, badgeBg);
        g.fill(panelX + 8, panelY + PANEL_H - 42, panelX + 11, panelY + PANEL_H - 10, themed(COL_ACCENT_DIM, 0xFF567CC8));
        drawText(g, truncate(username, SIDEBAR_W - 28), panelX + 16, panelY + PANEL_H - 31, textSecondary);
        drawText(g, "rank: Fish", panelX + 16, panelY + PANEL_H - 20, textMuted);
    }

    private void renderTopBar(GuiGraphics g) {
        int contentX = panelX + SIDEBAR_W + 14;
        String title = selectedCategory + " Modules";
        drawText(g, title, contentX, panelY + 12, themed(COL_TEXT_PRIMARY, 0xFFE7EEF9));
        drawText(g, "Fish Client - Dev UI", contentX, panelY + 24, themed(COL_TEXT_MUTED, 0xFF96A6C4));

        int count = filteredModules.size();
        String countLabel = count + " total";
        int badgeW = font.width(countLabel) + 12;
        int badgeX = contentX + font.width(title) + 10;
        g.fill(badgeX, panelY + 11, badgeX + badgeW, panelY + 26, themed(COL_BADGE_BG, 0xFF1C2536));
        fakeRoundCorners(g, badgeX, panelY + 11, badgeW, 15, themed(COL_PANEL_INSET, 0xFF101620));
        drawText(g, countLabel, badgeX + 6, panelY + 15, themed(COL_TEXT_MUTED, 0xFF96A6C4));

        int sx = searchX();
        int sy = searchY();
        g.fill(sx, sy, sx + SEARCH_W, sy + 24, searchFocused ? themed(COL_CARD_HOVER, 0xFF202A3F) : themed(COL_SEARCH_BG, 0xFF1B2436));
        g.fill(sx, sy, sx + 2, sy + 24, searchFocused ? themed(COL_ACCENT, 0xFF6E9CFF) : themed(COL_SEPARATOR, 0xFF2B3850));
        fakeRoundCorners(g, sx, sy, SEARCH_W, 24, themed(COL_PANEL_INSET, 0xFF101620));
        drawText(g, "S", sx + 5, sy + 6, themed(COL_TEXT_MUTED, 0xFF96A6C4));
        String placeholder = (searchText.isEmpty() && !searchFocused) ? "Search modules..." : searchText;
        drawText(g, placeholder, sx + 16, sy + 6, searchText.isEmpty() ? themed(COL_TEXT_MUTED, 0xFF9BACCA) : themed(COL_TEXT_PRIMARY, 0xFFE7EEF9));
        if (searchFocused && (System.currentTimeMillis() % 1000L) < 500L) {
            int cursorX = sx + 16 + font.width(searchText);
            g.fill(cursorX, sy + 4, cursorX + 1, sy + 18, themed(COL_TEXT_PRIMARY, 0xFFE7EEF9));
        }
    }

    private void renderModuleList(GuiGraphics g, int mx, int my) {
        int lx = listX();
        int ly = listY();
        int lw = listW();
        int lh = listH();
        int cardH = activeCardHeight();
        boolean dropdown = isDropdownStyle();
        int panelInset = themed(COL_PANEL_INSET, 0xFF101620);
        int cardBg = themed(COL_CARD_BG, 0xFF1A2233);
        int cardHover = themed(COL_CARD_HOVER, 0xFF202A3F);
        int cardActive = themed(COL_CARD_ACTIVE, 0xFF273553);
        int accent = themed(COL_ACCENT, 0xFF6E9CFF);
        int accentSoft = themed(COL_ACCENT_SOFT, 0x553F6DBA);
        int chipBg = themed(COL_CHIP_BG, 0xFF25314A);
        int chipActive = themed(COL_CHIP_ACTIVE, 0xFF6E9CFF);
        int textPrimary = themed(COL_TEXT_PRIMARY, 0xFFE7EEF9);
        int textSecondary = themed(COL_TEXT_SECONDARY, 0xFFB9C7E0);
        int textMuted = themed(COL_TEXT_MUTED, 0xFF96A6C4);

        for (int i = 0; i < 5; i++) {
            int lineY = ly + 16 + i * 24;
            g.fill(lx, lineY, lx + lw, lineY + 1, 0x12000000);
        }

        g.enableScissor(lx, ly, lx + lw, ly + lh);

        for (int i = 0; i < filteredModules.size(); i++) {
            ModuleEntry m = filteredModules.get(i);
            int cx = moduleCardX(i);
            int cy = moduleCardY(i);
            int cw = moduleCardWidth();
            if (cy + cardH < ly || cy > ly + lh) {
                continue;
            }

            boolean hovered = inRect(mx, my, cx, cy, cw, cardH);
            int bg = m.enabled ? cardActive : (hovered ? cardHover : cardBg);
            g.fill(cx, cy, cx + cw, cy + cardH, bg);
            fakeRoundCorners(g, cx, cy, cw, cardH, panelInset);
            if (m.enabled) {
                g.fill(cx, cy, cx + 3, cy + cardH, accent);
                g.fill(cx + 3, cy, cx + 18, cy + cardH, accentSoft);
            }

            int right = cx + cw - CARD_PAD;
            int tx = right - TOGGLE_W;
            int ty = cy + (cardH - TOGGLE_H) / 2;
            renderToggle(g, m, tx, ty);
            right = tx - 7;

            if (!m.settings.isEmpty()) {
                int gearW = font.width(SETTINGS_ICON) + 8;
                int gx = right - gearW;
                int gearY = cy + (cardH - 18) / 2;
                g.fill(gx, gearY, gx + gearW, gearY + 18, hovered ? themed(COL_BADGE_BG, 0xFF1C2536) : chipBg);
                fakeRoundCorners(g, gx, gearY, gearW, 18, bg);
                drawText(g, SETTINGS_ICON, gx + 4, gearY + 5, inRect(mx, my, gx, gearY, gearW, 18) ? accent : textMuted);
                right = gx - 7;
            }

            String keyLabel = m.capturingKey ? "..." : (m.keybind == 0 ? "NONE" : glfwKeyName(m.keybind));
            int chipW = Math.max(38, font.width(keyLabel) + 12);
            int chipX = right - chipW;
            int chipY = cy + (cardH - 16) / 2;
            g.fill(chipX, chipY, chipX + chipW, chipY + 16, m.capturingKey ? chipActive : chipBg);
            fakeRoundCorners(g, chipX, chipY, chipW, 16, bg);
            drawText(g, keyLabel, chipX + 6, chipY + 4, m.capturingKey ? textPrimary : textMuted);
            right = chipX - 8;

            int textX = cx + (m.enabled ? 11 : 8);
            int textW = Math.max(30, right - textX - 4);
            if (dropdown) {
                drawText(g, truncate(m.name, textW), textX, cy + ((cardH - 8) / 2), m.enabled ? textPrimary : textSecondary);
            } else {
                drawText(g, truncate(m.name, textW), textX, cy + 11, m.enabled ? textPrimary : textSecondary);
                drawText(g, truncate(m.description, textW), textX, cy + 26, textMuted);
            }
        }

        g.disableScissor();

        int total = moduleRows() * (cardH + activeCardGap()) + CARD_PAD * 2;
        if (total > lh) {
            int thumbH = Math.max(22, lh * lh / total);
            int range = total - lh;
            int thumbY = ly + (int) ((float) scrollOffset / (float) range * (float) (lh - thumbH));
            g.fill(lx + lw, ly, lx + lw + SCROLLBAR_W, ly + lh, cardBg);
            g.fill(lx + lw, thumbY, lx + lw + SCROLLBAR_W, thumbY + thumbH, themed(COL_ACCENT_DIM, 0xFF5F83CE));
        }
    }

    private void renderToggle(GuiGraphics g, ModuleEntry m, int x, int y) {
        int toggleOff = themed(COL_TOGGLE_OFF, 0xFF42526E);
        int toggleOn = themed(COL_TOGGLE_ON, 0xFF6E9CFF);
        int knob = themed(COL_TOGGLE_KNOB, 0xFFFFFFFF);
        int cardBg = themed(COL_CARD_BG, 0xFF1A2233);

        long now = System.currentTimeMillis();
        float dt = (now - m.toggleAnimMs) / 150.0f;
        m.toggleAnimMs = now;
        m.toggleAnim = m.enabled ? Math.min(1.0f, m.toggleAnim + dt) : Math.max(0.0f, m.toggleAnim - dt);

        g.fill(x, y, x + TOGGLE_W, y + TOGGLE_H, lerpColor(toggleOff, toggleOn, m.toggleAnim));
        int knobX = x + 2 + (int) (m.toggleAnim * (TOGGLE_W - TOGGLE_H));
        g.fill(knobX, y + 2, knobX + TOGGLE_H - 4, y + TOGGLE_H - 2, knob);
        fakeRoundCorners(g, x, y, TOGGLE_W, TOGGLE_H, cardBg);
    }

    private void renderSettingsPanel(GuiGraphics g, int mouseX) {
        int sx = panelX + SIDEBAR_W + 1;
        int sy = panelY + TOP_BAR_H + 1;
        int sw = PANEL_W - SIDEBAR_W - 1;
        int sh = PANEL_H - TOP_BAR_H - 1;
        int settingsBg = themed(COL_SETTINGS_BG, 0xFF131A28);
        int sideBg = themed(COL_SIDEBAR_BG, 0xFF131A26);
        int badgeBg = themed(COL_BADGE_BG, 0xFF1C2536);
        int accentDim = themed(COL_ACCENT_DIM, 0xFF567CC8);
        int separator = themed(COL_SEPARATOR, 0xFF2B3850);
        int textPrimary = themed(COL_TEXT_PRIMARY, 0xFFE7EEF9);

        g.fill(sx, sy, sx + sw, sy + sh, settingsBg);
        g.fill(sx, sy, sx + sw, sy + TOP_BAR_H, sideBg);
        g.fill(sx + 8, sy + 8, sx + 70, sy + 28, badgeBg);
        fakeRoundCorners(g, sx + 8, sy + 8, 62, 20, sideBg);
        drawText(g, "< Back", sx + 15, sy + 14, accentDim);
        drawText(g, openSettingsModule.name, sx + (sw - font.width(openSettingsModule.name)) / 2, sy + 12, textPrimary);
        g.fill(sx, sy + TOP_BAR_H, sx + sw, sy + TOP_BAR_H + 1, separator);

        int bodyY = sy + TOP_BAR_H + 1;
        int bodyH = sh - TOP_BAR_H - 1;
        g.enableScissor(sx, bodyY, sx + sw, bodyY + bodyH);

        int rowY = bodyY - settingsScrollOffset + 4;
        for (SettingEntry s : openSettingsModule.settings) {
            int rowH = renderSettingRow(g, mouseX, s, sx, rowY, sw);
            g.fill(sx + 8, rowY + rowH - 1, sx + sw - 8, rowY + rowH, separator);
            rowY += rowH;
        }

        g.disableScissor();
    }

    private int renderSettingRow(GuiGraphics g, int mouseX, SettingEntry s, int x, int y, int w) {
        int rowBg = themed(0x340E101A, 0x34101926);
        int settingsBg = themed(COL_SETTINGS_BG, 0xFF131A28);
        int textSecondary = themed(COL_TEXT_SECONDARY, 0xFFB9C7E0);
        int accent = themed(COL_ACCENT, 0xFF6E9CFF);
        int searchBg = themed(COL_SEARCH_BG, 0xFF1B2436);
        int textPrimary = themed(COL_TEXT_PRIMARY, 0xFFE7EEF9);

        if (s.type == SettingEntry.Type.TOGGLE) {
            g.fill(x + 6, y + 2, x + w - 6, y + 26, rowBg);
            fakeRoundCorners(g, x + 6, y + 2, w - 12, 24, settingsBg);
            drawText(g, s.label, x + 14, y + 10, textSecondary);
            int tx = x + w - TOGGLE_W - 10;
            int ty = y + 5;
            g.fill(tx, ty, tx + TOGGLE_W, ty + TOGGLE_H, s.boolValue ? themed(COL_TOGGLE_ON, 0xFF6E9CFF) : themed(COL_TOGGLE_OFF, 0xFF42526E));
            int knobX = s.boolValue ? tx + TOGGLE_W - TOGGLE_H + 2 : tx + 2;
            g.fill(knobX, ty + 2, knobX + TOGGLE_H - 4, ty + TOGGLE_H - 2, themed(COL_TOGGLE_KNOB, 0xFFFFFFFF));
            return 28;
        }
        if (s.type == SettingEntry.Type.SLIDER) {
            g.fill(x + 6, y + 2, x + w - 6, y + 32, rowBg);
            fakeRoundCorners(g, x + 6, y + 2, w - 12, 30, settingsBg);
            drawText(g, s.label, x + 14, y + 7, textSecondary);
            drawText(g, formatSlider(s), x + w - 45, y + 6, accent);
            int bx = x + 14;
            int by = y + 20;
            int bw = w - 28;
            g.fill(bx, by, bx + bw, by + 4, themed(COL_TOGGLE_OFF, 0xFF42526E));
            float pct = (float) ((s.sliderValue - s.sliderMin) / (s.sliderMax - s.sliderMin));
            pct = Math.max(0.0f, Math.min(1.0f, pct));
            g.fill(bx, by, bx + (int) (pct * bw), by + 4, accent);
            int thumb = bx + (int) (pct * bw) - 4;
            g.fill(thumb, by - 2, thumb + 8, by + 6, themed(COL_TOGGLE_KNOB, 0xFFFFFFFF));
            if (s.dragging && mouseX >= bx && mouseX <= bx + bw) {
                double np = Math.max(0.0, Math.min(1.0, (double) (mouseX - bx) / (double) bw));
                double raw = s.sliderMin + np * (s.sliderMax - s.sliderMin);
                s.sliderValue = Math.round(raw / s.sliderStep) * s.sliderStep;
                CloudBridge.broadcastState();
            }
            return 34;
        }
        if (s.type == SettingEntry.Type.SELECT) {
            g.fill(x + 6, y + 2, x + w - 6, y + 26, rowBg);
            fakeRoundCorners(g, x + 6, y + 2, w - 12, 24, settingsBg);
            drawText(g, s.label, x + 14, y + 10, textSecondary);
            String val = s.selectOptions[s.selectIndex];
            drawText(g, "< " + val + " >", x + (w - font.width("< " + val + " >")) / 2, y + 9, accent);
            return 28;
        }
        g.fill(x + 6, y + 2, x + w - 6, y + 38, rowBg);
        fakeRoundCorners(g, x + 6, y + 2, w - 12, 36, settingsBg);
        drawText(g, s.label, x + 14, y + 7, textSecondary);
        int ix = x + 14;
        int iy = y + 17;
        int iw = w - 28;
        g.fill(ix, iy, ix + iw, iy + 18, searchBg);
        fakeRoundCorners(g, ix, iy, iw, 18, rowBg);
        drawText(g, s.textValue, ix + 4, iy + 5, textPrimary);
        if (focusedTextSetting == s && (System.currentTimeMillis() % 1000L) < 500L) {
            int cx = ix + 4 + font.width(s.textValue);
            g.fill(cx, iy + 3, cx + 1, iy + 15, textPrimary);
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

        boolean inSearch = inRect(x, y, searchX(), searchY(), SEARCH_W, 24);
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
            if (inRect(x, y, sx + 8, sy + 8, 62, 20)) {
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
            int itemY = panelY + 64;
            for (String category : CATEGORIES) {
                if (inRect(x, y, panelX + 8, itemY, SIDEBAR_W - 16, 28)) {
                    selectedCategory = category;
                    rebuildFiltered();
                    return true;
                }
                itemY += 30;
            }
        }

        for (int i = 0; i < filteredModules.size(); i++) {
            ModuleEntry m = filteredModules.get(i);
            int cx = moduleCardX(i);
            int cy = moduleCardY(i);
            int cw = moduleCardWidth();
            int cardH = activeCardHeight();
            if (!inRect(x, y, cx, cy, cw, cardH)) {
                continue;
            }

            int tx = cx + cw - CARD_PAD - TOGGLE_W;
            int ty = cy + (cardH - TOGGLE_H) / 2;
            if (inRect(x, y, tx, ty, TOGGLE_W, TOGGLE_H)) {
                m.enabled = !m.enabled;
                m.toggleAnimMs = System.currentTimeMillis();
                CloudBridge.broadcastState();
                return true;
            }

            if (!m.settings.isEmpty()) {
                int right = cx + cw - CARD_PAD - TOGGLE_W - 7;
                int gearW = font.width(SETTINGS_ICON) + 8;
                int gx = right - gearW;
                int gearY = cy + (cardH - 18) / 2;
                if (inRect(x, y, gx, gearY, gearW, 18)) {
                    openSettingsModule = m;
                    settingsScrollOffset = 0;
                    focusedTextSetting = null;
                    return true;
                }
            }

            String keyLabel = m.capturingKey ? "..." : (m.keybind == 0 ? "NONE" : glfwKeyName(m.keybind));
            int right = cx + cw - CARD_PAD - TOGGLE_W - 7;
            if (!m.settings.isEmpty()) {
                right -= (font.width(SETTINGS_ICON) + 8) + 7;
            }
            int chipW = Math.max(38, font.width(keyLabel) + 12);
            int chipX = right - chipW;
            int chipY = cy + (cardH - 16) / 2;
            if (inRect(x, y, chipX, chipY, chipW, 16)) {
                for (ModuleEntry e : modules) {
                    e.capturingKey = false;
                }
                m.capturingKey = true;
                return true;
            }

            m.enabled = !m.enabled;
            m.toggleAnimMs = System.currentTimeMillis();
            CloudBridge.broadcastState();
            return true;
        }

        focusedTextSetting = null;
        return super.mouseClicked(event, allowDragging);
    }

    private void handleSettingClick(SettingEntry s, int mouseX, int mouseY, int rowY, int sx, int sw) {
        switch (s.type) {
            case TOGGLE -> {
                s.boolValue = !s.boolValue;
                CloudBridge.broadcastState();
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
                if (openSettingsModule != null && "Config Manager".equalsIgnoreCase(openSettingsModule.name)) {
                    if (FishConfigManager.handleSelectAction(openSettingsModule, s)) {
                        rebuildFiltered();
                    }
                }
                CloudBridge.broadcastState();
            }
            case TEXT -> {
                int ix = sx + 14;
                int iy = rowY + 17;
                int iw = sw - 28;
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
            CloudBridge.broadcastState();
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
            CloudBridge.broadcastState();
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
                CloudBridge.broadcastState();
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
                CloudBridge.broadcastState();
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
        int max = Math.max(0, moduleRows() * (activeCardHeight() + activeCardGap()) + CARD_PAD * 2 - listH());
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

    private int searchX() { return panelX + PANEL_W - SEARCH_W - 12; }
    private int searchY() { return panelY + 14; }
    private int listX() { return panelX + SIDEBAR_W + 1; }
    private int listY() { return panelY + TOP_BAR_H + 1; }
    private int listW() { return PANEL_W - SIDEBAR_W - 1 - SCROLLBAR_W; }
    private int listH() { return PANEL_H - TOP_BAR_H - 1; }
    private int activeColumns() { return isDropdownStyle() ? 1 : MODULE_COLUMNS; }
    private int activeGridGap() { return isDropdownStyle() ? 0 : GRID_GAP; }
    private int activeCardHeight() { return isDropdownStyle() ? 36 : CARD_H; }
    private int activeCardGap() { return isDropdownStyle() ? 4 : CARD_GAP; }
    private int moduleRows() { return (filteredModules.size() + activeColumns() - 1) / activeColumns(); }
    private int moduleCardWidth() { return (listW() - CARD_PAD * 2 - (activeColumns() - 1) * activeGridGap()) / activeColumns(); }
    private int moduleCardX(int index) {
        int col = index % activeColumns();
        return listX() + CARD_PAD + col * (moduleCardWidth() + activeGridGap());
    }
    private int moduleCardY(int index) {
        int row = index / activeColumns();
        return listY() + CARD_PAD + row * (activeCardHeight() + activeCardGap()) - scrollOffset;
    }

    private int themeSelect(String label, int fallback) {
        ModuleEntry theme = modules.stream()
            .filter(m -> "Theme".equalsIgnoreCase(m.category))
            .findFirst()
            .orElse(null);
        if (theme == null || theme.settings == null) {
            return fallback;
        }
        for (SettingEntry s : theme.settings) {
            if (s.type == SettingEntry.Type.SELECT && label.equalsIgnoreCase(s.label) && s.selectOptions != null && s.selectOptions.length > 0) {
                return Math.max(0, Math.min(s.selectIndex, s.selectOptions.length - 1));
            }
        }
        return fallback;
    }

    private boolean isAppleTheme() {
        return themeSelect("GUI STYLE", 0) == 1;
    }

    private boolean isDropdownStyle() {
        return themeSelect("ClickGUI Style", 0) == 1;
    }

    private int themed(int normal, int apple) {
        return isAppleTheme() ? apple : normal;
    }

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
        int border = themed(COL_SEPARATOR, 0xFF314057);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 1, border);
        g.fill(panelX, panelY + PANEL_H - 1, panelX + PANEL_W, panelY + PANEL_H, border);
        g.fill(panelX, panelY, panelX + 1, panelY + PANEL_H, border);
        g.fill(panelX + PANEL_W - 1, panelY, panelX + PANEL_W, panelY + PANEL_H, border);
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

