package com.fishclient.ui.clickgui;

import com.fishclient.FishClientMod;
import com.fishclient.modules.Category;
import com.fishclient.modules.Module;
import com.fishclient.utils.RenderUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ClickGUI extends GuiScreen {

    private static final int HEADER_H = 42;
    private static final int SIDEBAR_W = 145;
    private static final int CARD_GAP = 8;
    private static final int CARD_H = 34;

    private int panelX;
    private int panelY;
    private int panelW = 620;
    private int panelH = 350;

    private boolean dragging;
    private int dragX;
    private int dragY;

    private String searchQuery = "";
    private boolean typingSearch;
    private int scrollRows;

    private int cardsX;
    private int cardsY;
    private int cardsW;
    private int cardsH;

    private int selectedSidebarIndex = 4;

    private static final class SidebarItem {
        private final String name;
        private final String icon;
        private final Category category;

        private SidebarItem(String name, String icon, Category category) {
            this.name = name;
            this.icon = icon;
            this.category = category;
        }
    }

    private final SidebarItem[] sidebar = new SidebarItem[] {
        new SidebarItem("Combat", "!", Category.PLAYER),
        new SidebarItem("Movement", ">", Category.PLAYER),
        new SidebarItem("Player", "@", Category.PLAYER),
        new SidebarItem("Misc", "~", Category.MISC),
        new SidebarItem("Visual", "O", Category.VISUAL),
        new SidebarItem("Overlay", "#", Category.HUD),
        new SidebarItem("Theme", "R", Category.RADIO)
    };

    @Override
    public void initGui() {
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        if (dragging) {
            panelX = mouseX - dragX;
            panelY = mouseY - dragY;
            clampPanel();
        }

        RenderUtils.drawGlow(panelX, panelY, panelW, panelH, 12.0f, 0x338244D0, 3);
        RenderUtils.drawRoundedRect(panelX, panelY, panelW, panelH, 12.0f, 0xD3171820);
        RenderUtils.drawRoundedRect(panelX + 1.0f, panelY + 1.0f, panelW - 2.0f, 1.0f, 0.5f, 0x30FFFFFF);

        drawSidebar(mouseX, mouseY);
        drawHeader(mouseX, mouseY);
        drawCards(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0 && inBounds(mouseX, mouseY, panelX, panelY, panelW, HEADER_H)) {
            dragging = true;
            dragX = mouseX - panelX;
            dragY = mouseY - panelY;
        }

        if (mouseButton == 0 && handleSidebarClick(mouseX, mouseY)) {
            typingSearch = false;
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (mouseButton == 0 && handleSearchClick(mouseX, mouseY)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (mouseButton == 0 && handleModuleToggleClick(mouseX, mouseY)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        if (mouseButton == 0) {
            typingSearch = false;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }

        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (inBounds(mx, my, cardsX, cardsY, cardsW, cardsH)) {
            if (wheel < 0) {
                scrollRows++;
            } else {
                scrollRows--;
            }
            clampScrollRows();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (!typingSearch) {
            super.keyTyped(typedChar, keyCode);
            return;
        }

        if (keyCode == 1) {
            typingSearch = false;
            return;
        }
        if (keyCode == 14) {
            if (!searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                scrollRows = 0;
            }
            return;
        }
        if (keyCode == 28 || keyCode == 156) {
            return;
        }

        if (ChatAllowedCharacters.isAllowedCharacter(typedChar) && searchQuery.length() < 30) {
            searchQuery += typedChar;
            scrollRows = 0;
        }
    }

    private void drawSidebar(int mouseX, int mouseY) {
        float boxX = panelX + 8.0f;
        float boxY = panelY + 8.0f;
        float boxW = SIDEBAR_W - 12.0f;
        float boxH = panelH - 16.0f;
        RenderUtils.drawRoundedRect(boxX, boxY, boxW, boxH, 10.0f, 0xA113141B);

        mc.getTextureManager().bindTexture(new ResourceLocation("fishclient", "textures/logo.png"));
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawModalRectWithCustomSizedTexture(panelX + 16, panelY + 16, 0, 0, 14, 14, 64, 64);
        fontRendererObj.drawString("Meow", panelX + 35, panelY + 17, 0xFFE8B6FF);
        fontRendererObj.drawString("Version - 2.4", panelX + 35, panelY + 29, 0xFF7D7894);
        RenderUtils.drawRoundedRect(panelX + 14.0f, panelY + 43.0f, SIDEBAR_W - 24.0f, 1.0f, 0.5f, 0x22FFFFFF);

        for (int i = 0; i < sidebar.length; i++) {
            int rowY = panelY + 52 + i * 31;
            boolean selected = i == selectedSidebarIndex;
            boolean hovered = inBounds(mouseX, mouseY, panelX + 14, rowY, SIDEBAR_W - 24, 25);

            if (selected) {
                RenderUtils.drawRoundedRect(panelX + 14.0f, rowY, SIDEBAR_W - 24.0f, 25.0f, 7.0f, 0x3B7D3BC2);
            } else if (hovered) {
                RenderUtils.drawRoundedRect(panelX + 14.0f, rowY, SIDEBAR_W - 24.0f, 25.0f, 7.0f, 0x281F2231);
            }

            int iconColor = selected ? 0xFFE98EFF : 0xFFA29DBA;
            int textColor = selected ? 0xFFFFFFFF : 0xFFD0CDE2;
            fontRendererObj.drawString(sidebar[i].icon, panelX + 20, rowY + 9, iconColor);
            fontRendererObj.drawString(sidebar[i].name, panelX + 34, rowY + 9, textColor);
        }

        int footerY = panelY + panelH - 48;
        RenderUtils.drawRoundedRect(panelX + 14.0f, footerY, SIDEBAR_W - 24.0f, 31.0f, 7.0f, 0xAB0E1016);
        fontRendererObj.drawString("CozyArio", panelX + 20, footerY + 8, 0xFFDCD8EA);
        fontRendererObj.drawString("UID: 7673", panelX + 20, footerY + 19, 0xFF78748F);
    }

    private void drawHeader(int mouseX, int mouseY) {
        int contentX = panelX + SIDEBAR_W + 6;
        int contentW = panelW - SIDEBAR_W - 14;
        int topY = panelY + 8;

        RenderUtils.drawRoundedRect(contentX, topY, contentW, HEADER_H - 8.0f, 8.0f, 0x8B151721);

        List<String> chips = gatherHeaderChips();
        int chipX = contentX + 10;
        for (int i = 0; i < chips.size(); i++) {
            String chip = chips.get(i);
            int chipW = Math.max(58, fontRendererObj.getStringWidth(chip) + 24);
            RenderUtils.drawRoundedRect(chipX, topY + 9.0f, chipW, 19.0f, 6.0f, 0x862B2C3E);
            fontRendererObj.drawString("+", chipX + 7, topY + 15, 0xFFB4AFC8);
            fontRendererObj.drawString(chip, chipX + 15, topY + 15, 0xFFDCD9EE);
            chipX += chipW + 6;
        }

        int searchW = 165;
        int searchX = contentX + contentW - searchW - 10;
        int searchY = topY + 7;
        boolean hovered = inBounds(mouseX, mouseY, searchX, searchY, searchW, 22);
        int searchColor = typingSearch ? 0xD02B2837 : (hovered ? 0xB3252635 : 0x8A20212D);
        RenderUtils.drawRoundedRect(searchX, searchY, searchW, 22.0f, 7.0f, searchColor);

        String text;
        int color;
        if (searchQuery.isEmpty()) {
            text = "Search...";
            color = 0xFF8D88A4;
        } else {
            boolean blink = typingSearch && ((System.currentTimeMillis() / 350L) % 2L == 0L);
            text = searchQuery + (blink ? "_" : "");
            color = 0xFFE1DEF2;
        }
        fontRendererObj.drawString(text, searchX + 9, searchY + 8, color);
        fontRendererObj.drawString("Q", searchX + searchW - 14, searchY + 8, 0xFFB4B0C8);
    }

    private void drawCards(int mouseX, int mouseY) {
        int contentX = panelX + SIDEBAR_W + 6;
        int contentY = panelY + HEADER_H + 12;
        int contentW = panelW - SIDEBAR_W - 14;
        int contentH = panelH - HEADER_H - 20;

        RenderUtils.drawRoundedRect(contentX, contentY, contentW, contentH, 9.0f, 0x7A13141C);

        cardsX = contentX + 8;
        cardsY = contentY + 8;
        cardsW = contentW - 16;
        cardsH = contentH - 16;

        List<Module> modules = filteredModules();
        int cardW = (cardsW - CARD_GAP) / 2;
        int rowHeight = CARD_H + CARD_GAP;

        int totalRows = (modules.size() + 1) / 2;
        int visibleRows = Math.max(1, cardsH / rowHeight);
        int maxRows = Math.max(0, totalRows - visibleRows);
        if (scrollRows > maxRows) {
            scrollRows = maxRows;
        }

        int start = scrollRows * 2;
        int end = Math.min(modules.size(), start + visibleRows * 2 + 2);

        beginScissor(cardsX, cardsY, cardsW, cardsH);
        for (int i = start; i < end; i++) {
            Module module = modules.get(i);
            int slot = i - start;
            int col = slot % 2;
            int row = slot / 2;

            int x = cardsX + col * (cardW + CARD_GAP);
            int y = cardsY + row * rowHeight;
            boolean hovered = inBounds(mouseX, mouseY, x, y, cardW, CARD_H);

            int cardColor = hovered ? 0xBE242532 : 0x8E1A1C26;
            RenderUtils.drawRoundedRect(x, y, cardW, CARD_H, 7.0f, cardColor);
            RenderUtils.drawRoundedRect(x + 1.0f, y + 1.0f, cardW - 2.0f, 1.0f, 0.5f, 0x18FFFFFF);
            if (module.isEnabled()) {
                RenderUtils.drawRoundedRect(x, y, 2.0f, CARD_H, 1.0f, 0xFFAA63FF);
            }
            if (hovered) {
                RenderUtils.drawGlow(x, y, cardW, CARD_H, 7.0f, 0x2F8F4CD6, 2);
            }

            fontRendererObj.drawString(module.getName(), x + 10, y + 13, 0xFFE3DFF2);

            int badgeX = x + cardW - 86;
            RenderUtils.drawRoundedRect(badgeX, y + 10.0f, 34.0f, 14.0f, 5.0f, 0xA3383550);
            fontRendererObj.drawString("NONE", badgeX + 6, y + 13, 0xFFD8D3EA);

            RenderUtils.drawRoundedRect(x + cardW - 48.0f, y + 10.0f, 12.0f, 14.0f, 3.0f, 0x5B292B3B);
            fontRendererObj.drawString("*", x + cardW - 44, y + 13, 0xFFA7A3BE);

            drawToggle(x + cardW - 30, y + 11, module.isEnabled());
        }
        endScissor();

        if (modules.isEmpty()) {
            fontRendererObj.drawString("No modules found for this filter.", cardsX + 8, cardsY + 8, 0xFF8E8AA6);
        }
    }

    private void drawToggle(float x, float y, boolean enabled) {
        int track = enabled ? 0xFF9D5AFD : 0xFF545065;
        RenderUtils.drawRoundedRect(x, y, 20.0f, 12.0f, 6.0f, track);
        float knobX = enabled ? x + 14.0f : x + 6.0f;
        RenderUtils.drawCircle(knobX, y + 6.0f, 4.0f, 0xFFFFFFFF);
    }

    private boolean handleSidebarClick(int mouseX, int mouseY) {
        for (int i = 0; i < sidebar.length; i++) {
            int rowY = panelY + 52 + i * 31;
            if (inBounds(mouseX, mouseY, panelX + 14, rowY, SIDEBAR_W - 24, 25)) {
                selectedSidebarIndex = i;
                scrollRows = 0;
                return true;
            }
        }
        return false;
    }

    private boolean handleSearchClick(int mouseX, int mouseY) {
        int contentX = panelX + SIDEBAR_W + 6;
        int contentW = panelW - SIDEBAR_W - 14;
        int searchW = 165;
        int searchX = contentX + contentW - searchW - 10;
        int searchY = panelY + 15;
        typingSearch = inBounds(mouseX, mouseY, searchX, searchY, searchW, 22);
        return typingSearch;
    }

    private boolean handleModuleToggleClick(int mouseX, int mouseY) {
        List<Module> modules = filteredModules();
        int cardW = (cardsW - CARD_GAP) / 2;
        int rowHeight = CARD_H + CARD_GAP;

        int visibleRows = Math.max(1, cardsH / rowHeight);
        int start = scrollRows * 2;
        int end = Math.min(modules.size(), start + visibleRows * 2 + 2);
        for (int i = start; i < end; i++) {
            Module module = modules.get(i);
            int slot = i - start;
            int col = slot % 2;
            int row = slot / 2;

            int x = cardsX + col * (cardW + CARD_GAP);
            int y = cardsY + row * rowHeight;
            if (inBounds(mouseX, mouseY, x + cardW - 32, y + 8, 24, 16)) {
                module.toggle();
                return true;
            }
        }
        return false;
    }

    private List<Module> filteredModules() {
        List<Module> result = new ArrayList<Module>();
        if (FishClientMod.moduleManager == null) {
            return result;
        }

        Category target = sidebar[selectedSidebarIndex].category;
        String needle = searchQuery.toLowerCase();
        for (Module module : FishClientMod.moduleManager.getModules()) {
            if (target != null && module.getCategory() != target) {
                continue;
            }
            if (!needle.isEmpty() && !module.getName().toLowerCase().contains(needle)) {
                continue;
            }
            result.add(module);
        }
        return result;
    }

    private List<String> gatherHeaderChips() {
        List<String> chips = new ArrayList<String>();
        if (FishClientMod.moduleManager != null) {
            for (Module module : FishClientMod.moduleManager.getModules()) {
                if (module.isEnabled()) {
                    chips.add(module.getName());
                    if (chips.size() >= 3) {
                        return chips;
                    }
                }
            }
        }
        chips.add("KillAura");
        chips.add("Velocity");
        chips.add("ElytraTarget");
        return chips;
    }

    private void clampPanel() {
        panelX = Math.max(4, Math.min(panelX, width - panelW - 4));
        panelY = Math.max(4, Math.min(panelY, height - panelH - 4));
    }

    private void clampScrollRows() {
        int totalRows = (filteredModules().size() + 1) / 2;
        int visibleRows = Math.max(1, cardsH <= 0 ? 1 : cardsH / (CARD_H + CARD_GAP));
        int maxRows = Math.max(0, totalRows - visibleRows);
        if (scrollRows < 0) {
            scrollRows = 0;
        } else if (scrollRows > maxRows) {
            scrollRows = maxRows;
        }
    }

    private void beginScissor(int x, int y, int w, int h) {
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, mc.displayHeight - (y + h) * scale, w * scale, h * scale);
    }

    private void endScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
