package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class AutoCrit extends Module {

    public double range = 3.5;
    public boolean targetPlayers = true;
    public boolean targetHostile = true;

    private boolean waitingForDrop = false;
    private LivingEntity activeTarget = null;

    public AutoCrit() {
        super("AutoCrit", "Makes every hit a Crit", Category.COMBAT);

        this.settings.add(new Slider("Range", 1.0, 6.0, range, val -> range = val));
        this.settings.add(new ToggleButton("Target Players", targetPlayers, val -> targetPlayers = val));
        this.settings.add(new ToggleButton("Target Hostile", targetHostile, val -> targetHostile = val));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null) return;
        if (client.options.keyAttack.isDown() && !waitingForDrop) {
            LivingEntity target = getCrosshairTarget(client);

            if (target != null && isTargetValid(target, client)) {
                if (client.player.onGround()) {
                    client.player.jumpFromGround();
                    client.missTime = 10;

                    activeTarget = target;
                    waitingForDrop = true;
                }
            }
        }

        if (waitingForDrop && activeTarget != null) {
            if (!isTargetValid(activeTarget, client)) {
                reset();
                return;
            }


            if (!client.player.onGround() && client.player.getDeltaMovement().y < -0.1) {
                client.gameMode.attack(client.player, activeTarget);
                client.player.swing(InteractionHand.MAIN_HAND);

                client.options.keyAttack.setDown(false);
                reset();
            }

            if (client.player.onGround() && client.player.getDeltaMovement().y == 0) {
                reset();
            }
        }
    }

    private void reset() {
        waitingForDrop = false;
        activeTarget = null;
    }

    private LivingEntity getCrosshairTarget(Minecraft client) {
        HitResult hitResult = client.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hitResult).getEntity();
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    public boolean isTargetValid(LivingEntity targetEntity, Minecraft client) {
        if (targetEntity == client.player) return false;
        if (!targetEntity.isAlive()) return false;

        double dist = client.player.distanceTo(targetEntity);
        if (dist > range) return false;

        if (targetEntity instanceof Player && !targetPlayers) return false;
        if (targetEntity instanceof Enemy && !targetHostile) return false;

        return true;
    }

    @Override
    public void onDisable() {
        reset();
    }
}