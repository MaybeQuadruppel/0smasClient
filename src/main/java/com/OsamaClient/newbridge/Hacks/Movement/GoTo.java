package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.GoalBlock;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Navigator;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.PathRenderer;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class GoTo extends Module {

    private BlockPos targetPos = null;
    private boolean navigationFinished;
    private boolean navigationSucceeded;
    private int pathColor = 0xFF31C9FF;
    private int targetColor = 0xFFFFB347;
    private final PathRenderer pathRenderer = new PathRenderer();

    public GoTo() {
        super("GoTo", "Professional pathfinding navigation to specific coordinates", Category.MISC);
        this.settings.add(new ColorPicker("Path Color", pathColor, color -> pathColor = color));
        this.settings.add(new ColorPicker("Target Color", targetColor, color -> targetColor = color));
        EntryPoint.EVENT_BUS.subscribe(this);
    }

    public void setTarget(int x, int y, int z) {
        this.targetPos = new BlockPos(x, y, z);
        this.enabled = true;
        this.onEnable();
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || targetPos == null) {
            this.enabled = false; // nichts zu tun -> Modul nicht "an" hängen lassen
            return;
        }

        navigationFinished = false;
        navigationSucceeded = false;
        mc.player.sendSystemMessage(Component.literal("§e[GoTo] Starting navigation..."));
        Navigator.INSTANCE.goTo(mc, Navigator.OWNER_GOTO, Navigator.PRIORITY_GOTO,
                new GoalBlock(targetPos), success -> {
                    navigationFinished = true;
                    navigationSucceeded = success;
                });
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        Navigator.INSTANCE.cancel(mc, Navigator.OWNER_GOTO);
        targetPos = null;
        this.enabled = false;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled) return;

        if (navigationFinished && targetPos != null) {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal(navigationSucceeded
                        ? "§a[GoTo] Destination reached!"
                        : "§c[GoTo] Could not reach destination."));
            }
            this.onDisable();
        } else if (targetPos == null) {
            this.enabled = false;
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (enabled && targetPos != null) {
            pathRenderer.render(event, Navigator.INSTANCE.getRemainingPath(Navigator.OWNER_GOTO),
                    targetPos, pathColor, targetColor);
        }
    }
}
