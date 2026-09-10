package com.OsamaClient.newbridge.Hacks.Visual.ESP;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.ItemPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public class ItemESP extends Module {
    public static ItemESP INSTANCE;

    public float range = 128;
    public double outlineWidth = 2.0;
    public double tracerWidth = 2.0;
    public String renderMode = "Both";
    public boolean renderTracers = false;
    public ItemPicker itemPicker;
    private final ChamsBufferSource bufferSource = new ChamsBufferSource();

    public ItemESP() {
        super("ItemESP", "Highlights dropped items on the ground.", Category.VISUAL);
        INSTANCE = this;

        this.itemPicker = new ItemPicker("Items Filter");
        this.settings.add(this.itemPicker.withDescription("Select items to highlight. Right-click selected items to change color!"));

        this.settings.add(new Slider("Range", 1.0, 128.0, (double) range, val -> range = val.floatValue())
                .withDescription("Sets the maximum distance at which items are highlighted."));

        this.settings.add(new Slider("Outline Width", 0.5, 10.0, outlineWidth, val -> outlineWidth = val)
                .withDescription("Sets the line thickness for item box outlines."));

        this.settings.add(new Slider("Tracer Width", 0.5, 10.0, tracerWidth, val -> tracerWidth = val)
                .withDescription("Sets the line thickness for tracers."));

        List<String> modes = List.of("Fill", "Outline", "Both", "None");
        this.settings.add(new ModeButton("Mode", modes, modes.indexOf(renderMode), val -> renderMode = val)
                .withDescription("Selects box rendering style (Fill, Outline, Both, None)."));

        this.settings.add(new ToggleButton("Tracers", renderTracers, val -> renderTracers = val)
                .withDescription("Draws tracer lines to items."));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    public static ItemESP getInstance() {
        if (INSTANCE == null) INSTANCE = new ItemESP();
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

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            // Distanz Check
            if (client.player.distanceToSqr(itemEntity) > range * range) continue;

            Item rawItem = itemEntity.getItem().getItem();

            // Standardfarbe, falls keine Filter aktiv sind
            int outlineColor = 0xFFFFD700;

            // Filter Logik und Farbabruf
            if (!itemPicker.selectedItems.isEmpty()) {
                if (!itemPicker.selectedItems.containsKey(rawItem)) {
                    continue; // Überspringen, wenn es nicht in der Liste ist
                }
                // Hol dir die im Picker eingestellte Farbe
                outlineColor = itemPicker.selectedItems.get(rawItem);
            }

            int fillColor = getAdjustedColor(outlineColor, 0.4f);

            if (drawFill) {
                VertexConsumer fillConsumer = bufferSource.getBuffer(RenderTypes.storageEspFillSeeThrough());
                renderBox(poseStack, fillConsumer, itemEntity, tickDelta, camX, camY, camZ, fillColor, true, (float) outlineWidth);
            }

            if (drawOutline) {
                VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
                renderBox(poseStack, lineConsumer, itemEntity, tickDelta, camX, camY, camZ, outlineColor, false, (float) outlineWidth);
            }

            if (renderTracers) {
                VertexConsumer tracerConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());

                double x = Mth.lerp(tickDelta, itemEntity.xo, itemEntity.getX()) - camX;
                double y = Mth.lerp(tickDelta, itemEntity.yo, itemEntity.getY()) - camY;
                double z = Mth.lerp(tickDelta, itemEntity.zo, itemEntity.getZ()) - camZ;

                float targetX = (float) x;
                float targetY = (float) (y + itemEntity.getBbHeight() / 2f);
                float targetZ = (float) z;

                Matrix4f matrix = poseStack.last().pose();
                Matrix3f normalMatrix = poseStack.last().normal();
                line(matrix, normalMatrix, tracerConsumer, startX, startY, startZ, targetX, targetY, targetZ, outlineColor, (float) tracerWidth);
            }
        }

        bufferSource.uploadAndDraw();
    }

    private void renderBox(PoseStack poseStack, VertexConsumer consumer, ItemEntity entity, float tickDelta, double camX, double camY, double camZ, int color, boolean isFill, float lineWidth) {
        double x = Mth.lerp(tickDelta, entity.xo, entity.getX()) - camX;
        double y = Mth.lerp(tickDelta, entity.yo, entity.getY()) - camY;
        double z = Mth.lerp(tickDelta, entity.zo, entity.getZ()) - camZ;

        float w = entity.getBbWidth() / 2f;
        float h = entity.getBbHeight();

        poseStack.pushPose();
        poseStack.translate(x, y, z);

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        float x1 = -w, y1 = 0f, z1 = -w;
        float x2 = w, y2 = h, z2 = w;

        if (isFill) {
            renderFilledBox(matrix, normalMatrix, consumer, x1, y1, z1, x2, y2, z2, color);
        } else {
            renderBoxOutline(matrix, normalMatrix, consumer, x1, y1, z1, x2, y2, z2, color, lineWidth);
        }

        poseStack.popPose();
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

    private static void renderBoxOutline(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color, float lineWidth) {
        line(matrix, normalMatrix, consumer, x1, y1, z1, x2, y1, z1, color, lineWidth);
        line(matrix, normalMatrix, consumer, x2, y1, z1, x2, y1, z2, color, lineWidth);
        line(matrix, normalMatrix, consumer, x2, y1, z2, x1, y1, z2, color, lineWidth);
        line(matrix, normalMatrix, consumer, x1, y1, z2, x1, y1, z1, color, lineWidth);
        line(matrix, normalMatrix, consumer, x1, y2, z1, x2, y2, z1, color, lineWidth);
        line(matrix, normalMatrix, consumer, x2, y2, z1, x2, y2, z2, color, lineWidth);
        line(matrix, normalMatrix, consumer, x2, y2, z2, x1, y2, z2, color, lineWidth);
        line(matrix, normalMatrix, consumer, x1, y2, z2, x1, y2, z1, color, lineWidth);
        line(matrix, normalMatrix, consumer, x1, y1, z1, x1, y2, z1, color, lineWidth);
        line(matrix, normalMatrix, consumer, x2, y1, z1, x2, y2, z1, color, lineWidth);
        line(matrix, normalMatrix, consumer, x2, y1, z2, x2, y2, z2, color, lineWidth);
        line(matrix, normalMatrix, consumer, x1, y1, z2, x1, y2, z2, color, lineWidth);
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color, float lineWidth) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        line(matrix, normalMatrix, consumer, x1, y1, z1, x2, y2, z2, r, g, b, a, lineWidth);
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             int r, int g, int b, int a, float lineWidth) {
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

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z()).setLineWidth(lineWidth);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z()).setLineWidth(lineWidth);
    }
}