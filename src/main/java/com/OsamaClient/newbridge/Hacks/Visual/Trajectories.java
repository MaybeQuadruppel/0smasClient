package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.Render.Color;
import com.OsamaClient.newbridge.Utils.Render.Events.EventHandler;
import com.OsamaClient.newbridge.Utils.Render.Events.Render3DEvent;
import com.OsamaClient.newbridge.Utils.Render.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.Direction;

public class Trajectories extends Module {

    private int trajColor = 0xFFA000FF;
    private boolean showPath = true;

    public Trajectories() {
        super("Trajectories", "Predicts the flight path of arrows and targets", Category.VISUAL);

        this.settings.add(new ColorPicker("Color", trajColor, (newColor) -> this.trajColor = newColor).withDescription("Sets the color of the trajectory prediction line."));
        this.settings.add(new ToggleButton("Show Path", showPath, (val) -> this.showPath = val).withDescription("Enables or disables rendering of the flight path."));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

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

        for (int i = 0; i < 200; i++) {
            Vec3 nextPos = pos.add(motion);

            BlockHitResult blockHit = client.level.clip(new ClipContext(
                    pos, nextPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, client.player
            ));

            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    client.player,
                    pos,
                    nextPos,
                    client.player.getBoundingBox().expandTowards(motion).inflate(2.0D),
                    (e) -> !e.isSpectator() && e.isPickable() && e != client.player,
                    pos.distanceToSqr(nextPos)
            );

            if (entityHit != null) {
                if (blockHit.getType() == HitResult.Type.MISS || pos.distanceTo(entityHit.getLocation()) < pos.distanceTo(blockHit.getLocation())) {
                    renderEntityIndicator(event, entityHit.getEntity(), r, g, b);
                    break;
                }
            }

            if (blockHit.getType() != HitResult.Type.MISS) {
                renderLandingBlock(event, blockHit, r, g, b);
                break;
            }

            if (showPath) {
                // Weltposition relativ zur Kamera für den Punkt
                double rx = pos.x - event.offsetX;
                double ry = pos.y - event.offsetY;
                double rz = pos.z - event.offsetZ;

                double size = 0.08;
                event.renderer.boxNoDepth(
                        rx - size, ry - size, rz - size,
                        rx + size, ry + size, rz + size,
                        new Color(r, g, b, 100),
                        new Color(r, g, b, 255),
                        Renderer3D.ShapeMode.BOTH,
                        0
                );
            }

            pos = nextPos;
            motion = motion.scale(0.99).subtract(0, 0.05, 0);

            if (pos.y < client.level.getMinY()) break;
        }
    }

    private void renderEntityIndicator(Render3DEvent event, Entity target, int r, int g, int b) {
        double tx = target.getX() - event.offsetX;
        double ty = target.getY() - event.offsetY + (target.getBbHeight() / 2);
        double tz = target.getZ() - event.offsetZ;

        float w = target.getBbWidth() / 2f;
        float h = target.getBbHeight();

        // Zeigt eine Box um das getroffene Ziel
        event.renderer.boxNoDepth(
                tx - w, ty, tz - w,
                tx + w, ty + h, tz + w,
                new Color(r, g, b, 150),
                new Color(255, 0, 0, 255),
                Renderer3D.ShapeMode.BOTH,
                0
        );
    }

    private void renderLandingBlock(Render3DEvent event, BlockHitResult hit, int r, int g, int b) {
        Direction side = hit.getDirection();
        Vec3 landingPos = Vec3.atLowerCornerOf(hit.getBlockPos()).add(side.getStepX(), side.getStepY(), side.getStepZ());

        double lx = landingPos.x + 0.5 - event.offsetX;
        double ly = landingPos.y + 0.5 - event.offsetY;
        double lz = landingPos.z + 0.5 - event.offsetZ;

        double size = 0.5;
        event.renderer.boxNoDepth(
                lx - size, ly - size, lz - size,
                lx + size, ly + size, lz + size,
                new Color(r, g, b, 100),
                new Color(r, g, b, 255),
                Renderer3D.ShapeMode.BOTH,
                0
        );
    }
}