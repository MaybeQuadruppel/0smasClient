package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.qdrppl.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
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
    public int targetType = 0;
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

        this.settings.add(new ModeButton("Logic", List.of("Always", "On Hit"), mode, val -> mode = val.equals("Always") ? 0 : 1));
        this.settings.add(new ModeButton("Targets", List.of("Players", "All"), targetType, val -> targetType = val.equals("Players") ? 0 : 1));
        this.settings.add(new Slider("Range", 1.0, 6.0, (double)range, val -> range = val.floatValue()));
        this.settings.add(new Slider("Smoothness", 0.01, 1.0, (double)smoothness, val -> smoothness = val.floatValue()));
        this.settings.add(new Slider("FOV", 10.0, 180.0, (double)fov, val -> fov = val.floatValue()));

        this.settings.add(new ToggleButton("Random Height", randomHeight, val -> randomHeight = val));
        this.settings.add(new Slider("Static Height", 0.0, 1.0, (double)staticHeight, val -> staticHeight = val.floatValue()));
    }

    /**
     * WICHTIG: Rufe diese Methode aus deinem OnUpdate-Event oder Pre-Motion-Update auf.
     * Nutze NICHT onRender, um Vulcan/Grim Flags zu vermeiden.
     */
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

        if (targetType == 0 && !(target instanceof Player)) return;

        applyHumanizedAim(client, target);
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
                .filter(e -> targetType == 1 || e instanceof Player)
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