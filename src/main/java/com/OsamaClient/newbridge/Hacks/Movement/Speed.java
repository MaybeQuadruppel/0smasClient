package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Simple ground/air speed boost. */
public class Speed extends Module {

    private double multiplier = 1.4;
    private boolean onlyGround = true;

    public Speed() {
        super("Speed", "Makes you run faster.", Category.MOVEMENT);
        settings.add(new Slider("Multiplier", 1.0, 3.0, multiplier, 0.1, v -> multiplier = v)
                .withDescription("Speed relative to normal sprinting"));
        settings.add(new ToggleButton("Only Ground", onlyGround, v -> onlyGround = v)
                .withDescription("Only boost while on the ground (less detectable)"));
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || p.isPassenger() || p.isFallFlying() || p.onClimbable() || p.isInWater()) return;
        if (onlyGround && !p.onGround()) return;

        var o = mc.options;
        boolean moving = o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown();
        if (!moving) return;

        Vec3 dm = p.getDeltaMovement();
        double h = Math.hypot(dm.x, dm.z);
        double cap = 0.28 * multiplier;
        if (h > 0.05 && h < cap) {
            double s = Math.min(cap / h, 1.3);
            p.setDeltaMovement(dm.x * s, dm.y, dm.z * s);
        }
    }
}