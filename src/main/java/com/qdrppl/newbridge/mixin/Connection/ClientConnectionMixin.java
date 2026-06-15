package com.qdrppl.newbridge.mixin.Connection;

import com.qdrppl.newbridge.Hacks.Misc.FakeLag;
import com.qdrppl.newbridge.Hacks.Misc.Freecam;
import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ClientConnectionMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        try {
            if (ModuleManager.modules == null || ModuleManager.modules.isEmpty()) return;

            Module baseModule = ModuleManager.getModuleByName("FakeLag");
            if (baseModule == null || !baseModule.enabled) return;

            if (baseModule instanceof FakeLag fakeLag) {
                if (packet instanceof ServerboundMovePlayerPacket) {
                    fakeLag.packetQueue.add(packet);
                    ci.cancel();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Module freecam = ModuleManager.getModuleByName("Freecam");
        if (freecam != null && freecam.enabled) {
            if (packet instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket) {
                ci.cancel();
                return;
            }
        }
        if (Freecam.isActive && packet instanceof ServerboundMovePlayerPacket) {
            ci.cancel();
        }

    }
}