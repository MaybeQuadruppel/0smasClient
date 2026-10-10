package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import java.util.List;

import static com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation.Bone.*;
import static com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation.Builder.NA;
import static com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation.Easing.*;

/**
 * Emote library using upper & lower limb joints (elbows, knees, spine).
 *
 * Rules these keyframes follow:
 * <ul>
 *   <li>A pose that is held must have a "hold" key right before the release key. Without it the
 *       interpolation slowly drifts from the first key to the release key (arms sinking while waving etc.).</li>
 *   <li>BODY rotation leans around the hips and carries HEAD and ARMS (see EmoteApplier), so head and arm
 *       keys are RELATIVE to the torso whenever BODY is rotated.</li>
 *   <li>Feet stay on the floor: ROOT y has to match how far the legs are bent.</li>
 * </ul>
 */
public final class Emotes {

    private Emotes() {}

    public static final EmoteAnimation WAVE = wave();
    public static final EmoteAnimation DANCE = dance();
    public static final EmoteAnimation SPIN = spin();
    public static final EmoteAnimation SIT = sit();
    public static final EmoteAnimation TWERK = twerk();
    public static final EmoteAnimation CLAP = clap();
    public static final EmoteAnimation CHEER = cheer();
    public static final EmoteAnimation LAUGH = laugh();
    public static final EmoteAnimation FACEPALM = facepalm();
    public static final EmoteAnimation POINT = point();
    public static final EmoteAnimation SURRENDER = surrender();
    public static final EmoteAnimation SHRUG = shrug();
    public static final EmoteAnimation SALUTE = salute();

    public static final List<EmoteAnimation> ALL = List.of(
            WAVE, DANCE, SPIN, SIT, TWERK, CLAP, CHEER,
            LAUGH, FACEPALM, POINT, SURRENDER, SHRUG, SALUTE
    );

    /**
     * Deep squat with hands on the knees. Thigh -60 + knee 95 puts the feet on the floor at root y of about 4.3.
     * The torso leans around the hips, head/arm keys are relative to the torso.
     */
    private static EmoteAnimation twerk() {
        EmoteAnimation.Builder b = EmoteAnimation.builder("Twerk", "Squat low and shake it smoothly", 1.6f).loop().fade(0.25f, 0.25f);

        // constant squat stance (one key = constant)
        b.rot(RIGHT_LEG, 0f, EASE_IN_OUT, -60f, 12f, 14f);
        b.rot(LEFT_LEG, 0f, EASE_IN_OUT, -60f, -12f, -14f);
        b.rot(RIGHT_KNEE, 0f, EASE_IN_OUT, 95f, NA, NA);
        b.rot(LEFT_KNEE, 0f, EASE_IN_OUT, 95f, NA, NA);

        // hands on knees (relative to the leaning torso)
        b.rot(RIGHT_ARM, 0f, EASE_IN_OUT, -30f, 0f, 14f);
        b.rot(RIGHT_ELBOW, 0f, EASE_IN_OUT, -18f, NA, NA);
        b.rot(LEFT_ARM, 0f, EASE_IN_OUT, -30f, 0f, -14f);
        b.rot(LEFT_ELBOW, 0f, EASE_IN_OUT, -18f, NA, NA);

        // 8 beats over 1.6 s (0.2 s each), first and last key identical -> seamless loop
        for (int i = 0; i <= 8; i++) {
            float t = i * 0.20f;
            boolean beat = i % 2 == 0;

            float lean = beat ? 46f : 34f;
            float rootY = beat ? 4.5f : 4.1f;
            float yaw = beat ? 10f : -10f;
            float roll = beat ? 5f : -5f;

            b.pos(ROOT, t, EASE_IN_OUT, NA, rootY, NA);
            b.rot(BODY, t, EASE_IN_OUT, lean, yaw, roll);
            b.rot(SPINE, t, EASE_IN_OUT, beat ? -14f : -4f, -yaw * 0.5f, NA);
            // head keeps looking roughly forward: cancels most of the torso lean
            b.rot(HEAD, t, EASE_IN_OUT, 10f - lean, -yaw * 0.6f, -roll);
        }

        return b.build();
    }

