package com.fishclient.utils;

public final class AnimationUtils {

    private AnimationUtils() {
    }

    public static float approach(float current, float target, float speed) {
        if (speed <= 0.0f) {
            return target;
        }
        if (current < target) {
            return Math.min(target, current + speed);
        }
        if (current > target) {
            return Math.max(target, current - speed);
        }
        return current;
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * clamp01(t);
    }

    public static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}

