package com.OsamaClient.newbridge.mixin.chams;

import com.OsamaClient.newbridge.Hacks.Visual.render.ChamsContext;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ESPRenderUtil;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class ChamsLivingEntityMixin {

    @Inject(method = "submit", at = @At("HEAD"), require = 0)
    private void newbridge$chamsContextStart(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                             CameraRenderState camera, CallbackInfo ci) {
        ChamsContext.clear();

        if (ESPRenderUtil.hasChamsWork() && state instanceof ChamsHolder holder && holder.newbridge$chamsActive()) {
            ChamsContext.set(
                    holder.newbridge$chamsVisible(),
                    holder.newbridge$chamsOccluded(),
                    ESPRenderUtil.chamsDrawArmor()
            );
        }
    }

    @Inject(method = "submit", at = @At("RETURN"), require = 0)
    private void newbridge$chamsContextEnd(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                           CameraRenderState camera, CallbackInfo ci) {
        ChamsContext.clear();
    }
}