package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;

/** Jumps automatically at the edge of blocks. */
public class Parkour extends Module {

    private boolean sneakCheck = true;
    private boolean pressed;

    public Parkour() {
        super("Parkour", "Jumps automatically when you reach the edge of a block.", Category.MOVEMENT);
        settings.add(new ToggleButton("Not While Sneaking", sneakCheck, v -> sneakCheck = v)
                .withDescription("Don't jump while holding sneak (safe edge walking)"));
    }

    @Override
    public void onDisable() {
        release(Minecraft.getInstance());
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null) return;

        if (pressed) release(mc);

        if (!p.onGround() || p.isPassenger() || p.isInWater()) return;
        if (sneakCheck && p.isShiftKeyDown()) return;
        if (!mc.options.keyUp.isDown()) return;

        AABB below = p.getBoundingBox().inflate(-0.2, 0, -0.2).move(0, -0.5, 0);
        if (mc.level.noCollision(p, below)) {
            mc.options.keyJump.setDown(true);
            pressed = true;
        }
    }

    private void release(Minecraft mc) {
        if (mc.options != null) mc.options.keyJump.setDown(false);
        pressed = false;
    }
}