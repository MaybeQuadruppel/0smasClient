package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Splits arms, legs and torso into a chain of slices so limbs can bend smoothly.
 *
 * Chain per limb (parent -> child):  limb (upper)  ->  bend1  ->  bend2  ->  bend3  ->  LOWER
 * The emote system only ever writes the TOTAL bend into LOWER; {@link #distribute} then spreads it
 * evenly over all joints, so a 90 degree elbow becomes four 22.5 degree steps instead of one hard fold.
 *
 * Texture mapping stays exact: every slice samples its own rows of the original box texture, also
 * for armor (which is inflated by a CubeDeformation) - the inflation is converted into a per-slice
 * vertical grow so the texture is not stretched differently on each slice.
 */
public final class BendLimbs {

    /** Name of the last slice (the one the emote bones ELBOW / KNEE / SPINE rotate). */
    public static final String LOWER = "newbridge_lower";
    private static final String BEND = "newbridge_bend";
    private static final String LOWER_OVERLAY_SUFFIX = "_newbridge_lower";
    private static final String BEND_OVERLAY_SUFFIX = "_newbridge_bend";

    /** Slices per limb: 1 upper + 3 middle + 1 lower = 4 joints. */
    private static final int SLICES = 5;
    /** Rows a slice reaches back into the previous one, hides the gap on the outside of a bend. */
    private static final int OVERLAP = 2;
    private static final float EPS = 0.002f;   // per slice, avoids z-fighting between overlapping slices
    private static final float CAP = 0.015f;   // bottom cap with the real bottom texture, must beat EPS * SLICES
    private static final float OVERLAY = 0.25f;

    private static final int P_BODY = 1, P_ARMS = 2, P_LEGS = 4, P_ALL = 7;

    private static final Set<MeshDefinition> PREPARED = Collections.newSetFromMap(new WeakHashMap<>());

    private BendLimbs() {}

    /**
     * Called from EntityModelSetMixin before baking layers.
     *
     * NOTE: armor layers are called "player#helmet / chestplate / leggings / boots" (older versions:
     * "player#inner_armor / outer_armor"). Their MODEL path is also just "player", so the LAYER has to
     * be checked as well - otherwise the armor mesh gets the skin treatment (no inflation, 64x64 UVs).
     */
    public static void prepare(ModelLayerLocation location, MeshDefinition mesh) {
        if (location == null || mesh == null) return;

        String model = location.model().getPath();
        boolean slim;
        if ("player".equals(model)) slim = false;
        else if ("player_slim".equals(model)) slim = true;
        else return;

        String layer = location.layer();
        int parts;
        float grow;
        switch (layer) {
            case "main" -> {
                if (PREPARED.add(mesh)) apply(mesh, 0f, slim, false, P_ALL);
                return;
            }
            case "chestplate" -> { parts = P_BODY | P_ARMS; grow = 1.0f; }
            case "leggings" -> { parts = P_BODY | P_LEGS; grow = 0.5f; }
            case "boots" -> { parts = P_LEGS; grow = 1.0f; }
            case "inner_armor" -> { parts = P_ALL; grow = 0.5f; }
            case "outer_armor" -> { parts = P_ALL; grow = 1.0f; }
            default -> { return; } // helmet and anything unknown: nothing to split
        }
        if (PREPARED.add(mesh)) apply(mesh, grow, false, true, parts);
    }

    /**
     * @param grow   cube inflation (0 for skin, 0.5 / 1.0 for armor)
     * @param parts  which limbs exist in this mesh. Armor meshes only keep the parts of their slot
     *               (the others are emptied by vanilla) and those must NOT be re-created here.
     */
    public static void apply(MeshDefinition mesh, float grow, boolean slim, boolean isArmor, int parts) {
        PartDefinition root = mesh.getRoot();
        int armW = slim ? 3 : 4;
        float rightArmX = slim ? -2f : -3f;

        if (isArmor) {
            // 64x32 layout, left limbs are mirrored copies of the right ones, no skin overlays
            if ((parts & P_BODY) != 0) {
                split(root, "body", null, PartPose.ZERO,
                        -4f, 0f, -2f, 8, 12, 4, 16, 16, 0, 0, grow, false);
            }
            if ((parts & P_ARMS) != 0) {
                split(root, "right_arm", null, PartPose.offset(-5f, 2f, 0f),
                        -3f, -2f, -2f, 4, 12, 4, 40, 16, 0, 0, grow, false);
                split(root, "left_arm", null, PartPose.offset(5f, 2f, 0f),
                        -1f, -2f, -2f, 4, 12, 4, 40, 16, 0, 0, grow, true);
            }
            if ((parts & P_LEGS) != 0) {
                split(root, "right_leg", null, PartPose.offset(-1.9f, 12f, 0f),
                        -2f, 0f, -2f, 4, 12, 4, 0, 16, 0, 0, grow, false);
                split(root, "left_leg", null, PartPose.offset(1.9f, 12f, 0f),
                        -2f, 0f, -2f, 4, 12, 4, 0, 16, 0, 0, grow, true);
            }
        } else {
            // 64x64 skin layout with separate left limb UVs and overlay layers
            split(root, "body", "jacket", PartPose.ZERO,
                    -4f, 0f, -2f, 8, 12, 4, 16, 16, 16, 32, grow, false);
            split(root, "right_arm", "right_sleeve", PartPose.offset(-5f, 2f, 0f),
                    rightArmX, -2f, -2f, armW, 12, 4, 40, 16, 40, 32, grow, false);
            split(root, "left_arm", "left_sleeve", PartPose.offset(5f, 2f, 0f),
                    -1f, -2f, -2f, armW, 12, 4, 32, 48, 48, 48, grow, false);
            split(root, "right_leg", "right_pants", PartPose.offset(-1.9f, 12f, 0f),
                    -2f, 0f, -2f, 4, 12, 4, 0, 16, 0, 32, grow, false);
            split(root, "left_leg", "left_pants", PartPose.offset(1.9f, 12f, 0f),
                    -2f, 0f, -2f, 4, 12, 4, 16, 48, 0, 48, grow, false);
        }
    }

    /** Texture rows (0..h) at which the slices are cut. Joints sit at rows half-3, half-1, half+1, half+3. */
    private static int[] rows(int h) {
        int half = h / 2;
        return new int[]{0, half - 3, half - 1, half + 1, half + 3, h};
    }

    /** Model y of a texture row of a box whose geometry is inflated by g (UV stays h rows tall). */
    private static float rowY(float y, int h, float g, int row) {
        return (y - g) + row * (h + 2f * g) / h;
    }

    private static String sliceName(int i) {
        return i == SLICES - 1 ? LOWER : BEND + i;
    }

    private static String overlayName(String overlay, int i) {
        if (i == 0) return overlay;
        return i == SLICES - 1 ? overlay + LOWER_OVERLAY_SUFFIX : overlay + BEND_OVERLAY_SUFFIX + i;
    }

    private static void split(PartDefinition root, String name, String overlay, PartPose pose,
                              float x, float y, float z, int w, int h, int d,
                              int u, int v, int ou, int ov, float grow, boolean mirror) {
        PartDefinition old = root.getChild(name);
        if (old == null) return; // part does not exist in this mesh, do not invent it
        boolean hasOverlay = overlay != null && old.getChild(overlay) != null;
        int[] rows = rows(h);

        // limb-local y of every slice pivot (slice 0 is the limb itself, pivot = limb origin)
        float[] originY = new float[SLICES];
        for (int i = 1; i < SLICES; i++) originY[i] = rowY(y, h, grow, rows[i]);

        PartDefinition[] slices = new PartDefinition[SLICES];
        for (int i = 0; i < SLICES; i++) {
            CubeListBuilder cubes = sliceCubes(i, x, y, z, w, h, d, u, v, grow, mirror, originY[i]);
            if (i == 0) {
                slices[0] = root.addOrReplaceChild(name, cubes, pose);
            } else {
                slices[i] = slices[i - 1].addOrReplaceChild(sliceName(i), cubes,
                        PartPose.offset(0f, originY[i] - originY[i - 1], 0f));
            }
        }

        if (hasOverlay) {
            float og = grow + OVERLAY;
            for (int i = 0; i < SLICES; i++) {
                // overlay geometry is given in the same slice-local frame, so it follows its slice exactly
                CubeListBuilder cubes = sliceCubes(i, x, y, z, w, h, d, ou, ov, og, mirror, originY[i]);
                slices[i].addOrReplaceChild(overlayName(overlay, i), cubes, PartPose.ZERO);
            }
        }
    }

    private static CubeListBuilder sliceCubes(int i, float x, float y, float z, int w, int h, int d,
                                              int u, int v, float g, boolean mirror, float originY) {
        int[] rows = rows(h);
        int a = rows[i] - (i > 0 ? OVERLAP : 0); // first texture row of this slice
        int b = rows[i + 1];
        int height = b - a;
        float gy = height * g / h;               // vertical inflation that keeps the texture scale of the full box
        float bias = i * EPS;
        float top = rowY(y, h, g, a);

        CubeListBuilder builder = CubeListBuilder.create();
        if (mirror) builder.mirror();
        builder.texOffs(u, v + a).addBox(x, top + gy - originY, z, w, height, d,
                new CubeDeformation(g + bias, gy + bias, g + bias));

        if (i == SLICES - 1) {
            // zero-height cap carrying the real bottom texture (the slice's own bottom face has the wrong UV)
            builder.texOffs(u, v).addBox(x, rowY(y, h, g, h) - originY, z, w, 0f, d,
                    new CubeDeformation(g + CAP, CAP, g + CAP));
        }
        return builder;
    }

    /** Last slice of the chain below this limb, or null when the limb is not split. */
    private static ModelPart[] chain(ModelPart limb) {
        if (limb == null) return null;
        ModelPart[] out = new ModelPart[SLICES - 1];
        ModelPart cur = limb;
        for (int i = 1; i < SLICES; i++) {
            String n = sliceName(i);
            if (!cur.hasChild(n)) return null;
            cur = cur.getChild(n);
            out[i - 1] = cur;
        }
        return out;
    }

    public static ModelPart lower(HumanoidModel<?> model, String boneName) {
        ModelPart parent = switch (boneName) {
            case "RIGHT_ELBOW" -> model.rightArm;
            case "LEFT_ELBOW" -> model.leftArm;
            case "RIGHT_KNEE" -> model.rightLeg;
            case "LEFT_KNEE" -> model.leftLeg;
            case "SPINE" -> model.body;
            default -> null;
        };
        ModelPart[] c = chain(parent);
        return c == null ? null : c[c.length - 1];
    }

    /**
     * Call AFTER the pose was written: takes the total rotation stored in LOWER and spreads it evenly
     * over every joint of the chain. This is what makes the bend round instead of one hard fold.
     */
    public static void distribute(HumanoidModel<?> model) {
        spread(model.body);
        spread(model.rightArm);
        spread(model.leftArm);
        spread(model.rightLeg);
        spread(model.leftLeg);
    }

    private static void spread(ModelPart limb) {
        ModelPart[] c = chain(limb);
        if (c == null) return;
        ModelPart last = c[c.length - 1];
        float share = 1f / c.length;
        float xr = last.xRot * share, yr = last.yRot * share, zr = last.zRot * share;
        for (ModelPart p : c) {
            p.xRot = xr;
            p.yRot = yr;
            p.zRot = zr;
        }
    }

    public static void syncOverlays(HumanoidModel<?> model) {
        sync(model.body, "jacket");
        sync(model.rightArm, "right_sleeve");
        sync(model.leftArm, "left_sleeve");
        sync(model.rightLeg, "right_pants");
        sync(model.leftLeg, "left_pants");
    }

    private static void sync(ModelPart part, String overlay) {
        if (!part.hasChild(overlay)) return;
        boolean visible = part.getChild(overlay).visible;
        ModelPart cur = part;
        for (int i = 1; i < SLICES; i++) {
            String n = sliceName(i);
            if (!cur.hasChild(n)) return;
            cur = cur.getChild(n);
            String on = overlayName(overlay, i);
            if (cur.hasChild(on)) cur.getChild(on).visible = visible;
        }
    }
}