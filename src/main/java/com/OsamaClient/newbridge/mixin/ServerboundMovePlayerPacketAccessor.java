package com.OsamaClient.newbridge.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {

    @Mutable
    @Accessor("onGround") // Falls dein Mappings-System den Namen ändert (z.B. Yarn vs Mojmap), hier anpassen!
    void setOnGround(boolean onGround);

}