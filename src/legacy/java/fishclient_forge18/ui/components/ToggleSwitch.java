package com.fishclient.ui.components;

import com.fishclient.utils.RenderUtils;

public class ToggleSwitch {

    private static final float WIDTH = 28.0f;
    private static final float HEIGHT = 14.0f;
    private static final float KNOB_SIZE = 10.0f;
    private static final float PADDING = 2.0f;

    private static final int COLOR_ON = 0xFF9B59FF;
    private static final int COLOR_OFF = 0xFF2A2A33;
    private static final int COLOR_KNOB = 0xFFFFFFFF;

    private boolean enabled;
    private float animProgress;
    private long lastUpdateMs;

    public ToggleSwitch(boolean initialState) {
        this.enabled = initialState;
        this.animProgress = initialState ? 1.0f : 0.0f;
        this.lastUpdateMs = System.currentTimeMillis();
    }

    public void render(float x, float y) {
        long now = System.currentTimeMillis();
        float dt = (now - lastUpdateMs) / 150.0f;
        lastUpdateMs = now;

        float target = enabled ? 1.0f : 0.0f;
        if (animProgress < target) {
            animProgress = Math.min(target, animProgress + dt);
        } else if (animProgress > target) {
            animProgress = Math.max(target, animProgress - dt);
        }

        int trackColor = RenderUtils.lerpColor(COLOR_OFF, COLOR_ON, animProgress);
        RenderUtils.drawRoundedRect(x, y, WIDTH, HEIGHT, HEIGHT / 2.0f, trackColor);

        float knobX = x + PADDING + animProgress * (WIDTH - KNOB_SIZE - PADDING * 2.0f);
        float knobY = y + (HEIGHT - KNOB_SIZE) / 2.0f;
        RenderUtils.drawRoundedRect(knobX, knobY, KNOB_SIZE, KNOB_SIZE, KNOB_SIZE / 2.0f, COLOR_KNOB);
    }

    public boolean handleClick(float mouseX, float mouseY, float switchX, float switchY) {
        if (mouseX >= switchX && mouseX <= switchX + WIDTH && mouseY >= switchY && mouseY <= switchY + HEIGHT) {
            enabled = !enabled;
            return true;
        }
        return false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float getWidth() {
        return WIDTH;
    }

    public float getHeight() {
        return HEIGHT;
    }
}

