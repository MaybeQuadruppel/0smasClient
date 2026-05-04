package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.*;
import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

public class AutoDihhTap extends Module {
    private float targetYaw;
    private float targetPitch;
    private boolean rotating;
    private double rotationSpeed = 7.0;
    private long lastFrameTime;
    private Runnable afterRotationAction;
    private long delayStartTime = 0;

    private Step currentStep = Step.NONE;
    private LivingEntity currentTarget;
    private BlockPos obsidianPos;

    private String mode = "Auto";

    public AutoDihhTap() {
        super("AutoDihhTap", "Automated crystal sequence for DihhTap", Category.COMBAT);

        this.settings.add(new Slider("Rotation Speed", 1.0, 20.0, 7.0, val -> rotationSpeed = val));
        this.settings.add(new ModeButton("Activation", Arrays.asList("Auto", "Manual"), 0, val -> mode = val));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null) return;

        if (rotating) {
            smoothLookUpdate(client);
        } else {
            // Auto-Modus sucht Ziel im Crosshair selbstständig
            if (currentStep == Step.NONE && mode.equals("Auto")) {
                HitResult crosshairTarget = client.hitResult;
                if (crosshairTarget instanceof EntityHitResult entityHit) {
                    if (entityHit.getEntity() instanceof LivingEntity living && !(living instanceof ArmorStand)) {
                        this.currentTarget = living;
                        this.currentStep = Step.LOOK_AT_TARGET;
                    }
                }
            }
            this.runStep(client);
        }
    }

    private void runStep(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || rotating) return;

        switch (this.currentStep) {
            case LOOK_AT_TARGET -> {
                switchToItem(player, Items.NETHERITE_SWORD);
                attackEntity(client);
                lookAtTargetBottomRightSmooth(client, this.currentTarget, () -> {
                    this.currentStep = Step.SWITCH_TO_OBSIDIAN;
                    runStep(client); // Sofort weiter zum nächsten Schritt
                });
            }
            case SWITCH_TO_OBSIDIAN -> {
                if (switchToItem(player, Items.OBSIDIAN)) {
                    if (client.hitResult instanceof BlockHitResult blockHit) {
                        this.obsidianPos = blockHit.getBlockPos();
                        if (!client.level.getBlockState(this.obsidianPos).is(Blocks.OBSIDIAN)) {
                            interactBlock(client);
                        }
                        switchToItem(player, Items.END_CRYSTAL);
                        this.currentStep = Step.PLACE_CRYSTAL;
                        runStep(client); // Kein Warten auf nächsten Tick
                    }
                }
            }
            case PLACE_CRYSTAL -> {
                interactBlock(client);
                Vec3 targetPos = getCrystalVector(client);
                if (targetPos != null) {
                    lookAtSmooth(client, targetPos, () -> {
                        this.currentStep = Step.ATTACK_CRYSTAL;
                        runStep(client);
                    });
                }
            }
            case ATTACK_CRYSTAL -> {
                attackEntity(client);
                this.delayStartTime = System.currentTimeMillis();
                this.currentStep = Step.LOOK_AT_OBSIDIAN;
                runStep(client);


            }
            case LOOK_AT_OBSIDIAN -> {
                if (System.currentTimeMillis() - delayStartTime >= 200) {
                    Vec3 targetPos = Vec3.atCenterOf(this.obsidianPos).add(0.0, 0.7, 0.0);
                    lookAtSmooth(client, targetPos, () -> {
                        this.currentStep = Step.PLACE_NEW_CRYSTAL;
                        runStep(client);
                    });
                }
            }
            case PLACE_NEW_CRYSTAL -> {
                interactBlock(client);
                this.currentStep = Step.LOOK_AT_NEW_CRYSTAL;
                runStep(client);
            }
            case LOOK_AT_NEW_CRYSTAL -> {
                Vec3 targetPos = Vec3.atCenterOf(this.obsidianPos).add(0.0, 1.5, 0.0);
                lookAtSmooth(client, targetPos, () -> {
                    this.currentStep = Step.ATTACK_NEW_CRYSTAL;
                    runStep(client);
                });
            }
            case ATTACK_NEW_CRYSTAL -> {
                attackEntity(client);
                switchToItem(player, Items.NETHERITE_SWORD);
                this.currentStep = Step.DONE;
                runStep(client);
            }
            case DONE -> this.currentStep = Step.NONE;
        }
    }


    private Vec3 getCrystalVector(Minecraft client) {
        HitResult hr = client.hitResult;
        if (hr instanceof BlockHitResult bh) return Vec3.atCenterOf(bh.getBlockPos()).add(0.0, 0.4, 0.0);
        if (hr instanceof EntityHitResult eh) return eh.getEntity().position().add(0.0, 0.4, 0.0);
        return null;
    }

    private void smoothLookUpdate(Minecraft client) {
        if (client.player == null) return;

        long now = System.nanoTime();
        double deltaTime = (now - lastFrameTime) / 1.0E9;
        lastFrameTime = now;

        float currentYaw = client.player.getYRot();
        float currentPitch = client.player.getXRot();
        float yawDiff = Mth.wrapDegrees(targetYaw - currentYaw);
        float pitchDiff = targetPitch - currentPitch;
        float maxStep = (float) (rotationSpeed * 60.0 * deltaTime);

        client.player.setYRot(currentYaw + Mth.clamp(yawDiff, -maxStep, maxStep));
        client.player.setXRot(currentPitch + Mth.clamp(pitchDiff, -maxStep, maxStep));

        if (Math.abs(yawDiff) < 0.5f && Math.abs(pitchDiff) < 0.5f) {
            client.player.setYRot(targetYaw);
            client.player.setXRot(targetPitch);
            rotating = false;
            if (afterRotationAction != null) {
                Runnable action = afterRotationAction;
                afterRotationAction = null;
                action.run();
            }
        }
    }

    private void lookAtSmooth(Minecraft client, Vec3 target, Runnable afterLook) {
        if (client.player == null) return;
        Vec3 eyes = client.player.getEyePosition(1.0f);
        Vec3 dir = target.subtract(eyes);

        double dist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180 / Math.PI)) - 90.0f;
        float pitch = (float) (-(Mth.atan2(dir.y, dist) * (180 / Math.PI)));

        this.targetYaw = yaw;
        this.targetPitch = pitch;
        this.rotating = true;
        this.lastFrameTime = System.nanoTime();
        this.afterRotationAction = afterLook;
    }

    private void lookAtTargetBottomRightSmooth(Minecraft client, LivingEntity target, Runnable afterLook) {
        if (client.player == null) return;
        Vec3 playerDir = Vec3.directionFromRotation(client.player.getXRot(), client.player.getYRot());
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 right = playerDir.cross(up).normalize();
        Vec3 targetRight = target.position().add(right.scale(1.3));
        lookAtSmooth(client, targetRight, afterLook);
    }

    private void interactBlock(Minecraft client) {
        if (client.player != null && client.gameMode != null && client.hitResult instanceof BlockHitResult blockHit) {
            client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, blockHit);
            client.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private void attackEntity(Minecraft client) {
        if (client.player != null && client.gameMode != null && client.hitResult instanceof EntityHitResult entityHit) {
            client.gameMode.attack(client.player, entityHit.getEntity());
            client.player.swing(InteractionHand.MAIN_HAND);
        }
    }

    private boolean switchToItem(LocalPlayer player, Item item) {
        for (int i = 0; i < 9; ++i) {
            if (player.getInventory().getItem(i).is(item)) {
                player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onEnable() {
        this.currentStep = Step.NONE;
        this.rotating = false;
    }

    @Override
    public void onDisable() {
        this.currentStep = Step.NONE;
        this.rotating = false;
    }

    public void onToggle() {
        enabled = !enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public String getMode() {
        return this.mode;
    }

    public void triggerManual(LivingEntity target) {
        if (this.enabled && this.currentStep == Step.NONE) {
            this.currentTarget = target;
            this.currentStep = Step.LOOK_AT_TARGET;
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private enum Step {
        NONE, LOOK_AT_TARGET, SWITCH_TO_OBSIDIAN, PLACE_CRYSTAL, ATTACK_CRYSTAL,
        LOOK_AT_OBSIDIAN, PLACE_NEW_CRYSTAL, LOOK_AT_NEW_CRYSTAL, ATTACK_NEW_CRYSTAL, DONE
    }
}

