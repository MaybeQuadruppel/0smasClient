package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A* pathfinder.
 *
 * MAX_ITERATIONS controls how far one segment goes.
 * The Navigator automatically chains segments for long distances.
 */
public final class Pathfinder {

    // At ~20 moves/sec and WALK_ONE_BLOCK_COST ≈ 20 ticks, 50k iterations
    // covers roughly 200-400 blocks per segment — plenty for smooth chaining.
    private static final int    MAX_ITERATIONS = 50_000;
    private static final double MIN_IMPROVEMENT = 0.01;
    private static final int    PATH_CLEARANCE_SQ = 2 * 2;

    private Pathfinder() {}

    public static PathResult calculatePath(Minecraft mc, BlockPos start, Goal goal) {
        if (mc.level == null) return PathResult.EMPTY;

        BinaryHeapOpenSet openSet = new BinaryHeapOpenSet();
        Long2ObjectOpenHashMap<PathNode> nodeMap = new Long2ObjectOpenHashMap<>(4096, 0.75f);

        PathNode startNode = getOrCreate(nodeMap, start.getX(), start.getY(), start.getZ(), goal);
        startNode.cost         = 0;
        startNode.combinedCost = startNode.estimatedCostToGoal;
        openSet.insert(startNode);

        PathNode bestNode  = startNode;
        int      iterations = 0;

        while (!openSet.isEmpty() && iterations < MAX_ITERATIONS) {
            iterations++;
            PathNode current = openSet.removeLowest();

            if (goal.isInGoal(current.x, current.y, current.z)) {
                return buildPath(mc, current, goal);
            }

            if (current.estimatedCostToGoal < bestNode.estimatedCostToGoal) {
                bestNode = current;
            }

            for (MoveType move : MoveType.values()) {
                MoveResult res = move.apply(mc, current.x, current.y, current.z);
                if (res.isImpossible()) continue;

                BlockPos dp   = res.dest;
                if (MoveBlacklist.isBlocked(current.x, current.y, current.z, dp.getX(), dp.getY(), dp.getZ())) continue;
                if (isTooCloseToEarlierPath(current, dp)) continue;
                PathNode neighbor = getOrCreate(nodeMap, dp.getX(), dp.getY(), dp.getZ(), goal);

                double tentativeCost = current.cost + res.cost;
                if (neighbor.cost - tentativeCost > MIN_IMPROVEMENT) {
                    neighbor.previous    = current;
                    neighbor.moveFromPrevious = res;
                    neighbor.cost        = tentativeCost;
                    neighbor.combinedCost = tentativeCost + neighbor.estimatedCostToGoal;

                    if (neighbor.isOpen()) {
                        openSet.update(neighbor);
                    } else {
                        openSet.insert(neighbor);
                    }
                }
            }
        }

        // Return best partial path — Navigator will chain the next segment
        if (bestNode != startNode) {
            return buildPath(mc, bestNode, goal);
        }
        return PathResult.EMPTY;
    }

    private static PathResult buildPath(Minecraft mc, PathNode endNode, Goal goal) {
        List<PathNode> nodes = new ArrayList<>();
        PathNode cur = endNode;
        while (cur != null) {
            nodes.add(cur);
            cur = cur.previous;
        }
        Collections.reverse(nodes);

        if (nodes.size() < 2) return PathResult.EMPTY;

        List<BlockPos>   waypoints = new ArrayList<>();
        List<MoveResult> moves     = new ArrayList<>();

        waypoints.add(new BlockPos(nodes.get(0).x, nodes.get(0).y, nodes.get(0).z));

        for (int i = 0; i < nodes.size() - 1; i++) {
            PathNode from = nodes.get(i);
            PathNode to   = nodes.get(i + 1);
            BlockPos dest = new BlockPos(to.x, to.y, to.z);

            waypoints.add(dest);
            moves.add(to.moveFromPrevious == null
                    ? new MoveResult(dest, ActionCosts.WALK_ONE_BLOCK_COST, new BlockPos[0])
                    : to.moveFromPrevious);
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

    private static boolean isTooCloseToEarlierPath(PathNode current, BlockPos candidate) {
        PathNode earlier = current.previous;
        int ignoredRecentNodes = 0;
        while (earlier != null) {
            if (ignoredRecentNodes++ >= 2) {
                int dx = candidate.getX() - earlier.x;
                int dy = candidate.getY() - earlier.y;
                int dz = candidate.getZ() - earlier.z;
                if (dx * dx + dy * dy + dz * dz <= PATH_CLEARANCE_SQ) return true;
            }
            earlier = earlier.previous;
        }
        return false;
    }
}
