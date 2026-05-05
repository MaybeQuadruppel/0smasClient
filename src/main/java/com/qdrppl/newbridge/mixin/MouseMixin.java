package com.qdrppl.newbridge.mixin;

import com.qdrppl.newbridge.Hacks.Misc.Freelook;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void onTurn(CallbackInfo ci) {
        if (Freelook.isFreelooking()) {
            Minecraft mc = Minecraft.getInstance();
            double sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2;
            double multiplier = sensitivity * sensitivity * sensitivity * 8.0;
            double deltaX = this.accumulatedDX * multiplier;
            double deltaY = this.accumulatedDY * multiplier;

            if (mc.options.invertMouseY().get()) {
                deltaY *= -1.0;
            }
            if (mc.options.invertMouseX().get()) {
                deltaX *= -1.0;
            }
            Freelook.instance.cameraYaw += (float) (deltaX * 0.15);
            Freelook.instance.cameraPitch += (float) (deltaY * 0.15);
            Freelook.instance.cameraPitch = Mth.clamp(Freelook.instance.cameraPitch, -90.0f, 90.0f);
            this.accumulatedDX = 0;
            this.accumulatedDY = 0;

            ci.cancel();
        }
    }
}