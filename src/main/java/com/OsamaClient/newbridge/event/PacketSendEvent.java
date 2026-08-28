package com.OsamaClient.newbridge.event;

import net.minecraft.network.protocol.Packet;

public class PacketSendEvent {

    private final Packet<?> packet;
    private boolean cancelled;

    public PacketSendEvent(Packet<?> packet) {
        this.packet = packet;
        this.cancelled = false;
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}