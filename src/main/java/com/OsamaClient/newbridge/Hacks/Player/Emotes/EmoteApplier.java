package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation.Bone;
import static com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation.Channel.*;

/**
 * Writes a pose onto a humanoid model (player model or armor model):
 * rotations are blended in (absolute), positions are added.
 *
 * Two things happen on top of the plain keyframe values:
 * <ul>
 *   <li>Torso lean: BODY rotates around the HIPS and carries head and arms with it (their keys are
 *       relative to the torso). Without this the torso detaches from legs, neck and shoulders.</li>
 *   <li>Bend distribution: the ELBOW / KNEE / SPINE rotation is spread over all joints of the limb chain
 *       (see {@link BendLimbs#distribute}), which makes the bend round.</li>
 * </ul>
 */
public final class EmoteApplier {

    /** Distance from the body pivot (neck) down to the hip pivot, model pixels. */
    private static final float HIP_Y = 12f;

    private EmoteApplier() {}

    public static void apply(EmoteAnimation.Pose pose, HumanoidModel<?> model) {
        ModelPart[] parts = new ModelPart[Bone.values().length]; // index = Bone ordinal, ROOT stays null
        parts[Bone.HEAD.ordinal()] = model.head;
        parts[Bone.BODY.ordinal()] = model.body;
        parts[Bone.RIGHT_ARM.ordinal()] = model.rightArm;
        parts[Bone.LEFT_ARM.ordinal()] = model.leftArm;
        parts[Bone.RIGHT_LEG.ordinal()] = model.rightLeg;
        parts[Bone.LEFT_LEG.ordinal()] = model.leftLeg;
        for (Bone b : Bone.values()) {
            if (isLower(b)) parts[b.ordinal()] = EmoteLimbs.lower(model, b.name()); // null when not split: ignored
        }

        float w = pose.weight;
        Quaternionf bodyBefore = rotationOf(model.body);

        for (Bone b : Bone.values()) {
            ModelPart p = parts[b.ordinal()];
            if (p == null) continue;
            if (pose.has(b, ROT_X)) p.xRot = lerp(p.xRot, rad(pose.get(b, ROT_X)), w);
            if (pose.has(b, ROT_Y)) p.yRot = lerp(p.yRot, rad(pose.get(b, ROT_Y)), w);
            if (pose.has(b, ROT_Z)) p.zRot = lerp(p.zRot, rad(pose.get(b, ROT_Z)), w);
            if (pose.has(b, POS_X)) p.x += pose.get(b, POS_X) * w;
            if (pose.has(b, POS_Y)) p.y += pose.get(b, POS_Y) * w;
            if (pose.has(b, POS_Z)) p.z += pose.get(b, POS_Z) * w;
        }

        carryTorso(model, bodyBefore);

        float rootY = pose.has(Bone.ROOT, POS_Y) ? pose.get(Bone.ROOT, POS_Y) * w : 0f;
        if (rootY != 0f) addRootY(model, rootY);

        BendLimbs.distribute(model);
    }

    /** Whole-body vertical offset. Lower halves are children, they follow their parent already. */
    public static void addRootY(HumanoidModel<?> m, float dy) {
        m.head.y += dy;
        m.body.y += dy;
        m.rightArm.y += dy;
        m.leftArm.y += dy;
        m.rightLeg.y += dy;
        m.leftLeg.y += dy;
    }

    /**
     * Rotation change of the torso since {@code bodyBefore} is applied to head and arms as well,
     * around the hip pivot. The body itself only needs its position corrected (its rotation is already set).
     */
    public static void carryTorso(HumanoidModel<?> m, Quaternionf bodyBefore) {
        Quaternionf delta = rotationOf(m.body).mul(bodyBefore.conjugate(new Quaternionf()));
        if (Math.abs(delta.w) > 0.99999f) return; // no torso rotation

        Vector3f hip = new Vector3f(m.body.x, m.body.y + HIP_Y, m.body.z);
        rotateAbout(m.body, delta, hip, false);
        rotateAbout(m.head, delta, hip, true);
        rotateAbout(m.rightArm, delta, hip, true);
        rotateAbout(m.leftArm, delta, hip, true);
    }

    private static void rotateAbout(ModelPart p, Quaternionf delta, Vector3f pivot, boolean rotate) {
        Vector3f v = new Vector3f(p.x - pivot.x, p.y - pivot.y, p.z - pivot.z);
        delta.transform(v);
        p.x = pivot.x + v.x;
        p.y = pivot.y + v.y;
        p.z = pivot.z + v.z;
        if (rotate) {
            Vector3f e = delta.mul(rotationOf(p), new Quaternionf()).getEulerAnglesZYX(new Vector3f());
            p.xRot = e.x;
            p.yRot = e.y;
            p.zRot = e.z;
        }
    }

    /** Same rotation order as ModelPart (Z * Y * X). */
    public static Quaternionf rotationOf(ModelPart p) {
        return new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot);
    }

    /** Children of another part (elbows, knees, lower torso). */
    private static boolean isLower(Bone b) {
        return b == Bone.RIGHT_ELBOW || b == Bone.LEFT_ELBOW || b == Bone.RIGHT_KNEE || b == Bone.LEFT_KNEE
                || b == Bone.SPINE;
    }

    private static float rad(float deg) { return deg * (float) (Math.PI / 180.0); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}