package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Sneaks automatically right before you would walk off an edge. */
public class SafeWalk extends Module {

    public static SafeWalk INSTANCE;

    public double lookahead = 3;
    private boolean forced;

    public SafeWalk() {
        super("SafeWalk", "Sneaks at block edges so you never fall off.", Category.MOVEMENT);
        INSTANCE = this;
        settings.add(new Slider("Lookahead", 1, 6, lookahead, 1, v -> lookahead = v).withDescription("How many ticks of movement to look ahead."));
    }

    @Override
    public void onDisable() { release(Minecraft.getInstance()); }

    private void release(Minecraft mc) {
        if (forced && mc.options != null) mc.options.keyShift.setDown(false);
        forced = false;
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) return;
        if (p.getAbilities().flying || !p.onGround()) { release(mc); return; }
        Vec3 v = p.getDeltaMovement();
        AABB next = p.getBoundingBox().move(v.x * lookahead, -0.6, v.z * lookahead);
        boolean edge = mc.level.noCollision(p, next);
        boolean moving = v.x * v.x + v.z * v.z > 1.0E-4;
        if (edge && moving) {
            mc.options.keyShift.setDown(true);
            forced = true;
        } else {
            release(mc);
        }
    }
}
