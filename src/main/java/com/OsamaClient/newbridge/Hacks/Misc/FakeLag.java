package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.List;
import java.util.function.Predicate;

/**
 * FakeLag (also the Blink of the Autism client, GPL-3.0): holds your movement packets so the server sees you late.
 * Interval = release every N ticks, Hold = keep everything until you turn the module off.
 * Built on the PacketHub; the server only gets your newest position on release.
 */
public class FakeLag extends Module {

    public static FakeLag INSTANCE;
    private static final String ID = "fakelag";

    public String mode = "Interval";
    public double tickLimit = 10.0;
    public boolean holdActions = false;
    private int tickCounter;

    public FakeLag() {
        super("FakeLag", "Delays your movement packets to simulate lag. Hold mode = Blink (released when disabled).", Category.MISC);
        INSTANCE = this;
        settings.add(new ModeButton("Mode", List.of("Interval", "Hold"), 0, v -> mode = v)
                .withDescription("Interval = release every N ticks. Hold = Blink: keep packets until you disable the module."));
        settings.add(new Slider("Tick Limit", 1.0, 40.0, tickLimit, 1.0, v -> tickLimit = v)
                .withDescription("Interval mode: ticks between releases."));
        settings.add(new ToggleButton("Hold Actions", holdActions, v -> { holdActions = v; rebuild(); })
                .withDescription("Also delay attack / interact / place packets."));
    }

    private Predicate<Packet<?>> filter() {
        Predicate<Packet<?>> actions = PacketHub.byNames("UseItem,UseItemOn,Interact,PlayerAction,Punch,PlayerCommand,SetCarriedItem");
        return p -> p instanceof ServerboundMovePlayerPacket || (holdActions && actions.test(p));
    }

    private void rebuild() {
        if (enabled) PacketHub.open(ID, PacketHub.Mode.DELAY, filter());
    }

    @Override
    public void onEnable() {
        tickCounter = 0;
        rebuild();
    }

    @Override
    public void onDisable() {
        PacketHub.close(ID);
        int n = PacketHub.flush();
        tickCounter = 0;
        if (n > 0 && Minecraft.getInstance().player != null) Notify.bar("FakeLag released " + n + " packets");
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.getConnection() == null) return;
        if (mode.equals("Hold")) return;
        if (++tickCounter >= tickLimit) {
            tickCounter = 0;
            PacketHub.flush(true);
        }
    }
}
