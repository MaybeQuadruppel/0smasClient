package com.OsamaClient.newbridge.mixin.camera;

import com.OsamaClient.newbridge.Hacks.Misc.Freecam;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void onIsControlledCamera(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive) {
            cir.setReturnValue(false); // Zeigt deinen eigenen Körper an
        }
    }

    @Inject(method = "isMoving", at = @At("HEAD"), cancellable = true)
    private void onIsMoving(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive) {
            cir.setReturnValue(false);
        }
    }


    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void onAiStep(CallbackInfo ci) {
        if (Freecam.isActive) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            player.setDeltaMovement(0, 0, 0);
            ci.cancel();
        }
    }
}