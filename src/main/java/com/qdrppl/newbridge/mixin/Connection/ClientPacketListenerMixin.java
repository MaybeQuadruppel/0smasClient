package com.qdrppl.newbridge.mixin.Connection;

import com.qdrppl.newbridge.Hacks.Combat.Velocity;
import com.qdrppl.newbridge.Hacks.Combat.AutoTotem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
    private void onHandleSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || Velocity.INSTANCE == null || !Velocity.INSTANCE.enabled) return;

        if (packet.id() == mc.player.getId()) {
            if (Velocity.INSTANCE.horizontal == 0 && Velocity.INSTANCE.vertical == 0) {
                ci.cancel();
                return;
            }

            double hMult = Velocity.INSTANCE.horizontal / 100.0;
            double vMult = Velocity.INSTANCE.vertical / 100.0;

            Vec3 originalMovement = packet.movement();
            Vec3 scaledMovement = new Vec3(
                    originalMovement.x * hMult,
                    originalMovement.y * vMult,
                    originalMovement.z * hMult
            );

            mc.player.lerpMotion(scaledMovement);
            ci.cancel();
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (packet.getEventId() == 35) {
            if (packet.getEntity(mc.level) == mc.player) {
                if (AutoTotem.INSTANCE != null) {
                    AutoTotem.INSTANCE.onTotemPop();
                }
            }
        }
    }
}