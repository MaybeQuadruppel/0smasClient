package com.OsamaClient.newbridge.mixin.camera;

import com.OsamaClient.newbridge.Hacks.Misc.Freelook;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class FreeLookPlayerMixin implements Freelook.CameraOverriddenEntity {

    @Unique private float cameraPitch;
    @Unique private float cameraYaw;
    @Unique private float freelookAnchorYaw;
    @Unique private boolean freelookHasAnchor = false;

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    public void changeCameraLookDirection(double xDelta, double yDelta, CallbackInfo ci) {
        if (Freelook.isFreelooking() && (Object) this instanceof LocalPlayer) {
            float pitchDelta = (float) (yDelta * 0.15);
            float yawDelta   = (float) (xDelta * 0.15);

            if (!freelookHasAnchor) {
                freelookAnchorYaw  = this.cameraYaw;
                freelookHasAnchor  = true;
            }

            this.cameraPitch = Mth.clamp(this.cameraPitch + pitchDelta, -90.0f, 90.0f);

            float limit = Freelook.instance.maxYaw;
            if (limit >= 360.0f) {
                this.cameraYaw += yawDelta;
            } else {
                this.cameraYaw = Mth.clamp(
                        this.cameraYaw + yawDelta,
                        freelookAnchorYaw - limit,
                        freelookAnchorYaw + limit
                );
            }

            ci.cancel();

        } else if (freelookHasAnchor) {
            freelookHasAnchor = false;
        }
    }

    @Override @Unique public float freelook$getCameraPitch() { return this.cameraPitch; }
    @Override @Unique public float freelook$getCameraYaw()   { return this.cameraYaw; }
    @Override @Unique public void  freelook$setCameraPitch(float pitch) { this.cameraPitch = pitch; }
    @Override @Unique public void  freelook$setCameraYaw(float yaw) {
        this.cameraYaw         = yaw;
        this.freelookAnchorYaw = yaw;
        this.freelookHasAnchor = true;
    }
}