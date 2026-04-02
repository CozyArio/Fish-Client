package com.fishclient.ui.components;

import com.fishclient.utils.RenderUtils;
import net.minecraft.client.Minecraft;

public class SliderComponent {

    private final float min;
    private final float max;
    private float value;

    public SliderComponent(float min, float max, float initial) {
        this.min = min;
        this.max = max;
        this.value = clamp(initial);
    }

    public void render(float x, float y, float width) {
        RenderUtils.drawRoundedRect(x, y, width, 4.0f, 2.0f, 0xFF2A2A33);
        float pct = (value - min) / (max - min);
        RenderUtils.drawRoundedRect(x, y, width * pct, 4.0f, 2.0f, 0xFF9B59FF);
        Minecraft.getMinecraft().fontRendererObj.drawString(String.format("%.2f", value), (int) (x + width + 6), (int) (y - 2), 0xFF888899);
    }

    public void setFromMouse(float mouseX, float x, float width) {
        float pct = (mouseX - x) / width;
        setValue(min + (max - min) * pct);
    }

    public float getValue() {
        return value;
    }

    public void setValue(float value) {
        this.value = clamp(value);
    }

    private float clamp(float v) {
        return Math.max(min, Math.min(max, v));
    }
}

