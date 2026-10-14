package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.SwingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Prevents AFK kicks: small head movement, swing and optional jump at a fixed interval. */
public class AntiAfk extends Module {

    public static AntiAfk INSTANCE;

    public double intervalSeconds = 20.0;
    public boolean rotate = true;
    public boolean swing = true;
    public boolean jump = false;

    private int ticksLeft;
    private int actions;
    private boolean flip;
    private boolean wasIdle;

    public AntiAfk() {
        super("AntiAFK", "Moves a little from time to time so servers don't kick you for idling.", Category.MISC);
        INSTANCE = this;
        settings.add(new Slider("Interval (s)", 1.0, 120.0, intervalSeconds, 1.0, v -> intervalSeconds = v)
                .withDescription("Seconds of inactivity between two actions"));
        settings.add(new ToggleButton("Rotate", rotate, v -> rotate = v).withDescription("Wiggle the head by a few degrees"));
        settings.add(new ToggleButton("Swing", swing, v -> swing = v).withDescription("Swing the main hand"));
        settings.add(new ToggleButton("Jump", jump, v -> jump = v).withDescription("Jump (only while standing on the ground)"));
    }

    public int actions() { return actions; }

    @Override
    public void onEnable() {
        ticksLeft = interval();
        actions = 0;
    }

    private int interval() { return Math.max(20, (int) (intervalSeconds * 20)); }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.gui.screen() != null) return;
        var o = mc.options;
        boolean active = o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown()
                || o.keyJump.isDown() || o.keyAttack.isDown() || o.keyUse.isDown();
        if (active) { ticksLeft = interval(); return; }
        if (--ticksLeft > 0) return;
        ticksLeft = interval();
        actions++;
        flip = !flip;
        if (rotate) {
            p.setYRot(p.getYRot() + (flip ? 3f : -3f));
            p.setXRot(Math.max(-90f, Math.min(90f, p.getXRot() + (flip ? 1f : -1f))));
        }
        if (swing) SwingUtil.swing(p, InteractionHand.MAIN_HAND);
        if (jump && p.onGround()) p.jumpFromGround();
    }
}
