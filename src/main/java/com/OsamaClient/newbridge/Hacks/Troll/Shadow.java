package com.OsamaClient.newbridge.Hacks.Troll;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Sticks to the nearest player: looks at them and follows. */
public class Shadow extends Module {

    private double range = 30.0;
    private double distance = 2.5;
    private double smoothing = 2.0;
    private boolean sprint = true;

    private boolean controlling;

    public Shadow() {
        super("Shadow", "Follows the nearest player around and stares at them.", Category.TROLL);

        settings.add(new Slider("Range", 5.0, 60.0, range, 1.0, v -> range = v)
                .withDescription("Search range for a player to follow"));
        settings.add(new Slider("Distance", 1.0, 8.0, distance, 0.5, v -> distance = v)
                .withDescription("How close to stay"));
        settings.add(new Slider("Smoothing", 1.0, 10.0, smoothing, 0.5, v -> smoothing = v)
                .withDescription("Head turn smoothing"));
        settings.add(new ToggleButton("Sprint", sprint, v -> sprint = v)
                .withDescription("Sprint while following"));
    }

    @Override
    public void onDisable() {
        stop(Minecraft.getInstance());
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        Player target = null;
        double best = range;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            double d = mc.player.distanceTo(p);
            if (d <= best) {
                best = d;
                target = p;
            }
        }
        if (target == null) {
            stop(mc);
            return;
        }

        TrollUtil.lookAt(mc, new Vec3(target.getX(), target.getEyeY(), target.getZ()), (float) smoothing);

        var o = mc.options;
        boolean far = best > distance;
        o.keyUp.setDown(far);
        o.keySprint.setDown(far && sprint);
        o.keyJump.setDown(far && mc.player.horizontalCollision);
        controlling = true;
    }

    private void stop(Minecraft mc) {
        if (controlling) {
            TrollUtil.releaseKeys(mc);
            controlling = false;
        }
    }
}