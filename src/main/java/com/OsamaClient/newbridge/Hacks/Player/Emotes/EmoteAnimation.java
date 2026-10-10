package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** A keyframe animation: per bone and channel a list of keys. Rotations in degrees, positions in model pixels. */
public final class EmoteAnimation {

    /** ROOT is not a model part: ROT_Y = whole-body yaw (degrees), POS_Y = whole-body vertical offset. */
    /**
     * ELBOW / KNEE are the lower halves of the arms / legs (see BendLimbs), rotating relative to the upper half.
     * Elbow: negative ROT_X bends the forearm forward. Knee: positive ROT_X bends the shin backwards.
     * New bones are appended, so the ordinals of the old ones stay the same.
     */
    public enum Bone {
        ROOT, HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG,
        RIGHT_ELBOW, LEFT_ELBOW, RIGHT_KNEE, LEFT_KNEE,
        /** Lower torso (child of BODY, see BendLimbs): bends the back. Rotates relative to BODY. */
        SPINE
    }

    public enum Channel { ROT_X, ROT_Y, ROT_Z, POS_X, POS_Y, POS_Z }

    public enum Easing {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT,
        /** Smooth curve through the neighbouring keys: no stop at every key, no overshoot. Fluid motion. */
        SPLINE;

        float apply(float t) {
            return switch (this) {
                case LINEAR -> t;
                case EASE_IN -> t * t * t;
                case EASE_OUT -> { float u = 1f - t; yield 1f - u * u * u; }
                case EASE_IN_OUT -> t * t * (3f - 2f * t);
                case SPLINE -> t; // handled by Track.spline()
            };
        }
    }

    /** Result of sampling: which channels are animated, their values and the blend weight (0..1). */
    public static final class Pose {
        private static final int CH = Channel.values().length;
        private final float[] values = new float[Bone.values().length * CH];
        private final boolean[] set = new boolean[values.length];
        public float weight;
        /** true when the emote was stopped early (not played to its natural end). */
        public boolean cancelled;

        void clear() { Arrays.fill(set, false); weight = 0f; cancelled = false; }

        void put(Bone b, Channel c, float v) {
            int i = b.ordinal() * CH + c.ordinal();
            values[i] = v;
            set[i] = true;
        }

        public boolean has(Bone b, Channel c) { return set[b.ordinal() * CH + c.ordinal()]; }

        public float get(Bone b, Channel c) { return values[b.ordinal() * CH + c.ordinal()]; }

        /**
         * Whole-body yaw in degrees. At the natural end it is not weighted: a full turn (360) must end exactly
         * where it started. When cancelled mid-turn it blends to the nearest full turn instead of snapping back.
         */
        public float rootYaw() {
            if (!has(Bone.ROOT, Channel.ROT_Y)) return 0f;
            float y = get(Bone.ROOT, Channel.ROT_Y);
            if (!cancelled) return y;
            float target = Math.round(y / 360f) * 360f;
            return target + (y - target) * weight;
        }
    }

    private record Key(float time, float value, Easing easing) {}

    private record Track(Bone bone, Channel channel, Key[] keys) {
        float sample(float t, boolean loop, float duration) {
            Key first = keys[0];
            if (t <= first.time) return first.value;
            Key last = keys[keys.length - 1];
            if (t >= last.time) return last.value;
            for (int i = 0; i < keys.length - 1; i++) {
                Key a = keys[i], b = keys[i + 1];
                if (t < b.time) {
                    if (b.easing == Easing.SPLINE) return spline(i, t, loop, duration);
                    float f = (t - a.time) / (b.time - a.time);
                    return a.value + (b.value - a.value) * b.easing.apply(f); // easing of the target key
                }
            }
            return last.value;
        }

        /** Cubic Hermite through the keys with monotone tangents; wraps around when the emote loops. */
        private float spline(int i, float t, boolean loop, float duration) {
            int n = keys.length;
            Key a = keys[i], b = keys[i + 1];
            boolean wrap = loop && n >= 3; // key n-1 is the closing copy of key 0
            float pt, pv, nt, nv;
            if (i > 0) { pt = keys[i - 1].time; pv = keys[i - 1].value; }
            else if (wrap) { pt = keys[n - 2].time - duration; pv = keys[n - 2].value; }
            else { pt = a.time; pv = a.value; }
            if (i + 2 < n) { nt = keys[i + 2].time; nv = keys[i + 2].value; }
            else if (wrap) { nt = keys[1].time + duration; nv = keys[1].value; }
            else { nt = b.time; nv = b.value; }
            float m0 = Interp.tangent(pt, pv, a.time, a.value, b.time, b.value);
            float m1 = Interp.tangent(a.time, a.value, b.time, b.value, nt, nv);
            return Interp.hermite(t, a.time, a.value, m0, b.time, b.value, m1);
        }
    }

