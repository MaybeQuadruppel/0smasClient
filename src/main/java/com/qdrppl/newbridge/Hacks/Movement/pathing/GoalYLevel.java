package com.qdrppl.newbridge.Hacks.Movement.pathing;

import net.minecraft.core.BlockPos;

public class GoalYLevel implements Goal, ActionCosts {

    public final int targetY;

    public GoalYLevel(int y) {
        this.targetY = y;
    }

    @Override
    public boolean isInGoal(BlockPos pos) {
        return pos.getY() == targetY;
    }

    @Override
    public double heuristic(BlockPos pos) {
        int dy = pos.getY() - targetY;
        if (dy == 0) return 0;
        // Going up costs jump, going down costs fall
        return dy > 0
                ? dy * FALL_1_25_BLOCKS_COST   // need to descend
                : Math.abs(dy) * JUMP_ONE_BLOCK_COST; // need to ascend
    }

    @Override
    public String toString() {
        return "GoalYLevel{y=" + targetY + "}";
    }
}