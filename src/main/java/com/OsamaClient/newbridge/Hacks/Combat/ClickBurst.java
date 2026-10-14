package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.SwingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * One physical click starts a burst: {@code clicks} extra clicks at {@code cps} clicks per second
 * (more than one per tick when the CPS is above 20).
 */
public class ClickBurst extends Module {

    public static ClickBurst INSTANCE;

    public double cps = 20.0;
    public double clicks = 10.0;
    public boolean rightClick = false;
    public boolean stopOnRelease = false;

    private boolean wasDown;
    private int remaining;
    private double budget;
    private int sent;

    public ClickBurst() {
        super("ClickBurst", "One click starts a burst of fast clicks (any CPS, any amount).", Category.COMBAT);
        INSTANCE = this;
        settings.add(new ModeButton("Button", List.of("Left", "Right"), 0, m -> rightClick = m.equals("Right"))
                .withDescription("Which mouse button starts and repeats the clicks"));
        settings.add(new Slider("CPS", 1.0, 100.0, cps, 1.0, v -> cps = v)
                .withDescription("Clicks per second of the burst"));
        settings.add(new Slider("Clicks", 1.0, 500.0, clicks, 1.0, v -> clicks = v)
                .withDescription("How many extra clicks one click triggers"));
        settings.add(new ToggleButton("Stop On Release", stopOnRelease, v -> stopOnRelease = v)
                .withDescription("Cancel the burst as soon as the button is released"));
    }

    public int clicksSent() { return sent; }

    public int remaining() { return remaining; }

    @Override
    public void onEnable() {
        wasDown = false;
        remaining = 0;
        budget = 0;
        sent = 0;
    }

    @Override
    public void onDisable() { remaining = 0; }

    /** Starts a burst (same as one physical click). */
    public void trigger() {
        remaining = (int) Math.round(clicks);
        budget = 1.0;
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) {
            wasDown = false;
            return;
        }
        boolean down = (rightClick ? mc.options.keyUse : mc.options.keyAttack).isDown();
        if (down && !wasDown) trigger();
        if (!down && wasDown && stopOnRelease) remaining = 0;
        wasDown = down;
        if (remaining <= 0) return;
        budget += cps / 20.0;
        while (budget >= 1.0 && remaining > 0) {
            budget -= 1.0;
            remaining--;
            click(mc);
            sent++;
        }
        if (remaining == 0) budget = 0;
    }

    private void click(Minecraft mc) {
        var p = mc.player;
        HitResult hit = mc.hitResult;
        if (!rightClick) {
            if (hit instanceof EntityHitResult e) mc.gameMode.attack(p, e.getEntity());
            SwingUtil.swing(p, InteractionHand.MAIN_HAND);
            return;
        }
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) {
            InteractionResult r = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, b);
            if (r.consumesAction()) { SwingUtil.swing(p, InteractionHand.MAIN_HAND); return; }
        } else if (hit instanceof EntityHitResult e) {
            InteractionResult r = mc.gameMode.interact(p, e.getEntity(), e, InteractionHand.MAIN_HAND);
            if (r.consumesAction()) { SwingUtil.swing(p, InteractionHand.MAIN_HAND); return; }
        }
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
    }
}
