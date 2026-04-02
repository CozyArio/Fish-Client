package com.fishclient.ui.mainmenu;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ParticleSystem {

    private static final int PARTICLE_COUNT = 55;

    private static class Particle {
        float x;
        float y;
        float speedY;
        float speedX;
        float size;
        float alpha;
        float alphaSpeed;
        float phase;
        boolean isPink;
    }

    private final List<Particle> particles = new ArrayList<Particle>();
    private final Random rng = new Random();
    private long lastTime = System.currentTimeMillis();

    public ParticleSystem() {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            particles.add(spawnParticle(true));
        }
    }

    private Particle spawnParticle(boolean randomY) {
        Particle particle = new Particle();
        particle.x = rng.nextFloat();
        particle.y = randomY ? rng.nextFloat() : 1.05f;
        particle.speedY = 0.008f + rng.nextFloat() * 0.018f;
        particle.speedX = (rng.nextFloat() - 0.5f) * 0.004f;
        particle.size = 1.2f + rng.nextFloat() * 2.5f;
        particle.alpha = rng.nextFloat();
        particle.alphaSpeed = 0.4f + rng.nextFloat() * 0.8f;
        particle.phase = rng.nextFloat() * (float) (Math.PI * 2.0);
        particle.isPink = rng.nextFloat() < 0.35f;
        return particle;
    }

    public void update() {
        long now = System.currentTimeMillis();
        float dt = (now - lastTime) / 1000.0f;
        lastTime = now;
        dt = Math.min(dt, 0.1f);

        for (int i = 0; i < particles.size(); i++) {
            Particle particle = particles.get(i);
            particle.y -= particle.speedY * dt;
            particle.x += particle.speedX * dt;

            if (particle.y < -0.02f) {
                particles.set(i, spawnParticle(false));
                continue;
            }

            float elapsed = now / 1000.0f;
            particle.alpha = 0.3f + 0.7f * (float) (Math.sin(elapsed * particle.alphaSpeed + particle.phase) * 0.5 + 0.5);
        }
    }

    public void render(int screenWidth, int screenHeight) {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.disableAlpha();

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        for (Particle particle : particles) {
            float px = particle.x * screenWidth;
            float py = particle.y * screenHeight;
            float r = 1.0f;
            float g = particle.isPink ? 0.53f : 1.0f;
            float b = 1.0f;
            float a = particle.alpha * 0.85f;
            float s = particle.size;

            worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            worldRenderer.pos(px - s, py - s, 0).color(r, g, b, a).endVertex();
            worldRenderer.pos(px + s, py - s, 0).color(r, g, b, a).endVertex();
            worldRenderer.pos(px + s, py + s, 0).color(r, g, b, 0.0f).endVertex();
            worldRenderer.pos(px - s, py + s, 0).color(r, g, b, 0.0f).endVertex();
            tessellator.draw();
        }

        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}

