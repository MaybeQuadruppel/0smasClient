package com.qdrppl.newbridge.Hacks.Movement.pathing;

/**
 * A node in the A* path search.
 * Stores position, costs, and parent link.
 * Modeled after Baritone's PathNode.
 */
public final class PathNode implements Comparable<PathNode> {

    public final int x, y, z;

    /** g: cost from start to this node */
    public double cost;

    /** h: heuristic estimate to goal (cached) */
    public final double estimatedCostToGoal;

    /** f = g + h */
    public double combinedCost;

    /** Parent node for path reconstruction */
    public PathNode previous;

    /** Position in the binary heap (-1 = not in heap) */
    public int heapPosition = -1;

    public PathNode(int x, int y, int z, Goal goal) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.cost = ActionCosts.COST_INF;
        this.estimatedCostToGoal = goal.heuristic(x, y, z);
        this.combinedCost = this.cost + this.estimatedCostToGoal;
        this.previous = null;
    }

    public boolean isOpen() {
        return heapPosition != -1;
    }

    @Override
    public int compareTo(PathNode other) {
        return Double.compare(this.combinedCost, other.combinedCost);
    }

    /** Fast long hash for use in HashMap — from Baritone's BetterBlockPos */
    public static long longHash(int x, int y, int z) {
        // Pack into a single long: 26 bits x, 12 bits y, 26 bits z
        return (((long) x & 0x3FFFFFF) << 38) | (((long) y & 0xFFF) << 26) | ((long) z & 0x3FFFFFF);
    }

    @Override
    public int hashCode() {
        return (int) longHash(x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PathNode)) return false;
        PathNode other = (PathNode) obj;
        return x == other.x && y == other.y && z == other.z;
    }
}