    final String name, description;
    final float duration, fadeIn, fadeOut;
    final boolean loop, hold;
    private final Track[] tracks;

    private EmoteAnimation(Builder b, Track[] tracks) {
        this.name = b.name;
        this.description = b.description;
        this.duration = b.duration;
        this.fadeIn = b.fadeIn;
        this.fadeOut = b.fadeOut;
        this.loop = b.loop;
        this.hold = b.hold;
        this.tracks = tracks;
    }

    public String name() { return name; }

    public String description() { return description; }

    void sample(float time, Pose out) {
        out.clear();
        float t = loop ? time % duration : Math.min(time, duration);
        for (Track tr : tracks) out.put(tr.bone, tr.channel, tr.sample(t, loop, duration));
    }

    public static Builder builder(String name, String description, float duration) {
        return new Builder(name, description, duration);
    }

    public static final class Builder {
        /** "channel not animated" marker for rot()/pos(). */
        public static final float NA = Float.NaN;

        private static final int CH = Channel.values().length;
        private final String name, description;
        private final float duration;
        private float fadeIn = 0.25f, fadeOut = 0.25f;
        private boolean loop, hold;
        private final List<List<Key>> keys = new ArrayList<>();

        private Builder(String name, String description, float duration) {
            this.name = name;
            this.description = description;
            this.duration = duration;
            for (int i = 0; i < Bone.values().length * CH; i++) keys.add(new ArrayList<>());
        }

        /** Repeats forever until cancelled. First and last key of every track should match. */
        public Builder loop() { loop = true; return this; }

        /** Stays on the last pose until cancelled (e.g. sitting). */
        public Builder hold() { hold = true; return this; }

        public Builder fade(float in, float out) { fadeIn = in; fadeOut = out; return this; }

        /** Rotation key in degrees; use {@link #NA} for channels you do not want to touch. */
        public Builder rot(Bone b, float t, Easing e, float x, float y, float z) {
            add(b, Channel.ROT_X, t, x, e);
            add(b, Channel.ROT_Y, t, y, e);
            add(b, Channel.ROT_Z, t, z, e);
            return this;
        }

        /** Position offset key in model pixels (added on top of the vanilla pivot; y points down). */
        public Builder pos(Bone b, float t, Easing e, float x, float y, float z) {
            add(b, Channel.POS_X, t, x, e);
            add(b, Channel.POS_Y, t, y, e);
            add(b, Channel.POS_Z, t, z, e);
            return this;
        }

        private void add(Bone b, Channel c, float t, float v, Easing e) {
            if (Float.isNaN(v)) return;
            keys.get(b.ordinal() * CH + c.ordinal()).add(new Key(t, v, e));
        }

        public EmoteAnimation build() {
            List<Track> tracks = new ArrayList<>();
            for (Bone b : Bone.values()) {
                for (Channel c : Channel.values()) {
                    List<Key> list = keys.get(b.ordinal() * CH + c.ordinal());
                    if (list.isEmpty()) continue;
                    list.sort(Comparator.comparingDouble(Key::time));
                    tracks.add(new Track(b, c, list.toArray(new Key[0])));
                }
            }
            return new EmoteAnimation(this, tracks.toArray(new Track[0]));
        }
    }

    private static final class Interp {
        static float tangent(float x0, float y0, float x1, float y1, float x2, float y2) {
            float dx0 = x1 - x0;
            float dx1 = x2 - x1;
            if (dx0 <= 0f && dx1 <= 0f) return 0f;
            if (dx0 <= 0f) return (y2 - y1) / dx1;
            if (dx1 <= 0f) return (y1 - y0) / dx0;

            float d0 = (y1 - y0) / dx0;
            float d1 = (y2 - y1) / dx1;

            if (d0 * d1 <= 0f) return 0f;
            return (d0 + d1) * 0.5f;
        }

        static float hermite(float t, float x1, float y1, float m0, float x2, float y2, float m1) {
            float h = x2 - x1;
            if (h <= 0f) return y1;
            float s = (t - x1) / h;
            float s2 = s * s;
            float s3 = s2 * s;

            float h00 = 2f * s3 - 3f * s2 + 1f;
            float h10 = s3 - 2f * s2 + s;
            float h01 = -2f * s3 + 3f * s2;
            float h11 = s3 - s2;

            return h00 * y1 + h10 * h * m0 + h01 * y2 + h11 * h * m1;
        }
    }
}