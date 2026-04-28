package com.qdrppl.newbridge.Hacks.Visual.ESP;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;
import com.qdrppl.newbridge.UI.components.ToggleButton;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
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
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public class PlayerESP extends Module {
    public static PlayerESP INSTANCE;

    public boolean showPlayers = true;
    public boolean showHostiles = true;
    public boolean showPassives = true;
    public float alphaValue = 0.4f;

    private static final RenderPipeline FILLED_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("newbridge", "esp_filled"))
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build()
    );

    private static final ByteBufferBuilder allocator = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
    private BufferBuilder buffer;
    private MappableRingBuffer vertexBuffer;

    public PlayerESP() {
        super("EnitiyESP","(Lets you See Enitys by their Threadlevels)", Category.VISUAL);
        INSTANCE = this;

        this.settings.add(new ToggleButton("Show Players", showPlayers, val -> showPlayers = (boolean)val));
        this.settings.add(new ToggleButton("Show Hostiles", showHostiles, val -> showHostiles = (boolean)val));
        this.settings.add(new ToggleButton("Show Passives", showPassives, val -> showPassives = (boolean)val));
        this.settings.add(new Slider("Alpha", 0.0, 1.0, (double) alphaValue, val -> alphaValue = val.floatValue()));
    }

    public static PlayerESP getInstance() {
        if (INSTANCE == null) INSTANCE = new PlayerESP();
        return INSTANCE;
    }


    public void init(Minecraft client) {
        INSTANCE = this;
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(this::render);
    }

    private void render(WorldRenderContext context) {
        if (!this.enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        Vec3 camPos = context.worldState().cameraRenderState.pos;
        PoseStack matrices = context.matrices();
        float tickDelta = client.getDeltaTracker().getRealtimeDeltaTicks();

        if (buffer == null) {
            buffer = new BufferBuilder(allocator, VertexFormat.Mode.QUADS, FILLED_PIPELINE.getVertexFormat());
        }

        matrices.pushPose();
        matrices.translate((float)-camPos.x, (float)-camPos.y, (float)-camPos.z);

        int entitiesFound = 0;
        for (Entity entity : client.level.entitiesForRendering()) {
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
                double x = Mth.lerp(tickDelta, entity.xo, entity.getX());
                double y = Mth.lerp(tickDelta, entity.yo, entity.getY());
                double z = Mth.lerp(tickDelta, entity.zo, entity.getZ());
                float w = entity.getBbWidth() / 2f;
                float h = entity.getBbHeight();
                drawFilledBox(matrices.last().pose(), buffer, (float)x - w, (float)y, (float)z - w, (float)x + w, (float)y + h, (float)z + w, r, g, b, alphaValue);
            }
        }
        matrices.popPose();

        if (entitiesFound > 0) {
            submitDraw(client);
        }
    }

    private void drawFilledBox(Matrix4f matrix, BufferBuilder buffer, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float r, float g, float b, float a) {
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
            vertexBuffer = new MappableRingBuffer(() -> "esp_filled_render", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, vertexBufferSize);
        }
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (GpuBuffer.MappedView mappedView = encoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, builtBuffer.vertexBuffer().remaining()), false, true)) {
            MemoryUtil.memCopy(builtBuffer.vertexBuffer(), mappedView.data());
        }
        try (RenderPass pass = encoder.createRenderPass(() -> "esp_filled_pass", client.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(), client.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(FILLED_PIPELINE);
            pass.setVertexBuffer(0, vertexBuffer.currentBuffer());
            RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            pass.setIndexBuffer(shapeIndexBuffer.getBuffer(builtBuffer.drawState().indexCount()), shapeIndexBuffer.type());
            pass.drawIndexed(0, 0, builtBuffer.drawState().indexCount(), 1);
        }
        builtBuffer.close();
        vertexBuffer.rotate();
        buffer = null;
    }
}