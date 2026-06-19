package com.qdrppl.newbridge.mixin.camera;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {

    @Accessor("position")
    void setPosition(Vec3 position);

    @Accessor("xRot")
    void setXRot(float xRot);

    @Accessor("yRot")
    void setYRot(float yRot);
}