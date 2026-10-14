package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;

/** Holds the forward key for you. */
public class AutoWalk extends Module {

    public static AutoWalk INSTANCE;

    public boolean sprint = false;
    public boolean autoJump = false;
    private boolean held;

    public AutoWalk() {
        super("AutoWalk", "Walks forward automatically.", Category.MOVEMENT);
        INSTANCE = this;
        settings.add(new ToggleButton("Sprint", sprint, v -> sprint = v).withDescription("Also sprint"));
        settings.add(new ToggleButton("Auto Jump", autoJump, v -> autoJump = v).withDescription("Jump over obstacles"));
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (held) {
            mc.options.keyUp.setDown(false);
            mc.options.keySprint.setDown(false);
            held = false;
        }
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.gui.screen() != null) return;
        mc.options.keyUp.setDown(true);
        if (sprint) mc.options.keySprint.setDown(true);
        held = true;
        if (autoJump && p.horizontalCollision && p.onGround()) p.jumpFromGround();
    }
}
