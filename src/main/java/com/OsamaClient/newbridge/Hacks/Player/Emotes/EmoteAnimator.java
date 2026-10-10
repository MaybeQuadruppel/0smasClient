package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Runs emotes per player UUID. Works for the local player and (once something feeds it) for others. */
public final class EmoteAnimator {

    public static final EmoteAnimator INSTANCE = new EmoteAnimator();

    private static final class Playback {
        final EmoteAnimation anim;
        final long start = System.nanoTime();
        long stopAt; // 0 = running

        Playback(EmoteAnimation anim) { this.anim = anim; }
    }

    private final Map<UUID, Playback> active = new HashMap<>();

    private EmoteAnimator() {}

    public void play(UUID id, EmoteAnimation anim) { active.put(id, new Playback(anim)); }

    /** Starts the fade-out; the emote disappears when it is done. */
    public void stop(UUID id) {
        Playback p = active.get(id);
        if (p == null || p.stopAt != 0) return;
        float t = (System.nanoTime() - p.start) / 1e9f;
        if (naturalFadeStart(p) >= 0f && t >= naturalFadeStart(p)) return; // already fading out on its own
        p.stopAt = System.nanoTime();
    }

    /** True while the emote runs and has not been stopped. */
    public boolean isPlaying(UUID id) {
        Playback p = active.get(id);
        return p != null && p.stopAt == 0 && !isFinished(p);
    }

    /** True until the emote is completely gone (including its fade-out). Cleans up finished emotes. */
    public boolean isActive(UUID id) {
        Playback p = active.get(id);
        if (p == null) return false;
        if (isFinished(p)) {
            active.remove(id);
            return false;
        }
        return true;
    }

    public void clear() { active.clear(); }

    /** Time at which a non-looping, non-holding emote starts fading out by itself, otherwise -1. */
    private static float naturalFadeStart(Playback p) {
        EmoteAnimation a = p.anim;
        return (!a.loop && !a.hold) ? a.duration - a.fadeOut : -1f;
    }

    /** Time at which the fade-out begins, or -1 if the emote is not fading out. */
    private static float fadeOutStart(Playback p, float t) {
        if (p.stopAt != 0) return (p.stopAt - p.start) / 1e9f;
        float n = naturalFadeStart(p);
        return (n >= 0f && t >= n) ? n : -1f;
    }

    private static boolean isFinished(Playback p) {
        float t = (System.nanoTime() - p.start) / 1e9f;
        float s = fadeOutStart(p, t);
        if (s < 0f) return false;
        return p.anim.fadeOut <= 0f || (t - s) / p.anim.fadeOut >= 1f;
    }

    /** Current pose of this player or null if nothing is playing. Call once per render extract. */
    public EmoteAnimation.Pose pose(UUID id) {
        Playback p = active.get(id);
        if (p == null) return null;
        EmoteAnimation a = p.anim;
        float t = (System.nanoTime() - p.start) / 1e9f;

        float w = a.fadeIn > 0f ? Math.min(1f, t / a.fadeIn) : 1f;

        float outStart = fadeOutStart(p, t);
        if (outStart >= 0f) {
            float o = a.fadeOut > 0f ? (t - outStart) / a.fadeOut : 1f;
            if (o >= 1f) {
                active.remove(id);
                return null;
            }
            w = Math.min(w, 1f - o);
        }

        EmoteAnimation.Pose pose = new EmoteAnimation.Pose();
        a.sample(t, pose);
        pose.cancelled = p.stopAt != 0;
        pose.weight = w * w * (3f - 2f * w); // smoothstep
        return pose;
    }
}