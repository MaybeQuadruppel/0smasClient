package com.OsamaClient.newbridge.Hacks.Dupeing;

import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.Notify;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;

import java.util.List;

/**
 * PacketBurst: one-shot - sends the same packet N times back to back. Modes follow the Autism client's packet burst
 * action (GPL-3.0).
 */
public class PacketBurst extends Module {

    public static PacketBurst INSTANCE;
    public String mode = "Container Click";
    public double count = 10;
    public double slot = 0;
    public double button = 0;
    public boolean flushFirst = false;
    public int lastSent;

    public PacketBurst() {
        super("PacketBurst", "Sends one packet type many times in a row (one-shot, bind it to a key).", Category.MISC);
        INSTANCE = this;
        settings.add(new ModeButton("Packet", List.of("Container Click", "Bundle Select", "Use Item", "Swing", "Set Carried Item", "Close Container"), 0, v -> mode = v)
                .withDescription("Which packet to send."));
        settings.add(new Slider("Count", 1, 2000, count, 1, v -> count = v).withDescription("How many packets."));
        settings.add(new Slider("Slot", 0, 45, slot, 1, v -> slot = v).withDescription("Container/menu slot (clicks, bundle select) or hotbar slot."));
        settings.add(new Slider("Button", 0, 8, button, 1, v -> button = v).withDescription("Mouse button for container clicks."));
        settings.add(new ToggleButton("Flush First", flushFirst, v -> flushFirst = v).withDescription("Release held packets before bursting."));
    }

    private Packet<?> make(Minecraft mc) {
        var p = mc.player;
        return switch (mode) {
            case "Container Click" -> p.containerMenu == null ? null : new ServerboundContainerClickPacket(
                    p.containerMenu.containerId, p.containerMenu.getStateId(), (short) slot, (byte) button,
                    ContainerInput.PICKUP, new Int2ObjectOpenHashMap<>(), HashedStack.EMPTY);
            case "Bundle Select" -> new ServerboundSelectBundleItemPacket((int) slot, 0);
            case "Use Item" -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, 0, p.getYRot(), p.getXRot());
            case "Swing" -> ServerboundPunchPacket.INSTANCE;
            case "Set Carried Item" -> new ServerboundSetCarriedItemPacket(Math.max(0, Math.min(8, (int) slot)));
            case "Close Container" -> new ServerboundContainerClosePacket(p.containerMenu == null ? 0 : p.containerMenu.containerId);
            default -> null;
        };
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        enabled = false;
        if (mc.player == null || mc.getConnection() == null) return;
        if (flushFirst) PacketHub.flush();
        int n = Math.max(1, Math.min(2000, (int) count));
        int sent = 0;
        for (int i = 0; i < n; i++) {
            Packet<?> pk = make(mc);
            if (pk == null) break;
            mc.getConnection().send(pk);
            sent++;
        }
        lastSent = sent;
        Notify.bar("Burst: " + sent + " x " + mode);
    }
}
