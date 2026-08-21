package com.OsamaClient.newbridge.mixin.chams;

import com.OsamaClient.newbridge.Hacks.Visual.render.chams.Chams;
import com.OsamaClient.newbridge.Hacks.Visual.render.ChamsContext;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsRenderQueue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public abstract class ChamsSubmitMixin {

    @Inject(method = "submitModel", at = @At("HEAD"), cancellable = true, require = 0)
    private void newbridge$chamsModel(Model model, Object object, PoseStack pose, RenderType type, int light, int overlay,
                                      int tint, TextureAtlasSprite sprite, int outlineColor,
                                      ModelFeatureRenderer.CrumblingOverlay crumbling, CallbackInfo ci) {
        if (!ChamsContext.active()) return;

        if (!ChamsContext.claimBody()) {
            if (!ChamsContext.drawArmor()) {
                ci.cancel();
            } else {
                ChamsRenderQueue.submitLayer(model, object, pose.last().copy(), type,
                        light, overlay, tint, sprite);
                ci.cancel();
            }
            return;
        }

        RenderType visible = Chams.chamsVisible(type);
        RenderType occluded = Chams.chamsOccluded(type);
        if (visible == null || occluded == null) return;

        ChamsRenderQueue.submitBody(model, object, pose.last().copy(), visible, occluded,
                Chams.FULLBRIGHT, overlay, ChamsContext.visible(), ChamsContext.occluded(), sprite);
        ci.cancel();
    }
}