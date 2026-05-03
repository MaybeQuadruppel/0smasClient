package com.qdrppl.newbridge.Hacks.Combat.Util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Util {
    private static final double GRAVITY = 0.05;
    private static final double DRAG = 0.99;
    private static final int MAX_TICKS = 1200;

    public static BlockPos predictLanding(LivingEntity entity, float speed) {
        Level world = entity.level();
        if (world == null) return null;

        Vec3 pos = new Vec3(entity.getX(), entity.getEyeY() - 0.1, entity.getZ());
        float pitch = entity.getXRot();
        float yaw = entity.getYRot();

        float vx = -Mth.sin(yaw * 0.017453292F) * Mth.cos(pitch * 0.017453292F);
        float vy = -Mth.sin(pitch * 0.017453292F);
        float vz =  Mth.cos(yaw * 0.017453292F) * Mth.cos(pitch * 0.017453292F);

        Vec3 vel = new Vec3(vx, vy, vz).multiply(speed, speed, speed);
        Vec3 shooterVel = entity.getDeltaMovement();
        vel = vel.add(shooterVel.x, entity.onGround() ? 0.0 : shooterVel.y, shooterVel.z);

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            Vec3 nextPos = pos.add(vel);
            BlockHitResult hit = world.clip(new ClipContext(
                    pos, nextPos,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    entity
            ));

            if (hit.getType() == HitResult.Type.BLOCK) {
                return hit.getBlockPos();
            }

            pos = nextPos;
            vel = new Vec3(vel.x * DRAG, vel.y * DRAG - GRAVITY, vel.z * DRAG);
            if (pos.y < world.getMinY() - 64) break;
        }
        return null;
    }
}