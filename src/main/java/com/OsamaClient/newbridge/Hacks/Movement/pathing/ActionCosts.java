package com.OsamaClient.newbridge.Hacks.Movement.pathing;

/**
 * Cost constants for the pathfinder.
 * Values are in ticks (20 ticks = 1 second).
 * Adapted from Baritone's ActionCosts.
 */
public interface ActionCosts {

    double COST_INF = 1_000_000;

    // Walk speed: 4.317 blocks/s → 20/4.317 ticks per block
    double WALK_ONE_BLOCK_COST     = 20.0 / 4.317;   // ~4.633
    double SPRINT_ONE_BLOCK_COST   = 20.0 / 5.612;   // ~3.564
    double WALK_ONE_IN_WATER_COST  = 20.0 / 2.2;     // ~9.091
    public static final double FALLING_BLOCK_PENALTY = 50.0;
    // Jump: from Baritone's distanceToTicks math
    double FALL_1_25_BLOCKS_COST   = distanceToTicks(1.25);
    double FALL_0_25_BLOCKS_COST   = distanceToTicks(0.25);
    double JUMP_ONE_BLOCK_COST     = FALL_1_25_BLOCKS_COST - FALL_0_25_BLOCKS_COST;

    double WATER_PENALTY           = WALK_ONE_IN_WATER_COST * 3.0;
    double MINING_OVERHEAD         = WALK_ONE_BLOCK_COST * 2.0;

    /**
     * Converts fall distance to ticks using Minecraft's velocity formula.
     * Directly from Baritone source.
     */
    static double distanceToTicks(double distance) {
        if (distance == 0) return 0;
        double tmpDistance = distance;
        int tickCount = 0;
        while (true) {
            double fallDistance = (Math.pow(0.98, tickCount) - 1) * -3.92;
            if (tmpDistance <= fallDistance) {
                return tickCount + tmpDistance / fallDistance;
            }
            tmpDistance -= fallDistance;
            tickCount++;
        }
    }
}
