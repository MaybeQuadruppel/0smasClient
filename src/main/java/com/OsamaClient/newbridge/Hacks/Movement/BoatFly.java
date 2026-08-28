package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class BoatFly extends Module {

    public double speed = 1.0;
    public double verticalSpeed = 0.5;
    public boolean lockRotation = true;

    public BoatFly() {
        super("BoatFly", "Allows you to fly while riding a boat or other entities.", Category.MOVEMENT);

        this.settings.add(new Slider("Speed", 0.1, 5.0, speed, val -> speed = val)
                .withDescription("Your horizontal flying speed."));

        this.settings.add(new Slider("V-Speed", 0.1, 5.0, verticalSpeed, val -> verticalSpeed = val)
                .withDescription("Your vertical flying speed (Up/Down)."));

        this.settings.add(new ToggleButton("Lock Rotation", lockRotation, val -> lockRotation = val)
                .withDescription("Forces the boat to face the direction you are looking."));
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null) return;
        Entity vehicle = client.player.getVehicle();
        if (vehicle == null) return;

        double moveY = 0.0;

        if (client.options.keyJump.isDown()) {
            moveY += verticalSpeed;
        }
        else if (client.options.keySprint.isDown()) {
            moveY -= verticalSpeed;
        }

        Vec3 wishDir = getMovementDirection(client, client.player.getYRot());
        double moveX = wishDir.x * speed;
        double moveZ = wishDir.z * speed;

        vehicle.setDeltaMovement(moveX, moveY, moveZ);

        if (lockRotation) {
            vehicle.setYRot(client.player.getYRot());
        }
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.getVehicle() != null) {
            client.player.getVehicle().setDeltaMovement(0, 0, 0);
        }
    }

    private Vec3 getMovementDirection(Minecraft client, float yaw) {
        double forward = 0;
        double strafe = 0;

        if (client.options.keyUp.isDown()) forward += 1;
        if (client.options.keyDown.isDown()) forward -= 1;
        if (client.options.keyLeft.isDown()) strafe += 1;
        if (client.options.keyRight.isDown()) strafe -= 1;

        if (forward == 0 && strafe == 0) {
            return Vec3.ZERO;
        }

        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        return new Vec3(
                forward * -sin + strafe * cos,
                0,
                forward * cos + strafe * sin
        ).normalize();
    }
}