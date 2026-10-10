package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Boosts the jump impulse. */
public class HighJump extends Module {

    private double multiplier = 2.0;
    private boolean boosted;

    public HighJump() {
        super("HighJump", "Makes you jump higher.", Category.MOVEMENT);
        settings.add(new Slider("Multiplier", 1.0, 5.0, multiplier, 0.25, v -> multiplier = v)
                .withDescription("Jump strength multiplier"));
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null) return;
        if (p.onGround()) {
            boosted = false;
            return;
        }
        Vec3 dm = p.getDeltaMovement();
        // the tick right after a normal jump has y velocity of about 0.42
        if (!boosted && dm.y > 0.40 && dm.y < 0.45) {
            p.setDeltaMovement(dm.x, dm.y * multiplier, dm.z);
            boosted = true;
        }
    }
}