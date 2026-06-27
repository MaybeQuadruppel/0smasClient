package com.qdrppl.newbridge.Hacks.Movement.pathing;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * The result of a path calculation.
 *
 * Contains:
 *  - waypoints: the BlockPos positions to travel through (feet level)
 *  - moves:     the MoveResult for each step (what to mine, cost, etc.)
 *
 * moves.size() == waypoints.size() - 1
 * moves.get(i) is the move FROM waypoints.get(i) TO waypoints.get(i+1)
 */
public final class PathResult {

    public static final PathResult EMPTY = new PathResult(List.of(), List.of());

    public final List<BlockPos> waypoints;
    public final List<MoveResult> moves;

    public PathResult(List<BlockPos> waypoints, List<MoveResult> moves) {
        this.waypoints = waypoints;
        this.moves = moves;
    }

    public boolean isEmpty() {
        return waypoints.isEmpty() || moves.isEmpty();
    }

    public int length() {
        return waypoints.size();
    }

    /** Get the destination of this path */
    public BlockPos getDest() {
        return waypoints.isEmpty() ? null : waypoints.get(waypoints.size() - 1);
    }
}
