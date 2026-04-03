package com.fishclient.client.ui;

import com.fishclient.client.macro.MacroManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MacroGuiScreen extends Screen {

    private static final int COL_BG = 0xDE0A0D14;
    private static final int COL_PANEL = 0xF0141824;
    private static final int COL_LINE = 0xFF2C3246;
    private static final int COL_ACCENT = 0xFF7E9BFF;
    private static final int COL_TEXT = 0xFFFFFFFF;
    private static final int COL_TEXT_DIM = 0xFFBCC6E0;
    private static final int COL_TEXT_MUTED = 0xFF7E8AA9;
    private static final int COL_ROW = 0xFF1A2032;
    private static final int COL_ROW_HOVER = 0xFF222A40;
    private static final int COL_ROW_SELECTED = 0xFF2B3250;

    private final Screen parent;
    private final List<MacroManager.MacroEntry> macros = new ArrayList<>();

    private int selectedIndex = -1;
    private EditBox nameInput;
    private EditBox commandInput;
    private Button keybindButton;
    private Button enabledButton;
    private boolean captureKeybind;
    private int editKeybind;
    private boolean editEnabled = true;
    private String statusLine = "";

    public MacroGuiScreen(Screen parent) {
        super(Component.literal("Fish Macro GUI"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        macros.clear();
        macros.addAll(MacroManager.getMacros());
        if (!macros.isEmpty()) {
            selectedIndex = 0;
        }

        int panelX = width / 2 - 260;
        int panelY = height / 2 - 175;

        nameInput = new EditBox(font, panelX + 292, panelY + 66, 210, 20, Component.literal("Macro Name"));
        nameInput.setMaxLength(28);
        addRenderableWidget(nameInput);

        commandInput = new EditBox(font, panelX + 292, panelY + 102, 210, 20, Component.literal("Command"));
        commandInput.setMaxLength(120);
        addRenderableWidget(commandInput);

        keybindButton = addRenderableWidget(Button.builder(Component.literal("Keybind: NONE"), btn -> {
            captureKeybind = true;
            statusLine = "Press a key. ESC clears keybind.";
            refreshButtons();
        }).bounds(panelX + 292, panelY + 136, 102, 20).build());

        enabledButton = addRenderableWidget(Button.builder(Component.literal("Enabled: YES"), btn -> {
            editEnabled = !editEnabled;
            refreshButtons();
        }).bounds(panelX + 400, panelY + 136, 102, 20).build());

        addRenderableWidget(Button.builder(Component.literal("New"), btn -> {
            selectedIndex = -1;
            captureKeybind = false;
            nameInput.setValue("");
            commandInput.setValue("");
            editKeybind = 0;
            editEnabled = true;
            statusLine = "Creating new macro.";
            refreshButtons();
        }).bounds(panelX + 292, panelY + 170, 65, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Save"), btn -> saveMacro())
            .bounds(panelX + 364, panelY + 170, 65, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Delete"), btn -> deleteSelected())
            .bounds(panelX + 436, panelY + 170, 66, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(panelX + 292, panelY + 302, 210, 20).build());

        syncInputsFromSelection();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, COL_BG);

        int panelX = width / 2 - 260;
        int panelY = height / 2 - 175;
        int panelW = 520;
        int panelH = 350;

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, COL_LINE);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, COL_LINE);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, COL_LINE);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, COL_LINE);
        g.fill(panelX + 276, panelY + 44, panelX + 277, panelY + panelH - 20, COL_LINE);

        g.drawString(font, "Fish Macro GUI", panelX + 16, panelY + 12, COL_ACCENT);
        g.drawString(font, ".macrogui opens this screen in-game", panelX + 16, panelY + 26, COL_TEXT_DIM);

        renderMacroList(g, mouseX, mouseY, panelX + 16, panelY + 50, 250, 248);

        g.drawString(font, "Name", panelX + 292, panelY + 54, COL_TEXT_DIM);
        g.drawString(font, "Command (/spawn, /home, ...)", panelX + 292, panelY + 90, COL_TEXT_DIM);

        if (!statusLine.isBlank()) {
            g.drawString(font, statusLine, panelX + 292, panelY + 278, COL_TEXT_MUTED);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderMacroList(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF11172A);
        g.fill(x, y, x + w, y + 1, COL_LINE);
        g.fill(x, y + h - 1, x + w, y + h, COL_LINE);
        g.fill(x, y, x + 1, y + h, COL_LINE);
        g.fill(x + w - 1, y, x + w, y + h, COL_LINE);

        int rowY = y + 4;
        for (int i = 0; i < macros.size(); i++) {
            MacroManager.MacroEntry macro = macros.get(i);
            int rowH = 24;
            boolean hover = inRect(mouseX, mouseY, x + 4, rowY, w - 8, rowH);
            boolean selected = i == selectedIndex;
            int rowColor = selected ? COL_ROW_SELECTED : (hover ? COL_ROW_HOVER : COL_ROW);
            g.fill(x + 4, rowY, x + w - 4, rowY + rowH, rowColor);

            String title = macro.name + (macro.enabled ? "" : " [off]");
            g.drawString(font, truncate(title, w - 96), x + 10, rowY + 4, selected ? COL_TEXT : COL_TEXT_DIM);
            g.drawString(font, keyName(macro.keybind), x + w - 70, rowY + 4, COL_TEXT_MUTED);

            rowY += rowH + 4;
            if (rowY > y + h - 24) {
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean allowDragging) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (event.button() == 0) {
            int panelX = width / 2 - 260;
            int listX = panelX + 16;
            int listY = height / 2 - 175 + 50;
            int rowY = listY + 4;
            for (int i = 0; i < macros.size(); i++) {
                if (inRect(mouseX, mouseY, listX + 4, rowY, 242, 24)) {
                    selectedIndex = i;
                    captureKeybind = false;
                    syncInputsFromSelection();
                    return true;
                }
                rowY += 28;
            }
        }
        return super.mouseClicked(event, allowDragging);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (captureKeybind) {
            captureKeybind = false;
            editKeybind = key == GLFW.GLFW_KEY_ESCAPE ? 0 : key;
            statusLine = "Keybind set: " + keyName(editKeybind);
            refreshButtons();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void syncInputsFromSelection() {
        if (selectedIndex < 0 || selectedIndex >= macros.size()) {
            nameInput.setValue("");
            commandInput.setValue("");
            editKeybind = 0;
            editEnabled = true;
            refreshButtons();
            return;
        }
        MacroManager.MacroEntry macro = macros.get(selectedIndex);
        nameInput.setValue(macro.name == null ? "" : macro.name);
        commandInput.setValue(macro.command == null ? "" : macro.command);
        editKeybind = Math.max(0, macro.keybind);
        editEnabled = macro.enabled;
        refreshButtons();
    }

    private void refreshButtons() {
        keybindButton.setMessage(Component.literal(captureKeybind ? "Press key..." : "Keybind: " + keyName(editKeybind)));
        enabledButton.setMessage(Component.literal("Enabled: " + (editEnabled ? "YES" : "NO")));
    }

    private void saveMacro() {
        String name = nameInput.getValue() == null ? "" : nameInput.getValue().trim();
        String command = commandInput.getValue() == null ? "" : commandInput.getValue().trim();
        if (name.isBlank()) {
            statusLine = "Name is required.";
            return;
        }
        if (command.isBlank()) {
            statusLine = "Command is required.";
            return;
        }

        if (selectedIndex >= 0 && selectedIndex < macros.size()) {
            MacroManager.MacroEntry existing = macros.get(selectedIndex);
            existing.name = name;
            existing.command = command;
            existing.keybind = editKeybind;
            existing.enabled = editEnabled;
            statusLine = "Macro updated: " + name;
        } else {
            String id = "macro-" + System.currentTimeMillis();
            MacroManager.MacroEntry created = new MacroManager.MacroEntry(id, name, command, editKeybind, editEnabled);
            macros.add(created);
            selectedIndex = macros.size() - 1;
            statusLine = "Macro added: " + name;
        }

        MacroManager.replaceAll(macros);
        macros.clear();
        macros.addAll(MacroManager.getMacros());
        if (selectedIndex >= macros.size()) {
            selectedIndex = macros.isEmpty() ? -1 : macros.size() - 1;
        }
        syncInputsFromSelection();
    }

    private void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= macros.size()) {
            statusLine = "Select a macro first.";
            return;
        }
        String name = macros.get(selectedIndex).name;
        macros.remove(selectedIndex);
        if (selectedIndex >= macros.size()) {
            selectedIndex = macros.isEmpty() ? -1 : macros.size() - 1;
        }
        MacroManager.replaceAll(macros);
        statusLine = "Deleted: " + name;
        syncInputsFromSelection();
    }

    private String keyName(int keyCode) {
        if (keyCode <= 0) {
            return "NONE";
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            return "RSHIFT";
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT_SHIFT) {
            return "LSHIFT";
        }
        if (keyCode == GLFW.GLFW_KEY_INSERT) {
            return "INS";
        }
        String name = GLFW.glfwGetKeyName(keyCode, 0);
        if (name == null || name.isBlank()) {
            return "[" + keyCode + "]";
        }
        return name.toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int maxPixels) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (font.width(value) <= maxPixels) {
            return value;
        }
        String out = value;
        while (!out.isEmpty() && font.width(out + "...") > maxPixels) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "...";
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void onClose() {
        MacroManager.replaceAll(macros);
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
