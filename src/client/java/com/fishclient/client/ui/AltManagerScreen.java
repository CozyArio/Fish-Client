package com.fishclient.client.ui;

import com.fishclient.client.account.AltAccountStore;
import com.fishclient.client.account.MicrosoftAuthService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AltManagerScreen extends Screen {

    private static final int COL_BG = 0xE60E1018;
    private static final int COL_PANEL = 0xF0131622;
    private static final int COL_LINE = 0xFF2A2D3A;
    private static final int COL_ACCENT = 0xFFAA55FF;
    private static final int COL_TEXT = 0xFFFFFFFF;
    private static final int COL_TEXT_DIM = 0xFFB7BCD1;
    private static final int COL_TEXT_MUTED = 0xFF7E8398;
    private static final int COL_ROW = 0xFF181B2A;
    private static final int COL_ROW_HOVER = 0xFF1F2234;
    private static final int COL_ROW_SELECTED = 0xFF251A3A;
    private static final String MICROSOFT_CLIENT_ID = "04b07795-8ddb-461a-bbee-02f9e1bf7b46";

    private final Screen parent;
    private AltAccountStore.AltState altState;
    private List<AltAccountStore.AltAccount> alts = new ArrayList<>();

    private int selectedIndex = -1;
    private EditBox usernameInput;
    private EditBox noteInput;
    private Button typeButton;
    private String typeValue = "offline";
    private String statusLine = "";
    private volatile boolean microsoftLoginRunning;

    public AltManagerScreen(Screen parent) {
        super(Component.literal("Fish Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        altState = AltAccountStore.ensureDefaults(mc, AltAccountStore.load());
        alts = altState.alts;
        selectedIndex = findSelectedIndex();
        if (selectedIndex < 0 && !alts.isEmpty()) {
            selectedIndex = 0;
        }

        int panelX = width / 2 - 250;
        int panelY = height / 2 - 170;

        usernameInput = new EditBox(font, panelX + 285, panelY + 64, 195, 20, Component.literal("Username"));
        usernameInput.setMaxLength(16);
        addRenderableWidget(usernameInput);

        noteInput = new EditBox(font, panelX + 285, panelY + 98, 195, 20, Component.literal("Note"));
        noteInput.setMaxLength(42);
        addRenderableWidget(noteInput);

        typeButton = addRenderableWidget(Button.builder(typeLabel(), btn -> {
            typeValue = "offline".equals(typeValue) ? "alias" : "offline";
            btn.setMessage(typeLabel());
        }).bounds(panelX + 285, panelY + 132, 95, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Save Alt"), btn -> saveCurrentAlt())
            .bounds(panelX + 385, panelY + 132, 95, 20)
            .build());

        addRenderableWidget(Button.builder(Component.literal("Apply Selected"), btn -> applySelectedAlt())
            .bounds(panelX + 285, panelY + 164, 195, 20)
            .build());

        addRenderableWidget(Button.builder(Component.literal("Remove Selected"), btn -> removeSelectedAlt())
            .bounds(panelX + 285, panelY + 196, 195, 20)
            .build());

        addRenderableWidget(Button.builder(Component.literal("Microsoft Device Login"), btn -> beginMicrosoftDeviceLogin())
            .bounds(panelX + 285, panelY + 228, 195, 20)
            .build());

        addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
            .bounds(panelX + 285, panelY + 294, 195, 20)
            .build());

        syncInputsFromSelection();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, COL_BG);

        int panelX = width / 2 - 250;
        int panelY = height / 2 - 170;
        int panelW = 500;
        int panelH = 340;

        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, COL_PANEL);
        g.fill(panelX, panelY, panelX + panelW, panelY + 1, COL_LINE);
        g.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, COL_LINE);
        g.fill(panelX, panelY, panelX + 1, panelY + panelH, COL_LINE);
        g.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, COL_LINE);
        g.fill(panelX + 270, panelY + 42, panelX + 271, panelY + panelH - 20, COL_LINE);

        g.drawString(font, "Fish Alt Manager", panelX + 16, panelY + 12, COL_ACCENT);
        String currentUser = Minecraft.getInstance() != null && Minecraft.getInstance().getUser() != null
            ? Minecraft.getInstance().getUser().getName()
            : "Unknown";
        g.drawString(font, "Current session: " + currentUser, panelX + 16, panelY + 26, COL_TEXT_DIM);
        String tip = microsoftLoginRunning
            ? "Microsoft login is running... finish the browser code flow."
            : "Use Microsoft Device Login for premium auth.";
        g.drawString(font, tip, panelX + 16, panelY + panelH - 18, COL_TEXT_MUTED);

        renderAltList(g, mouseX, mouseY, panelX + 16, panelY + 50, 244, 250);

        g.drawString(font, "Username", panelX + 285, panelY + 52, COL_TEXT_DIM);
        g.drawString(font, "Note", panelX + 285, panelY + 86, COL_TEXT_DIM);
        g.drawString(font, "Type", panelX + 285, panelY + 120, COL_TEXT_DIM);

        if (statusLine != null && !statusLine.isBlank()) {
            g.drawString(font, statusLine, panelX + 285, panelY + 274, COL_TEXT_MUTED);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    private void renderAltList(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF111423);
        g.fill(x, y, x + w, y + 1, COL_LINE);
        g.fill(x, y + h - 1, x + w, y + h, COL_LINE);
        g.fill(x, y, x + 1, y + h, COL_LINE);
        g.fill(x + w - 1, y, x + w, y + h, COL_LINE);

        int rowY = y + 4;
        for (int i = 0; i < alts.size(); i++) {
            AltAccountStore.AltAccount alt = alts.get(i);
            int rowH = 24;
            boolean hover = inRect(mouseX, mouseY, x + 4, rowY, w - 8, rowH);
            boolean selected = i == selectedIndex;

            int rowColor = selected ? COL_ROW_SELECTED : (hover ? COL_ROW_HOVER : COL_ROW);
            g.fill(x + 4, rowY, x + w - 4, rowY + rowH, rowColor);

            String prefix;
            if ("microsoft".equalsIgnoreCase(alt.type)) {
                prefix = "[M]";
            } else if ("alias".equalsIgnoreCase(alt.type)) {
                prefix = "[A]";
            } else {
                prefix = "[C]";
            }
            g.drawString(font, prefix + " " + alt.username, x + 10, rowY + 4, selected ? COL_TEXT : COL_TEXT_DIM);
            if (alt.note != null && !alt.note.isBlank()) {
                g.drawString(font, alt.note, x + 130, rowY + 4, COL_TEXT_MUTED);
            }
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
            int panelX = width / 2 - 250;
            int listX = panelX + 16;
            int listY = height / 2 - 170 + 50;
            int rowY = listY + 4;
            for (int i = 0; i < alts.size(); i++) {
                if (inRect(mouseX, mouseY, listX + 4, rowY, 236, 24)) {
                    selectedIndex = i;
                    altState.selectedAltId = alts.get(i).id;
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
        if (event.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    private int findSelectedIndex() {
        if (altState == null || altState.selectedAltId == null || altState.selectedAltId.isBlank()) {
            return -1;
        }
        for (int i = 0; i < alts.size(); i++) {
            if (altState.selectedAltId.equals(alts.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    private void syncInputsFromSelection() {
        if (selectedIndex < 0 || selectedIndex >= alts.size()) {
            usernameInput.setValue("");
            noteInput.setValue("");
            typeValue = "offline";
            typeButton.setMessage(typeLabel());
            return;
        }

        AltAccountStore.AltAccount alt = alts.get(selectedIndex);
        usernameInput.setValue(alt.username == null ? "" : alt.username);
        noteInput.setValue(alt.note == null ? "" : alt.note);
        typeValue = "alias".equalsIgnoreCase(alt.type) ? "alias" : "offline";
        typeButton.setMessage(typeLabel());
    }

    private Component typeLabel() {
        return Component.literal("Type: " + ("alias".equals(typeValue) ? "Alias" : "Cracked"));
    }

    private void saveCurrentAlt() {
        String username = AltAccountStore.sanitizeUsername(usernameInput.getValue());
        if (username.length() < 3) {
            statusLine = "Username must be 3-16 chars (letters/numbers/_).";
            return;
        }

        if (selectedIndex >= 0 && selectedIndex < alts.size()) {
            AltAccountStore.AltAccount existing = alts.get(selectedIndex);
            existing.username = username;
            existing.type = normalizeType(typeValue);
            existing.note = noteInput.getValue();
            altState.selectedAltId = existing.id;
            AltAccountStore.save(altState);
            statusLine = "Updated alt: " + username;
            return;
        }

        AltAccountStore.AltAccount alt = new AltAccountStore.AltAccount();
        alt.id = "alt-" + username.toLowerCase() + "-" + System.currentTimeMillis();
        alt.username = username;
        alt.type = normalizeType(typeValue);
        alt.note = noteInput.getValue();
        alts.add(alt);
        selectedIndex = alts.size() - 1;
        altState.selectedAltId = alt.id;
        AltAccountStore.save(altState);
        statusLine = "Added alt: " + username;
    }

    private void removeSelectedAlt() {
        if (selectedIndex < 0 || selectedIndex >= alts.size()) {
            statusLine = "Select an alt first.";
            return;
        }

        alts.remove(selectedIndex);
        if (alts.isEmpty()) {
            altState = AltAccountStore.ensureDefaults(Minecraft.getInstance(), altState);
            alts = altState.alts;
            selectedIndex = 0;
        } else {
            selectedIndex = Math.max(0, Math.min(selectedIndex, alts.size() - 1));
            altState.selectedAltId = alts.get(selectedIndex).id;
        }
        AltAccountStore.save(altState);
        syncInputsFromSelection();
        statusLine = "Alt removed.";
    }

    private void applySelectedAlt() {
        if (selectedIndex < 0 || selectedIndex >= alts.size()) {
            statusLine = "Select an alt first.";
            return;
        }

        AltAccountStore.AltAccount alt = alts.get(selectedIndex);
        altState.selectedAltId = alt.id;
        AltAccountStore.save(altState);

        boolean applied;
        if ("microsoft".equalsIgnoreCase(alt.type)) {
            UUID parsedUuid = null;
            try {
                if (alt.uuid != null && !alt.uuid.isBlank()) {
                    parsedUuid = UUID.fromString(alt.uuid);
                }
            } catch (IllegalArgumentException ignored) {
                parsedUuid = null;
            }
            if (parsedUuid == null || alt.accessToken == null || alt.accessToken.isBlank()) {
                statusLine = "Microsoft token missing/expired. Use Microsoft Device Login.";
                return;
            }
            applied = AltAccountStore.applyMicrosoftSession(Minecraft.getInstance(), alt.username, parsedUuid, alt.accessToken, alt.xuid);
        } else {
            applied = AltAccountStore.applyAltToSession(Minecraft.getInstance(), alt);
        }
        if (applied) {
            if ("microsoft".equalsIgnoreCase(alt.type)) {
                statusLine = "Applied Microsoft session: " + alt.username;
            } else if ("alias".equalsIgnoreCase(alt.type)) {
                statusLine = "Applied alias session: " + alt.username + " (not Microsoft-auth).";
            } else {
                statusLine = "Applied cracked session: " + alt.username;
            }
        } else {
            statusLine = "Could not apply session now. Restart client.";
        }
    }

    private void beginMicrosoftDeviceLogin() {
        if (microsoftLoginRunning) {
            statusLine = "Microsoft login already running...";
            return;
        }
        microsoftLoginRunning = true;
        statusLine = "Requesting Microsoft device code...";

        Thread worker = new Thread(() -> {
            try {
                MicrosoftAuthService.DeviceCodeStart start = MicrosoftAuthService.beginDeviceCode(MICROSOFT_CLIENT_ID);
                setStatusOnMainThread("Code: " + start.userCode + " | Opening " + start.verificationUri);
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(new URI(start.verificationUri));
                    }
                } catch (Exception ignored) {
                }

                MicrosoftAuthService.MinecraftAuth auth = MicrosoftAuthService.pollForMinecraftAuth(
                    MICROSOFT_CLIENT_ID,
                    start,
                    this::setStatusOnMainThread
                );

                Minecraft mc = Minecraft.getInstance();
                if (mc == null) {
                    setStatusOnMainThread("Minecraft not available.");
                    return;
                }
                mc.execute(() -> {
                    boolean applied = AltAccountStore.applyMicrosoftSession(mc, auth.username, auth.uuid, auth.minecraftAccessToken, auth.xuid);
                    if (!applied) {
                        statusLine = "Auth success, but session apply failed.";
                        microsoftLoginRunning = false;
                        return;
                    }
                    upsertMicrosoftAlt(auth);
                    AltAccountStore.save(altState);
                    statusLine = "Microsoft login successful: " + auth.username;
                    microsoftLoginRunning = false;
                });
                return;
            } catch (Exception exception) {
                setStatusOnMainThread("Microsoft login failed: " + exception.getMessage());
            } finally {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> microsoftLoginRunning = false);
                } else {
                    microsoftLoginRunning = false;
                }
            }
        }, "FishClient-Microsoft-Login");
        worker.setDaemon(true);
        worker.start();
    }

    private void upsertMicrosoftAlt(MicrosoftAuthService.MinecraftAuth auth) {
        AltAccountStore.AltAccount target = null;
        for (AltAccountStore.AltAccount alt : alts) {
            if (alt != null && "microsoft".equalsIgnoreCase(alt.type) && auth.username.equalsIgnoreCase(alt.username)) {
                target = alt;
                break;
            }
        }
        if (target == null) {
            target = new AltAccountStore.AltAccount();
            target.id = "msa-" + auth.username.toLowerCase() + "-" + System.currentTimeMillis();
            alts.add(target);
        }
        target.username = auth.username;
        target.type = "microsoft";
        target.note = "Microsoft";
        target.accessToken = auth.minecraftAccessToken == null ? "" : auth.minecraftAccessToken;
        target.refreshToken = auth.microsoftRefreshToken == null ? "" : auth.microsoftRefreshToken;
        target.xuid = auth.xuid == null ? "" : auth.xuid;
        target.uuid = auth.uuid == null ? "" : auth.uuid.toString();
        altState.selectedAltId = target.id;
        selectedIndex = findSelectedIndex();
    }

    private void setStatusOnMainThread(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            statusLine = text;
            return;
        }
        mc.execute(() -> statusLine = text);
    }

    private String normalizeType(String value) {
        return "alias".equalsIgnoreCase(value) ? "alias" : "offline";
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
