package com.OsamaClient.newbridge.mixin.emote;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.BendLimbs;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteApplier;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteEditor.EmoteEditor;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteLimbs;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteStateAccess;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs after vanilla has calculated the humanoid pose, for EVERY humanoid model (player model and armor models):
 * <ul>
 *   <li>Editor preview pose (same steps as EmoteApplier, so the preview matches the game).</li>
 *   <li>Emote pose for armor models. The player model itself gets it in PlayerModelEmoteMixin (its own tail).</li>
 * </ul>
 * Elbows / knees / spine are the lower halves provided by {@link EmoteLimbs}.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends HumanoidRenderState> {

    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void newbridge$applyPose(T state, CallbackInfo ci) {
        HumanoidModel<?> self = (HumanoidModel<?>) (Object) this;
        BendLimbs.syncOverlays(self);

        if (Boolean.TRUE.equals(((FabricRenderState) state).getData(EmoteEditor.PREVIEW_KEY))) {
            applyEditorPose(self);
            return;
        }

        // armor (and any other non-player humanoid model sharing the player's render state)
        if (!(self instanceof PlayerModel) && state instanceof EmoteStateAccess access) {
            EmoteAnimation.Pose pose = access.newbridge$getEmotePose();
            if (pose != null) EmoteApplier.apply(pose, self);
        }
    }

    private void applyEditorPose(HumanoidModel<?> self) {
        Quaternionf bodyBefore = EmoteApplier.rotationOf(body);

        apply(EmoteEditor.Bone.HEAD, head);
        apply(EmoteEditor.Bone.BODY, body);
        apply(EmoteEditor.Bone.RIGHT_ARM, rightArm);
        apply(EmoteEditor.Bone.LEFT_ARM, leftArm);
        apply(EmoteEditor.Bone.RIGHT_LEG, rightLeg);
        apply(EmoteEditor.Bone.LEFT_LEG, leftLeg);
        for (EmoteEditor.Bone bone : EmoteEditor.Bone.values()) {
            if (bone.isLower()) apply(bone, EmoteLimbs.lower(self, bone.name()));
        }

        EmoteApplier.carryTorso(self, bodyBefore); // same torso lean as in game

        float rootY = EmoteEditor.INSTANCE.previewRootY();
        if (rootY != 0f) EmoteApplier.addRootY(self, rootY);

        BendLimbs.distribute(self); // round bend, same as in game
    }

    private static void apply(EmoteEditor.Bone bone, ModelPart part) {
        if (part == null) return;
        EmoteEditor editor = EmoteEditor.INSTANCE;
        float[] t = editor.getPreviewTransform(bone);
        if (t == null) return;
        if (editor.isRotActive(bone)) {
            part.xRot = (float) Math.toRadians(t[0]);
            part.yRot = (float) Math.toRadians(t[1]);
            part.zRot = (float) Math.toRadians(t[2]);
        }
        if (editor.isPosActive(bone)) {
            part.x += t[3];
            part.y += t[4];
            part.z += t[5];
        }
    }
}