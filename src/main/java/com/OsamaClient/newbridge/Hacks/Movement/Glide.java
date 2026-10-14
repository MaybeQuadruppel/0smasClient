package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Slow falling: limits the fall speed. */
public class Glide extends Module {

    public static Glide INSTANCE;

    public double fallSpeed = 0.1;
    public boolean sneakToDrop = true;

    public Glide() {
        super("Glide", "Falls slowly like with a feather fall potion.", Category.MOVEMENT);
        INSTANCE = this;
        settings.add(new Slider("Fall Speed", 0.1, 0.5, fallSpeed, 0.01, v -> fallSpeed = v)
                .withDescription("Maximum downward speed in blocks per tick"));
        settings.add(new ToggleButton("Sneak To Drop", sneakToDrop, v -> sneakToDrop = v)
                .withDescription("Hold sneak to fall normally"));
    }

    /**
     * Limits a downward velocity before the physics step of the tick. Gravity (0.08, drag 0.98) is applied
     * afterwards, so the cap is chosen such that the resulting speed equals maxFall. Separated for testing.
     */
    public static double limit(double vy, double maxFall) {
        double cap = Math.min(0.0, 0.08 - maxFall / 0.98);
        return vy < cap ? cap : vy;
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || p.onGround() || p.isPassenger() || p.isFallFlying() || p.isInWater() || p.isInLava() || p.onClimbable()) return;
        if (sneakToDrop && p.isCrouching()) return;
        Vec3 v = p.getDeltaMovement();
        double ny = limit(v.y, fallSpeed);
        if (ny != v.y) {
            p.setDeltaMovement(v.x, ny, v.z);
            p.fallDistance = 0.0f;
        }
    }
}
