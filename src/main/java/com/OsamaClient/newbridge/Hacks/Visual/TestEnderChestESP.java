package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TestEnderChestESP extends Module {

    public static TestEnderChestESP INSTANCE;

    public String renderMode = "Both";

    private final List<AABB> renderBoxes = new CopyOnWriteArrayList<>();

    public TestEnderChestESP() {
        super("TestEnderChestESP", "Renders Ender Chests through walls using Chams", Category.VISUAL);
        INSTANCE = this;

        List<String> modes = List.of("Fill", "Outline", "Both");
        this.settings.add(new ModeButton("Mode", modes, modes.indexOf(renderMode), val -> renderMode = val)
                .withDescription("Selects box rendering style (Fill, Outline, Both)."));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (!this.enabled || mc.level == null || mc.player == null) {
            renderBoxes.clear();
            return;
        }

        if (mc.player.tickCount % 10 == 0) {
            scanEnderChests(mc);
        }
    }

    private void scanEnderChests(Minecraft mc) {
        List<AABB> foundBoxes = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        int chunkRadius = 4;

        int pChunkX = playerPos.getX() >> 4;
        int pChunkZ = playerPos.getZ() >> 4;

        for (int cx = pChunkX - chunkRadius; cx <= pChunkX + chunkRadius; cx++) {
            for (int cz = pChunkZ - chunkRadius; cz <= pChunkZ + chunkRadius; cz++) {
                if (!mc.level.getChunkSource().hasChunk(cx, cz)) continue;

                LevelChunk chunk = mc.level.getChunk(cx, cz);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    if (entry.getValue() instanceof EnderChestBlockEntity) {
                        BlockPos pos = entry.getKey();

                        foundBoxes.add(new AABB(
                                pos.getX() + 0.0625, pos.getY(), pos.getZ() + 0.0625,
                                pos.getX() + 0.9375, pos.getY() + 0.875, pos.getZ() + 0.9375
                        ));
                    }
                }
            }
        }

        renderBoxes.clear();
        renderBoxes.addAll(foundBoxes);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled || renderBoxes.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer.mainCamera() == null) return;

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        boolean drawFill = renderMode.equals("Fill") || renderMode.equals("Both");
        boolean drawOutline = renderMode.equals("Outline") || renderMode.equals("Both");

        ChamsBufferSource bufferSource = new ChamsBufferSource();

        int colorFill = 0x40AA00FF;
        int colorOutline = 0xFFAA00FF;

        Camera camera = mc.gameRenderer.mainCamera();
        double camX = camera.position().x;
        double camY = camera.position().y;
        double camZ = camera.position().z;

        if (drawFill) {
            VertexConsumer fillConsumer = bufferSource.getBuffer(RenderTypes.storageEspFillSeeThrough());
            for (AABB box : renderBoxes) {
                float minX = (float) (box.minX - camX);
                float minY = (float) (box.minY - camY);
                float minZ = (float) (box.minZ - camZ);
                float maxX = (float) (box.maxX - camX);
                float maxY = (float) (box.maxY - camY);
                float maxZ = (float) (box.maxZ - camZ);


                renderFilledBox(matrix, normalMatrix, fillConsumer, minX, minY, minZ, maxX, maxY, maxZ, colorFill);
            }
        }

        if (drawOutline) {
            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
            for (AABB box : renderBoxes) {
                float minX = (float) (box.minX - camX);
                float minY = (float) (box.minY - camY);
                float minZ = (float) (box.minZ - camZ);
                float maxX = (float) (box.maxX - camX);
                float maxY = (float) (box.maxY - camY);
                float maxZ = (float) (box.maxZ - camZ);

                // Normalenmatrix mit übergeben
                renderBoxOutline(matrix, normalMatrix, lineConsumer, minX, minY, minZ, maxX, maxY, maxZ, colorOutline);
            }
        }
        bufferSource.uploadAndDraw();
    }

    private static void renderFilledBox(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        addQuad(matrix, normalMatrix, consumer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0, -1, 0, r, g, b, a);
        addQuad(matrix, normalMatrix, consumer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, 0, 1, 0, r, g, b, a);
        addQuad(matrix, normalMatrix, consumer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, 0, 0, -1, r, g, b, a);
        addQuad(matrix, normalMatrix, consumer, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, 0, 0, 1, r, g, b, a);
        addQuad(matrix, normalMatrix, consumer, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, -1, 0, 0, r, g, b, a);
        addQuad(matrix, normalMatrix, consumer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, 1, 0, 0, r, g, b, a);
    }

    private static void addQuad(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float nx, float ny, float nz,
                                int r, int g, int b, int a) {

        Vector3f normal = new Vector3f(nx, ny, nz);
        normal.mul(normalMatrix);

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a)
                .setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a)
                .setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a)
                .setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a)
                .setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
    }

    private static void renderBoxOutline(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        line(matrix, normalMatrix, consumer, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(matrix, normalMatrix, consumer, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(matrix, normalMatrix, consumer, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(matrix, normalMatrix, consumer, x1, y1, z2, x1, y1, z1, r, g, b, a);

        line(matrix, normalMatrix, consumer, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(matrix, normalMatrix, consumer, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(matrix, normalMatrix, consumer, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(matrix, normalMatrix, consumer, x1, y2, z2, x1, y2, z1, r, g, b, a);

        line(matrix, normalMatrix, consumer, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(matrix, normalMatrix, consumer, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(matrix, normalMatrix, consumer, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(matrix, normalMatrix, consumer, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             int r, int g, int b, int a) {

        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;

        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len != 0) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dy = 1.0f;
        }


        Vector3f normal = new Vector3f(dx, dy, dz);
        normal.mul(normalMatrix);

        // WICHTIG: "matrix" als Argument übergeben
        consumer.addVertex(matrix, x1, y1, z1)
                .setColor(r, g, b, a)
                .setNormal(normal.x(), normal.y(), normal.z())
                .setLineWidth(1.0f);

        consumer.addVertex(matrix, x2, y2, z2)
                .setColor(r, g, b, a)
                .setNormal(normal.x(), normal.y(), normal.z())
                .setLineWidth(1.0f);
    }
}