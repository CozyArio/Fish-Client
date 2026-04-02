package com.fishclient.utils;

public final class ColorUtils {

    private ColorUtils() {
    }

    public static int withAlpha(int rgb, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (clampedAlpha << 24) | (rgb & 0x00FFFFFF);
    }

    public static int lerpColor(int from, int to, float t) {
        t = AnimationUtils.clamp01(t);
        int fa = (from >>> 24) & 0xFF;
        int fr = (from >>> 16) & 0xFF;
        int fg = (from >>> 8) & 0xFF;
        int fb = from & 0xFF;

        int ta = (to >>> 24) & 0xFF;
        int tr = (to >>> 16) & 0xFF;
        int tg = (to >>> 8) & 0xFF;
        int tb = to & 0xFF;

        int a = (int) (fa + (ta - fa) * t);
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

