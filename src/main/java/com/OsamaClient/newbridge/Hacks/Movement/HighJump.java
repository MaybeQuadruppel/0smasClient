package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Boosts the jump impulse. */
public class HighJump extends Module {

    private double multiplier = 2.0;
    private boolean boosted, wasGround;

    public HighJump() {
        super("HighJump", "Makes you jump higher.", Category.MOVEMENT);
        settings.add(new Slider("Multiplier", 1.0, 5.0, multiplier, 0.25, v -> multiplier = v)
                .withDescription("Jump strength multiplier"));
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null) return;
        boolean ground = p.onGround();
        Vec3 dm = p.getDeltaMovement();
        // the tick the player leaves the ground upwards = the jump (the velocity is already reduced by one tick of gravity)
        if (wasGround && !ground && !boosted && dm.y > 0.1 && dm.y < 0.7 && !p.isInLiquid() && !p.onClimbable()) {
            p.setDeltaMovement(dm.x, dm.y * multiplier, dm.z);
            boosted = true;
        }
        if (ground) boosted = false;
        wasGround = ground;
    }
}
