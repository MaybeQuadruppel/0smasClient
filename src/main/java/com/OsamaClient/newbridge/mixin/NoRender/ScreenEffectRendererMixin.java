package com.OsamaClient.newbridge.mixin.NoRender;

import com.OsamaClient.newbridge.Hacks.Visual.NoRender;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    // Blockiert die Flammen auf dem Bildschirm, wenn der Spieler brennt
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void onSubmitFire(CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.enabled && NoRender.INSTANCE.fire) {
            ci.cancel();
        }
    }
}