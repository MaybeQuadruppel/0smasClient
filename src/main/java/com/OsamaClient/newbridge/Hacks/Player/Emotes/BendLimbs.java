package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Smooth bending of arms, legs and torso.
 *
 * The geometry of the player model and of the armor models is NOT changed any more (every limb stays one
 * vanilla box with its exact texture). Each limb only gets an empty child part {@link #LOWER} that carries
 * the total bend (elbow / knee / spine rotation) written by the emote system. {@link #distribute} copies it
 * onto the limb ({@link BendHolder}); the renderer ({@link BendRender}) then bends the box smoothly around the
 * joint, so there are no gaps or hard folds.
 */
public final class BendLimbs {

    /** Name of the empty child part that holds the ELBOW / KNEE / SPINE rotation. */
    public static final String LOWER = "newbridge_lower";

    /** Joint height inside the limb box (model pixels, limb local, y down). */
    public static final float ARM_PIVOT_Y = 4f;   // arm box y -2..10
    public static final float LEG_PIVOT_Y = 6f;   // leg box y 0..12
    public static final float BODY_PIVOT_Y = 6f;  // body box y 0..12

    private static final Set<MeshDefinition> PREPARED = Collections.newSetFromMap(new WeakHashMap<>());

    private BendLimbs() {}

    /**
     * Called from EntityModelSetMixin before baking layers: adds the empty {@link #LOWER} marker to the limbs
     * of the player model and of the player armor layers.
     */
    public static void prepare(ModelLayerLocation location, MeshDefinition mesh) {
        if (location == null || mesh == null) return;
        String model = location.model().getPath();
        if (!"player".equals(model) && !"player_slim".equals(model)) return;
        if ("helmet".equals(location.layer())) return;
        if (!PREPARED.add(mesh)) return;
        PartDefinition root = mesh.getRoot();
        for (String name : new String[]{"body", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            PartDefinition limb = root.getChild(name);
            if (limb != null && limb.getChild(LOWER) == null) {
                limb.addOrReplaceChild(LOWER, CubeListBuilder.create(), PartPose.ZERO);
            }
        }
    }

    /** The marker part that stores the total bend of a limb (null if the model is not prepared). */
    private static ModelPart marker(ModelPart limb) {
        return limb != null && limb.hasChild(LOWER) ? limb.getChild(LOWER) : null;
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
        return marker(parent);
    }

    /** Call AFTER the pose was written: hands the stored bend of every limb to the renderer. */
    public static void distribute(HumanoidModel<?> model) {
        spread(model.body, BODY_PIVOT_Y);
        spread(model.rightArm, ARM_PIVOT_Y);
        spread(model.leftArm, ARM_PIVOT_Y);
        spread(model.rightLeg, LEG_PIVOT_Y);
        spread(model.leftLeg, LEG_PIVOT_Y);
    }

    /** Removes every bend (no emote active). */
    public static void clear(HumanoidModel<?> model) {
        for (ModelPart limb : new ModelPart[]{model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
            if (limb != null) holder(limb).newbridge$clearBend();
        }
    }

    private static void spread(ModelPart limb, float pivotY) {
        ModelPart m = marker(limb);
        if (m == null) return;
        holder(limb).newbridge$setBend(pivotY, m.xRot, m.yRot, m.zRot);
    }

    private static BendHolder holder(ModelPart part) {
        return (BendHolder) (Object) part;
    }

    /** Kept for old call sites: overlays are children of their limb and bend with it, nothing to sync. */
    public static void syncOverlays(HumanoidModel<?> model) {}
}
