package com.OsamaClient.newbridge.mixin.combat;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Zugriff auf die zuletzt an den Server gesendete Rotation (für Silent Aim). */
@Mixin(LocalPlayer.class)
public interface LocalPlayerRotationAccessor {

    @Accessor("yRotLast")
    float newbridge$getYRotLast();

    @Accessor("yRotLast")
    void newbridge$setYRotLast(float value);

    @Accessor("xRotLast")
    float newbridge$getXRotLast();

    @Accessor("xRotLast")
    void newbridge$setXRotLast(float value);
}
