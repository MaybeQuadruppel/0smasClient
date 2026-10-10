package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation.Easing.EASE_IN_OUT;

/**
 * A user-made emote as saved on disk: keyframes (time -> per-bone [rotX, rotY, rotZ, posX, posY, posZ]).
 * Bone keys are the names of {@link EmoteAnimation.Bone}. Compiled to an {@link EmoteAnimation} for playback.
 */
public final class CustomEmote {

    public static final class Frame {
        public float time;
        public Map<String, float[]> bones = new LinkedHashMap<>();
    }

    public String name = "Custom Emote";
    public float duration = 2f;
    public boolean loop;
    public List<Frame> frames = new ArrayList<>();

    /** Repairs data loaded from disk. @return false if the emote is unusable. */
    boolean sanitize() {
        if (name == null || name.isBlank()) return false;
        name = name.trim();
        if (!(duration >= 0.2f)) duration = 2f;
        if (frames == null) frames = new ArrayList<>();
        frames.removeIf(f -> f == null || f.bones == null);
        return true;
    }

    private static boolean anyNonZero(List<float[]> values, int from, int to) {
        for (float[] v : values) for (int i = from; i < to; i++) if (Math.abs(v[i]) > 0.001f) return true;
        return false;
    }

    /**
     * Bones (and rotation / position groups) that are zero in every keyframe are left out, so vanilla
     * animations (walking legs, head look) keep working for everything the emote does not touch.
     */
    public EmoteAnimation toAnimation() {
        float dur = Math.max(0.2f, duration);
        EmoteAnimation.Builder b = EmoteAnimation.builder(name, "Custom emote", dur).fade(0.25f, 0.25f);
        if (loop) b.loop();

        List<Frame> sorted = new ArrayList<>(frames);
        sorted.sort(Comparator.comparingDouble((Frame f) -> f.time));

        for (EmoteAnimation.Bone bone : EmoteAnimation.Bone.values()) {
            List<Float> times = new ArrayList<>();
            List<float[]> values = new ArrayList<>();
            for (Frame f : sorted) {
                float[] v = f.bones.get(bone.name());
                if (v == null || v.length < 6) continue;
                times.add(Math.max(0f, Math.min(dur, f.time)));
                values.add(v);
            }
            if (values.isEmpty()) continue;

            boolean rot = anyNonZero(values, 0, 3), pos = anyNonZero(values, 3, 6);
            if (!rot && !pos) continue;

            // seamless loop: close the track back to its first pose at the very end
            if (loop && times.get(times.size() - 1) < dur - 0.001f) {
                times.add(dur);
                values.add(values.get(0));
            }
            for (int i = 0; i < times.size(); i++) {
                float[] v = values.get(i);
                if (rot) b.rot(bone, times.get(i), EASE_IN_OUT, v[0], v[1], v[2]);
                if (pos) b.pos(bone, times.get(i), EASE_IN_OUT, v[3], v[4], v[5]);
            }
        }
        return b.build();
    }
}