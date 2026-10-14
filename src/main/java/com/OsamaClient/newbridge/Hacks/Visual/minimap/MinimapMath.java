package com.OsamaClient.newbridge.Hacks.Visual.minimap;

/**
 * Umrechnung zwischen Karten-Pixeln (Mitte = Spieler, x nach rechts, y nach unten) und Welt.
 * Ohne Drehung zeigt oben nach Norden (-Z). Mit Drehung zeigt oben in Blickrichtung.
 */
public final class MinimapMath {

    private MinimapMath() {}

    /** Karte -> Welt-Offset {dx, dz} (Blöcke) für den Pixel-Offset (sx, sy) * blocksPerPixel. */
    public static double[] mapToWorld(double sx, double sy, double blocksPerPixel, boolean rotate, float yawDeg) {
        double mx = sx * blocksPerPixel, my = sy * blocksPerPixel;
        if (!rotate) return new double[]{mx, my};
        double yaw = Math.toRadians(yawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // Blickrichtung
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // rechte Hand
        return new double[]{fx * -my + rx * mx, fz * -my + rz * mx};
    }

    /** Welt-Offset (dx, dz) -> Karten-Pixel {sx, sy}. */
    public static double[] worldToMap(double dx, double dz, double blocksPerPixel, boolean rotate, float yawDeg) {
        if (!rotate) return new double[]{dx / blocksPerPixel, dz / blocksPerPixel};
        double yaw = Math.toRadians(yawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double right = dx * rx + dz * rz;
        double fwd = dx * fx + dz * fz;
        return new double[]{right / blocksPerPixel, -fwd / blocksPerPixel};
    }

    /** Punkt auf den Kartenrand ziehen (Kreis oder Quadrat mit Halbmesser r). */
    public static double[] clampToEdge(double sx, double sy, double r, boolean circle) {
        if (circle) {
            double len = Math.sqrt(sx * sx + sy * sy);
            if (len <= r || len == 0) return new double[]{sx, sy};
            return new double[]{sx / len * r, sy / len * r};
        }
        double m = Math.max(Math.abs(sx), Math.abs(sy));
        if (m <= r || m == 0) return new double[]{sx, sy};
        return new double[]{sx / m * r, sy / m * r};
    }
}
