package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.Hacks.Troll.TrollUtil;
import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/** Aims bows/crossbows at the nearest player with gravity and movement prediction. */
public class BowAimbot extends Module {

    private double range = 60.0;
    private double smoothing = 2.0;
    private boolean predict = true;
    private boolean needLineOfSight = true;

    public BowAimbot() {
        super("BowAimbot", "Aims your bow at the nearest player (with lead and drop).", Category.COMBAT);

        settings.add(new Slider("Range", 10.0, 120.0, range, 5.0, v -> range = v)
                .withDescription("Maximum target distance"));
        settings.add(new Slider("Smoothing", 1.0, 10.0, smoothing, 0.5, v -> smoothing = v)
                .withDescription("1 = instant snap, higher = smoother"));
        settings.add(new ToggleButton("Predict", predict, v -> predict = v)
                .withDescription("Lead moving targets"));
        settings.add(new ToggleButton("Line of Sight", needLineOfSight, v -> needLineOfSight = v)
                .withDescription("Only target players you can see"));
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (!mc.player.isUsingItem()) return;

        Item item = mc.player.getUseItem().getItem();
        boolean bow = item instanceof BowItem;
        if (!bow && !(item instanceof CrossbowItem)) return;

        Player target = null;
        double best = range;
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive()) continue;
            if (needLineOfSight && !mc.player.hasLineOfSight(p)) continue;
            double d = mc.player.distanceTo(p);
            if (d <= best) {
                best = d;
                target = p;
            }
        }
        if (target == null) return;

        float power = bow ? BowItem.getPowerForTime(mc.player.getTicksUsingItem()) : 1.0f;
        double speed = Math.max(1.5, power * 3.0);
        double t = best / speed; // flight time in ticks

        Vec3 aim = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        if (predict) {
            aim = aim.add((target.getX() - target.xo) * t, 0, (target.getZ() - target.zo) * t);
        }
        aim = aim.add(0, 0.5 * 0.05 * t * t, 0); // arrow drop compensation

        TrollUtil.lookAt(mc, aim, (float) smoothing);
    }
}