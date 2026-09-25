package com.OsamaClient.newbridge.UI.gui.anim;

/**
 * Linear 0..1 progress over a fixed duration that can run forward or backward (open/close animations).
 * {@link #delayed(float)} gives a staggered view for elements that should start a bit later.
 */
public final class Progress {

    private final float duration;
    private float elapsed;
    private boolean forward;

    public Progress(float duration) {
        this.duration = duration;
    }

    public void setForward(boolean forward) { this.forward = forward; }

    public boolean forward() { return forward; }

    public void update(float dt, float speedMul) {
        float d = Math.min(Math.max(dt, 0f), Anim.MAX_DT) * speedMul;
        elapsed = forward ? Math.min(duration, elapsed + d) : Math.max(0f, elapsed - d);
    }

    /** Jumps to fully open (1) or closed (0). */
    public void snap(boolean open) {
        forward = open;
        elapsed = open ? duration : 0f;
    }

    public float raw() { return elapsed / duration; }

    public float eased() { return Anim.easeOutCubic(raw()); }

    /** Progress of an element that starts {@code delay} seconds later (still ends at 1 when fully open). */
    public float delayed(float delay) {
        if (elapsed >= duration + delay || (!forward && raw() >= 1f)) return 1f;
        return Anim.clamp01((elapsed - delay) / duration);
    }

    public boolean finished() { return forward ? elapsed >= duration : elapsed <= 0f; }
}
