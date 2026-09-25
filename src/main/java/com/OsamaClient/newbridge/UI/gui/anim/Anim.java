package com.OsamaClient.newbridge.UI.gui.anim;

/**
 * Frame-time animated value: exponential smoothing toward a target, independent of the frame rate.
 * {@code value += (target - value) * (1 - exp(-speed * mul * dt))}. Pure Java, no Minecraft classes.
 */
public final class Anim {

    /** Frame deltas above this are clamped (e.g. after a lag spike). */
    public static final float MAX_DT = 0.1f;
    private static final float EPSILON = 0.0005f;

    private final float speed;
    private float value;
    private float target;

    public Anim(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public float value() { return value; }

    public float target() { return target; }

    public void setTarget(float target) { this.target = target; }

    /** Jumps to {@code v} without animating. */
    public void snap(float v) {
        value = v;
        target = v;
    }

    public void update(float dt, float speedMul) {
        if (value == target) return;
        float t = Math.min(Math.max(dt, 0f), MAX_DT);
        float k = 1f - (float) Math.exp(-speed * speedMul * t);
        value += (target - value) * k;
        if (Math.abs(target - value) < EPSILON) value = target;
    }

    public boolean done() { return value == target; }

    public static float clamp01(float t) { return t < 0f ? 0f : (t > 1f ? 1f : t); }

    public static float easeOutCubic(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    public static float easeInOutQuad(float t) {
        t = clamp01(t);
        return t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f;
    }
}
