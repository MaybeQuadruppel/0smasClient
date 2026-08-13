package com.OsamaClient.newbridge.Hacks.Combat;


import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class Reach extends Module {

    public double maxRange = 4.5;
    public boolean legitMode = false;
    public double legitChance = 20.0;

    public EntityFilterPicker entityFilter;

    public Reach() {
        super("Reach", "Lets you hit further", Category.COMBAT);

        this.entityFilter = new EntityFilterPicker("Targets");
        this.settings.add(this.entityFilter.withDescription("Selects which types of entities to target."));

        this.settings.add(new Slider("Range", 3.5, 10.0, maxRange, val -> maxRange = val).withDescription("Sets the extended attack reach distance."));
        this.settings.add(new ToggleButton("Legit Mode", legitMode, val -> legitMode = val).withDescription("Only applies reach by the Probability to look more natural."));
        this.settings.add(new Slider("Legit Chance %", 10.0, 30.0, legitChance, val -> legitChance = val).withDescription("Probability percentage for the reach to apply in Legit Mode."));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null || client.level == null || client.gui.screen() != null) return;

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

        String filterKey = null;
        if (targetEntity instanceof Player) {
            filterKey = "Players";
        } else if (targetEntity instanceof ArmorStand) {
            filterKey = "ArmorStands";
        } else if (targetEntity instanceof Enemy) {
            filterKey = "Hostiles";
        } else if (targetEntity instanceof Animal) {
            filterKey = "Animals";
        } else if (targetEntity instanceof Villager || targetEntity instanceof WanderingTrader) {
            filterKey = "NPCs";
        }

        if (filterKey != null && entityFilter != null && !entityFilter.isFilterEnabled(filterKey)) {
            return false;
        }

        return true;
    }
}