    /** Sitting on the floor: hips on the ground (root 9.5), legs forward, hands on the thighs. */
    private static EmoteAnimation sit() {
        return EmoteAnimation.builder("Sit", "Sit down comfortably", 0.6f).hold().fade(0.2f, 0.25f)
                .pos(ROOT, 0f, EASE_OUT, NA, 0f, NA)
                .pos(ROOT, 0.6f, EASE_OUT, NA, 9.5f, NA)
                .rot(RIGHT_LEG, 0.6f, EASE_OUT, -88f, 10f, 0f)
                .rot(RIGHT_KNEE, 0.6f, EASE_OUT, 6f, NA, NA)
                .rot(LEFT_LEG, 0.6f, EASE_OUT, -88f, -10f, 0f)
                .rot(LEFT_KNEE, 0.6f, EASE_OUT, 6f, NA, NA)
                .rot(SPINE, 0.6f, EASE_OUT, -3f, 0f, 0f)
                .rot(RIGHT_ARM, 0.6f, EASE_OUT, -35f, 0f, 8f)
                .rot(RIGHT_ELBOW, 0.6f, EASE_OUT, -15f, NA, NA)
                .rot(LEFT_ARM, 0.6f, EASE_OUT, -35f, 0f, -8f)
                .rot(LEFT_ELBOW, 0.6f, EASE_OUT, -15f, NA, NA)
                .build();
    }

    private static EmoteAnimation clap() {
        EmoteAnimation.Builder b = EmoteAnimation.builder("Clap", "Applaud and clap hands", 2.2f).fade(0.2f, 0.25f);

        // Bring arms in front with bent elbows
        b.rot(RIGHT_ARM, 0.3f, EASE_OUT, -45f, -25f, 15f);
        b.rot(RIGHT_ELBOW, 0.3f, EASE_OUT, -75f, NA, NA);
        b.rot(LEFT_ARM, 0.3f, EASE_OUT, -45f, 25f, -15f);
        b.rot(LEFT_ELBOW, 0.3f, EASE_OUT, -75f, NA, NA);

        for (int i = 0; i < 8; i++) {
            float t = 0.3f + i * 0.20f;
            boolean together = i % 2 == 1;
            EmoteAnimation.Easing e = together ? EASE_IN : EASE_OUT;

            b.rot(RIGHT_ARM, t, e, -48f, together ? -10f : -30f, 15f);
            b.rot(LEFT_ARM, t, e, -48f, together ? 10f : 30f, -15f);
            b.rot(SPINE, t, e, together ? 3f : 0f, 0f, 0f);
        }

        // elbows stay bent while clapping, only then release (no drift)
        b.rot(RIGHT_ELBOW, 1.7f, EASE_IN_OUT, -75f, NA, NA);
        b.rot(LEFT_ELBOW, 1.7f, EASE_IN_OUT, -75f, NA, NA);

        b.rot(RIGHT_ARM, 2.2f, EASE_IN_OUT, 0f, 0f, 0f);
        b.rot(RIGHT_ELBOW, 2.2f, EASE_IN_OUT, 0f, NA, NA);
        b.rot(LEFT_ARM, 2.2f, EASE_IN_OUT, 0f, 0f, 0f);
        b.rot(LEFT_ELBOW, 2.2f, EASE_IN_OUT, 0f, NA, NA);
        return b.build();
    }

    private static EmoteAnimation wave() {
        return EmoteAnimation.builder("Wave", "Wave hello", 2.2f).fade(0.2f, 0.25f)
                .rot(RIGHT_ARM, 0.3f, EASE_OUT, -30f, 0f, 105f)
                .rot(RIGHT_ARM, 1.7f, EASE_IN_OUT, -30f, 0f, 105f)   // arm stays up while waving
                .rot(RIGHT_ARM, 2.2f, EASE_IN_OUT, 0f, 0f, 0f)
                .rot(RIGHT_ELBOW, 0.3f, EASE_OUT, -40f, NA, NA)
                .rot(RIGHT_ELBOW, 0.6f, EASE_IN_OUT, -70f, NA, NA)
                .rot(RIGHT_ELBOW, 0.9f, EASE_IN_OUT, -30f, NA, NA)
                .rot(RIGHT_ELBOW, 1.2f, EASE_IN_OUT, -70f, NA, NA)
                .rot(RIGHT_ELBOW, 1.5f, EASE_IN_OUT, -30f, NA, NA)
                .rot(RIGHT_ELBOW, 1.7f, EASE_IN_OUT, -30f, NA, NA)
                .rot(RIGHT_ELBOW, 2.2f, EASE_IN_OUT, 0f, NA, NA)
                .rot(HEAD, 0.3f, EASE_IN_OUT, 0f, 0f, -8f)
                .rot(HEAD, 1.5f, EASE_IN_OUT, 0f, 0f, -8f)
                .rot(HEAD, 2.0f, EASE_IN_OUT, 0f, 0f, 0f)
                .build();
    }

