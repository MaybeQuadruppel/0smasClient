package com.qdrppl.newbridge.Hacks.Movement;

import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;

public class AutoSprint extends Module {
    public AutoSprint() {
        super("AutoSprint", "Sprints automatically when moving forward", Category.MOVEMENT);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null) return;

        if (client.player.input.hasForwardImpulse() && !client.player.isCrouching()&& !client.player.horizontalCollision) {
            client.player.setSprinting(true);
        }
    }
}