package com.qdrppl.newbridge.Hacks.Movement;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;
import com.qdrppl.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

public class Flight extends Module {

    public double speed = 0.1;
    public boolean verticalSpeedMatch = false;
    public boolean antiKick = true;
    public int delay = 20;

    private int delayLeft = 20;

    public enum Mode {
        VANILLA, VELOCITY
    }
    public Mode flightMode = Mode.VELOCITY;

    public Flight() {
        super("Flight","Go like a Plane...", Category.MOVEMENT);
        this.settings.add(new Slider("Speed", 0.05, 1.0, speed, val -> speed = val));
        this.settings.add(new ToggleButton("V-Match", verticalSpeedMatch, val -> verticalSpeedMatch = val));
        this.settings.add(new ToggleButton("Anti-Kick", antiKick, val -> antiKick = val));
        this.settings.add(new Slider("Kick Delay", 10, 100, delay, val -> delay = val.intValue()));
    }

    @Override
    public void onEnable() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && flightMode == Mode.VANILLA) {
            client.player.getAbilities().mayfly = true;
            client.player.getAbilities().flying = true;
        }
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.getAbilities().flying = false;

            if (!client.player.isCreative() && !client.player.isSpectator()) {
                client.player.getAbilities().mayfly = false;
            }

            client.player.fallDistance = 0;

            client.player.setDeltaMovement(0, 0, 0);

        }
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null) return;

        client.player.fallDistance = 0;

        if (delayLeft > 0) delayLeft--;

        if (flightMode == Mode.VELOCITY) {
            handleVelocityMode(client);
        } else {
            handleVanillaMode(client);
        }
    }

    private void handleVelocityMode(Minecraft client) {

        client.player.fallDistance = 0;


        client.player.setOnGround(true);

        double moveX = 0, moveY = 0, moveZ = 0;
        float yaw = client.player.getYRot();
        double vSpeed = speed * (verticalSpeedMatch ? 10.0 : 5.0);

        if (client.options.keyJump.isDown()) {
            moveY = vSpeed;
        }
        else if (client.options.keyShift.isDown()) {
            moveY = -vSpeed;
            sendOnGroundPacket(client);
        }
        else {
            if (antiKick && delayLeft <= 0) {
                moveY = -0.04;
                delayLeft = delay;
                sendOnGroundPacket(client);
            }
        }

        Vec3 wishDir = getMovementDirection(client, yaw);
        moveX = wishDir.x * speed * 10.0;
        moveZ = wishDir.z * speed * 10.0;

        client.player.setDeltaMovement(moveX, moveY, moveZ);
    }

    private void sendOnGroundPacket(Minecraft client) {
        if (client.getConnection() != null) {
            client.getConnection().send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(true, client.player.horizontalCollision));
        }
    }

    private void handleVanillaMode(Minecraft client) {
        client.player.getAbilities().setFlyingSpeed((float) speed);
        client.player.getAbilities().flying = true;

        if (antiKick && delayLeft <= 0) {
            Vec3 pos = client.player.position();
            client.player.setPos(pos.x, pos.y - 0.032, pos.z);
            delayLeft = delay;
        }
    }

    private Vec3 getMovementDirection(Minecraft client, float yaw) {
        double forward = 0, strafe = 0;
        if (client.options.keyUp.isDown()) forward += 1;
        if (client.options.keyDown.isDown()) forward -= 1;
        if (client.options.keyLeft.isDown()) strafe += 1;
        if (client.options.keyRight.isDown()) strafe -= 1;

        if (forward == 0 && strafe == 0) return Vec3.ZERO;

        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad);
        double cos = Math.cos(rad);

        return new Vec3((forward * -sin + strafe * cos), 0, (forward * cos + strafe * sin)).normalize();
    }
}