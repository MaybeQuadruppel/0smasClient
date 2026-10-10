package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The ONE place that knows where the lower arm / lower leg / lower torso parts (BendLimbs) live.
 * Used by the in-game mixins and by the editor preview, so both always find the same parts.
 */
public final class EmoteLimbs {

    private EmoteLimbs() {}

    /**
     * @param boneName RIGHT_ELBOW, LEFT_ELBOW, RIGHT_KNEE, LEFT_KNEE or SPINE
     * @return the lower half as ModelPart, or null when the model is not split (the bone is then ignored)
     */
    public static ModelPart lower(HumanoidModel<?> model, String boneName) {
        return BendLimbs.lower(model, boneName);
    }
}