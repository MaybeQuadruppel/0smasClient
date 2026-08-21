package com.OsamaClient.newbridge.mixin.chams;

import com.OsamaClient.newbridge.Hacks.Visual.render.ChamsContext;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class ChamsCapeLayerMixin {

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true, require = 0)
    private void newbridge$hideCapeForChams(CallbackInfo ci) {
        if (ChamsContext.active()) {
            ci.cancel();
        }
    }
}