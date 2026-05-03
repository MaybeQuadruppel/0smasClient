package com.qdrppl.newbridge.Hacks.Visual;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ColorPicker;
import com.qdrppl.newbridge.UI.components.ToggleButton;
import com.qdrppl.newbridge.Utils.RenderBlock;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext; // world -> level
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;  // world -> level
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
        super("Trajectories", "Predicts the flight path of arrows and targets.", Category.VISUAL);

        this.settings.add(new ColorPicker("Color", trajColor, (newColor) -> this.trajColor = newColor));
        this.settings.add(new ToggleButton("Show Path", showPath, (val) -> this.showPath = val));

        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(context -> {
            if (!this.enabled) return;
            renderTrajectory(context);
        });
    }

    private void renderTrajectory(LevelRenderContext context) { // WorldRenderContext -> LevelRenderContext
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        ItemStack stack = client.player.getUseItem();
        if (!(stack.getItem() instanceof BowItem)) return;

        int useDuration = client.player.getUseItemRemainingTicks();
        int ticksHeld = stack.getUseDuration(client.player) - useDuration;
        float pullProgress = BowItem.getPowerForTime(ticksHeld);

        if (pullProgress < 0.1f) return;

        double velocityMag = pullProgress * 3.0f;
        float r = ((trajColor >> 16) & 0xFF) / 255f;
        float g = ((trajColor >> 8) & 0xFF) / 255f;
        float b = (trajColor & 0xFF) / 255f;

        Vec3 pos = client.player.getEyePosition();
        Vec3 motion = client.player.getLookAngle().scale(velocityMag);

        RenderBlock.begin();

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
                    renderEntityIndicator(context, entityHit.getEntity(), r, g, b, 0.6f);
                    break;
                }
            }

            if (blockHit.getType() != HitResult.Type.MISS) {
                renderLandingBlock(context, blockHit, r, g, b, 0.6f);
                break;
            }

            if (showPath) {
                RenderBlock.renderPoint(context, pos, 0.08f, r, g, b, 0.4f);
            }

            pos = nextPos;
            // Die Physik-Werte bleiben identisch zu 1.21
            motion = motion.scale(0.99).subtract(0, 0.05, 0);

            if (pos.y < client.level.getMinY()) break;
        }

        RenderBlock.draw(client);
    }

    private void renderEntityIndicator(LevelRenderContext context, Entity target, float r, float g, float b, float a) {
        RenderBlock.renderPoint(context, target.position().add(0, target.getBbHeight() / 2, 0),
                target.getBbHeight(), r, g, b, a);

        RenderBlock.renderPoint(context, target.getEyePosition(), 0.15f, 1f, 0f, 0f, 1f);
    }

    private void renderLandingBlock(LevelRenderContext context, BlockHitResult hit, float r, float g, float b, float a) {
        Direction side = hit.getDirection();
        Vec3 landingPos = Vec3.atLowerCornerOf(hit.getBlockPos()).add(side.getStepX(), side.getStepY(), side.getStepZ());
        RenderBlock.renderPoint(context, landingPos.add(0.5, 0.5, 0.5), 1.0f, r, g, b, a);
    }
}