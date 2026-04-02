package com.fishclient.ui.mainmenu;

import com.fishclient.ui.components.RoundedButton;
import com.fishclient.utils.AnimationUtils;
import com.fishclient.utils.RenderUtils;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSelectWorld;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.net.URI;

public class CustomMainMenu extends GuiScreen {

    private final GodRayRenderer godRays = new GodRayRenderer();
    private final ParticleSystem particles = new ParticleSystem();
    private final MusicWidget music = new MusicWidget();

    private static final int BTN_SINGLEPLAYER = 1;
    private static final int BTN_MULTIPLAYER = 2;
    private static final int BTN_ACCOUNTS = 3;
    private static final int BTN_SETTINGS = 4;
    private static final int BTN_EXIT = 5;

    private final float[] buttonHover = new float[6];
    private final String[] socialLabels = {"YT", "TG", "DC"};
    private final String[] socialLinks = {
        "https://youtube.com",
        "https://t.me",
        "https://discord.com"
    };
    private int hoveredSocial = -1;

    @Override
    public void initGui() {
        buttonList.clear();

        int cx = width / 2;
        int cy = height / 2;

        buttonList.add(new RoundedButton(BTN_SINGLEPLAYER, cx - 130, cy - 20, 260, 34, "SinglePlayer"));
        buttonList.add(new RoundedButton(BTN_MULTIPLAYER, cx - 130, cy + 18, 260, 34, "MultiPlayer"));
        buttonList.add(new RoundedButton(BTN_ACCOUNTS, cx - 130, cy + 56, 260, 34, "Accounts"));
        buttonList.add(new RoundedButton(BTN_SETTINGS, cx - 134, cy + 94, 126, 28, "Settings"));
        buttonList.add(new RoundedButton(BTN_EXIT, cx + 8, cy + 94, 126, 28, "Exit"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        hoveredSocial = -1;

        drawRect(0, 0, width, height, 0xFF08000B);
        RenderUtils.drawGradientRect(0, 0, width, height, 0x800E0019, 0xD0000000);
        godRays.render(width, height);
        particles.update();
        particles.render(width, height);
        RenderUtils.drawGradientRect(0, 0, width, height, 0x05000000, 0x78000000);

        mc.getTextureManager().bindTexture(new ResourceLocation("fishclient", "textures/logo.png"));
        drawModalRectWithCustomSizedTexture(width / 2 - 32, height / 2 - 132, 0, 0, 64, 64, 64, 64);

        for (GuiButton button : buttonList) {
            boolean hovered = mouseX >= button.xPosition
                && mouseX <= button.xPosition + button.width
                && mouseY >= button.yPosition
                && mouseY <= button.yPosition + button.height;
            int id = button.id;
            if (id >= 0 && id < buttonHover.length) {
                float target = hovered ? 1.0f : 0.0f;
                buttonHover[id] = AnimationUtils.approach(buttonHover[id], target, 0.12f);

                int base = 0xA014101B;
                int hover = 0xCC251531;
                int color = RenderUtils.lerpColor(base, hover, buttonHover[id]);
                RenderUtils.drawGlow(button.xPosition, button.yPosition, button.width, button.height, 8.0f, 0x4A8A2CD7, 2);
                RenderUtils.drawRoundedRect(button.xPosition, button.yPosition, button.width, button.height, 8.0f, color);
                RenderUtils.drawRoundedRect(button.xPosition + 1.0f, button.yPosition + 1.0f, button.width - 2.0f, 1.0f, 0.5f, 0x18FFFFFF);

                if (button.width > 200) {
                    String icon = iconFor(button.id);
                    int iconColor = hovered ? 0xFFF08CFF : 0xFFB26ED1;
                    RenderUtils.drawRoundedRect(button.xPosition + 8.0f, button.yPosition + 6.0f, 20.0f, button.height - 12.0f, 6.0f, 0x342A1F39);
                    fontRendererObj.drawString(icon, button.xPosition + 15, button.yPosition + (button.height - 8) / 2, iconColor);
                }

                if (hovered && button.width <= 140) {
                    RenderUtils.drawGlow(button.xPosition, button.yPosition, button.width, button.height, 8.0f, 0x3FD56DFF, 2);
                }
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        drawSocialBar(mouseX, mouseY);
        fontRendererObj.drawString("(C) Fish Client 2026", width / 2 - 44, height - 11, 0x48FFFFFF);
        music.render(width, height, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BTN_SINGLEPLAYER:
                mc.displayGuiScreen(new GuiSelectWorld(this));
                break;
            case BTN_MULTIPLAYER:
                mc.displayGuiScreen(new GuiMultiplayer(this));
                break;
            case BTN_ACCOUNTS:
                // Account screen placeholder.
                break;
            case BTN_SETTINGS:
                mc.displayGuiScreen(new GuiOptions(this, mc.gameSettings));
                break;
            case BTN_EXIT:
                mc.shutdown();
                break;
            default:
                break;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (handleSocialClick(mouseX, mouseY)) {
            return;
        }
        if (!music.handleMouseClick(mouseX, mouseY, mouseButton)) {
            super.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private String iconFor(int buttonId) {
        switch (buttonId) {
            case BTN_SINGLEPLAYER:
                return "S";
            case BTN_MULTIPLAYER:
                return "M";
            case BTN_ACCOUNTS:
                return "A";
            case BTN_SETTINGS:
                return "O";
            case BTN_EXIT:
                return "X";
            default:
                return ".";
        }
    }

    private void drawSocialBar(int mouseX, int mouseY) {
        float barW = 160.0f;
        float barH = 24.0f;
        float barX = width / 2.0f - barW / 2.0f;
        float barY = height - 34.0f;

        RenderUtils.drawGlow(barX, barY, barW, barH, 7.0f, 0x35792EC8, 2);
        RenderUtils.drawRoundedRect(barX, barY, barW, barH, 7.0f, 0xA1120E19);

        float slotW = barW / 3.0f;
        for (int i = 0; i < 3; i++) {
            float sx = barX + i * slotW;
            boolean hovered = mouseX >= sx && mouseX <= sx + slotW && mouseY >= barY && mouseY <= barY + barH;
            if (hovered) {
                hoveredSocial = i;
                RenderUtils.drawRoundedRect(sx + 1.0f, barY + 2.0f, slotW - 2.0f, barH - 4.0f, 5.0f, 0x2BAE4DE7);
            }
            if (i > 0) {
                RenderUtils.drawRoundedRect(sx, barY + 4.0f, 1.0f, barH - 8.0f, 0.5f, 0x2FFFFFFF);
            }
            int textColor = hovered ? 0xFFF48CFF : 0xFFD179EF;
            int tx = (int) (sx + slotW / 2.0f - fontRendererObj.getStringWidth(socialLabels[i]) / 2.0f);
            fontRendererObj.drawString(socialLabels[i], tx, (int) (barY + 8.0f), textColor);
        }
    }

    private boolean handleSocialClick(int mouseX, int mouseY) {
        float barW = 160.0f;
        float barH = 24.0f;
        float barX = width / 2.0f - barW / 2.0f;
        float barY = height - 34.0f;
        if (mouseX < barX || mouseX > barX + barW || mouseY < barY || mouseY > barY + barH) {
            return false;
        }

        int slot = (int) ((mouseX - barX) / (barW / 3.0f));
        if (slot >= 0 && slot < socialLinks.length) {
            openLink(socialLinks[slot]);
            return true;
        }
        return false;
    }

    private void openLink(String link) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new URI(link));
            }
        } catch (Exception ignored) {
        }
    }
}

