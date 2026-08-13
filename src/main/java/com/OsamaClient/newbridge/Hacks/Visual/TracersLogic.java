package com.OsamaClient.newbridge.Hacks.Visual;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import org.joml.Vector2f;
import org.joml.Vector3d;

public class TracersLogic {
    private static final Minecraft mc = Minecraft.getInstance();

    /**
     * Berechnet den Offscreen-Winkel und die Position für einen Pfeil am Bildschirmrand,
     * falls sich das Ziel außerhalb des Sichtfelds befindet.
     */
    public static OffscreenData calculateOffscreenPosition(Entity entity, float distanceOffscreen, float sizeOffscreen) {
        if (mc.player == null || mc.getWindow() == null) return null;

        Vec2 screenCenter = new Vec2(mc.getWindow().getWidth() / 2.0f, mc.getWindow().getHeight() / 2.0f);

        // Position des Entities projizieren
        Vector3d projection = new Vector3d(entity.getX(), entity.getY(), entity.getZ());


        Vector2f angle = vectorAngles(new Vector3d(screenCenter.x - projection.x, screenCenter.y - projection.y, 0));
        angle.y += 180;

        float angleYawRad = (float) Math.toRadians(angle.y);

        Vector2f newPoint = new Vector2f(
                screenCenter.x + distanceOffscreen * (float) Math.cos(angleYawRad),
                screenCenter.y + distanceOffscreen * (float) Math.sin(angleYawRad)
        );

        Vector2f[] trianglePoints = {
                new Vector2f(newPoint.x - sizeOffscreen, newPoint.y - sizeOffscreen),
                new Vector2f(newPoint.x + sizeOffscreen * 0.73205f, newPoint.y),
                new Vector2f(newPoint.x - sizeOffscreen, newPoint.y + sizeOffscreen)
        };

        rotateTriangle(trianglePoints, angle.y);

        return new OffscreenData(trianglePoints, angle.y);
    }

    private static void rotateTriangle(Vector2f[] points, float ang) {
        Vector2f triangleCenter = new Vector2f(0, 0);
        triangleCenter.add(points[0]).add(points[1]).add(points[2]).div(3.0f);

        float theta = (float) Math.toRadians(ang);
        float cos = (float) Math.cos(theta);
        float sin = (float) Math.sin(theta);

        for (int i = 0; i < 3; i++) {
            Vector2f point = new Vector2f(points[i].x, points[i].y).sub(triangleCenter);
            Vector2f newPoint = new Vector2f(point.x * cos - point.y * sin, point.x * sin + point.y * cos);
            newPoint.add(triangleCenter);
            points[i] = newPoint;
        }
    }

    private static Vector2f vectorAngles(final Vector3d forward) {
        float tmp, yaw, pitch;

        if (forward.x == 0 && forward.y == 0) {
            yaw = 0;
            pitch = (forward.z > 0) ? 270 : 90;
        } else {
            yaw = (float) (Math.atan2(forward.y, forward.x) * 180 / Math.PI);
            if (yaw < 0) yaw += 360;

            tmp = (float) Math.sqrt(forward.x * forward.x + forward.y * forward.y);
            pitch = (float) (Math.atan2(-forward.z, tmp) * 180 / Math.PI);
            if (pitch < 0) pitch += 360;
        }

        return new Vector2f(pitch, yaw);
    }

    // Hilfsklasse für Offscreen-Daten
    public static class OffscreenData {
        public final Vector2f[] points;
        public final float angle;

        public OffscreenData(Vector2f[] points, float angle) {
            this.points = points;
            this.angle = angle;
        }
    }
}