    private static EmoteAnimation salute() {
        return EmoteAnimation.builder("Salute", "Respectful military salute", 1.8f).fade(0.15f, 0.2f)
                .rot(RIGHT_ARM, 0.35f, EASE_OUT, -65f, -30f, 65f)
                .rot(RIGHT_ARM, 1.4f, EASE_IN_OUT, -65f, -30f, 65f)
                .rot(RIGHT_ARM, 1.8f, EASE_IN_OUT, 0f, 0f, 0f)
                .rot(RIGHT_ELBOW, 0.35f, EASE_OUT, -115f, NA, NA)
                .rot(RIGHT_ELBOW, 1.4f, EASE_IN_OUT, -115f, NA, NA)
                .rot(RIGHT_ELBOW, 1.8f, EASE_IN_OUT, 0f, NA, NA)
                .rot(HEAD, 0.35f, EASE_OUT, -4f, -6f, 0f)
                .rot(HEAD, 1.4f, EASE_IN_OUT, -4f, -6f, 0f)
                .rot(HEAD, 1.8f, EASE_IN_OUT, 0f, 0f, 0f)
                .build();
    }

    private static EmoteAnimation facepalm() {
        return EmoteAnimation.builder("Facepalm", "Express utter disbelief", 2.2f).hold().fade(0.2f, 0.25f)
                .rot(HEAD, 0.45f, EASE_OUT, 22f, 6f, 0f)
                .rot(BODY, 0.45f, EASE_OUT, 10f, 0f, 0f)
                .rot(SPINE, 0.45f, EASE_OUT, 8f, 0f, 0f)
                .rot(RIGHT_ARM, 0.45f, EASE_OUT, -75f, -20f, 35f)
                .rot(RIGHT_ELBOW, 0.45f, EASE_OUT, -115f, NA, NA)
                .build();
    }

    private static EmoteAnimation laugh() {
        EmoteAnimation.Builder b = EmoteAnimation.builder("Laugh", "Laugh out loud holding belly", 2.2f).fade(0.2f, 0.25f);

        // Hands on belly
        b.rot(RIGHT_ARM, 0.3f, EASE_OUT, -40f, -30f, 25f);
        b.rot(RIGHT_ELBOW, 0.3f, EASE_OUT, -70f, NA, NA);
        b.rot(LEFT_ARM, 0.3f, EASE_OUT, -40f, 30f, -25f);
        b.rot(LEFT_ELBOW, 0.3f, EASE_OUT, -70f, NA, NA);

        for (int i = 0; i <= 8; i++) {
            float t = 0.3f + i * 0.18f;
            boolean laughBeat = i % 2 == 0;
            b.rot(HEAD, t, EASE_IN_OUT, laughBeat ? -24f : -12f, 0f, 0f);
            b.rot(SPINE, t, EASE_IN_OUT, laughBeat ? -15f : -5f, 0f, 0f);
            b.pos(ROOT, t, EASE_IN_OUT, NA, laughBeat ? -0.8f : 0f, NA);
        }

        // hands stay on the belly until the release (no drift)
        b.rot(RIGHT_ARM, 1.8f, EASE_IN_OUT, -40f, -30f, 25f);
        b.rot(RIGHT_ELBOW, 1.8f, EASE_IN_OUT, -70f, NA, NA);
        b.rot(LEFT_ARM, 1.8f, EASE_IN_OUT, -40f, 30f, -25f);
        b.rot(LEFT_ELBOW, 1.8f, EASE_IN_OUT, -70f, NA, NA);

        b.rot(RIGHT_ARM, 2.2f, EASE_IN_OUT, 0f, 0f, 0f);
        b.rot(RIGHT_ELBOW, 2.2f, EASE_IN_OUT, 0f, NA, NA);
        b.rot(LEFT_ARM, 2.2f, EASE_IN_OUT, 0f, 0f, 0f);
        b.rot(LEFT_ELBOW, 2.2f, EASE_IN_OUT, 0f, NA, NA);
        return b.build();
    }

