package com.OsamaClient.newbridge.Hacks.Dupeing;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;

/**
 * GuiDesync: tells the server the container was closed while your client keeps it open. One-shot - enable it (key bind)
 * while a container screen is open. Port of the Autism client's desync action (GPL-3.0).
 */
public class GuiDesync extends Module {

    public static GuiDesync INSTANCE;
    public boolean closeScreen = false;
    public int fired;

    public GuiDesync() {
        super("GuiDesync", "Sends the container close packet but keeps the screen open.", Category.MISC);
        INSTANCE = this;
        settings.add(new ToggleButton("Close Screen", closeScreen, v -> closeScreen = v).withDescription("Also close the screen on your side."));
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null && mc.gui.screen() != null && mc.player.containerMenu != null) {
            PacketHub.sendNow(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
            fired++;
            Notify.bar("GUI desynced");
            if (closeScreen) mc.gui.setScreen(null);
        } else {
            Notify.bar("Open a container screen first");
        }
        enabled = false;
    }
}
