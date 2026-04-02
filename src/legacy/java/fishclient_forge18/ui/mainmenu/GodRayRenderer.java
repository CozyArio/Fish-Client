package com.fishclient.ui.mainmenu;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

public class GodRayRenderer {

    private static final int RAY_COUNT = 14;
    private static final float RAY_ORIGIN_X_FRACTION = 0.5f;
    private static final float RAY_ORIGIN_Y_FRACTION = 0.05f;

    private final float[] rayAngles = new float[RAY_COUNT];
    private final float[] rayWidths = new float[RAY_COUNT];
    private final float[] rayAlphas = new float[RAY_COUNT];
    private final float[] rayLengths = new float[RAY_COUNT];
    private final float[] rayPhases = new float[RAY_COUNT];
    private final float[] raySpeeds = new float[RAY_COUNT];

    private final long startTime;

    public GodRayRenderer() {
        startTime = System.currentTimeMillis();
        java.util.Random rng = new java.util.Random(42L);
        for (int i = 0; i < RAY_COUNT; i++) {
            rayAngles[i] = -80.0f + (160.0f / RAY_COUNT) * i + rng.nextFloat() * 8.0f - 4.0f;
            rayWidths[i] = 15.0f + rng.nextFloat() * 40.0f;
            rayAlphas[i] = 0.04f + rng.nextFloat() * 0.09f;
            rayLengths[i] = 0.6f + rng.nextFloat() * 0.5f;
            rayPhases[i] = rng.nextFloat() * (float) (Math.PI * 2.0);
            raySpeeds[i] = 0.3f + rng.nextFloat() * 0.4f;
        }
    }

    public void render(int screenWidth, int screenHeight) {
        float elapsed = (System.currentTimeMillis() - startTime) / 1000.0f;
        float originX = screenWidth * RAY_ORIGIN_X_FRACTION;
        float originY = screenHeight * RAY_ORIGIN_Y_FRACTION;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.disableAlpha();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        for (int i = 0; i < RAY_COUNT; i++) {
            float pulse = (float) (Math.sin(elapsed * raySpeeds[i] + rayPhases[i]) * 0.5 + 0.5);
            float alpha = rayAlphas[i] * pulse;

            double angleRad = Math.toRadians(rayAngles[i] + 90.0f);
            float length = screenHeight * rayLengths[i];
            float dirX = (float) Math.cos(angleRad);
            float dirY = (float) Math.sin(angleRad);
            float perpX = -dirY;
            float perpY = dirX;
            float halfW = rayWidths[i] * 0.5f;

            float tipX = originX + dirX * length;
            float tipY = originY + dirY * length;
            float baseX1 = originX + perpX * halfW;
            float baseY1 = originY + perpY * halfW;
            float baseX2 = originX - perpX * halfW;
            float baseY2 = originY - perpY * halfW;

            worldRenderer.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
            worldRenderer.pos(baseX1, baseY1, 0).color(0x6B / 255.0f, 0.0f, 0xAA / 255.0f, alpha).endVertex();
            worldRenderer.pos(baseX2, baseY2, 0).color(0x6B / 255.0f, 0.0f, 0xAA / 255.0f, alpha).endVertex();
            worldRenderer.pos(tipX, tipY, 0).color(0x6B / 255.0f, 0.0f, 0xAA / 255.0f, 0.0f).endVertex();
            tessellator.draw();
        }

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}

