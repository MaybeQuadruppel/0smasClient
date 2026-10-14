package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Blocking convenience wrapper around {@link IncrementalPathfinder}.
 * The Navigator uses the incremental version directly; this exists for callers that
 * want a complete path in one call (same A*, same moves, same safety rules).
 */
public final class Pathfinder {

    private static final int MAX_ITERATIONS = 50_000;

    private Pathfinder() {}

    public static PathResult calculatePath(Minecraft mc, BlockPos start, Goal goal) {
        if (mc.level == null) return PathResult.EMPTY;
        IncrementalPathfinder finder = new IncrementalPathfinder(mc, start, goal);
        PathResult result = finder.tick(MAX_ITERATIONS);
        return result == null ? finder.finishNow() : result;
    }
}