    private static EmoteAnimation dance() {
        EmoteAnimation.Builder b = EmoteAnimation.builder("Dance", "Dance to the rhythm", 1.4f).loop().fade(0.25f, 0.25f);

        for (int i = 0; i <= 4; i++) {
            float t = i * 0.35f;
            boolean step = i % 2 == 0;

            b.pos(ROOT, t, EASE_IN_OUT, NA, step ? 0f : -2.5f, NA);
            b.rot(BODY, t, EASE_IN_OUT, 0f, step ? 12f : -12f, step ? 4f : -4f);
            b.rot(SPINE, t, EASE_IN_OUT, 0f, step ? -6f : 6f, 0f);

            b.rot(RIGHT_ARM, t, EASE_IN_OUT, step ? -20f : 40f, 0f, step ? 110f : 25f);
            b.rot(RIGHT_ELBOW, t, EASE_IN_OUT, step ? -80f : -20f, NA, NA);
            b.rot(LEFT_ARM, t, EASE_IN_OUT, step ? 40f : -20f, 0f, step ? -25f : -110f);
            b.rot(LEFT_ELBOW, t, EASE_IN_OUT, step ? -20f : -80f, NA, NA);

            b.rot(RIGHT_LEG, t, EASE_IN_OUT, step ? 20f : -15f, 0f, 0f);
            b.rot(RIGHT_KNEE, t, EASE_IN_OUT, step ? 30f : 5f, NA, NA);
            b.rot(LEFT_LEG, t, EASE_IN_OUT, step ? -15f : 20f, 0f, 0f);
            b.rot(LEFT_KNEE, t, EASE_IN_OUT, step ? 5f : 30f, NA, NA);
        }
        return b.build();
    }

    private static EmoteAnimation cheer() {
        EmoteAnimation.Builder b = EmoteAnimation.builder("Cheer", "Jump and cheer with joy", 1.8f).fade(0.15f, 0.2f);

        // Windup squat
        b.pos(ROOT, 0.2f, EASE_OUT, NA, 3f, NA);
        b.rot(RIGHT_KNEE, 0.2f, EASE_OUT, 35f, NA, NA);
        b.rot(LEFT_KNEE, 0.2f, EASE_OUT, 35f, NA, NA);

        // Jump up!
        b.pos(ROOT, 0.5f, EASE_OUT, NA, -7f, NA);
        b.rot(RIGHT_ARM, 0.5f, EASE_OUT, 0f, 0f, 155f);
        b.rot(RIGHT_ELBOW, 0.5f, EASE_OUT, -25f, NA, NA);
        b.rot(LEFT_ARM, 0.5f, EASE_OUT, 0f, 0f, -155f);
        b.rot(LEFT_ELBOW, 0.5f, EASE_OUT, -25f, NA, NA);
        b.rot(RIGHT_KNEE, 0.5f, EASE_OUT, 15f, NA, NA);
        b.rot(LEFT_KNEE, 0.5f, EASE_OUT, 15f, NA, NA);

        // arms stay up while landing
        b.rot(RIGHT_ARM, 1.1f, EASE_IN_OUT, 0f, 0f, 155f);
        b.rot(RIGHT_ELBOW, 1.1f, EASE_IN_OUT, -25f, NA, NA);
        b.rot(LEFT_ARM, 1.1f, EASE_IN_OUT, 0f, 0f, -155f);
        b.rot(LEFT_ELBOW, 1.1f, EASE_IN_OUT, -25f, NA, NA);

        // Land
        b.pos(ROOT, 0.9f, EASE_IN, NA, 2f, NA);
        b.pos(ROOT, 1.3f, EASE_IN_OUT, NA, 0f, NA);
        b.rot(RIGHT_ARM, 1.8f, EASE_IN_OUT, 0f, 0f, 0f);
        b.rot(RIGHT_ELBOW, 1.8f, EASE_IN_OUT, 0f, NA, NA);
        b.rot(LEFT_ARM, 1.8f, EASE_IN_OUT, 0f, 0f, 0f);
        b.rot(LEFT_ELBOW, 1.8f, EASE_IN_OUT, 0f, NA, NA);
        b.rot(RIGHT_KNEE, 1.3f, EASE_IN_OUT, 0f, NA, NA);
        b.rot(LEFT_KNEE, 1.3f, EASE_IN_OUT, 0f, NA, NA);

        return b.build();
    }

