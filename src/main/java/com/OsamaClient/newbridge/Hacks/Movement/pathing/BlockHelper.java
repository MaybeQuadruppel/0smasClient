package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

/**
 * Static helpers for block evaluation.
 * Used by MoveType cost calculations and MovementExecutor.
 */
public final class BlockHelper {

    private static final double MAX_REACH_SQ = 4.5 * 4.5;

    private static final Set<net.minecraft.world.level.block.Block> FORBIDDEN = Set.of(
            Blocks.BEDROCK,
            Blocks.LAVA,
            Blocks.WATER // don't try to mine water source blocks
    );

    private BlockHelper() {}

    /**
     * Returns the cost to mine this block in ticks.
     * Returns COST_INF if the block is impassable/forbidden.
     * Returns 0 if the block is already air/passable.
     *
     * Falling blocks (Gravel, Sand, etc.) get extra cost because:
     * - After mining the block below them they fall down
     * - This can block the path or hurt the player
     */
    public static double miningCost(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return ActionCosts.COST_INF;
        if (!mc.level.isLoaded(pos)) return 0; // unloaded = assume passable (will recheck later)

        BlockState state = mc.level.getBlockState(pos);

        if (state.isAir()) return 0;
        if (FORBIDDEN.contains(state.getBlock())) return ActionCosts.COST_INF;

        // Lava check via fluid state
        if (state.getFluidState().is(FluidTags.LAVA)) return ActionCosts.COST_INF;

        // Unbreakable
        float hardness = state.getDestroySpeed(mc.level, pos);
        if (hardness < 0) return ActionCosts.COST_INF;

        // Water = passable but slow
        if (state.getFluidState().is(FluidTags.WATER)) return 0;

        // Hardness-based cost (calibrated: stone with iron pickaxe ≈ 7.5 ticks)
        double cost = hardness * 30.0 + ActionCosts.MINING_OVERHEAD;

        // Falling block penalty: check if this block or anything above it is a falling block
        // that would drop into our path after mining
        if (isFallingBlock(state)) {
            cost += ActionCosts.FALLING_BLOCK_PENALTY;
        }

        return cost;
    }

    /**
     * Checks if mining the block at `pos` would cause falling blocks above it
     * to drop down and block the path.
     *
     * Returns COST_INF if an unminable falling block column is above,
     * otherwise returns the extra cost to also mine the falling blocks.
     *
     * Call this in addition to miningCost() when clearing a block that has
     * something above it (e.g. in traverse/ascend for destFeet and destHead).
     */
    public static double fallingBlockColumnCost(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return 0;

        double extraCost = 0;
        BlockPos check = pos.above();

        // Scan upward until we hit air or a non-falling solid block
        for (int i = 0; i < 32; i++) {
            if (!mc.level.isLoaded(check)) break;

            BlockState above = mc.level.getBlockState(check);

            if (above.isAir()) break; // column ends, nothing will fall

            if (isFallingBlock(above)) {
                // This gravel/sand will fall into our path – add mining cost for it
                float hardness = above.getDestroySpeed(mc.level, check);
                if (hardness < 0) return ActionCosts.COST_INF; // unbreakable falling block (shouldn't happen but safety check)
                extraCost += hardness * 30.0 + ActionCosts.FALLING_BLOCK_PENALTY;
                check = check.above();
            } else {
                break;
            }
        }

        return extraCost;
    }

    /**
     * True if the player can walk through this block without mining
     * (air, water, most plants, etc.)
     */
    public static boolean fullyPassable(Minecraft mc, BlockPos pos) {
        return miningCost(mc, pos) == 0;
    }

    /**
     * Returns true if this block is a gravity-affected falling block
     * (Gravel, Sand, Red Sand, Concrete Powder, Anvil, etc.)
     */
    public static boolean isFallingBlock(BlockState state) {
        return state.getBlock() instanceof FallingBlock;
    }

    /**
     * True if mining the block at pos is safe – i.e. no falling block column
     * above it would drop into an unminable position.
     */
    public static boolean isSafeToMine(Minecraft mc, BlockPos pos) {
        return fallingBlockColumnCost(mc, pos) < ActionCosts.COST_INF;
    }

    /**
     * Turns the player to look at a block's center.
     */
    public static void lookAt(Minecraft mc, BlockPos target) {
        if (mc.player == null) return;
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);

        double dx = center.x - eyes.x;
        double dy = center.y - eyes.y;
        double dz = center.z - eyes.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, dxz)));

        mc.player.setYRot(yaw);
        mc.player.setXRot(net.minecraft.util.Mth.clamp(pitch, -90, 90));
    }

    /**
     * Returns true if the block is within mining reach of the player.
     */
    public static boolean canReach(Minecraft mc, BlockPos pos) {
        if (mc.player == null) return false;
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        return eyes.distanceToSqr(center) <= MAX_REACH_SQ;
    }

    public static boolean isForbidden(BlockState state) {
        return FORBIDDEN.contains(state.getBlock())
                || state.getFluidState().is(FluidTags.LAVA);
    }
}