package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.Hacks.Movement.pathing.GoalBlock;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Navigator; // Sicherstellen, dass der Navigator importiert ist
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class GoTo extends Module {

    private BlockPos targetPos = null;

    public GoTo() {
        super("GoTo", "Professional pathfinding navigation to specific coordinates", Category.MISC);
    }

    public void setTarget(int x, int y, int z) {
        this.targetPos = new BlockPos(x, y, z);
        this.onEnable();
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || targetPos == null) return;

        mc.gui.getChat().addClientSystemMessage(Component.literal("§e[GoTo] Starting navigation..."));

        Navigator.INSTANCE.goTo(mc, new GoalBlock(targetPos));
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        Navigator.INSTANCE.stop(mc);
        targetPos = null;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled) return;

        if (Navigator.INSTANCE.isNavigating()) {
            Navigator.INSTANCE.onTick(client);
        } else {
            client.gui.getChat().addClientSystemMessage(Component.literal("§a[GoTo] Destination reached!"));
            this.onDisable();
        }
    }
}