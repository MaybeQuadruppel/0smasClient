package com.OsamaClient.newbridge.Utils;

import net.minecraft.client.player.LocalPlayer;

public class Tracker {
    public static boolean isAiming = false;

    // --- Silent-Rotation: Spieler-Rotation nur für die Dauer EINES Ticks überschreiben ---
    private static LocalPlayer spoofedPlayer;
    private static float realYaw, realPitch;

    /** Setzt die Rotation für den Rest dieses Ticks (Rotations-Pakete + Interaktionen nutzen sie). */
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

    /** Am Tick-ENDE aufrufen (MinecraftMixin, RETURN von tick) – NACH dem Senden der Pakete. */
    public static void restoreSpoof() {
        LocalPlayer p = spoofedPlayer;
        spoofedPlayer = null;
        if (p == null) return;
        p.setYRot(realYaw);
        p.setXRot(realPitch);
        p.yRotO = realYaw;   // sonst interpoliert die Kamera zwischen Spoof und echtem Wert (Ruckler)
        p.xRotO = realPitch;
    }
}