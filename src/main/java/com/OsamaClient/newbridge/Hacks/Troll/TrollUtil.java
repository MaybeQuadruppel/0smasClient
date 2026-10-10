package com.OsamaClient.newbridge.Hacks.Troll;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for the Troll modules. */
public final class TrollUtil {

    private TrollUtil() {}

    /**
     * Rotates the player towards an absolute yaw/pitch with GCD quantization (same as smoothLookAt).
     * smoothing 1 = instant (1:1), higher = slower.
     */
    public static void rotate(Minecraft mc, float yaw, float pitch, float smoothing) {
        if (mc.player == null) return;

        float yawDiff = Mth.wrapDegrees(yaw - mc.player.getYRot());
        float pitchDiff = Mth.wrapDegrees(pitch - mc.player.getXRot());

        double sensitivity = mc.options.sensitivity().get();
        float f = (float) (sensitivity * 0.6F + 0.2F);
        float gcd = f * f * f * 1.2F;
        float pct = 1.0f / Math.max(1.0f, smoothing);

        float roundedYaw = Math.round((yawDiff * pct) / gcd) * gcd;
        float roundedPitch = Math.round((pitchDiff * pct) / gcd) * gcd;

        mc.player.setYRot(mc.player.getYRot() + roundedYaw);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + roundedPitch, -90f, 90f));
    }

    /** Rotates towards a world position. */
    public static void lookAt(Minecraft mc, Vec3 target, float smoothing) {
        if (mc.player == null) return;
        Vec3 eyes = mc.player.getEyePosition();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dxz)));
        rotate(mc, yaw, pitch, smoothing);
    }

    /** Releases every movement key we may have been holding. */
    public static void releaseKeys(Minecraft mc) {
        if (mc.options == null) return;
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
        mc.options.keySprint.setDown(false);
    }
}