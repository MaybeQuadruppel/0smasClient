package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;
import com.qdrppl.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

public class SwordBot extends Module {

    // Settings
    public double lockRange = 8.0;
    public double loseRange = 12.0;
    public double wTapRange = 4.0; // Geändert auf 4 Blöcke
    public double smoothSpeed = 0.22;
    public boolean doStrafing = true;
    public double CritEnable = 3.5; // Geändert auf 3,5 Blöcke

    private LivingEntity target = null;
    private long lastAttackTime = 0;
    private long jumpTime = 0;
    private boolean waitingForCrit = false;

    // Combo States
    private boolean isReleasingW = false;
    private long resumeWTime = 0;

    private final Random random = new Random();
    private int strafeDir = 0;
    private long nextStrafeChange = 0;

    public SwordBot() {
        super("ComboBot", "A Prototype SwordBot", Category.COMBAT);
        this.settings.add(new Slider("Lock Range", 3.0, 12.0, lockRange, val -> lockRange = val));
        this.settings.add(new Slider("Lose Range", 5.0, 15.0, loseRange, val -> loseRange = val));
        this.settings.add(new Slider("W-Tap Range", 3.0, 6.0, wTapRange, val -> wTapRange = val));
        this.settings.add(new Slider("CritMode", 0.1, 5.0, CritEnable, val -> CritEnable = val));
        this.settings.add(new Slider("Smoothness", 0.05, 0.5, smoothSpeed, val -> smoothSpeed = val));
        this.settings.add(new ToggleButton("Strafing", doStrafing, val -> doStrafing = val));
        // Sprint Jump Setting entfernt
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) {
            resetKeys(client);
            return;
        }

        if (target != null) {
            if (!target.isAlive() || client.player.distanceTo(target) > loseRange) {
                target = null;
                resetKeys(client);
            }
        } else {
            target = findTarget(client);
        }

        if (target == null) return;

        aimAtTarget(client, target);

        double dist = client.player.distanceTo(target);
        long now = System.currentTimeMillis();

        handleMovement(client, now, dist);

        if (client.player.hasLineOfSight(target)) {

            boolean canAttack = (now - lastAttackTime) >= 625;

            if (canAttack) {

                if (dist <= CritEnable) {
                    if (!waitingForCrit) {
                        if (client.player.onGround()) {
                            client.options.keyJump.setDown(true);
                            jumpTime = now;
                            waitingForCrit = true;
                        }
                    } else {
                        if ((now - jumpTime) >= 350) {
                            attack(client);
                            client.options.keyJump.setDown(false);
                            waitingForCrit = false;
                        }
                    }
                }
                else if (dist <= wTapRange) {

                    if (!waitingForCrit) {
                        attack(client);
                        isReleasingW = true;
                        resumeWTime = now + 130;

                        client.options.keyJump.setDown(false);
                    }
                }
            }
        }
    }

    private void handleMovement(Minecraft client, long now, double dist) {
        if (isReleasingW) {
            if (now > resumeWTime) {
                isReleasingW = false;
                client.options.keyUp.setDown(true);
                client.player.setSprinting(true);
            } else {
                client.options.keyUp.setDown(false);
                client.player.setSprinting(false);

                if (dist < 4.0) client.options.keyDown.setDown(true);
                else client.options.keyDown.setDown(false);
            }
        } else {
            client.options.keyUp.setDown(true);
            client.options.keyDown.setDown(false);

            if (!waitingForCrit) {
                client.options.keyJump.setDown(false);
            }
        }

        if (doStrafing && target != null) {
            if (now > nextStrafeChange) {
                strafeDir = random.nextInt(4) - 1;
                nextStrafeChange = now + 400 + random.nextInt(800);
            }
            client.options.keyLeft.setDown(strafeDir == -1);
            client.options.keyRight.setDown(strafeDir == 1);
        }
    }

    private void aimAtTarget(Minecraft client, Entity target) {
        Vec3 targetPos = target.position().add(0, target.getEyeHeight() - 0.45, 0);
        Vec3 playerPos = client.player.getEyePosition();

        double diffX = targetPos.x - playerPos.x;
        double diffY = targetPos.y - playerPos.y;
        double diffZ = targetPos.z - playerPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90F;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diffY, diffXZ));

        float s = (float) smoothSpeed;
        client.player.setYRot(client.player.getYRot() + Mth.wrapDegrees(targetYaw - client.player.getYRot()) * s);
        client.player.setXRot(client.player.getXRot() + Mth.wrapDegrees(targetPitch - client.player.getXRot()) * (s * 0.8f));
    }

    private void attack(Minecraft client) {
        if (client.gameMode != null && target != null) {
            client.gameMode.attack(client.player, target);
            client.player.swing(InteractionHand.MAIN_HAND);
            lastAttackTime = System.currentTimeMillis();
        }
    }

    private LivingEntity findTarget(Minecraft client) {
        for (Entity e : client.level.entitiesForRendering()) {
            if (e instanceof Player && e != client.player && e.isAlive()) {
                if (client.player.distanceTo(e) <= lockRange && client.player.hasLineOfSight(e)) {
                    return (LivingEntity) e;
                }
            }
        }
        return null;
    }

    private void resetKeys(Minecraft client) {
        client.options.keyUp.setDown(false);
        client.options.keyDown.setDown(false);
        client.options.keyLeft.setDown(false);
        client.options.keyRight.setDown(false);
        client.options.keyJump.setDown(false);
        waitingForCrit = false;
        isReleasingW = false;
    }

    @Override
    public void onDisable() {
        resetKeys(Minecraft.getInstance());
        target = null;
    }
}