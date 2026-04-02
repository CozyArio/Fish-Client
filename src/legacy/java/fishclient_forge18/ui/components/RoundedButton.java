package com.fishclient.ui.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class RoundedButton extends GuiButton {

    public RoundedButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
        super(buttonId, x, y, widthIn, heightIn, buttonText);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        this.hovered = mouseX >= this.xPosition
            && mouseY >= this.yPosition
            && mouseX < this.xPosition + this.width
            && mouseY < this.yPosition + this.height;

        int textColor = hovered ? 0xFFFFFFFF : 0xFFB7B4C9;
        if (width >= 200) {
            mc.fontRendererObj.drawString(displayString, xPosition + 44, yPosition + (height - 8) / 2, textColor);
        } else {
            drawCenteredString(mc.fontRendererObj, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, textColor);
        }
    }
}

