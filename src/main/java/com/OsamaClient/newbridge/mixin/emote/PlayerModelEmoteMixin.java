package com.OsamaClient.newbridge.mixin.emote;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.BendLimbs;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteApplier;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteStateAccess;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player model: emote pose at the very end of PlayerModel.setupAnim (armor models: see HumanoidModelMixin). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelEmoteMixin extends HumanoidModel<AvatarRenderState> {
    protected PlayerModelEmoteMixin(ModelPart root) { super(root); }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void newbridge$emote(AvatarRenderState state, CallbackInfo ci) {
        BendLimbs.syncOverlays(this); // always, also without an emote: skin options may hide the sleeves
        EmoteAnimation.Pose pose = ((EmoteStateAccess) state).newbridge$getEmotePose();
        if (pose == null) return;
        EmoteApplier.apply(pose, this);
    }
}