package com.qdrppl.newbridge.Hacks.Misc;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeLag extends Module {

    public final CopyOnWriteArrayList<Packet<?>> packetQueue = new CopyOnWriteArrayList<>();

    private double tickLimit = 10.0;
    private int tickCounter = 0;

    public FakeLag() {
        super("FakeLag", "Delays your movement packets to simulate lag.", Category.MISC);
        this.settings.add(new Slider("Tick Limit", 1.0, 40.0, tickLimit, val -> tickLimit = val));
    }

    @Override
    public void onEnable() {
        super.onEnable();
        packetQueue.clear();
        tickCounter = 0;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return;

        tickCounter++;

        if (tickCounter >= tickLimit) {
            sendPackets(client);
            tickCounter = 0;
        }
    }

    @Override
    public void onDisable() {
        if (Minecraft.getInstance().getConnection() != null) {
            sendPackets(Minecraft.getInstance());
        }
        tickCounter = 0;
        super.onDisable();
    }

    private void sendPackets(Minecraft client) {
        if (client.getConnection() == null || packetQueue.isEmpty()) return;

        try {
            for (Packet<?> packet : packetQueue) {
                client.getConnection().getConnection().send(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        packetQueue.clear();
    }
}