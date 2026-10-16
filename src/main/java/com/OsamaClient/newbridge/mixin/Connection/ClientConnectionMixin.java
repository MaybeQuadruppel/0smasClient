package com.OsamaClient.newbridge.mixin.Connection;

import com.OsamaClient.newbridge.Hacks.Misc.Freecam;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.mixin.ServerboundMovePlayerPacketAccessor;
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
        com.OsamaClient.newbridge.Hacks.Misc.AntiVanish.noteOutgoing(packet);
        if (com.OsamaClient.newbridge.Hacks.Movement.AntiHunger.handle(packet)) {
            ci.cancel();
            return;
        }
        if (com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.intercept(packet)) {
            ci.cancel();
            return;
        }
        try {
            if (ModuleManager.modules == null || ModuleManager.modules.isEmpty()) return;


        } catch (Exception e) {
            e.printStackTrace();
        }

        Module freecam = ModuleManager.getModuleByName("Freecam");
        if (freecam != null && freecam.enabled) {
            if (packet instanceof ServerboundMovePlayerPacket) {
                ci.cancel();
                return;
            }
        }
        if (Freecam.isActive && packet instanceof ServerboundMovePlayerPacket) {
            ci.cancel();
            return;
        }
        // last step: what really goes out is measured by the CheckMeter and cleaned by the sanitizer
        if (com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.resending) return;
        com.OsamaClient.newbridge.Hacks.Client.CheckMeter.observe(packet);
        Packet<?> fixed = com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(packet);
        if (fixed != packet) {
            ci.cancel();
            if (fixed != null) {
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.resending = true;
                try { ((Connection) (Object) this).send(fixed); }
                finally { com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.resending = false; }
            }
        }
    }
}