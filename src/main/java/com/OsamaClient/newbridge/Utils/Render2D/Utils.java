package com.OsamaClient.newbridge.Utils.Render2D;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.Random;
import java.util.regex.Pattern;

public class Utils {
    private static final Minecraft mc = Minecraft.getInstance();

    public static final Pattern FILE_NAME_INVALID_CHARS_PATTERN = Pattern.compile("[\\s\\\\/:*?\"<>|]");
    private static final Random RANDOM = new Random();

    public static double frameTime = 0.0;
    public static boolean rendering3D = true;

    private Utils() {}

    public static int getWindowWidth() {
        return mc.getWindow().getWidth();
    }

    public static int getWindowHeight() {
        return mc.getWindow().getHeight();
    }

    public static Vec3 vec3(BlockPos pos) {
        return new Vec3(pos.getX(), pos.getY(), pos.getZ());
    }

    public static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dX = x2 - x1;
        double dY = y2 - y1;
        double dZ = z2 - z1;
        return dX * dX + dY * dY + dZ * dZ;
    }

    public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt(squaredDistance(x1, y1, z1, x2, y2, z2));
    }

    public static Vector3d set(Vector3d vec, Vec3 v) {
        vec.x = v.x;
        vec.y = v.y;
        vec.z = v.z;
        return vec;
    }

    public static boolean canUpdate() {
        return mc != null && mc.level != null && mc.player != null;
    }

    public static int random(int min, int max) {
        return RANDOM.nextInt(max - min) + min;
    }

    public static double random(double min, double max) {
        return min + (max - min) * RANDOM.nextDouble();
    }
}