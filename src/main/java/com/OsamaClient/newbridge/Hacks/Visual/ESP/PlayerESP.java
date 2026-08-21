package com.OsamaClient.newbridge.Hacks.Visual.ESP;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Visual.Trajectories;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public class PlayerESP extends Module {
    public static PlayerESP INSTANCE;

    public float range = 128;
    public String renderMode = "Both";
    public boolean renderTracers = false;
    public EntityFilterPicker targetPicker;

    public PlayerESP() {
        super("EntityESP", "Lets you See Entities by their Threatlevels", Category.VISUAL);
        INSTANCE = this;

        this.targetPicker = new EntityFilterPicker("Targets");
        this.settings.add(this.targetPicker.withDescription("Selects which entity types to highlight with ESP."));

        this.settings.add(new Slider("Range", 1.0, 128.0, (double) range, val -> range = val.floatValue())
                .withDescription("Sets the maximum distance at which entities are highlighted."));

        List<String> modes = List.of("Fill", "Outline", "Both", "None");
        this.settings.add(new ModeButton("Mode", modes, modes.indexOf(renderMode), val -> renderMode = val)
                .withDescription("Selects box rendering style (Fill, Outline, Both, None)."));

        this.settings.add(new ToggleButton("Tracers", renderTracers, val -> renderTracers = val)
                .withDescription("Draws tracer lines to entities."));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    public static PlayerESP getInstance() {
        if (INSTANCE == null) INSTANCE = new PlayerESP();
        return INSTANCE;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.gameRenderer.mainCamera() == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = client.gameRenderer.mainCamera();
        double camX = camera.position().x;
        double camY = camera.position().y;
        double camZ = camera.position().z;

        float tickDelta = event.getTickDelta();

        boolean drawFill = (renderMode.equals("Fill") || renderMode.equals("Both")) && !renderMode.equals("None");
        boolean drawOutline = (renderMode.equals("Outline") || renderMode.equals("Both")) && !renderMode.equals("None");

        ChamsBufferSource bufferSource = new ChamsBufferSource();

        float startX = 0f, startY = 0f, startZ = 0f;
        if (renderTracers) {
            float pitch = camera.xRot();
            float yaw = camera.yRot();
            float f = (float) Math.PI / 180.0F;

            float dirX = -((float) Math.sin(yaw * f)) * ((float) Math.cos(pitch * f));
            float dirY = -((float) Math.sin(pitch * f));
            float dirZ = ((float) Math.cos(yaw * f)) * ((float) Math.cos(pitch * f));

            float offset = 50.0f;
            startX = dirX * offset;
            startY = dirY * offset;
            startZ = dirZ * offset;
        }

        // --- SCHLEIFE 1: FILLS ---
        if (drawFill) {
            VertexConsumer fillConsumer = bufferSource.getBuffer(RenderTypes.storageEspFillSeeThrough());
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living) || entity == client.player || !entity.isAlive()) continue;

                boolean isTrajTarget = (Trajectories.targetedEntity == entity);

                // Ignoriere die Range-Sperre, wenn das Entity im Visier ist!
                if (!isTrajTarget && client.player.distanceToSqr(entity) > range * range) continue;

                String filterKey = getFilterKey(entity);
                boolean isEnabledInPicker = filterKey != null && targetPicker != null && targetPicker.isFilterEnabled(filterKey);

                if (isTrajTarget || isEnabledInPicker) {
                    int color = isTrajTarget ? 0x80FF0000 : getAdjustedColor(targetPicker.getColor(filterKey), 0.5f);
                    renderRotatedBox(poseStack, fillConsumer, living, tickDelta, camX, camY, camZ, color, true);
                }
            }
        }

        // --- SCHLEIFE 2: OUTLINES ---
        if (drawOutline) {
            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living) || entity == client.player || !entity.isAlive()) continue;

                boolean isTrajTarget = (Trajectories.targetedEntity == entity);
                if (!isTrajTarget && client.player.distanceToSqr(entity) > range * range) continue;

                String filterKey = getFilterKey(entity);
                boolean isEnabledInPicker = filterKey != null && targetPicker != null && targetPicker.isFilterEnabled(filterKey);

                if (isTrajTarget || isEnabledInPicker) {
                    int color = isTrajTarget ? 0xFFFF0000 : getAdjustedColor(targetPicker.getColor(filterKey), 1.0f);
                    renderRotatedBox(poseStack, lineConsumer, living, tickDelta, camX, camY, camZ, color, false);
                }
            }
        }

        // --- SCHLEIFE 3: TRACERS ---
        if (renderTracers) {
            VertexConsumer tracerConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living) || entity == client.player || !entity.isAlive()) continue;

                boolean isTrajTarget = (Trajectories.targetedEntity == entity);
                if (!isTrajTarget && client.player.distanceToSqr(entity) > range * range) continue;

                String filterKey = getFilterKey(entity);
                boolean isEnabledInPicker = filterKey != null && targetPicker != null && targetPicker.isFilterEnabled(filterKey);

                if (isTrajTarget || isEnabledInPicker) {
                    int color = isTrajTarget ? 0xFFFF0000 : getAdjustedColor(targetPicker.getColor(filterKey), 1.0f);

                    double x = Mth.lerp(tickDelta, living.xo, living.getX()) - camX;
                    double y = Mth.lerp(tickDelta, living.yo, living.getY()) - camY;
                    double z = Mth.lerp(tickDelta, living.zo, living.getZ()) - camZ;

                    float targetX = (float) x;
                    float targetY = (float) (y + living.getBbHeight() / 2f);
                    float targetZ = (float) z;

                    Matrix4f matrix = poseStack.last().pose();
                    Matrix3f normalMatrix = poseStack.last().normal();
                    line(matrix, normalMatrix, tracerConsumer, startX, startY, startZ, targetX, targetY, targetZ, color);
                }
            }
        }

        bufferSource.uploadAndDraw();
    }

    private void renderRotatedBox(PoseStack poseStack, VertexConsumer consumer, LivingEntity entity, float tickDelta, double camX, double camY, double camZ, int color, boolean isFill) {
        double x = Mth.lerp(tickDelta, entity.xo, entity.getX()) - camX;
        double y = Mth.lerp(tickDelta, entity.yo, entity.getY()) - camY;
        double z = Mth.lerp(tickDelta, entity.zo, entity.getZ()) - camZ;

        float w = entity.getBbWidth() / 2f;
        float h = entity.getBbHeight();
        float yaw = Mth.lerp(tickDelta, entity.yBodyRotO, entity.yBodyRot);

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        float x1 = -w, y1 = 0f, z1 = -w;
        float x2 = w, y2 = h, z2 = w;

        if (isFill) {
            renderFilledBox(matrix, normalMatrix, consumer, x1, y1, z1, x2, y2, z2, color);
        } else {
            renderBoxOutline(matrix, normalMatrix, consumer, x1, y1, z1, x2, y2, z2, color);
        }

        poseStack.popPose();
    }

    private String getFilterKey(Entity entity) {
        if (entity instanceof Player) return "Players";
        if (entity instanceof ArmorStand) return "ArmorStands";
        if (entity instanceof Enemy) return "Hostiles";
        if (entity instanceof Animal) return "Animals";
        if (entity instanceof Villager || entity instanceof WanderingTrader) return "NPCs";
        return null;
    }

    private int getAdjustedColor(int argb, float alphaMultiplier) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        if (a == 102 || a <= 5) {
            a = 255;
        }

        a = (int) (a * alphaMultiplier);
        return (a << 24) | (r << 16) | (g << 8) | b;
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
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float x3, float y3, float z3, float x4, float y4, float z4,
                                float nx, float ny, float nz, int r, int g, int b, int a) {
        Vector3f normal = new Vector3f(nx, ny, nz);
        normal.mul(normalMatrix);

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
    }

    private static void renderBoxOutline(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        line(matrix, normalMatrix, consumer, x1, y1, z1, x2, y1, z1, color);
        line(matrix, normalMatrix, consumer, x2, y1, z1, x2, y1, z2, color);
        line(matrix, normalMatrix, consumer, x2, y1, z2, x1, y1, z2, color);
        line(matrix, normalMatrix, consumer, x1, y1, z2, x1, y1, z1, color);

        line(matrix, normalMatrix, consumer, x1, y2, z1, x2, y2, z1, color);
        line(matrix, normalMatrix, consumer, x2, y2, z1, x2, y2, z2, color);
        line(matrix, normalMatrix, consumer, x2, y2, z2, x1, y2, z2, color);
        line(matrix, normalMatrix, consumer, x1, y2, z2, x1, y2, z1, color);

        line(matrix, normalMatrix, consumer, x1, y1, z1, x1, y2, z1, color);
        line(matrix, normalMatrix, consumer, x2, y1, z1, x2, y2, z1, color);
        line(matrix, normalMatrix, consumer, x2, y1, z2, x2, y2, z2, color);
        line(matrix, normalMatrix, consumer, x1, y1, z2, x1, y1, z2, color);
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        line(matrix, normalMatrix, consumer, x1, y1, z1, x2, y2, z2, r, g, b, a);
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
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

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z()).setLineWidth(1.0f);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z()).setLineWidth(1.0f);
    }
}