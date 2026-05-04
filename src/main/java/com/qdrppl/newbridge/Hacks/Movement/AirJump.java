package com.qdrppl.newbridge.Hacks.Movement;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class AirJump extends Module {

    // Einstellungen
    public double maxJumps = 2.0;
    public double jumpHeight = 0.42;


    private int jumpCount = 0;
    private boolean lastTickJumpPressed = false;

    public AirJump() {
        super("AirJump","Lets you Jump in the Air", Category.MOVEMENT);
        this.settings.add(new Slider("Max Jumps", 1.0, 20.0, maxJumps, val -> maxJumps = val));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null) return;

        if (client.player.onGround()) {
            jumpCount = 0;
        } else {
            boolean isJumpPressed = client.options.keyJump.isDown();
            if (isJumpPressed && !lastTickJumpPressed && jumpCount < (int)maxJumps) {
                Vec3 velocity = client.player.getDeltaMovement();
                client.player.setDeltaMovement(velocity.x, jumpHeight, velocity.z);
                jumpCount++;

                client.player.fallDistance = 0;
            }

            lastTickJumpPressed = isJumpPressed;
        }
    }
}