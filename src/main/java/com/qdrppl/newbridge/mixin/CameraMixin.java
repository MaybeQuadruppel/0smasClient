package com.qdrppl.newbridge.mixin;

import com.qdrppl.newbridge.Hacks.Misc.Freelook;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Unique
    private boolean firstTime = true;

    /** Tracks the alt-held state from the previous frame to detect transitions. */
    @Unique
    private boolean wasFreelooking = false;

    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(
            method = "alignWithEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V",
                    ordinal = 1,
                    shift = At.Shift.AFTER)
    )
    public void lockRotation(float f, CallbackInfo ci) {
        boolean freelooking = Freelook.isFreelooking();
        Minecraft mc = Minecraft.getInstance();

        if (freelooking && !wasFreelooking) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            firstTime = true;
        }

        if (!freelooking && wasFreelooking) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            firstTime = true;
        }

        wasFreelooking = freelooking;

        if (freelooking && this.entity instanceof LocalPlayer) {
            Freelook.CameraOverriddenEntity cam =
                    (Freelook.CameraOverriddenEntity) this.entity;

            if (firstTime && mc.player != null) {
                cam.freelook$setCameraPitch(mc.player.getXRot());
                cam.freelook$setCameraYaw(mc.player.getYRot());
                firstTime = false;
            }

            this.setRotation(cam.freelook$getCameraYaw(), cam.freelook$getCameraPitch());
        }
    }
}