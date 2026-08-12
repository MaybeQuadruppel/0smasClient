package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.StreamSupport;

public class AimAssist extends Module {

    public static AimAssist INSTANCE;
    private final Random random = new Random();

    // Settings
    public int mode = 0;
    public EntityFilterPicker entityFilter;
    public float range = 3.8f;
    public float smoothness = 0.15f;
    public float fov = 40.0f;
    public boolean randomHeight = true;
    public float staticHeight = 0.5f;

    private float currentAimHeight = 0.5f;
    private UUID lockedTargetUUID = null;

    public AimAssist() {
        super("AimAssist", "Advanced Humanized Aim", Category.COMBAT);
        INSTANCE = this;

        this.entityFilter = new EntityFilterPicker("Targets").withDescription("Selects which types of entities to target.");

        this.settings.add(new ModeButton("Logic", List.of("Always", "On Hit"), mode, val -> mode = val.equals("Always") ? 0 : 1).withDescription("Determines when the aim assist should activate."));
        this.settings.add(this.entityFilter);
        this.settings.add(new Slider("Range", 1.0, 6.0, (double) range, val -> range = val.floatValue()).withDescription("Maximum distance to target entities."));
        this.settings.add(new Slider("Smoothness", 0.01, 1.0, (double) smoothness, val -> smoothness = val.floatValue()).withDescription("Controls how smooth and human-like the aim movement is."));
        this.settings.add(new Slider("FOV", 10.0, 180.0, (double) fov, val -> fov = val.floatValue()).withDescription("Field of view restriction for valid targets."));

        this.settings.add(new ToggleButton("Random Height", randomHeight, val -> randomHeight = val).withDescription("Randomizes the hit position height on the entity."));
        this.settings.add(new Slider("Static Height", 0.0, 1.0, (double) staticHeight, val -> staticHeight = val.floatValue()).withDescription("Sets a fixed vertical offset for the target position."));
    }

    public void onUpdate(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return;

        LivingEntity target = null;
        if (mode == 0) {
            target = getBestTarget(client);
        } else if (lockedTargetUUID != null) {
            target = findByUUID(client, lockedTargetUUID);
        }

        if (target == null || !target.isAlive() || client.player.distanceTo(target) > range) {
            lockedTargetUUID = null;
            updateAimHeight();
            return;
        }

        if (!isValidTarget(target)) return;

        applyHumanizedAim(client, target);
    }

    /**
     * Prüft, ob eine Entität gemäß den Einstellungen im EntityFilterPicker anvisiert werden darf.
     */
    public boolean isValidTarget(Entity entity) {
        if (!(entity instanceof LivingEntity) || entity == Minecraft.getInstance().player || !entity.isAlive()) {
            return false;
        }

        if (entityFilter.isFilterEnabled("Players") && entity instanceof Player) return true;
        if (entityFilter.isFilterEnabled("Hostiles") && entity instanceof Enemy) return true;
        if (entityFilter.isFilterEnabled("Animals") && entity instanceof Animal) return true;
        if (entityFilter.isFilterEnabled("NPCs") && entity instanceof Npc) return true;
        if (entityFilter.isFilterEnabled("ArmorStands") && entity instanceof ArmorStand) return true;

        return false;
    }

    private void applyHumanizedAim(Minecraft client, LivingEntity target) {

        double dx = target.getX() - client.player.getX();
        double dz = target.getZ() - client.player.getZ();
        float heightToUse = randomHeight ? currentAimHeight : staticHeight;
        double targetPosDirY = target.getY() + (target.getEyeHeight() * heightToUse);
        double playerPosDirY = client.player.getY() + client.player.getEyeHeight();
        double dy = targetPosDirY - playerPosDirY;

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, distanceXZ)));

        float yawDiff = Mth.wrapDegrees(targetYaw - client.player.getYRot());
        float pitchDiff = Mth.wrapDegrees(targetPitch - client.player.getXRot());

        if (Math.abs(yawDiff) <= fov) {

            double sensitivity = client.options.sensitivity().get();
            float f = (float) (sensitivity * 0.6F + 0.2F);
            float gcd = f * f * f * 1.2F;

            float speedFactor = Math.max(0.1f, Math.min(smoothness, Math.abs(yawDiff) / 30f));

            float jitter = (random.nextFloat() - 0.5f) * 0.12f;
            float pct = speedFactor + jitter;

            float moveYaw = yawDiff * pct;
            float movePitch = pitchDiff * pct;

            float roundedYaw = Math.round(moveYaw / gcd) * gcd;
            float roundedPitch = Math.round(movePitch / gcd) * gcd;

            client.player.setYRot(client.player.getYRot() + roundedYaw);
            client.player.setXRot(Mth.clamp(client.player.getXRot() + roundedPitch, -90, 90));
        }
    }

    private void updateAimHeight() {
        if (randomHeight) {
            currentAimHeight = 0.4f + random.nextFloat() * 0.7f;
        }
    }

    private LivingEntity getBestTarget(Minecraft client) {
        return StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
                .filter(e -> e instanceof LivingEntity && e != client.player && e.isAlive())
                .map(e -> (LivingEntity) e)
                .filter(this::isValidTarget)
                .filter(e -> client.player.distanceTo(e) <= range)
                .filter(e -> {
                    float yaw = (float) (Math.toDegrees(Math.atan2(e.getZ() - client.player.getZ(), e.getX() - client.player.getX())) - 90.0);
                    return Math.abs(Mth.wrapDegrees(yaw - client.player.getYRot())) <= fov;
                })
                .min(Comparator.comparingDouble(e -> {
                    float y = (float) (Math.toDegrees(Math.atan2(e.getZ() - client.player.getZ(), e.getX() - client.player.getX())) - 90.0);
                    return Math.abs(Mth.wrapDegrees(y - client.player.getYRot()));
                }))
                .orElse(null);
    }

    private LivingEntity findByUUID(Minecraft client, UUID uuid) {
        if (uuid == null) return null;
        return (LivingEntity) StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
                .filter(e -> e.getUUID().equals(uuid))
                .findFirst().orElse(null);
    }

    public void setLockedTarget(Entity target) {
        if (target instanceof LivingEntity) {
            lockedTargetUUID = target.getUUID();
            updateAimHeight();
        }
    }
}