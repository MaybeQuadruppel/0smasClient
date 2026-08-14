package com.OsamaClient.newbridge.Utils.Render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

public class WorldToScreen {

    public static Vector2f project(Vec3 worldPos, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();

        // Position relativ zur Kamera
        Vec3 relPos = worldPos.subtract(camPos);

        // Kamera-Rotationen in Bogenmaß (Radians)
        float yaw = (float) Math.toRadians(camera.yRot());
        float pitch = (float) Math.toRadians(camera.xRot());

        // Um Y-Achse (Yaw) rotieren
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double x1 = relPos.z * sinYaw + relPos.x * cosYaw;
        double y1 = relPos.y;
        double z1 = relPos.z * cosYaw - relPos.x * sinYaw;

        // Um X-Achse (Pitch) rotieren
        double cosPitch = Math.cos(pitch);
        double sinPitch = Math.sin(pitch);
        double x2 = x1;
        double y2 = y1 * cosPitch - z1 * sinPitch;
        double z2 = y1 * sinPitch + z1 * cosPitch;

        // Prüfen, ob das Ziel hinter der Kamera liegt
        if (z2 <= 0.0) {
            return null;
        }

        // FOV-Berechnung für die Skalierung
        double fov = mc.options.fov().get();
        double halfFovRad = Math.toRadians(fov / 2.0);
        double fovFactor = screenHeight / (2.0 * Math.tan(halfFovRad));

        // Bildschirmmitte
        double centerX = screenWidth / 2.0;
        double centerY = screenHeight / 2.0;

        // Auf den Bildschirm projizieren
        float screenX = (float) (centerX - x2 * fovFactor / z2);
        float screenY = (float) (centerY - y2 * fovFactor / z2);

        return new Vector2f(screenX, screenY);
    }
}