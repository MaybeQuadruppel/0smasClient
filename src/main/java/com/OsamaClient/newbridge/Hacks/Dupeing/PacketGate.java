package com.OsamaClient.newbridge.Hacks.Dupeing;

import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.TextBox;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.List;
import java.util.function.Predicate;

/**
 * PacketGate: holds (Delay) or drops (Cancel) selected outgoing packets while enabled. Switching it off releases the
 * held packets in order - the classic "hold, act, release" trick used for timing based exploits.
 * Based on the packet gate idea of the Autism client (GPL-3.0).
 */
public class PacketGate extends Module {

    public static PacketGate INSTANCE;
    private static final String ID = "packetgate";

    public String mode = "Delay";
    public String preset = "GUI";
    public String custom = "ContainerClick,ContainerClose,ContainerButtonClick,SelectBundleItem";
    public boolean flushOnDisable = true;
    public double autoFlushTicks = 0;
    private int age;

    public PacketGate() {
        super("PacketGate", "Holds or drops selected outgoing packets; releases them when you turn it off.", Category.MISC);
        INSTANCE = this;
        settings.add(new ModeButton("Mode", List.of("Delay", "Cancel"), 0, v -> { mode = v; rebuild(); })
                .withDescription("Delay = hold packets until released. Cancel = drop them."));
        settings.add(new ModeButton("Packets", List.of("GUI", "Movement", "Interaction", "All", "Custom"), 0, v -> { preset = v; rebuild(); })
                .withDescription("Which packets the gate catches."));
        settings.add(new TextBox("Custom List", custom, v -> { custom = v; rebuild(); })
                .withDescription("Comma separated packet names for the Custom preset, e.g. ContainerClick,UseItem."));
        settings.add(new ToggleButton("Flush On Disable", flushOnDisable, v -> flushOnDisable = v)
                .withDescription("Send the held packets when the gate closes (otherwise they are discarded)."));
        settings.add(new Slider("Auto Flush", 0, 200, autoFlushTicks, 5, v -> autoFlushTicks = v)
                .withDescription("Release the held packets every N ticks while enabled (0 = never)."));
    }

    public Predicate<Packet<?>> filter() {
        return switch (preset) {
            case "Movement" -> p -> p instanceof ServerboundMovePlayerPacket;
            case "Interaction" -> PacketHub.byNames("UseItem,UseItemOn,Interact,PlayerAction,Swing,PlayerCommand,Punch,SetCarriedItem");
            case "All" -> p -> true;
            case "Custom" -> PacketHub.byNames(custom);
            default -> PacketHub.byNames("ContainerClick,ContainerClose,ContainerButtonClick,SelectBundleItem,PlaceRecipe,ContainerSlotStateChanged,RenameItem,SelectTrade");
        };
    }

    private void rebuild() {
        if (enabled) PacketHub.open(ID, "Cancel".equals(mode) ? PacketHub.Mode.CANCEL : PacketHub.Mode.DELAY, filter());
    }

    @Override
    public void onEnable() {
        age = 0;
        rebuild();
    }

    @Override
    public void onDisable() {
        PacketHub.close(ID);
        int n = flushOnDisable ? PacketHub.flush() : PacketHub.discard();
        if (n > 0 && Minecraft.getInstance().player != null) Notify.bar((flushOnDisable ? "Sent " : "Discarded ") + n + " packets");
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        if (autoFlushTicks > 0 && ++age >= autoFlushTicks) {
            age = 0;
            PacketHub.flush();
        }
    }
}