    private static EmoteAnimation spin() {
        return EmoteAnimation.builder("Spin", "Jump and spin around", 1.1f).fade(0.1f, 0.12f)
                .rot(ROOT, 0f, EASE_IN_OUT, NA, 0f, NA)
                .rot(ROOT, 1.0f, EASE_IN_OUT, NA, 360f, NA)
                .pos(ROOT, 0f, EASE_OUT, NA, 0f, NA)               // start on the ground, then jump
                .pos(ROOT, 0.25f, EASE_OUT, NA, -7f, NA)
                .pos(ROOT, 0.85f, EASE_IN, NA, 0f, NA)
                // Tuck knees & arms during mid-air spin
                .rot(RIGHT_ARM, 0f, EASE_OUT, 0f, 0f, 0f)
                .rot(RIGHT_ARM, 0.2f, EASE_OUT, 0f, 0f, 70f)
                .rot(RIGHT_ARM, 0.7f, EASE_IN_OUT, 0f, 0f, 70f)
                .rot(RIGHT_ARM, 1.1f, EASE_IN_OUT, 0f, 0f, 0f)
                .rot(RIGHT_ELBOW, 0f, EASE_OUT, 0f, NA, NA)
                .rot(RIGHT_ELBOW, 0.2f, EASE_OUT, -60f, NA, NA)
                .rot(RIGHT_ELBOW, 0.7f, EASE_IN_OUT, -60f, NA, NA)
                .rot(RIGHT_ELBOW, 1.1f, EASE_IN_OUT, 0f, NA, NA)
                .rot(LEFT_ARM, 0f, EASE_OUT, 0f, 0f, 0f)
                .rot(LEFT_ARM, 0.2f, EASE_OUT, 0f, 0f, -70f)
                .rot(LEFT_ARM, 0.7f, EASE_IN_OUT, 0f, 0f, -70f)
                .rot(LEFT_ARM, 1.1f, EASE_IN_OUT, 0f, 0f, 0f)
                .rot(LEFT_ELBOW, 0f, EASE_OUT, 0f, NA, NA)
                .rot(LEFT_ELBOW, 0.2f, EASE_OUT, -60f, NA, NA)
                .rot(LEFT_ELBOW, 0.7f, EASE_IN_OUT, -60f, NA, NA)
                .rot(LEFT_ELBOW, 1.1f, EASE_IN_OUT, 0f, NA, NA)
                .rot(RIGHT_KNEE, 0f, EASE_OUT, 0f, NA, NA)
                .rot(RIGHT_KNEE, 0.3f, EASE_OUT, 45f, NA, NA)
                .rot(RIGHT_KNEE, 0.7f, EASE_IN_OUT, 45f, NA, NA)
                .rot(RIGHT_KNEE, 1.1f, EASE_IN_OUT, 0f, NA, NA)
                .rot(LEFT_KNEE, 0f, EASE_OUT, 0f, NA, NA)
                .rot(LEFT_KNEE, 0.3f, EASE_OUT, 45f, NA, NA)
                .rot(LEFT_KNEE, 0.7f, EASE_IN_OUT, 45f, NA, NA)
                .rot(LEFT_KNEE, 1.1f, EASE_IN_OUT, 0f, NA, NA)
                .build();
    }

