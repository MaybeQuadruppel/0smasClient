package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;
import com.qdrppl.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class Reach extends Module {

    public double maxRange = 4.5;
    public boolean legitMode = false;
    public double legitChance = 20.0;

    public boolean targetPlayers = true;
    public boolean targetHostile = true;

    public Reach() {
        super("Reach", "Lets you hit further", Category.COMBAT);


        this.settings.add(new Slider("Range", 3.5, 10.0, maxRange, val -> maxRange = val));

        this.settings.add(new ToggleButton("Legit Mode", legitMode, val -> legitMode = val));

        this.settings.add(new Slider("Legit Chance %", 10.0, 30.0, legitChance, val -> legitChance = val));


        this.settings.add(new ToggleButton("Players", targetPlayers, val -> targetPlayers = val));
        this.settings.add(new ToggleButton("Monsters", targetHostile, val -> targetHostile = val));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null) return;

        if (client.options.keyAttack.isDown()) {
            if (legitMode) {
                double randomValue = Math.random() * 100;
                if (randomValue > legitChance) {
                    return;
                }
            }
            Entity extendedTarget = getExtendedCrosshairTarget(client, maxRange);

            if (extendedTarget instanceof LivingEntity livingTarget) {
                if (isTargetValid(livingTarget, client)) {
                    client.gameMode.attack(client.player, livingTarget);
                    client.player.swing(InteractionHand.MAIN_HAND);
                    client.options.keyAttack.setDown(false);
                }
            }
        }
    }
    private Entity getExtendedCrosshairTarget(Minecraft client, double range) {
        Entity cameraEntity = client.getCameraEntity();
        if (cameraEntity == null || client.level == null) return null;

        Vec3 eyePosition = cameraEntity.getEyePosition(1.0F);
        Vec3 viewVector = cameraEntity.getViewVector(1.0F);
        Vec3 reachVector = eyePosition.add(viewVector.x * range, viewVector.y * range, viewVector.z * range);
        AABB searchBox = cameraEntity.getBoundingBox().expandTowards(viewVector.scale(range)).inflate(1.0D, 1.0D, 1.0D);

        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
                cameraEntity,
                eyePosition,
                reachVector,
                searchBox,
                entity -> !entity.isSpectator() && entity.isPickable(),
                range * range
        );

        if (hitResult != null) {
            return hitResult.getEntity();
        }

        return null;
    }

    public boolean isTargetValid(LivingEntity targetEntity, Minecraft client) {
        if (targetEntity == client.player) return false;
        if (!targetEntity.isAlive()) return false;

        // Prüfen, ob der Spieler-Typ angegriffen werden darf
        if (targetEntity instanceof net.minecraft.world.entity.player.Player && !targetPlayers) return false;
        if (targetEntity instanceof net.minecraft.world.entity.monster.Enemy && !targetHostile) return false;

        return true;
    }
}