package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Optional;

public class Trajectories extends Module {

    // Statische Referenz auf das aktuell im Visier befindliche Entity
    public static Entity targetedEntity = null;

    private int trajColor = 0xFFA000FF;
    private boolean showPath = true;

    public Trajectories() {
        super("Trajectories", "Predicts the flight path of arrows and targets", Category.VISUAL);

        this.settings.add(new ColorPicker("Color", trajColor, (newColor) -> this.trajColor = newColor).withDescription("Sets the color of the trajectory prediction line."));
        this.settings.add(new ToggleButton("Show Path", showPath, (val) -> this.showPath = val).withDescription("Enables or disables rendering of the flight path."));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        targetedEntity = null; // In jedem Frame zurücksetzen

        if (!this.enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.gameRenderer.mainCamera() == null) return;

        ItemStack stack = client.player.getUseItem();
        if (!(stack.getItem() instanceof BowItem)) return;

        int useDuration = client.player.getUseItemRemainingTicks();
        int ticksHeld = stack.getUseDuration(client.player) - useDuration;
        float pullProgress = BowItem.getPowerForTime(ticksHeld);

        if (pullProgress < 0.1f) return;

        double velocityMag = pullProgress * 3.0f;
        int r = (trajColor >> 16) & 0xFF;
        int g = (trajColor >> 8) & 0xFF;
        int b = trajColor & 0xFF;

        Vec3 pos = client.player.getEyePosition();
        Vec3 motion = client.player.getLookAngle().scale(velocityMag);

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        ChamsBufferSource bufferSource = new ChamsBufferSource();
        Camera camera = client.gameRenderer.mainCamera();
        double camX = camera.position().x;
        double camY = camera.position().y;
        double camZ = camera.position().z;

        VertexConsumer fillConsumer = bufferSource.getBuffer(RenderTypes.storageEspFillSeeThrough());

        for (int i = 0; i < 200; i++) {
            Vec3 nextPos = pos.add(motion);

            // 1. Prüfe, ob ein Block getroffen wurde
            BlockHitResult blockHit = client.level.clip(new ClipContext(
                    pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player
            ));

            // 2. Erstelle eine Box exakt für diesen einen Flug-Schritt der Trajectory-Linie
            net.minecraft.world.phys.AABB segmentBox = new net.minecraft.world.phys.AABB(pos, nextPos).inflate(1.0);

            EntityHitResult entityHit = null;
            double closestDistance = Double.MAX_VALUE;

            // 3. Suche manuell und präzise alle Entities, die sich in DIESER Box befinden (egal wie weit weg vom Spieler)
            for (Entity entity : client.level.getEntities(client.player, segmentBox, e -> !e.isSpectator() && e.isPickable() && e.isAlive())) {
                net.minecraft.world.phys.AABB entityBox = entity.getBoundingBox().inflate(0.3); // Die Hitbox für Pfeile etwas vergrößern (wie im echten Spiel)
                Optional<Vec3> hitPos = entityBox.clip(pos, nextPos);

                if (hitPos.isPresent()) {
                    double dist = pos.distanceToSqr(hitPos.get());
                    if (dist < closestDistance) {
                        closestDistance = dist;
                        entityHit = new EntityHitResult(entity, hitPos.get());
                    }
                }
            }

            // 4. Prüfen, was zuerst getroffen wurde: Block oder Entity?
            if (entityHit != null) {
                if (blockHit.getType() == HitResult.Type.MISS || pos.distanceToSqr(entityHit.getLocation()) < pos.distanceToSqr(blockHit.getLocation())) {
                    targetedEntity = entityHit.getEntity();
                    break; // Treffer! Brich die Berechnungslinie sofort ab
                }
            }

            if (blockHit.getType() != HitResult.Type.MISS) {
                renderLandingBlock(matrix, normalMatrix, fillConsumer, blockHit, camX, camY, camZ, r, g, b);
                break; // Block getroffen! Brich die Berechnung ab
            }

            // 5. Rendere die Route
            if (showPath) {
                double rx = pos.x - camX;
                double ry = pos.y - camY;
                double rz = pos.z - camZ;

                double size = 0.08;
                renderFilledBox(matrix, normalMatrix, fillConsumer,
                        (float)(rx - size), (float)(ry - size), (float)(rz - size),
                        (float)(rx + size), (float)(ry + size), (float)(rz + size),
                        (200 << 24) | (r << 16) | (g << 8) | b);
            }

            // 6. Werte für den nächsten Frame berechnen (Gravitation & Reibung)
            pos = nextPos;
            motion = motion.scale(0.99).subtract(0, 0.05, 0);

            if (pos.y < client.level.getMinY()) break; // Wenn es ins Nichts fällt, abbrechen
        }

        bufferSource.uploadAndDraw();
    }

    private void renderLandingBlock(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer fillConsumer, BlockHitResult hit, double camX, double camY, double camZ, int r, int g, int b) {
        Direction side = hit.getDirection();
        Vec3 landingPos = Vec3.atLowerCornerOf(hit.getBlockPos()).add(side.getStepX(), side.getStepY(), side.getStepZ());

        double lx = landingPos.x + 0.5 - camX;
        double ly = landingPos.y + 0.5 - camY;
        double lz = landingPos.z + 0.5 - camZ;

        double size = 0.5;
        float x1 = (float)(lx - size), y1 = (float)(ly - size), z1 = (float)(lz - size);
        float x2 = (float)(lx + size), y2 = (float)(ly + size), z2 = (float)(lz + size);

        renderFilledBox(matrix, normalMatrix, fillConsumer, x1, y1, z1, x2, y2, z2, (120 << 24) | (r << 16) | (g << 8) | b);
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
}