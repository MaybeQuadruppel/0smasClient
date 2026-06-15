package com.qdrppl.newbridge.mixin.Connection;

//import com.qdrppl.newbridge.Hacks.Dupeing.PacketState;
//import net.minecraft.client.multiplayer.ClientCommonNetworkHandler;
//import net.minecraft.client.multiplayer.Ne;
//import net.minecraft.network.protocol.Packet;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//
///**
// * FIX: Connection.send(Packet, PacketSendListener) was removed / changed in 26.1.2.
// *
// * We target ClientCommonNetworkHandler.send(Packet) instead — this is the method
// * every packet handler subclass calls to dispatch C2S packets, and it exists with
// * a clean single-argument signature that is stable across versions.
// *
// * Flow:
// *   module code  →  handler.send(packet)
// *              →  [THIS MIXIN]  →  PacketState.shouldIntercept?
// *                   yes → hold / drop,  ci.cancel()
// *                   no  → let through normally
// *
// * Flush path:
// *   PacketState.flush() sets bypassing=true, calls handler.send(packet) again,
// *   mixin sees bypassing=true and skips → packet reaches the network layer.
// */
//@Mixin(ClientCommonNetworkHandler.class)
//public class ConnectionMixin {
//
//    @Inject(
//            method      = "send(Lnet/minecraft/network/protocol/Packet;)V",
//            at          = @At("HEAD"),
//            cancellable = true
//    )
//    private void onSend(Packet<?> packet, CallbackInfo ci) {
//        if (PacketState.shouldIntercept(packet)) {
//            if (!PacketState.sendEnabled) {
//                // Hold mode: buffer the packet for later flush
//                PacketState.hold(packet);
//            }
//            // Desync-only mode: packet is dropped silently (no hold)
//            ci.cancel();
//        }
//    }
//}