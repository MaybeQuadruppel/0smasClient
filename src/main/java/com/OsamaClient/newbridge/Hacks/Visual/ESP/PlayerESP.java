package com.OsamaClient.newbridge.Hacks.Visual.ESP;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;

public class PlayerESP extends Module {
    public static PlayerESP INSTANCE;

    public boolean showPlayers = true;
    public boolean showHostiles = true;
    public boolean showPassives = true;
    public float alphaValue = 0.4f;
    public float range = 128;

    private static final RenderPipeline FILLED_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("newbridge", "esp_filled"))
                    .withDepthStencilState(Optional.empty())
                    .build()
    );

    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private BufferBuilder buffer;
    private MappableRingBuffer vertexBuffer;

    public PlayerESP() {
        super("EnitiyESP", "Lets you See Enitys by their Threadlevels", Category.VISUAL);
        INSTANCE = this;

        this.settings.add(new ToggleButton("Show Players", showPlayers, val -> showPlayers = (boolean) val));
        this.settings.add(new ToggleButton("Show Hostiles", showHostiles, val -> showHostiles = (boolean) val));
        this.settings.add(new ToggleButton("Show Passives", showPassives, val -> showPassives = (boolean) val));
        this.settings.add(new Slider("Alpha", 0.0, 1.0, (double) alphaValue, val -> alphaValue = val.floatValue()));
        this.settings.add(new Slider("Range", 1.0, 128.0, (double) range, val -> range = val.floatValue()));
    }

    public static PlayerESP getInstance() {
        if (INSTANCE == null) INSTANCE = new PlayerESP();
        return INSTANCE;
    }

    public void init(Minecraft client) {
        INSTANCE = this;
        LevelRenderEvents.END_MAIN.register(this::render);
    }

    private void render(LevelRenderContext context) {
        if (!this.enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        Vec3 camPos = context.levelState().cameraRenderState.pos;
        float tickDelta = client.getDeltaTracker().getGameTimeDeltaTicks();

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }

        int entitiesFound = 0;
        AABB searchBox = client.player.getBoundingBox().inflate(range);

        for (Entity entity : client.level.getEntities(null, searchBox)) {
            if (!(entity instanceof LivingEntity) || entity == client.player || !entity.isAlive()) continue;

            float r = 1, g = 1, b = 1;
            boolean valid = false;

            if (entity instanceof Player) {
                if (showPlayers) { r = 1; g = 0; b = 0; valid = true; }
            } else if (entity instanceof Enemy) {
                if (showHostiles) { r = 1; g = 0.5f; b = 0; valid = true; }
            } else {
                if (showPassives) { r = 0; g = 1; b = 0; valid = true; }
            }

            if (valid) {
                entitiesFound++;

                double x = Mth.lerp(tickDelta, entity.xOld, entity.getX()) - camPos.x;
                double y = Mth.lerp(tickDelta, entity.yOld, entity.getY()) - camPos.y;
                double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ()) - camPos.z;

                float w = entity.getBbWidth() / 2f;
                float h = entity.getBbHeight();

                drawFilledBox(new Matrix4f(), buffer, (float) x - w, (float) y, (float) x + w, (float) y + h, (float) z - w, (float) z + w, r, g, b, alphaValue);
            }
        }

        if (entitiesFound > 0) {
            submitDraw(client);
        }
    }

    private void drawFilledBox(Matrix4f matrix, BufferBuilder buffer, float minX, float minY, float maxX, float maxY, float minZ, float maxZ, float r, float g, float b, float a) {
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

    private void submitDraw(Minecraft client) {
        MeshData builtBuffer = buffer.build();
        if (builtBuffer == null) return;
        int vertexBufferSize = builtBuffer.drawState().vertexCount() * builtBuffer.drawState().format().getVertexSize();

        if (vertexBuffer == null || vertexBuffer.size() < vertexBufferSize) {
            if (vertexBuffer != null) vertexBuffer.close();
            vertexBuffer = new MappableRingBuffer(() -> "esp_filled_render", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST, vertexBufferSize);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        encoder.writeToBuffer(
                vertexBuffer.currentBuffer().slice(0L, (long) builtBuffer.vertexBuffer().remaining()),
                builtBuffer.vertexBuffer()
        );

        // Dynamic Transforms für das DEBUG_FILLED_SNIPPET generieren
        Vector4f colorModulator = new Vector4f(1f, 1f, 1f, 1f);
        Vector3f modelOffset = new Vector3f(0f, 0f, 0f);
        Matrix4f textureMatrix = new Matrix4f();
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), colorModulator, modelOffset, textureMatrix);

        try (RenderPass pass = encoder.createRenderPass(() -> "esp_filled_pass", client.gameRenderer.mainRenderTarget().getColorTextureView(), Optional.empty(), client.gameRenderer.mainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(FILLED_PIPELINE);

            // WICHTIG: Uniforms an den Render-Pass übergeben, damit der Shader weiß, wohin projiziert werden soll
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamicTransforms);

            pass.setVertexBuffer(0, vertexBuffer.currentBuffer().slice());

            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);

            pass.setIndexBuffer(shapeIndexBuffer.getBuffer(builtBuffer.drawState().indexCount()), shapeIndexBuffer.type());
            pass.drawIndexed(builtBuffer.drawState().indexCount(), 1, 0, 0, 0);
        }

        builtBuffer.close();
        vertexBuffer.rotate();
        buffer = null;
    }
}