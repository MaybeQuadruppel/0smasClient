package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.core.BlockPos;

/**
 * The result of evaluating a MoveType at a given position.
 * Contains the destination, total cost, and which blocks need to be mined.
 */
public final class MoveResult {

    public static final MoveResult IMPOSSIBLE = new MoveResult(null, ActionCosts.COST_INF, new BlockPos[0]);

    /** Where the player ends up after this move */
    public final BlockPos dest;

    /** Total cost of this move (movement + mining) */
    public final double cost;

    /**
     * Blocks that must be mined before this move can execute.
     * ORDER MATTERS — they should be mined in array order.
     */
    public final BlockPos[] positionsToBreak;

    public MoveResult(BlockPos dest, double cost, BlockPos[] positionsToBreak) {
        this.dest = dest;
        this.cost = cost;
        this.positionsToBreak = positionsToBreak;
    }

    public boolean isImpossible() {
        return cost >= ActionCosts.COST_INF || dest == null;
    }
}
