package com.qdrppl.newbridge.Hacks.Movement.pathing;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A* pathfinder.
 * Modeled after Baritone's AStarPathFinder / AbstractNodeCostSearch.
 *
 * Key differences from the old version:
 *  - Uses BinaryHeapOpenSet instead of PriorityQueue (O(log n) decrease-key)
 *  - Uses Long2ObjectOpenHashMap for O(1) node lookup (like Baritone)
 *  - Each move type (MoveType enum) computes its own cost and positionsToBreak
 *  - Returns a PathResult containing both the waypoints AND the MoveResult
 *    for each step (so MovementExecutor knows exactly what to mine)
 */
public final class Pathfinder {

    private static final int MAX_ITERATIONS = 10_000;
    private static final double MIN_IMPROVEMENT = 0.01;

    private Pathfinder() {}

    /**
     * Calculate a path from start to goal.
     * Returns an empty PathResult if no path found.
     */
    public static PathResult calculatePath(Minecraft mc, BlockPos start, Goal goal) {
        if (mc.level == null) return PathResult.EMPTY;

        BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
        Long2ObjectOpenHashMap<PathNode> nodeMap = new Long2ObjectOpenHashMap<>(4096, 0.75f);

        PathNode startNode = getOrCreate(nodeMap, start.getX(), start.getY(), start.getZ(), goal);
        startNode.cost = 0;
        startNode.combinedCost = startNode.estimatedCostToGoal;
        openSet.insert(startNode);

        PathNode bestNode = startNode;
        int iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            PathNode current = openSet.removeLowest();

            if (goal.isInGoal(current.x, current.y, current.z)) {
                return buildPath(mc, current, nodeMap, goal);
            }

            if (current.estimatedCostToGoal < bestNode.estimatedCostToGoal) {
                bestNode = current;
            }

            // Try all move types from the current node
            for (MoveType move : MoveType.values()) {
                MoveResult res = move.apply(mc, current.x, current.y, current.z);
                if (res.isImpossible()) continue;

                BlockPos dp = res.dest;
                long hash = PathNode.longHash(dp.getX(), dp.getY(), dp.getZ());
                PathNode neighbor = getOrCreate(nodeMap, dp.getX(), dp.getY(), dp.getZ(), goal);

                double tentativeCost = current.cost + res.cost;
                if (neighbor.cost - tentativeCost > MIN_IMPROVEMENT) {
                    neighbor.previous = current;
                    neighbor.cost = tentativeCost;
                    neighbor.combinedCost = tentativeCost + neighbor.estimatedCostToGoal;

                    if (neighbor.isOpen()) {
                        openSet.update(neighbor);
                    } else {
                        openSet.insert(neighbor);
                    }
                }
            }
        }

        // Return best partial path if we didn't reach the goal
        if (bestNode != startNode) {
            return buildPath(mc, bestNode, nodeMap, goal);
        }
        return PathResult.EMPTY;
    }

    private static PathResult buildPath(Minecraft mc, PathNode endNode,
                                         Long2ObjectOpenHashMap<PathNode> nodeMap, Goal goal) {
        // Reconstruct node chain
        List<PathNode> nodes = new ArrayList<>();
        PathNode cur = endNode;
        while (cur != null) {
            nodes.add(cur);
            cur = cur.previous;
        }
        Collections.reverse(nodes);

        if (nodes.size() < 2) return PathResult.EMPTY;

        // Re-evaluate each step to get the MoveResult (positionsToBreak etc.)
        List<BlockPos> waypoints = new ArrayList<>();
        List<MoveResult> moves = new ArrayList<>();

        waypoints.add(new BlockPos(nodes.get(0).x, nodes.get(0).y, nodes.get(0).z));

        for (int i = 0; i < nodes.size() - 1; i++) {
            PathNode from = nodes.get(i);
            PathNode to   = nodes.get(i + 1);
            BlockPos dest = new BlockPos(to.x, to.y, to.z);

            // Find which MoveType produced this step
            MoveResult bestMove = null;
            for (MoveType mt : MoveType.values()) {
                MoveResult res = mt.apply(mc, from.x, from.y, from.z);
                if (!res.isImpossible() && res.dest.equals(dest)) {
                    bestMove = res;
                    break;
                }
            }

            if (bestMove == null) {
                bestMove = new MoveResult(dest, ActionCosts.WALK_ONE_BLOCK_COST, new BlockPos[0]);
            }

            waypoints.add(dest);
            moves.add(bestMove);
        }

        return new PathResult(waypoints, moves);
    }

    private static PathNode getOrCreate(Long2ObjectOpenHashMap<PathNode> map,
                                         int x, int y, int z, Goal goal) {
        long hash = PathNode.longHash(x, y, z);
        PathNode node = map.get(hash);
        if (node == null) {
            node = new PathNode(x, y, z, goal);
            map.put(hash, node);
        }
        return node;
    }
}
