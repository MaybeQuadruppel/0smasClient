package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class Spider extends Module {

    private final Minecraft mc = Minecraft.getInstance();
    public double climbSpeed = 0.2;

    public Spider() {
        super("Spider","Lets you climb walls", Category.MOVEMENT);

        this.settings.add(new Slider("Climb Speed", 0.1, 1.0, climbSpeed, val -> climbSpeed = val));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null) return;
        if (!client.player.horizontalCollision) return;
        Vec3 velocity = client.player.getDeltaMovement();
        if (velocity.y < climbSpeed) {
            client.player.setDeltaMovement(velocity.x, climbSpeed, velocity.z);
            client.player.fallDistance = 0;
        }
    }
}