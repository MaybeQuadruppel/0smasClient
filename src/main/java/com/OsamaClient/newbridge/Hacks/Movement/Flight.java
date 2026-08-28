package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class Flight extends Module {

    public String flightMode = "Velocity";
    public double speed = 0.1;
    public boolean verticalSpeedMatch = false;

    public String antiKickMode = "Normal";
    public double delay = 20;
    public double offTime = 1;

    private int delayLeft = 20;
    private int offLeft = 1;

    public Flight() {
        super("Flight", "FLYYYY! No Fall is recommended with this module.", Category.MOVEMENT);

        List<String> modes = List.of("Vanilla", "Velocity");
        this.settings.add(new ModeButton(
                "Mode",
                modes,
                modes.indexOf(flightMode),
                val -> {
                    flightMode = val;
                    if (this.enabled) {
                        switchMode();
                    }
                }
        ).withDescription("The mode for Flight."));

        this.settings.add(new Slider(
                "Speed",
                0.0,
                1.0,
                speed,
                val -> speed = val
        ).withDescription("Your speed when flying."));

        this.settings.add(new ToggleButton(
                "V-Match",
                verticalSpeedMatch,
                val -> verticalSpeedMatch = val
        ).withDescription("Matches vertical speed to your horizontal speed."));

        List<String> akModes = List.of("Normal", "None");
        this.settings.add(new ModeButton(
                "Anti-Kick",
                akModes,
                akModes.indexOf(antiKickMode),
                val -> antiKickMode = val
        ).withDescription("The mode for anti kick."));

        this.settings.add(new Slider(
                "AK Delay",
                1,
                200,
                delay,
                val -> delay = val.intValue()
        ).withDescription("Amount of delay, in ticks, between anti-kick actions."));

        this.settings.add(new Slider(
                "AK Off-Time",
                1,
                20,
                offTime,
                val -> offTime = val.intValue()
        ).withDescription("Amount of ticks to fly down a bit to reset floating ticks."));
    }

    @Override
    public void onEnable() {
        delayLeft = (int) delay;
        offLeft = (int) offTime;

        Minecraft client = Minecraft.getInstance();

        if (client.player != null) {
            client.player.fallDistance = 0.0f; // Beim Einschalten einmalig resetten

            if (flightMode.equals("Vanilla") && !client.player.isSpectator()) {
                client.player.getAbilities().flying = true;
                if (!client.player.getAbilities().instabuild) {
                    client.player.getAbilities().mayfly = true;
                }
            }
        }
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();

        if (client.player != null) {
            client.player.fallDistance = 0.0f; // Beim Ausschalten einmalig resetten

            if (flightMode.equals("Vanilla") && !client.player.isSpectator()) {
                abilitiesOff(client);
            }
            client.player.setDeltaMovement(0, 0, 0);
        }
    }

    private void switchMode() {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null) {
            return;
        }

        if (!flightMode.equals("Vanilla") && !client.player.isSpectator()) {
            abilitiesOff(client);
        }
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null) {
            return;
        }

        handleAntiKick(client);

        if (flightMode.equals("Velocity")) {
            handleVelocityMode(client);
        } else {
            handleVanillaMode(client);
        }
    }

    private void handleAntiKick(Minecraft client) {
        if (delayLeft > 0) {
            delayLeft--;
        }

        if (offLeft <= 0 && delayLeft <= 0) {
            delayLeft = (int) delay;
            offLeft = (int) offTime;
            return;
        }

        if (delayLeft <= 0 && offLeft > 0) {
            if (antiKickMode.equals("Normal")) {
                if (flightMode.equals("Vanilla")) {
                    abilitiesOff(client);
                }
            }
            offLeft--;
        }
    }

    private void handleVelocityMode(Minecraft client) {
        client.player.getAbilities().flying = false;

        double moveY = 0.0;
        double vSpeed = speed * (verticalSpeedMatch ? 10.0 : 5.0);

        if (client.options.keyJump.isDown()) {
            moveY += vSpeed;
        } else if (client.options.keyShift.isDown()) {
            moveY -= vSpeed;
        } else if (antiKickMode.equals("Normal") && delayLeft <= 0) {
            moveY -= 0.04;
        }

        double mult = client.player.isSprinting() ? 15.0 : 10.0;
        Vec3 wishDir = getMovementDirection(client, client.player.getYRot());

        double moveX = wishDir.x * speed * mult;
        double moveZ = wishDir.z * speed * mult;

        client.player.setDeltaMovement(moveX, moveY, moveZ);
    }

    private void handleVanillaMode(Minecraft client) {
        if (client.player.isSpectator()) {
            return;
        }

        client.player.getAbilities().setFlyingSpeed((float) speed);
        client.player.getAbilities().flying = true;

        if (!client.player.getAbilities().instabuild) {
            client.player.getAbilities().mayfly = true;
        }
    }

    private void abilitiesOff(Minecraft client) {
        client.player.getAbilities().flying = false;
        client.player.getAbilities().setFlyingSpeed(0.05f);

        if (!client.player.getAbilities().instabuild) {
            client.player.getAbilities().mayfly = false;
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