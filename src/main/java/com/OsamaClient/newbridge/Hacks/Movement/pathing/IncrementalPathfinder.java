package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A* pathfinder that runs in slices on the main thread.
 * Call tick(maxIterations) each game tick until it returns non-null.
 *
 * Running on the main thread fixes thread-safety issues with mc.level
 * that caused the pathfinder to only find 1-step paths.
 */
public final class IncrementalPathfinder {

    private static final double MIN_IMPROVEMENT = 0.01;
    private static final int    MAX_TOTAL_ITERATIONS = 100_000;

    private final Minecraft mc;
    private final Goal      goal;

    private final BinaryHeapOpenSet              openSet;
    private final Long2ObjectOpenHashMap<PathNode> nodeMap;

    private PathNode bestNode;
    private int      totalIterations = 0;
    private boolean  done            = false;

    public IncrementalPathfinder(Minecraft mc, BlockPos start, Goal goal) {
        this.mc   = mc;
        this.goal = goal;

        this.openSet = new BinaryHeapOpenSet();
        this.nodeMap = new Long2ObjectOpenHashMap<>(4096, 0.75f);

        PathNode startNode = getOrCreate(start.getX(), start.getY(), start.getZ());
        startNode.cost         = 0;
        startNode.combinedCost = startNode.estimatedCostToGoal;
        openSet.insert(startNode);

        this.bestNode = startNode;
    }

    /**
     * Run up to maxIterations A* steps.
     * Returns a PathResult when done (goal reached or max iterations hit), null otherwise.
     */
    public PathResult tick(int maxIterations) {
        if (done) return buildResult();

        int i = 0;
        while (!openSet.isEmpty() && i < maxIterations && totalIterations < MAX_TOTAL_ITERATIONS) {
            i++;
            totalIterations++;

            PathNode current = openSet.removeLowest();

            if (goal.isInGoal(current.x, current.y, current.z)) {
                bestNode = current;
                done = true;
                return buildResult();
            }

            if (current.estimatedCostToGoal < bestNode.estimatedCostToGoal) {
                bestNode = current;
            }

            for (MoveType move : MoveType.values()) {
                MoveResult res = move.apply(mc, current.x, current.y, current.z);
                if (res.isImpossible()) continue;

                BlockPos dp       = res.dest;
                PathNode neighbor = getOrCreate(dp.getX(), dp.getY(), dp.getZ());

                double tentativeCost = current.cost + res.cost;
                if (neighbor.cost - tentativeCost > MIN_IMPROVEMENT) {
                    neighbor.previous     = current;
                    neighbor.cost         = tentativeCost;
                    neighbor.combinedCost = tentativeCost + neighbor.estimatedCostToGoal;

                    if (neighbor.isOpen()) {
                        openSet.update(neighbor);
                    } else {
                        openSet.insert(neighbor);
                    }
                }
            }
        }

        // Hit iteration limit — return partial path
        if (openSet.isEmpty() || totalIterations >= MAX_TOTAL_ITERATIONS) {
            done = true;
            return buildResult();
        }

        return null; // still running
    }

    private PathResult buildResult() {
        if (bestNode == null) return PathResult.EMPTY;

        List<PathNode> nodes = new ArrayList<>();
        PathNode cur = bestNode;
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

    private PathNode getOrCreate(int x, int y, int z) {
        long hash = PathNode.longHash(x, y, z);
        PathNode node = nodeMap.get(hash);
        if (node == null) {
            node = new PathNode(x, y, z, goal);
            nodeMap.put(hash, node);
        }
        return node;
    }
}