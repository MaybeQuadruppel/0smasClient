package com.qdrppl.newbridge.mixin;

import com.qdrppl.newbridge.Hacks.Misc.Freelook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setRotation", at = @At("TAIL"))
    private void onSetRotation(float yRot, float xRot, CallbackInfo ci) {
        if (Freelook.isFreelooking()) {
            this.setRotation(Freelook.instance.cameraYaw, Freelook.instance.cameraPitch);
        }
    }
}