package com.qdrppl.newbridge.Hacks.Visual.ESP;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

public class RenderUtils {
    private static RenderUtils instance;
    public static final String MOD_ID = "newbridge";

    // Globale Map für Blockfarben (Wird vom BlockPicker/Config befüllt)
    public static final Map<Block, Integer> BLOCK_COLORS = new ConcurrentHashMap<>();

    public static float BoxSizeX = 1;
    public static float BoxSizeY = 1;
    public static float BoxSizeZ = 1;

    // Ein einfaches Record für gekoppelte Positions- und Farbdaten
    public record ESPBlockData(BlockPos pos, int color) {}

    public static final List<ESPBlockData> BLOCKS_TO_RENDER = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("minecraft", "debug_filled_box"))
                    .withDepthStencilState(Optional.empty())
                    .build()
    );

    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private BufferBuilder buffer;

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private MappableRingBuffer vertexBuffer;

    public static RenderUtils getInstance() {
        if (instance == null) instance = new RenderUtils();
        return instance;
    }

    public void init(Minecraft client) {
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(this::extractAndDrawESP);
    }

    private void extractAndDrawESP(LevelRenderContext context) {
        if (BLOCKS_TO_RENDER.isEmpty()) return;
        if (Minecraft.getInstance().level == null) return;

        try {
            renderAllBlocks(context);
            if (buffer != null) {
                drawFilledThroughWalls(Minecraft.getInstance(), FILLED_THROUGH_WALLS);
            }
        } catch (Exception e) {
            e.printStackTrace();
            buffer = null;
        }
    }

    private void renderAllBlocks(LevelRenderContext context) {
        PoseStack matrices = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        if (matrices == null || camera == null) return;

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, VertexFormat.Mode.QUADS, FILLED_THROUGH_WALLS.getVertexFormat());
        }

        matrices.pushPose();
        matrices.translate((float)-camera.x, (float)-camera.y, (float)-camera.z);

        // Wir loopen durch die Daten und extrahieren die individuelle Farbe jedes Blocks
        for (ESPBlockData data : BLOCKS_TO_RENDER) {
            BlockPos pos = data.pos();
            int c = data.color();

            // Aufsplitten von ARGB in Floats
            float r = ((c >> 16) & 0xFF) / 255f;
            float g = ((c >> 8) & 0xFF) / 255f;
            float b = (c & 0xFF) / 255f;
            float a = 0.4f; // Feste Transparenz oder Alpha aus der Farbe ziehen falls gewünscht

            renderFilledBox(matrices.last().pose(), buffer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + BoxSizeX, pos.getY() + BoxSizeY, pos.getZ() + BoxSizeZ,
                    r, g, b, a);
        }

        matrices.popPose();
    }

    private void renderFilledBox(Matrix4fc matrix, BufferBuilder buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a) {
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, maxY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, minZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, maxX, minY, maxZ).setColor(r, g, b, a);
        buffer.addVertex(matrix, minX, minY, maxZ).setColor(r, g, b, a);
    }

    private void drawFilledThroughWalls(Minecraft client, RenderPipeline pipeline) {
        MeshData builtBuffer = buffer.build();
        if (builtBuffer == null) return;

        MeshData.DrawState drawParameters = builtBuffer.drawState();
        int vertexBufferSize = drawParameters.vertexCount() * drawParameters.format().getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> MOD_ID + "_esp_buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }

        GpuBuffer vertices = vertexBuffer.currentBuffer();

        RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indices = shapeIndexBuffer.getBuffer(drawParameters.indexCount());
        VertexFormat.IndexType indexType = shapeIndexBuffer.type();

        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass renderPass = commandEncoder.createRenderPass(() -> MOD_ID + "_esp_pass", client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(), client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);
            renderPass.setVertexBuffer(0, vertices);
            renderPass.setIndexBuffer(indices, indexType);
            renderPass.drawIndexed(0, 0, drawParameters.indexCount(), 1);
        }

        builtBuffer.close();
        vertexBuffer.rotate();
        buffer = null;
    }

    public void close() {
        allocator.close();
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }
}