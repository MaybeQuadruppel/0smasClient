package com.qdrppl.newbridge.Hacks.Combat.Util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AutoCartUtil {
    public static BlockPos predictLanding(LivingEntity entity, float power) {
        Level world = entity.level();
        Vec3 pos = new Vec3(entity.getX(), entity.getEyeY() - 0.1, entity.getZ());

        float speed = power * 3.0f;
        float yaw = entity.getYRot();
        float pitch = entity.getXRot();

        double vx = -Mth.sin(yaw * 0.017453292F) * Mth.cos(pitch * 0.017453292F);
        double vy = -Mth.sin(pitch * 0.017453292F);
        double vz =  Mth.cos(yaw * 0.017453292F) * Mth.cos(pitch * 0.017453292F);

        Vec3 arrowVel = new Vec3(vx, vy, vz).normalize().scale(speed);

        Vec3 playerVel = entity.getDeltaMovement();

        Vec3 finalVel = arrowVel.add(playerVel.x, entity.onGround() ? 0.0 : playerVel.y, playerVel.z);

        for (int i = 0; i < 200; i++) {
            Vec3 nextPos = pos.add(finalVel);
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

            finalVel = finalVel.scale(0.99);
            finalVel = finalVel.subtract(0, 0.05, 0);

            if (pos.y < world.getMinY()) break;
        }
        return null;
    }
}