    private static EmoteAnimation point() {
        return EmoteAnimation.builder("Point", "Point forward", 1.8f).fade(0.2f, 0.25f)
                .rot(RIGHT_ARM, 0.3f, EASE_OUT, -85f, 0f, 5f)
                .rot(RIGHT_ARM, 1.3f, EASE_IN_OUT, -85f, 0f, 5f)
                .rot(RIGHT_ARM, 1.8f, EASE_IN_OUT, 0f, 0f, 0f)
                .rot(RIGHT_ELBOW, 0.3f, EASE_OUT, -15f, NA, NA)
                .rot(RIGHT_ELBOW, 1.3f, EASE_IN_OUT, -15f, NA, NA)
                .rot(RIGHT_ELBOW, 1.8f, EASE_IN_OUT, 0f, NA, NA)
                // Left arm resting on hip
                .rot(LEFT_ARM, 0.3f, EASE_OUT, -20f, -15f, -25f)
                .rot(LEFT_ARM, 1.3f, EASE_IN_OUT, -20f, -15f, -25f)
                .rot(LEFT_ARM, 1.8f, EASE_IN_OUT, 0f, 0f, 0f)
                .rot(LEFT_ELBOW, 0.3f, EASE_OUT, -55f, NA, NA)
                .rot(LEFT_ELBOW, 1.3f, EASE_IN_OUT, -55f, NA, NA)
                .rot(LEFT_ELBOW, 1.8f, EASE_IN_OUT, 0f, NA, NA)
                .rot(HEAD, 0.3f, EASE_OUT, -4f, 12f, 0f)
                .rot(HEAD, 1.3f, EASE_IN_OUT, -4f, 12f, 0f)
                .rot(HEAD, 1.8f, EASE_IN_OUT, 0f, 0f, 0f)
                .build();
    }

    private static EmoteAnimation surrender() {
        return EmoteAnimation.builder("Surrender", "Raise hands in defeat", 2.0f).hold().fade(0.2f, 0.25f)
                .rot(RIGHT_ARM, 0.4f, EASE_OUT, 0f, 0f, 110f)
                .rot(RIGHT_ELBOW, 0.4f, EASE_OUT, -85f, NA, NA)
                .rot(LEFT_ARM, 0.4f, EASE_OUT, 0f, 0f, -110f)
                .rot(LEFT_ELBOW, 0.4f, EASE_OUT, -85f, NA, NA)
                .rot(HEAD, 0.4f, EASE_OUT, -12f, 0f, 0f)
                .build();
    }

    private static EmoteAnimation shrug() {
        return EmoteAnimation.builder("Shrug", "Shrug shoulders in confusion", 1.8f).fade(0.2f, 0.25f)
                .pos(ROOT, 0.3f, EASE_OUT, NA, -1.8f, NA)
                .pos(ROOT, 1.0f, EASE_IN_OUT, NA, -1.8f, NA)
                .pos(ROOT, 1.5f, EASE_IN_OUT, NA, 0f, NA)
                .rot(RIGHT_ARM, 0.3f, EASE_OUT, -20f, 15f, 40f)
                .rot(RIGHT_ARM, 1.0f, EASE_IN_OUT, -20f, 15f, 40f)
                .rot(RIGHT_ARM, 1.5f, EASE_IN_OUT, 0f, 0f, 0f)
                .rot(RIGHT_ELBOW, 0.3f, EASE_OUT, -65f, NA, NA)
                .rot(RIGHT_ELBOW, 1.0f, EASE_IN_OUT, -65f, NA, NA)
                .rot(RIGHT_ELBOW, 1.5f, EASE_IN_OUT, 0f, NA, NA)
                .rot(LEFT_ARM, 0.3f, EASE_OUT, -20f, -15f, -40f)
                .rot(LEFT_ARM, 1.0f, EASE_IN_OUT, -20f, -15f, -40f)
                .rot(LEFT_ARM, 1.5f, EASE_IN_OUT, 0f, 0f, 0f)
                .rot(LEFT_ELBOW, 0.3f, EASE_OUT, -65f, NA, NA)
                .rot(LEFT_ELBOW, 1.0f, EASE_IN_OUT, -65f, NA, NA)
                .rot(LEFT_ELBOW, 1.5f, EASE_IN_OUT, 0f, NA, NA)
                .rot(HEAD, 0.3f, EASE_OUT, 0f, 0f, 15f)
                .rot(HEAD, 1.0f, EASE_IN_OUT, 0f, 0f, 15f)
                .rot(HEAD, 1.5f, EASE_IN_OUT, 0f, 0f, 0f)
                .build();
    }
}