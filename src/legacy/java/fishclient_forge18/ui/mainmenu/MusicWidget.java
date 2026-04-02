package com.fishclient.ui.mainmenu;

import com.fishclient.modules.radio.RadioManager;
import com.fishclient.modules.radio.RadioStation;
import com.fishclient.utils.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import java.util.List;

public class MusicWidget {

    private static final float X = 8.0f;
    private static final float Y = 8.0f;
    private static final float W = 160.0f;
    private static final float H = 78.0f;
    private static final int BG_COLOR = 0xB0100A14;
    private static final int BORDER_COLOR = 0x3CFFFFFF;
    private static final int ACCENT = 0xFFE65CFF;
    private static final int TRACK_COLOR = 0xA0221B2C;

    private final RadioManager radio = RadioManager.getInstance();
    private final float[] eqPhases = {0.0f, 0.8f, 1.6f, 2.4f};
    private final float[] eqSpeeds = {2.1f, 3.3f, 2.7f, 1.9f};

    public void render(int screenW, int screenH, float partialTicks) {
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;

        RenderUtils.drawGlow(X, Y, W, H, 10.0f, 0x50A438D7, 3);
        RenderUtils.drawRoundedRect(X, Y, W, H, 8.0f, BG_COLOR);
        RenderUtils.drawRoundedRect(X + 1.0f, Y + 1.0f, W - 2.0f, 1.0f, 0.5f, BORDER_COLOR);

        String title = radio.getCurrentTrackTitle();
        if (title == null || title.isEmpty()) {
            title = radio.isPlaying() ? "Loading..." : "Not playing";
        }
        if (font.getStringWidth(title) > (int) (W - 12.0f)) {
            while (font.getStringWidth(title + "...") > (int) (W - 12.0f) && title.length() > 3) {
                title = title.substring(0, title.length() - 1);
            }
            title = title + "...";
        }

        font.drawString(title, (int) (X + 8.0f), (int) (Y + 8.0f), 0xFFD7D2EA);
        RenderUtils.drawRoundedRect(X + 8.0f, Y + 18.0f, W - 16.0f, 1.0f, 0.5f, 0x2BFFFFFF);

        float btnY = Y + 28.0f;
        font.drawString("<|", (int) (X + 12.0f), (int) btnY, ACCENT);
        font.drawString(radio.isPlaying() ? "||" : ">", (int) (X + 37.0f), (int) btnY, ACCENT);
        font.drawString("|>", (int) (X + 59.0f), (int) btnY, ACCENT);

        if (radio.isPlaying()) {
            float elapsed = System.currentTimeMillis() / 1000.0f;
            float eqX = X + W - 44.0f;
            for (int i = 0; i < 4; i++) {
                float barH = 4.0f + 8.0f * (float) (Math.sin(elapsed * eqSpeeds[i] + eqPhases[i]) * 0.5 + 0.5);
                float bx = eqX + i * 8.0f;
                float by = btnY + 14.0f - barH;
                RenderUtils.drawRoundedRect(bx, by, 4.0f, barH, 2.0f, ACCENT);
            }
        }

        float volY = Y + H - 14.0f;
        float trackW = W - 16.0f;
        RenderUtils.drawRoundedRect(X + 8.0f, volY, trackW, 3.0f, 1.5f, TRACK_COLOR);
        RenderUtils.drawRoundedRect(X + 8.0f, volY, trackW * radio.getVolume(), 3.0f, 1.5f, ACCENT);
        RenderUtils.drawCircle(X + 8.0f + trackW * radio.getVolume(), volY + 1.5f, 2.6f, 0xFFF4BAFF);
    }

    public boolean handleMouseClick(int mouseX, int mouseY, int button) {
        if (inBounds(mouseX, mouseY, X + 8.0f, Y + 26.0f, 18.0f, 14.0f)) {
            prevStation();
            return true;
        }
        if (inBounds(mouseX, mouseY, X + 30.0f, Y + 26.0f, 18.0f, 14.0f)) {
            if (radio.isPlaying()) {
                radio.pause();
            } else {
                radio.resume();
            }
            return true;
        }
        if (inBounds(mouseX, mouseY, X + 52.0f, Y + 26.0f, 18.0f, 14.0f)) {
            nextStation();
            return true;
        }

        float volY = Y + H - 14.0f;
        if (inBounds(mouseX, mouseY, X + 8.0f, volY - 4.0f, W - 16.0f, 10.0f)) {
            float volume = (mouseX - (X + 8.0f)) / (W - 16.0f);
            radio.setVolume(volume);
            return true;
        }

        return false;
    }

    private void prevStation() {
        List<RadioStation> stations = radio.getAllStations();
        if (stations.isEmpty()) {
            return;
        }
        RadioStation current = radio.getCurrentStation();
        if (current == null) {
            radio.play(stations.get(0));
            return;
        }
        int index = stations.indexOf(current) - 1;
        if (index < 0) {
            index = stations.size() - 1;
        }
        radio.play(stations.get(index));
    }

    private void nextStation() {
        List<RadioStation> stations = radio.getAllStations();
        if (stations.isEmpty()) {
            return;
        }
        RadioStation current = radio.getCurrentStation();
        if (current == null) {
            radio.play(stations.get(0));
            return;
        }
        int index = (stations.indexOf(current) + 1) % stations.size();
        radio.play(stations.get(index));
    }

    private boolean inBounds(int mx, int my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}

