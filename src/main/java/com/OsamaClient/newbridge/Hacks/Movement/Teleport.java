package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class Teleport extends Module {
    public static Teleport INSTANCE;

    public Teleport() {
        super("Teleport", "Instantly teleports you to target coordinates", Category.MOVEMENT);
        INSTANCE = this;
    }

    public static Teleport getInstance() {
        if (INSTANCE == null) INSTANCE = new Teleport();
        return INSTANCE;
    }

    public void setTarget(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        mc.player.setPos(x, y, z);

        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                x, y, z,
                mc.player.onGround(),
                mc.player.horizontalCollision
        ));

        mc.player.sendSystemMessage(Component.literal("§a[TP] Teleported to X: " + x + " Y: " + y + " Z: " + z));
        this.enabled = false;
    }
}