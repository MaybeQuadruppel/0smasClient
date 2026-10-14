package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/** Renders a navigation route and its destination with the existing ESP pipelines. */
public final class PathRenderer {

    private static final float PATH_WIDTH = 2.5f;
    private final ChamsBufferSource bufferSource = new ChamsBufferSource();

    public void render(Render3DEvent event, List<BlockPos> waypoints, BlockPos target,
                       int pathColor, int targetColor) {
        if ((waypoints == null || waypoints.size() < 2) && target == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer.mainCamera() == null) return;

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();
        Camera camera = mc.gameRenderer.mainCamera();
        double cameraX = camera.position().x;
        double cameraY = camera.position().y;
        double cameraZ = camera.position().z;

        if (waypoints != null && waypoints.size() >= 2) {
            VertexConsumer lines = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
            for (int i = 0; i < waypoints.size() - 1; i++) {
                BlockPos from = waypoints.get(i);
                BlockPos to = waypoints.get(i + 1);
                line(matrix, normalMatrix, lines,
                        (float) (from.getX() + 0.5 - cameraX), (float) (from.getY() + 0.08 - cameraY), (float) (from.getZ() + 0.5 - cameraZ),
                        (float) (to.getX() + 0.5 - cameraX), (float) (to.getY() + 0.08 - cameraY), (float) (to.getZ() + 0.5 - cameraZ),
                        pathColor, PATH_WIDTH);
            }
        }

        if (target != null) {
            VertexConsumer fill = bufferSource.getBuffer(RenderTypes.storageEspFillSeeThrough());
            float minX = (float) (target.getX() - cameraX) - 0.02f;
            float minY = (float) (target.getY() - cameraY) - 0.02f;
            float minZ = (float) (target.getZ() - cameraZ) - 0.02f;
            float maxX = minX + 1.04f;
            float maxY = minY + 1.04f;
            float maxZ = minZ + 1.04f;
            box(matrix, normalMatrix, fill, minX, minY, minZ, maxX, maxY, maxZ, targetColor);
        }

        bufferSource.uploadAndDraw();
    }

    private static void box(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                            float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        quad(matrix, normalMatrix, consumer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0, -1, 0, r, g, b, a);
        quad(matrix, normalMatrix, consumer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, 0, 1, 0, r, g, b, a);
        quad(matrix, normalMatrix, consumer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, 0, 0, -1, r, g, b, a);
        quad(matrix, normalMatrix, consumer, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, 0, 0, 1, r, g, b, a);
        quad(matrix, normalMatrix, consumer, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, -1, 0, 0, r, g, b, a);
        quad(matrix, normalMatrix, consumer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, 1, 0, 0, r, g, b, a);
    }

    private static void quad(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz, int r, int g, int b, int a) {
        Vector3f normal = new Vector3f(nx, ny, nz).mul(normalMatrix);
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z());
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             int color, float width) {
        int a = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        Vector3f normal = new Vector3f(x2 - x1, y2 - y1, z2 - z1).normalize().mul(normalMatrix);
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z()).setLineWidth(width);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z()).setLineWidth(width);
    }
}
