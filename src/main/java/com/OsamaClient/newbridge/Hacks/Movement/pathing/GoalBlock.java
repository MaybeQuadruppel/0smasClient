package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.core.BlockPos;

/**
 * Goal: reach a specific block position (feet level).
 */
public class GoalBlock implements Goal, ActionCosts {

    public final int x, y, z;

    public GoalBlock(BlockPos pos) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
    }

    public GoalBlock(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean isInGoal(BlockPos pos) {
        return pos.getX() == x && pos.getY() == y && pos.getZ() == z;
    }

    @Override
    public double heuristic(BlockPos pos) {
        return calculate(pos.getX() - x, pos.getY() - y, pos.getZ() - z);
    }

    /**
     * Octile heuristic: optimal for 4-directional + vertical movement.
     * Adapted from Baritone's GoalBlock.calculate().
     */
    public static double calculate(int xDiff, int yDiff, int zDiff) {
        int xAbs = Math.abs(xDiff);
        int zAbs = Math.abs(zDiff);

        // Octile distance (4 straight + 4 diagonal moves)
        int straight = Math.abs(xAbs - zAbs);
        int diagonal = Math.min(xAbs, zAbs);
        double flatCost = straight * WALK_ONE_BLOCK_COST + diagonal * WALK_DIAGONAL_COST;

        // Vertical cost
        double vertCost = yDiff < 0
                ? -yDiff * JUMP_ONE_BLOCK_COST                // Ziel liegt höher -> springen
                : yDiff * FALL_0_25_BLOCKS_COST;              // Ziel liegt tiefer -> fallen (günstig)

        return flatCost + vertCost;
    }

    @Override
    public String toString() {
        return "GoalBlock{x=" + x + ", y=" + y + ", z=" + z + "}";
    }
}
