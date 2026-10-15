package com.OsamaClient.newbridge.Hacks.Player;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Bounces you back up when you are about to fall out of the world. */
public class AntiVoid extends Module {

    public static AntiVoid INSTANCE;

    public double margin = 6;
    public double boost = 0.9;

    public AntiVoid() {
        super("AntiVoid", "Launches you upward just above the bottom of the world.", Category.PLAYER);
        INSTANCE = this;
        settings.add(new Slider("Margin", 2, 20, margin, 1, v -> margin = v).withDescription("Blocks above the world bottom where it kicks in."));
        settings.add(new Slider("Boost", 0.4, 2.0, boost, 0.1, v -> boost = v).withDescription("Upward speed."));
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null || p.getAbilities().flying || p.isSpectator()) return;
        if (p.getY() < mc.level.getMinY() + margin && p.getDeltaMovement().y < 0) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, boost, v.z);
            p.resetFallDistance();
        }
    }
}
