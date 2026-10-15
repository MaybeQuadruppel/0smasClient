package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.Hacks.Visual.Viewmodel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Viewmodel: wraps the rendering of each hand in an extra transform. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonHandsMixin {

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void newbridge$viewmodelPush(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float a, float b,
                                         InteractionHand hand, float c, ItemStack stack, float d, PoseStack pose,
                                         SubmitNodeCollector collector, int light, CallbackInfo ci) {
        pose.pushPose();
        Viewmodel v = Viewmodel.INSTANCE;
        if (v == null || !v.enabled) return;
        boolean main = hand == InteractionHand.MAIN_HAND;
        if (main ? !v.mainOn : !v.offOn) return;
        double x = main ? v.mainX : v.offX, y = main ? v.mainY : v.offY, z = main ? v.mainZ : v.offZ;
        double s = main ? v.mainScale : v.offScale;
        float rx = (float) (main ? v.mainRotX : v.offRotX), ry = (float) (main ? v.mainRotY : v.offRotY), rz = (float) (main ? v.mainRotZ : v.offRotZ);
        pose.translate(x, y, z);
        if (rx != 0) pose.mulPose(new org.joml.Matrix4f().rotation(new org.joml.Quaternionf().rotationX((float) Math.toRadians(rx))));
        if (ry != 0) pose.mulPose(new org.joml.Matrix4f().rotation(new org.joml.Quaternionf().rotationY((float) Math.toRadians(ry))));
        if (rz != 0) pose.mulPose(new org.joml.Matrix4f().rotation(new org.joml.Quaternionf().rotationZ((float) Math.toRadians(rz))));
        float k = (float) (1 + s);
        pose.scale(k, k, k);
        v.applied++;
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void newbridge$viewmodelPop(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float a, float b,
                                        InteractionHand hand, float c, ItemStack stack, float d, PoseStack pose,
                                        SubmitNodeCollector collector, int light, CallbackInfo ci) {
        pose.popPose();
    }
}
