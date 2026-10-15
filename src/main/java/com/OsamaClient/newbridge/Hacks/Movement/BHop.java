package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;

/** Bunny hop: jumps again the moment you land while moving. */
public class BHop extends Module {

    public static BHop INSTANCE;

    public boolean onlyForward = true;
    public boolean sprint = true;

    public BHop() {
        super("BHop", "Jumps automatically while you move.", Category.MOVEMENT);
        INSTANCE = this;
        settings.add(new ToggleButton("Only Forward", onlyForward, v -> onlyForward = v).withDescription("Only hop while holding forward."));
        settings.add(new ToggleButton("Sprint", sprint, v -> sprint = v).withDescription("Also sprint while hopping."));
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.gui.screen() != null || p.isInWater() || p.onClimbable() || p.isShiftKeyDown()) return;
        boolean moving = onlyForward ? mc.options.keyUp.isDown()
                : mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        if (!moving) return;
        if (sprint) mc.options.keySprint.setDown(true);
        if (p.onGround()) p.jumpFromGround();
    }
}
