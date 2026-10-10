package com.OsamaClient.newbridge.Hacks.Troll;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Copies yaw/pitch and movement inputs of a player (the one you hit, or the nearest). */
public class Mimic extends Module {

    private String targetMode = "On Hit";     // On Hit | Nearest
    private String moveMode = "Relative";     // Relative | World
    private double range = 20.0;
    private double timeoutSec = 8.0;
    private boolean copyRotation = true;
    private boolean copyMovement = true;
    private boolean copySneak = true;
    private boolean copySprint = true;
    private boolean copyJump = true;
    private double smoothing = 1.0;           // 1 = 1:1

    private Player target;
    private Player lastTarget;
    private int timeoutTicks;
    private boolean controlling;

    // smoothed target velocity (blocks/tick)
    private Vec3 lastPos;
    private double velX, velY, velZ;

    public Mimic() {
        super("Mimic", "Copies the rotation and movement of a player you hit or the nearest one.", Category.TROLL);

        settings.add(new ModeButton("Target", List.of("On Hit", "Nearest"), 0, m -> targetMode = m)
                .withDescription("Who to copy"));
        settings.add(new ModeButton("Move Mode", List.of("Relative", "World"), 0, m -> moveMode = m)
                .withDescription("Relative: same keys as the target. World: walk in the same compass direction"));
        settings.add(new Slider("Range", 5.0, 50.0, range, 1.0, v -> range = v)
                .withDescription("Maximum distance to the target"));
        settings.add(new Slider("Timeout (s)", 1.0, 30.0, timeoutSec, 1.0, v -> timeoutSec = v)
                .withDescription("On Hit: stop copying after this time without a new hit"));
        settings.add(new ToggleButton("Rotation", copyRotation, v -> copyRotation = v)
                .withDescription("Copy yaw and pitch"));
        settings.add(new Slider("Smoothing", 1.0, 10.0, smoothing, 0.5, v -> smoothing = v)
                .withDescription("1 = exact copy, higher = smoother"));
        settings.add(new ToggleButton("Movement", copyMovement, v -> copyMovement = v)
                .withDescription("Copy forward/back/strafe"));
        settings.add(new ToggleButton("Sneak", copySneak, v -> copySneak = v)
                .withDescription("Copy sneaking"));
        settings.add(new ToggleButton("Sprint", copySprint, v -> copySprint = v)
                .withDescription("Copy sprinting"));
        settings.add(new ToggleButton("Jump", copyJump, v -> copyJump = v)
                .withDescription("Copy jumping"));
    }

    @Override
    public void onEnable() {
        target = null;
        lastTarget = null;
        timeoutTicks = 0;
    }

    @Override
    public void onDisable() {
        target = null;
        lastTarget = null;
        stopControl(Minecraft.getInstance());
    }

    @Override
    public void onAttack(Minecraft mc, LivingEntity hit) {
        if (!(hit instanceof Player p) || p == mc.player) return;
        if (!targetMode.equals("On Hit")) return;
        target = p;
        timeoutTicks = (int) Math.round(timeoutSec * 20);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;

        if (targetMode.equals("Nearest")) {
            target = nearest(mc);
        } else if (target != null) {
            if (--timeoutTicks <= 0 || target.isRemoved() || mc.player.distanceTo(target) > range) target = null;
        }

        if (target == null) {
            lastTarget = null;
            stopControl(mc);
            return;
        }

        updateVelocity();

        if (copyRotation) {
            TrollUtil.rotate(mc, target.getYHeadRot(), target.getXRot(), (float) smoothing);
        }

        var o = mc.options;

        if (copyMovement) {
            double speed = Math.hypot(velX, velZ);
            boolean moving = speed > 0.03;

            // Players move relative to their head yaw (remote body yaw lags behind it).
            float basisYaw = moveMode.equals("Relative") ? target.getYHeadRot() : mc.player.getYRot();
            double yaw = Math.toRadians(basisYaw);
            double fwd = velX * -Math.sin(yaw) + velZ * Math.cos(yaw);
            double right = velX * -Math.cos(yaw) + velZ * -Math.sin(yaw);

            // thresholds relative to speed, so diagonals (W+D) work and sneaking is detected too
            double t = speed * 0.35;
            o.keyUp.setDown(moving && fwd > t);
            o.keyDown.setDown(moving && fwd < -t);
            o.keyRight.setDown(moving && right > t);
            o.keyLeft.setDown(moving && right < -t);
            controlling = true;
        }
        if (copySneak) {
            o.keyShift.setDown(target.isCrouching() || target.isShiftKeyDown());
            controlling = true;
        }
        if (copySprint) {
            o.keySprint.setDown(target.isSprinting());
            controlling = true;
        }
        if (copyJump) {
            o.keyJump.setDown(!target.onGround() && velY > 0.03);
            controlling = true;
        }
    }

    /** Smoothed velocity over a few ticks; a single-tick delta is too jittery for remote players. */
    private void updateVelocity() {
        Vec3 pos = target.position();
        if (target != lastTarget || lastPos == null) {
            lastTarget = target;
            lastPos = pos;
            velX = velY = velZ = 0;
            return;
        }
        velX = velX * 0.5 + (pos.x - lastPos.x) * 0.5;
        velY = velY * 0.5 + (pos.y - lastPos.y) * 0.5;
        velZ = velZ * 0.5 + (pos.z - lastPos.z) * 0.5;
        lastPos = pos;
    }

    private Player nearest(Minecraft mc) {
        Player best = null;
        double bestD = range;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            double d = mc.player.distanceTo(p);
            if (d <= bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private void stopControl(Minecraft mc) {
        if (controlling) {
            TrollUtil.releaseKeys(mc);
            controlling = false;
        }
    }
}