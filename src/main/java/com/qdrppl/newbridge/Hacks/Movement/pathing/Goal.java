package com.qdrppl.newbridge.Hacks.Movement.pathing;


import net.minecraft.core.BlockPos;

public interface Goal {

    boolean isInGoal(BlockPos pos);


    double heuristic(BlockPos pos);

    default boolean isInGoal(int x, int y, int z) {
        return isInGoal(new net.minecraft.core.BlockPos(x, y, z));
    }

    default double heuristic(int x, int y, int z) {
        return heuristic(new net.minecraft.core.BlockPos(x, y, z));
    }
}
