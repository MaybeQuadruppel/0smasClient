package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import com.OsamaClient.newbridge.Hacks.Combat.AimAssist;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Alles rund um "auf ein sichtbares Face schauen und genau dieses Face benutzen":
 *
 *  - rayFromLook():   was trifft der Blick des Spielers GERADE (eigener Raycast, kein mc.hitResult,
 *                     das kann um einen Frame hinterherhinken)
 *  - findMineFace():  sichtbares Face eines Blocks + Zielpunkt darauf (zum Abbauen)
 *  - findPlaceFace(): sichtbares Face eines Nachbarblocks, an dem man einen Block platzieren kann
 *  - smoothLookAt():  menschlich wirkende Rotation (GCD-gerundet wie die Maus)
 */
public final class BlockInteraction {

    /** Etwas kleiner als die echte Reichweite (4.5): Anti-Cheats rechnen mit der Server-Position, die etwas hinterherhinkt. */
    public static final double REACH = 4.5;

    /** Abtastpunkte pro Face (Mitte zuerst, dann Richtung Ecken). */
    private static final double[] GRID = {0.5, 0.28, 0.72};

    public record AimTarget(BlockPos block, Direction face, Vec3 point) {}

    private record Scored(AimTarget aim, double score) {}

    private BlockInteraction() {}

    // ─── Raycast ──────────────────────────────────────────────────────────────

    private static BlockHitResult clip(Minecraft mc, Vec3 from, Vec3 to) {
        return mc.level.clip(new ClipContext(from, to,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    /** Block, auf den der Spieler mit seiner aktuellen Blickrichtung zeigt (oder null). */
    public static BlockHitResult rayFromLook(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 end  = eyes.add(mc.player.getLookAngle().scale(REACH));
        BlockHitResult hit = clip(mc, eyes, end);
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    // ─── Face search ──────────────────────────────────────────────────────────

    /** Bestes sichtbares Face zum Abbauen von {@code target}, oder null wenn keins sichtbar/erreichbar ist. */
    public static AimTarget findMineFace(Minecraft mc, BlockPos target) {
        if (mc.player == null || mc.level == null) return null;
        Scored best = null;
        for (Direction dir : Direction.values()) {
            Scored s = searchFace(mc, target, dir);
            if (s != null && (best == null || s.score < best.score)) best = s;
        }
        return best == null ? null : best.aim;
    }

    /**
     * Bestes sichtbares Face, um an {@code placePos} einen Block zu platzieren.
     * Sucht einen festen Nachbarblock und dessen Face, das zu placePos zeigt.
     */
    public static AimTarget findPlaceFace(Minecraft mc, BlockPos placePos) {
        if (mc.player == null || mc.level == null) return null;
        Scored best = null;
        for (Direction dir : Direction.values()) {
            BlockPos support = placePos.relative(dir);
            if (!isSupport(mc, support)) continue;
            Scored s = searchFace(mc, support, dir.getOpposite());
            if (s != null && (best == null || s.score < best.score)) best = s;
        }
        return best == null ? null : best.aim;
    }

    /** Fester, ungefährlicher Block, an dem man platzieren darf (kein GUI-Block, keine Flüssigkeit). */
    public static boolean isSupport(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return false;
        BlockState st = mc.level.getBlockState(pos);
        if (st.isAir() || !st.getFluidState().isEmpty()) return false;
        if (st.getBlock() instanceof EntityBlock || st.getBlock() instanceof CraftingTableBlock) return false;
        return st.isCollisionShapeFullBlock(mc.level, pos);
    }

    private static Scored searchFace(Minecraft mc, BlockPos block, Direction face) {
        Vec3 eyes   = mc.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(block);

        // Augen müssen VOR der Face-Ebene liegen, sonst ist das Face von hinten.
        double side = (eyes.x - center.x) * face.getStepX()
                + (eyes.y - center.y) * face.getStepY()
                + (eyes.z - center.z) * face.getStepZ();
        if (side <= 0.5) return null;

        Vec3 look = mc.player.getLookAngle();
        Scored best = null;

        for (double u : GRID) {
            for (double v : GRID) {
                Vec3 p    = facePoint(block, face, u, v);
                Vec3 dir  = p.subtract(eyes);
                double dist = dir.length();
                if (dist < 1.0E-4 || dist > REACH) continue;

                Vec3 end = eyes.add(dir.scale((dist + 0.6) / dist));
                BlockHitResult hit = clip(mc, eyes, end);
                if (hit.getType() != HitResult.Type.BLOCK) continue;
                if (!hit.getBlockPos().equals(block) || hit.getDirection() != face) continue;
                if (eyes.distanceTo(hit.getLocation()) > REACH) continue;

                // Score: möglichst wenig drehen
                double score = 1.0 - look.dot(dir.scale(1.0 / dist));
                if (best == null || score < best.score) {
                    best = new Scored(new AimTarget(block, face, p), score);
                }
            }
        }
        return best;
    }

    private static Vec3 facePoint(BlockPos p, Direction d, double u, double v) {
        double x = p.getX() + 0.5, y = p.getY() + 0.5, z = p.getZ() + 0.5;
        switch (d.getAxis()) {
            case X -> { x += d.getStepX() * 0.5; y += u - 0.5; z += v - 0.5; }
            case Y -> { y += d.getStepY() * 0.5; x += u - 0.5; z += v - 0.5; }
            default -> { z += d.getStepZ() * 0.5; x += u - 0.5; y += v - 0.5; }
        }
        return new Vec3(x, y, z);
    }

    // ─── Rotation ─────────────────────────────────────────────────────────────

    /**
     * Dreht den Spieler schrittweise zum Zielpunkt (GCD-gerundet wie echte Mausbewegung).
     * @return verbleibender Winkel in Grad VOR diesem Schritt
     */
    public static double smoothLookAt(Minecraft mc, Vec3 targetPos) {
        return AimAssist.smoothLookAt(mc, targetPos, 110.0f, 2.0f);
    }
}
