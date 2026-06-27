package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.core.BlockPos;

/**
 * Goal: get within 'range' blocks of a target position.
 * Useful for AutoMiner (don't need to stand ON the ore, just next to it).
 */
public class GoalNear implements Goal, ActionCosts {

    public final int x, y, z;
    public final int rangeSq;

    public GoalNear(BlockPos pos, int range) {
        this.x = pos.getX();
        this.y = pos.getY();
        this.z = pos.getZ();
        this.rangeSq = range * range;
    }

    @Override
    public boolean isInGoal(BlockPos pos) {
        int dx = pos.getX() - x;
        int dy = pos.getY() - y;
        int dz = pos.getZ() - z;
        return (dx * dx + dy * dy + dz * dz) <= rangeSq;
    }

    @Override
    public double heuristic(BlockPos pos) {
        int dx = pos.getX() - x;
        int dy = pos.getY() - y;
        int dz = pos.getZ() - z;
        // Reduce heuristic by range so it doesn't overshoot
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz) - Math.sqrt(rangeSq);
        if (dist <= 0) return 0;
        return GoalBlock.calculate(dx, dy, dz) * 0.8; // admissible underestimate
    }

    @Override
    public String toString() {
        return "GoalNear{x=" + x + ", y=" + y + ", z=" + z + ", range=" + (int)Math.sqrt(rangeSq) + "}";
    }
}
