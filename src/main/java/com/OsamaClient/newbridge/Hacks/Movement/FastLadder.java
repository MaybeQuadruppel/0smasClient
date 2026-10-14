package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Climb ladders/vines faster. */
public class FastLadder extends Module {

    public static FastLadder INSTANCE;

    public double speed = 0.3;
    public boolean onlyWhenMoving = true;

    public FastLadder() {
        super("FastLadder", "Climbs ladders and vines faster.", Category.MOVEMENT);
        INSTANCE = this;
        settings.add(new Slider("Speed", 0.12, 0.6, speed, 0.01, v -> speed = v)
                .withDescription("Upward speed in blocks per tick (vanilla is 0.12)"));
        settings.add(new ToggleButton("Only When Moving", onlyWhenMoving, v -> onlyWhenMoving = v)
                .withDescription("Only climb faster while a movement key or jump is held"));
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || p.isPassenger() || p.isFallFlying() || !p.onClimbable() || p.isCrouching()) return;
        var o = mc.options;
        boolean pushing = o.keyUp.isDown() || o.keyJump.isDown();
        if (onlyWhenMoving && !pushing) return;
        if (!p.horizontalCollision && !o.keyJump.isDown()) return;
        Vec3 v = p.getDeltaMovement();
        if (v.y < speed) p.setDeltaMovement(v.x, speed, v.z);
    }
}
