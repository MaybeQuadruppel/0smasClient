package com.OsamaClient.newbridge.Hacks.Visual;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
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
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;

public class Tracers extends Module {
    public static Tracers INSTANCE;

    public enum Target {
        Head,
        Body,
        Feet
    }

    public float alphaValue = 0.8f;
    public float range = 128.0f;

    public Target target = Target.Body;
    public boolean stem = true;
    public boolean ignoreSelf = true;
    public boolean showInvisible = true;

    public EntityFilterPicker targetPicker;

    private static final RenderPipeline TRACER_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("newbridge", "tracer_filled"))
                    .withDepthStencilState(Optional.empty())
                    .build()
    );

    private static final ByteBufferBuilder allocator =
            new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);

    private BufferBuilder buffer;
    private MappableRingBuffer vertexBuffer;

    private int count;

    public Tracers() {
        super(
                "Tracers",
                "Draws tracer lines to selected entities.",
                Category.VISUAL
        );

        INSTANCE = this;

        this.targetPicker = new EntityFilterPicker("Targets");

        this.settings.add(
                this.targetPicker.withDescription(
                        "Selects which entity types should receive tracers."
                )
        );

        this.settings.add(
                new Slider(
                        "Alpha",
                        0.0,
                        1.0,
                        (double) alphaValue,
                        val -> alphaValue = val.floatValue()
                ).withDescription("Controls tracer transparency.")
        );

        this.settings.add(
                new Slider(
                        "Range",
                        1.0,
                        128.0,
                        (double) range,
                        val -> range = val.floatValue()
                ).withDescription("Maximum tracer distance.")
        );
    }

    public static Tracers getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new Tracers();
        }

        return INSTANCE;
    }

    public void init(Minecraft client) {
        INSTANCE = this;
        LevelRenderEvents.END_MAIN.register(this::render);
    }

    private void render(LevelRenderContext context) {
        if (!this.enabled) return;

        Minecraft client = Minecraft.getInstance();

        if (client.level == null || client.player == null) {
            return;
        }

        Vec3 camPos = context.levelState().cameraRenderState.pos;

        float tickDelta = client
                .getDeltaTracker()
                .getGameTimeDeltaTicks();

        if (buffer == null) {
            buffer = new BufferBuilder(
                    allocator,
                    PrimitiveTopology.QUADS,
                    DefaultVertexFormat.POSITION_COLOR
            );
        }

        count = 0;

        AABB searchBox = client.player
                .getBoundingBox()
                .inflate(range);

        for (Entity entity : client.level.getEntities(null, searchBox)) {
            if (!(entity instanceof LivingEntity)) continue;
            if (!entity.isAlive()) continue;

            if (ignoreSelf && entity == client.player) {
                continue;
            }

            if (!showInvisible && entity.isInvisible()) {
                continue;
            }

            String filterKey = getFilterKey(entity);

            if (filterKey == null) {
                continue;
            }

            if (targetPicker == null ||
                    !targetPicker.isFilterEnabled(filterKey)) {
                continue;
            }

            int argb = targetPicker.getColor(filterKey);

            float r = ((argb >> 16) & 0xFF) / 255.0f;
            float g = ((argb >> 8) & 0xFF) / 255.0f;
            float b = (argb & 0xFF) / 255.0f;
            float a = (((argb >> 24) & 0xFF) / 255.0f) * alphaValue;

            double x = Mth.lerp(
                    tickDelta,
                    entity.xOld,
                    entity.getX()
            ) - camPos.x;

            double y = Mth.lerp(
                    tickDelta,
                    entity.yOld,
                    entity.getY()
            ) - camPos.y;

            double z = Mth.lerp(
                    tickDelta,
                    entity.zOld,
                    entity.getZ()
            ) - camPos.z;

            double height = entity.getBoundingBox().maxY
                    - entity.getBoundingBox().minY;

            switch (target) {
                case Head -> y += height;

                case Body -> y += height / 2.0;

                case Feet -> {
                    // Kein Offset
                }
            }

            drawTracer(
                    new Matrix4f(),
                    buffer,
                    (float) x,
                    (float) y,
                    (float) z,
                    0.025f,
                    r,
                    g,
                    b,
                    a
            );

            if (stem) {
                drawStem(
                        new Matrix4f(),
                        buffer,
                        (float) (
                                Mth.lerp(
                                        tickDelta,
                                        entity.xOld,
                                        entity.getX()
                                ) - camPos.x
                        ),
                        (float) (
                                Mth.lerp(
                                        tickDelta,
                                        entity.yOld,
                                        entity.getY()
                                ) - camPos.y
                        ),
                        (float) (
                                Mth.lerp(
                                        tickDelta,
                                        entity.zOld,
                                        entity.getZ()
                                ) - camPos.z
                        ),
                        (float) height,
                        0.025f,
                        r,
                        g,
                        b,
                        a
                );
            }

            count++;
        }

        if (count > 0) {
            submitDraw(client);
        }
    }

    private String getFilterKey(Entity entity) {
        if (entity instanceof Player) {
            return "Players";
        }

        if (entity instanceof ArmorStand) {
            return "ArmorStands";
        }

        if (entity instanceof Enemy) {
            return "Hostiles";
        }

        if (entity instanceof Animal) {
            return "Animals";
        }

        if (entity instanceof Villager ||
                entity instanceof WanderingTrader) {
            return "NPCs";
        }

        return null;
    }

    private void drawTracer(
            Matrix4f matrix,
            BufferBuilder buffer,
            float x2,
            float y2,
            float z2,
            float thickness,
            float r,
            float g,
            float b,
            float a
    ) {
        Vector3f dir = new Vector3f(x2, y2, z2);

        if (dir.lengthSquared() < 0.000001f) {
            return;
        }

        dir.normalize();

        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f();

        dir.cross(up, right);

        if (right.lengthSquared() < 0.001f) {
            right.set(1, 0, 0);
        } else {
            right.normalize();
        }

        right.cross(dir, up);
        up.normalize();

        right.mul(thickness);
        up.mul(thickness);

        float sx1 = -right.x - up.x;
        float sy1 = -right.y - up.y;
        float sz1 = -right.z - up.z;

        float sx2 = right.x - up.x;
        float sy2 = right.y - up.y;
        float sz2 = right.z - up.z;

        float sx3 = right.x + up.x;
        float sy3 = right.y + up.y;
        float sz3 = right.z + up.z;

        float sx4 = -right.x + up.x;
        float sy4 = -right.y + up.y;
        float sz4 = -right.z + up.z;

        float ex1 = x2 + sx1;
        float ey1 = y2 + sy1;
        float ez1 = z2 + sz1;

        float ex2 = x2 + sx2;
        float ey2 = y2 + sy2;
        float ez2 = z2 + sz2;

        float ex3 = x2 + sx3;
        float ey3 = y2 + sy3;
        float ez3 = z2 + sz3;

        float ex4 = x2 + sx4;
        float ey4 = y2 + sy4;
        float ez4 = z2 + sz4;

        // Seite 1
        buffer.addVertex(matrix, sx1, sy1, sz1)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, sx2, sy2, sz2)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, ex2, ey2, ez2)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, ex1, ey1, ez1)
                .setColor(r, g, b, a);

        // Seite 2
        buffer.addVertex(matrix, sx2, sy2, sz2)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, sx3, sy3, sz3)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, ex3, ey3, ez3)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, ex2, ey2, ez2)
                .setColor(r, g, b, a);

        // Seite 3
        buffer.addVertex(matrix, sx3, sy3, sz3)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, sx4, sy4, sz4)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, ex4, ey4, ez4)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, ex3, ey3, ez3)
                .setColor(r, g, b, a);

        // Seite 4
        buffer.addVertex(matrix, sx4, sy4, sz4)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, sx1, sy1, sz1)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, ex1, ey1, ez1)
                .setColor(r, g, b, a);

        buffer.addVertex(matrix, ex4, ey4, ez4)
                .setColor(r, g, b, a);
    }

    private void drawStem(
            Matrix4f matrix,
            BufferBuilder buffer,
            float x,
            float y,
            float z,
            float height,
            float thickness,
            float r,
            float g,
            float b,
            float a
    ) {
        drawTracer(
                matrix,
                buffer,
                x,
                y + height,
                z,
                thickness,
                r,
                g,
                b,
                a
        );
    }

    private void submitDraw(Minecraft client) {
        MeshData builtBuffer = buffer.build();

        if (builtBuffer == null) {
            buffer = null;
            return;
        }

        int vertexBufferSize =
                builtBuffer.drawState().vertexCount()
                        * builtBuffer.drawState()
                        .format()
                        .getVertexSize();

        if (vertexBuffer == null ||
                vertexBuffer.size() < vertexBufferSize) {

            if (vertexBuffer != null) {
                vertexBuffer.close();
            }

            vertexBuffer = new MappableRingBuffer(
                    () -> "tracers_render",
                    GpuBuffer.USAGE_VERTEX
                            | GpuBuffer.USAGE_MAP_WRITE
                            | GpuBuffer.USAGE_COPY_DST,
                    vertexBufferSize
            );
        }

        CommandEncoder encoder =
                RenderSystem.getDevice().createCommandEncoder();

        encoder.writeToBuffer(
                vertexBuffer.currentBuffer().slice(
                        0L,
                        (long) builtBuffer.vertexBuffer().remaining()
                ),
                builtBuffer.vertexBuffer()
        );

        Vector4f colorModulator =
                new Vector4f(1f, 1f, 1f, 1f);

        Vector3f modelOffset =
                new Vector3f(0f, 0f, 0f);

        Matrix4f textureMatrix =
                new Matrix4f();

        GpuBufferSlice dynamicTransforms =
                RenderSystem.getDynamicUniforms().writeTransform(
                        RenderSystem.getModelViewMatrixCopy(),
                        colorModulator,
                        modelOffset,
                        textureMatrix
                );

        try (
                RenderPass pass = encoder.createRenderPass(
                        () -> "tracers_pass",
                        client.gameRenderer
                                .mainRenderTarget()
                                .getColorTextureView(),
                        Optional.empty(),
                        client.gameRenderer
                                .mainRenderTarget()
                                .getDepthTextureView(),
                        OptionalDouble.empty()
                )
        ) {
            pass.setPipeline(TRACER_PIPELINE);

            RenderSystem.bindDefaultUniforms(pass);

            pass.setUniform(
                    "DynamicTransforms",
                    dynamicTransforms
            );

            pass.setVertexBuffer(
                    0,
                    vertexBuffer.currentBuffer().slice()
            );

            RenderSystem.AutoStorageIndexBuffer indexBuffer =
                    RenderSystem.getSequentialBuffer(
                            PrimitiveTopology.QUADS
                    );

            pass.setIndexBuffer(
                    indexBuffer.getBuffer(
                            builtBuffer.drawState().indexCount()
                    ),
                    indexBuffer.type()
            );

            pass.drawIndexed(
                    builtBuffer.drawState().indexCount(),
                    1,
                    0,
                    0,
                    0
            );
        }

        builtBuffer.close();

        vertexBuffer.rotate();

        buffer = null;
    }

    public int getCount() {
        return count;
    }
}