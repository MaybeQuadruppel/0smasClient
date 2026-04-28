package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.*;
import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;

public class AimAssist extends Module {

    public static AimAssist INSTANCE;


    public int mode = 0;
    public int targetType = 0;
    public float range = 3.8f;
    public float smoothness = 0.15f;
    public float fov = 40.0f;
    public float aimHeight = 0.5f;

    private UUID lockedTargetUUID = null;

    public AimAssist() {
        super("AimAssist","(Tweaks your Aim)", Category.COMBAT);
        INSTANCE = this;

        this.settings.add(new ModeButton("Logic", List.of("Always", "On Hit"), mode, val -> mode = val.equals("Always") ? 0 : 1));
        this.settings.add(new ModeButton("Targets", List.of("Players", "All"), targetType, val -> targetType = val.equals("Players") ? 0 : 1));
        this.settings.add(new Slider("Range", 1.0, 6.0, (double)range, val -> range = val.floatValue()));
        this.settings.add(new Slider("Smoothness", 0.01, 1.0, (double)smoothness, val -> smoothness = val.floatValue()));
        this.settings.add(new Slider("FOV", 10.0, 180.0, (double)fov, val -> fov = val.floatValue()));
        this.settings.add(new Slider("AimHeight", 0.0, 1.0, (double)aimHeight, val -> aimHeight = val.floatValue()));
    }

    public void setLockedTarget(Entity target) {
        if (target instanceof LivingEntity) {
            lockedTargetUUID = target.getUUID();
        }
    }



    public void onRender(Minecraft client, float partialTicks) {
        if (!enabled || client.player == null || client.level == null) return;
        LivingEntity target = null;
        if (mode == 0) {
            target = getBestTarget(client);
        } else if (lockedTargetUUID != null) {
            target = findByUUID(client, lockedTargetUUID);
        }

        if (target == null || !target.isAlive() || client.player.distanceTo(target) > range) {
            lockedTargetUUID = null;
            return;
        }

        if (targetType == 0 && !(target instanceof Player)) {

            return;
        }

        applySmoothAim(client, target, partialTicks);
    }

    private void applySmoothAim(Minecraft client, LivingEntity target, float partialTicks) {

        double x = Mth.lerp(partialTicks, target.xo, target.getX());
        double y = Mth.lerp(partialTicks, target.yo, target.getY());
        double z = Mth.lerp(partialTicks, target.zo, target.getZ());

        double dx = x - client.player.getX();
        double dz = z - client.player.getZ();

        double targetPosDirY = y + (target.getEyeHeight() * aimHeight);
        double playerPosDirY = client.player.getY() + client.player.getEyeHeight();
        double dy = targetPosDirY - playerPosDirY;

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, distanceXZ)));

        float yawDiff = Mth.wrapDegrees(targetYaw - client.player.getYRot());
        float pitchDiff = Mth.wrapDegrees(targetPitch - client.player.getXRot());

        if (Math.abs(yawDiff) <= fov) {

            float speed = smoothness * (partialTicks * 2.0f);

            float nextYaw = client.player.getYRot() + (yawDiff * smoothness);
            float nextPitch = client.player.getXRot() + (pitchDiff * smoothness);

            client.player.setYRot(nextYaw);
            client.player.setXRot(Mth.clamp(nextPitch, -90, 90));
        }
    }

    private LivingEntity getBestTarget(Minecraft client) {
        return StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
                .filter(e -> e instanceof LivingEntity && e != client.player && e.isAlive())
                .map(e -> (LivingEntity) e)
                .filter(e -> targetType == 1 || e instanceof Player)
                .filter(e -> client.player.distanceTo(e) <= range)
                .filter(e -> {

                    float yaw = (float) (Math.toDegrees(Math.atan2(e.getZ() - client.player.getZ(), e.getX() - client.player.getX())) - 90.0);
                    return Math.abs(Mth.wrapDegrees(yaw - client.player.getYRot())) <= fov / 2;
                })
                .min(Comparator.comparingDouble(e -> {
                    float y = (float) (Math.toDegrees(Math.atan2(e.getZ() - client.player.getZ(), e.getX() - client.player.getX())) - 90.0);
                    return Math.abs(Mth.wrapDegrees(y - client.player.getYRot()));
                }))
                .orElse(null);
    }

    private LivingEntity findByUUID(Minecraft client, UUID uuid) {
        return (LivingEntity) StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
                .filter(e -> e.getUUID().equals(uuid))
                .findFirst().orElse(null);
    }
}

