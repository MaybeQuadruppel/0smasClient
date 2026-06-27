package com.OsamaClient.newbridge.mixin.camera;

import com.OsamaClient.newbridge.Hacks.Misc.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurnPlayer(CallbackInfo ci) {
        if (Freecam.isActive) {

            double sensitivity = minecraft.options.sensitivity().get() * 0.6F + 0.2F;
            double multiplier = sensitivity * sensitivity * sensitivity * 8.0D;

            double deltaX = this.accumulatedDX * multiplier;
            double deltaY = this.accumulatedDY * multiplier;

            if (minecraft.options.invertMouseY().get()) {
                deltaY = -deltaY;
            }

            Freecam.cameraYaw += (float) (deltaX * 0.15F);
            Freecam.cameraPitch += (float) (deltaY * 0.15F);


            if (Freecam.cameraPitch > 90.0F) Freecam.cameraPitch = 90.0F;
            if (Freecam.cameraPitch < -90.0F) Freecam.cameraPitch = -90.0F;

            this.accumulatedDX = 0;
            this.accumulatedDY = 0;

            ci.cancel();
        }
    }
}