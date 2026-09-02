package com.OsamaClient.newbridge.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {

    @Mutable
    @Accessor("onGround")
    void setOnGround(boolean onGround);

    @Mutable
    @Accessor("yRot")
    void setYRot(float yRot);

    @Mutable
    @Accessor("xRot")
    void setXRot(float xRot);

    @Mutable
    @Accessor("hasRot")
    void setHasRot(boolean hasRot);

    @Accessor("hasRot")
    boolean getHasRot();
}