package com.fishclient.utils;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public final class RenderUtils {

    private RenderUtils() {
    }

    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        float right = x + width;
        float bottom = y + height;
        float r = Math.max(0.0f, Math.min(radius, Math.min(width, height) * 0.5f));

        if (r <= 0.5f) {
            Gui.drawRect((int) x, (int) y, (int) right, (int) bottom, color);
            return;
        }

        setup2D();
        color(color);

        GL11.glBegin(GL11.GL_POLYGON);
        arc(right - r, y + r, r, 270, 360, 8);
        arc(right - r, bottom - r, r, 0, 90, 8);
        arc(x + r, bottom - r, r, 90, 180, 8);
        arc(x + r, y + r, r, 180, 270, 8);
        GL11.glEnd();

        restore2D();
    }

    public static void drawRect(float x, float y, float x2, float y2, int color) {
        Gui.drawRect((int) x, (int) y, (int) x2, (int) y2, color);
    }

    public static void drawHorizontalLine(float x, float y, float width, float thickness, int color) {
        drawRect(x, y, x + width, y + thickness, color);
    }

    public static void drawAccentBar(float x, float y, float height, float width, int color) {
        drawRoundedRect(x, y, width, height, width / 2.0f, color);
    }

    public static void drawRoundedOutline(float x, float y, float width, float height, float radius, float thickness, int color) {
        float innerX = x + thickness;
        float innerY = y + thickness;
        float innerW = width - thickness * 2.0f;
        float innerH = height - thickness * 2.0f;
        drawRoundedRect(x, y, width, height, radius, color);
        if (innerW > 0 && innerH > 0) {
            drawRoundedRect(innerX, innerY, innerW, innerH, Math.max(0.0f, radius - thickness), 0x00000000);
        }
    }

    public static void drawGlow(float x, float y, float width, float height, float radius, int color, int passes) {
        int count = Math.max(1, passes);
        for (int i = count; i >= 1; i--) {
            float grow = i * 2.0f;
            int alpha = ((color >>> 24) & 0xFF) / (count + 1);
            int passColor = (alpha << 24) | (color & 0x00FFFFFF);
            drawRoundedRect(x - grow, y - grow, width + grow * 2.0f, height + grow * 2.0f, radius + grow, passColor);
        }
    }

    public static void drawCircle(float centerX, float centerY, float radius, int color) {
        if (radius <= 0.0f) {
            return;
        }
        setup2D();
        color(color);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(centerX, centerY);
        for (int i = 0; i <= 32; i++) {
            double angle = Math.PI * 2.0 * i / 32.0;
            GL11.glVertex2d(centerX + Math.cos(angle) * radius, centerY + Math.sin(angle) * radius);
        }
        GL11.glEnd();
        restore2D();
    }

    public static int lerpColor(int from, int to, float t) {
        return ColorUtils.lerpColor(from, to, t);
    }

    public static void drawGradientRect(float left, float top, float right, float bottom, int startColor, int endColor) {
        float a1 = ((startColor >> 24) & 0xFF) / 255.0f;
        float r1 = ((startColor >> 16) & 0xFF) / 255.0f;
        float g1 = ((startColor >> 8) & 0xFF) / 255.0f;
        float b1 = (startColor & 0xFF) / 255.0f;

        float a2 = ((endColor >> 24) & 0xFF) / 255.0f;
        float r2 = ((endColor >> 16) & 0xFF) / 255.0f;
        float g2 = ((endColor >> 8) & 0xFF) / 255.0f;
        float b2 = (endColor & 0xFF) / 255.0f;

        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        worldRenderer.pos(right, top, 0.0D).color(r1, g1, b1, a1).endVertex();
        worldRenderer.pos(left, top, 0.0D).color(r1, g1, b1, a1).endVertex();
        worldRenderer.pos(left, bottom, 0.0D).color(r2, g2, b2, a2).endVertex();
        worldRenderer.pos(right, bottom, 0.0D).color(r2, g2, b2, a2).endVertex();
        tessellator.draw();

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    private static void setup2D() {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
    }

    private static void restore2D() {
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private static void color(int color) {
        float a = ((color >>> 24) & 0xFF) / 255.0f;
        float r = ((color >>> 16) & 0xFF) / 255.0f;
        float g = ((color >>> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        GlStateManager.color(r, g, b, a);
    }

    private static void arc(float cx, float cy, float radius, int startDeg, int endDeg, int steps) {
        int segments = Math.max(4, steps);
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            double angle = Math.toRadians(startDeg + (endDeg - startDeg) * t);
            GL11.glVertex2d(cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius);
        }
    }
}

