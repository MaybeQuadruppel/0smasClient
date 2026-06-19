package com.qdrppl.newbridge.Utils;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;

public class Renderpath {

    /**
     * Spans a flat 3D ribbon from waypoint to waypoint using the RenderBlock pipeline.
     */
    public static void renderLineStrip(LevelRenderContext context, List<BlockPos> path, float width, float r, float g, float b, float a) {
        if (path == null || path.size() < 2) return;

        RenderBlock.begin();
        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = matrices.last().pose();

        float halfWidth = width / 2f;

        for (int i = 0; i < path.size() - 1; i++) {
            Vec3 p1 = Vec3.atCenterOf(path.get(i)).subtract(0, 0.43, 0);
            Vec3 p2 = Vec3.atCenterOf(path.get(i + 1)).subtract(0, 0.43, 0);
            float dx = (float) (p2.x - p1.x);
            float dz = (float) (p2.z - p1.z);
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            if (len < 0.001f) continue;
            float nx = (-dz / len) * halfWidth;
            float nz = (dx / len) * halfWidth;
            var buf = RenderBlock.getBuffer();
            if (buf == null) continue;
            buf.addVertex(matrix, (float)(p1.x - nx), (float)p1.y, (float)(p1.z - nz)).setColor(r, g, b, a);
            buf.addVertex(matrix, (float)(p1.x + nx), (float)p1.y, (float)(p1.z + nz)).setColor(r, g, b, a);
            buf.addVertex(matrix, (float)(p2.x + nx), (float)p2.y, (float)(p2.z + nz)).setColor(r, g, b, a);
            buf.addVertex(matrix, (float)(p2.x - nx), (float)p2.y, (float)(p2.z - nz)).setColor(r, g, b, a);
        }

        matrices.popPose();
    }
}