package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * Blink: suspends your outgoing movement / action packets so the server keeps seeing you at the old spot; they are
 * released when you turn it off. Based on the Blink module of the Autism client (GPL-3.0), built on the PacketHub.
 */
public class Blink extends Module {

    public static Blink INSTANCE;
    private static final String ID = "blink";

    public boolean holdMovement = true;
    public boolean holdActions = true;
    public boolean autoReset = false;
    public double resetAfter = 50;
    /** Where the server thinks we are while blinking. */
    public Vec3 serverPos;
    private int age;

    public Blink() {
        super("Blink", "Holds your packets so the server still sees you at the old position. Released when disabled.", Category.MOVEMENT);
        INSTANCE = this;
        settings.add(new ToggleButton("Hold Movement", holdMovement, v -> { holdMovement = v; rebuild(); }).withDescription("Delay movement packets."));
        settings.add(new ToggleButton("Hold Actions", holdActions, v -> { holdActions = v; rebuild(); }).withDescription("Delay attack / interact / place packets."));
        settings.add(new ToggleButton("Auto Reset", autoReset, v -> autoReset = v).withDescription("Release the held packets every N ticks."));
        settings.add(new Slider("Reset After", 1, 100, resetAfter, 1, v -> resetAfter = v).withDescription("Ticks between automatic releases."));
    }

    private Predicate<Packet<?>> filter() {
        Predicate<Packet<?>> actions = PacketHub.byNames("UseItem,UseItemOn,Interact,PlayerAction,Punch,PlayerCommand,SetCarriedItem");
        return p -> (holdMovement && p instanceof ServerboundMovePlayerPacket) || (holdActions && actions.test(p));
    }

    private void rebuild() {
        if (enabled) PacketHub.open(ID, PacketHub.Mode.DELAY, filter());
    }

    @Override
    public void onEnable() {
        age = 0;
        var p = Minecraft.getInstance().player;
        serverPos = p == null ? null : p.position();
        rebuild();
    }

    @Override
    public void onDisable() {
        PacketHub.close(ID);
        int n = PacketHub.flush();
        serverPos = null;
        if (n > 0 && Minecraft.getInstance().player != null) Notify.bar("Blink released " + n + " packets");
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        if (autoReset && ++age >= resetAfter) {
            age = 0;
            PacketHub.flush();
            serverPos = mc.player.position();
        }
    }
}
