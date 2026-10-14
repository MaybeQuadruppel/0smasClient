package com.OsamaClient.newbridge.Utils;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public class Tracker {
    public static boolean isAiming = false;

    private static LocalPlayer spoofedPlayer;
    private static float realYaw, realPitch;

    // --- Bewegungskorrektur ---
    private static boolean keysOverridden = false;
    private static final boolean[] savedKeys = new boolean[4]; // {vor, zurück, links, rechts}

    public static void applySpoof(LocalPlayer player, float yaw, float pitch) {
        if (player == null) return;
        if (spoofedPlayer != player) {
            realYaw = player.getYRot();
            realPitch = player.getXRot();
            spoofedPlayer = player;
        }
        player.setYRot(yaw);
        player.setXRot(pitch);
    }

    public static float getRealYaw() {
        return realYaw;
    }

    public static float getRealPitch() {
        return realPitch;
    }

    public static void restoreSpoof() {
        restoreKeys(Minecraft.getInstance());

        LocalPlayer p = spoofedPlayer;
        spoofedPlayer = null;
        if (p == null) return;
        p.setYRot(realYaw);
        p.setXRot(realPitch);
        p.yRotO = realYaw;
        p.xRotO = realPitch;
    }

    private static KeyMapping[] moveKeys(Minecraft mc) {
        return new KeyMapping[]{mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight};
    }

    private static void restoreKeys(Minecraft mc) {
        if (!keysOverridden) return;
        keysOverridden = false;
        if (mc == null || mc.options == null) return;
        KeyMapping[] keys = moveKeys(mc);
        for (int i = 0; i < 4; i++) keys[i].setDown(savedKeys[i]);
    }

    public static boolean[] physicalMoveKeys(Minecraft mc) {
        if (keysOverridden) return savedKeys.clone();
        boolean[] out = new boolean[4];
        if (mc.options == null) return out;
        KeyMapping[] keys = moveKeys(mc);
        for (int i = 0; i < 4; i++) out[i] = keys[i].isDown();
        return out;
    }

    public static void applyMovementCorrection(Minecraft mc) {
        restoreKeys(mc);
        if (mc.player == null || mc.options == null) return;

        LocalPlayer p = spoofedPlayer;
        if (p == null || mc.gui.screen() != null) return;

        KeyMapping[] keys = moveKeys(mc);
        boolean[] phys = new boolean[4];
        for (int i = 0; i < 4; i++) phys[i] = keys[i].isDown();

        int f = (phys[0] ? 1 : 0) - (phys[1] ? 1 : 0);
        int s = (phys[2] ? 1 : 0) - (phys[3] ? 1 : 0);
        if (f == 0 && s == 0) return;

        double rr = Math.toRadians(realYaw);
        double dx = -Math.sin(rr) * f + Math.cos(rr) * s;
        double dz = Math.cos(rr) * f + Math.sin(rr) * s;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6) return;
        dx /= len;
        dz /= len;

        double sr = Math.toRadians(p.getYRot());
        int bestF = 0, bestS = 0;
        double bestScore = -2.0;
        for (int cf = -1; cf <= 1; cf++) {
            for (int cs = -1; cs <= 1; cs++) {
                if (cf == 0 && cs == 0) continue;
                double mx = -Math.sin(sr) * cf + Math.cos(sr) * cs;
                double mz = Math.cos(sr) * cf + Math.sin(sr) * cs;
                double l = Math.sqrt(mx * mx + mz * mz);
                double score = (mx * dx + mz * dz) / l;
                if (score > bestScore) {
                    bestScore = score;
                    bestF = cf;
                    bestS = cs;
                }
            }
        }

        boolean[] mapped = {bestF > 0, bestF < 0, bestS > 0, bestS < 0};

        boolean same = true;
        for (int i = 0; i < 4; i++) if (mapped[i] != phys[i]) same = false;
        if (same) return;

        System.arraycopy(phys, 0, savedKeys, 0, 4);
        for (int i = 0; i < 4; i++) keys[i].setDown(mapped[i]);
        keysOverridden = true;
    }
}