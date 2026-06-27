package com.OsamaClient.newbridge.mixin.camera;

import com.OsamaClient.newbridge.Hacks.Misc.Freelook;
import com.OsamaClient.newbridge.Hacks.Misc.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow private boolean detached;
    @Shadow private Entity entity;

    @Shadow
    @Final
    private Minecraft minecraft;
    @Unique private boolean wasFreelooking = false;
    @Unique private boolean firstTimeFreelook = true;

    @Inject(method = "alignWithEntity", at = @At("HEAD"))
    private void onAlignWithEntityHead(float partialTicks, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        if (Freecam.isActive) {
            this.detached = true;

            // WICHTIG: Holt sich deine Freecam-Instanz (oder ruft die Logik statisch auf)
            // Hier triggern wir die flüssige Frame-Bewegung!
            Freecam.renderMovement();
        }

        // Freelook Logik
        boolean freelooking = Freelook.isFreelooking();
        if (freelooking && !wasFreelooking) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            firstTimeFreelook = true;
        }
        if (!freelooking && wasFreelooking) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        wasFreelooking = freelooking;
    }

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void onSetPositionArgs(Args args) {
        if (Freecam.isActive && Freecam.cameraPos != null) {
            args.set(0, Freecam.cameraPos.x);
            args.set(1, Freecam.cameraPos.y);
            args.set(2, Freecam.cameraPos.z);
        }
    }

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void onSetRotationArgs(Args args) {
        if (Freecam.isActive) {
            args.set(0, Freecam.cameraYaw);
            args.set(1, Freecam.cameraPitch);
        }
        else if (Freelook.isFreelooking() && this.entity instanceof LocalPlayer) {
            Freelook.CameraOverriddenEntity cam = (Freelook.CameraOverriddenEntity) this.entity;
            if (firstTimeFreelook && minecraft.player != null) {
                cam.freelook$setCameraYaw(minecraft.player.getYRot());
                cam.freelook$setCameraPitch(minecraft.player.getXRot());
                firstTimeFreelook = false;
            }
            args.set(0, cam.freelook$getCameraYaw());
            args.set(1, cam.freelook$getCameraPitch());
        }
    }
}