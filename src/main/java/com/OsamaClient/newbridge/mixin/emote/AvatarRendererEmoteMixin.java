package com.OsamaClient.newbridge.mixin.emote;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimator;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteStateAccess;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererEmoteMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void newbridge$emote(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        EmoteAnimation.Pose pose = EmoteAnimator.INSTANCE.pose(avatar.getUUID());
        ((EmoteStateAccess) state).newbridge$setEmotePose(pose);
        if (pose != null) { // whole-body yaw: turn the body, keep the head looking where it looked
            float yaw = pose.rootYaw();
            state.bodyRot += yaw;
            state.yRot -= yaw;
        }
    }
}