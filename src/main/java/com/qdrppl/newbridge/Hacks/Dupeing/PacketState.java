package com.qdrppl.newbridge.Hacks.Dupeing;

//import net.minecraft.client.Minecraft;
//import net.minecraft.client.multiplayer.ClientCommonNetworkHandler;
//import net.minecraft.network.protocol.Packet;
//
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * Shared state for the packet interception system.
// * Read by ConnectionMixin on every outgoing packet.
// */
//public class PacketState {
//
//    // ── Flags ─────────────────────────────────────────────────────────────────
//
//    /** false → all C2S packets are queued instead of sent. */
//    public static volatile boolean sendEnabled   = true;
//
//    /** true → movement packets are silently dropped (position desync). */
//    public static volatile boolean desyncEnabled = false;
//
//    /**
//     * true while flush() is running so the mixin doesn't re-intercept packets
//     * we are intentionally releasing from the queue.
//     */
//    public static volatile boolean bypassing = false;
//
//    // ── Packet queue ──────────────────────────────────────────────────────────
//
//    private static final List<Packet<?>> held = new ArrayList<>();
//
//    /** Queue a packet (called by the mixin). */
//    public static synchronized void hold(Packet<?> packet) {
//        held.add(packet);
//    }
//
//    /** How many packets are currently held. */
//    public static synchronized int heldCount() {
//        return held.size();
//    }
//
//    // ── Actions ───────────────────────────────────────────────────────────────
//
//    /**
//     * Send all held packets in order, then clear the queue.
//     * Uses ClientCommonNetworkHandler.send() — the same method the mixin hooks.
//     * bypassing=true prevents re-interception during flush.
//     */
//    public static synchronized void flush() {
//        Minecraft mc = Minecraft.getInstance();
//        if (mc.player == null) { held.clear(); return; }
//
//        // ClientPacketListener extends ClientCommonNetworkHandler
//        ClientCommonNetworkHandler handler = mc.player.connection;
//        bypassing = true;
//        try {
//            for (Packet<?> p : new ArrayList<>(held)) {
//                handler.send(p);
//            }
//        } finally {
//            held.clear();
//            bypassing = false;
//        }
//    }
//
//    /** Discard all held packets without sending. */
//    public static synchronized void clear() {
//        held.clear();
//    }
//
//    // ── Intercept decision ────────────────────────────────────────────────────
//
//    /**
//     * Called by ConnectionMixin for every outgoing packet.
//     * Returns true → the mixin should cancel the send and optionally queue it.
//     */
//    public static boolean shouldIntercept(Packet<?> packet) {
//        if (bypassing) return false;
//        if (desyncEnabled && isMovementPacket(packet)) return true;
//        if (!sendEnabled) return true;
//        return false;
//    }
//
//    private static boolean isMovementPacket(Packet<?> packet) {
//        String name = packet.getClass().getSimpleName();
//        return name.startsWith("ServerboundMovePlayer")
//                || name.equals("ServerboundAcceptTeleportationPacket");
//    }
//}