package com.qdrppl.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * All movement types the pathfinder can consider.
 * Modeled after Baritone's Moves enum.
 *
 * Each move type knows:
 * - what blocks need to be cleared (positionsToBreak)
 * - what the cost is
 * - whether it's valid (solid floor, not impassable)
 */
public enum MoveType {

    // ─── Flat movement (4 cardinal directions) ───────────────────────────────

    TRAVERSE_NORTH(0, 0, -1) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            return traverse(mc, x, y, z, x, y, z - 1);
        }
    },
    TRAVERSE_SOUTH(0, 0, +1) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            return traverse(mc, x, y, z, x, y, z + 1);
        }
    },
    TRAVERSE_EAST(+1, 0, 0) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            return traverse(mc, x, y, z, x + 1, y, z);
        }
    },
    TRAVERSE_WEST(-1, 0, 0) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            return traverse(mc, x, y, z, x - 1, y, z);
        }
    },

    // ─── Ascend (+1y, 4 cardinal directions) – sehr teuer, Pathfinder bevorzugt PILLAR_UP ──

    ASCEND_NORTH(0, +1, -1) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            MoveResult base = ascend(mc, x, y, z, x, y - 1, z - 1);
            if (base == MoveResult.IMPOSSIBLE) return MoveResult.IMPOSSIBLE;
            return new MoveResult(base.dest, base.cost * 10.0, base.positionsToBreak);
        }
    },
    ASCEND_SOUTH(0, +1, +1) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            MoveResult base = ascend(mc, x, y, z, x, y - 1, z + 1);
            if (base == MoveResult.IMPOSSIBLE) return MoveResult.IMPOSSIBLE;
            return new MoveResult(base.dest, base.cost * 10.0, base.positionsToBreak);
        }
    },
    ASCEND_EAST(+1, +1, 0) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            MoveResult base = ascend(mc, x, y, z, x + 1, y - 1, z);
            if (base == MoveResult.IMPOSSIBLE) return MoveResult.IMPOSSIBLE;
            return new MoveResult(base.dest, base.cost * 10.0, base.positionsToBreak);
        }
    },
    ASCEND_WEST(-1, +1, 0) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            MoveResult base = ascend(mc, x, y, z, x - 1, y - 1, z);
            if (base == MoveResult.IMPOSSIBLE) return MoveResult.IMPOSSIBLE;
            return new MoveResult(base.dest, base.cost * 10.0, base.positionsToBreak);
        }
    },

    // ─── Descend (-1y, 4 cardinal directions) ────────────────────────────────

    DESCEND_NORTH(0, -1, -1) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            return descend(mc, x, y, z, x, z - 1);
        }
    },
    DESCEND_SOUTH(0, -1, +1) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            return descend(mc, x, y, z, x, z + 1);
        }
    },
    DESCEND_EAST(+1, -1, 0) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            return descend(mc, x, y, z, x + 1, z);
        }
    },
    DESCEND_WEST(-1, -1, 0) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            return descend(mc, x, y, z, x - 1, z);
        }
    },

    // ─── Downward (drop/dig straight down) ───────────────────────────────────
    // DIG_DOWN integriert: baut Kopfblock (y+1) zuerst ab, dann Block unter
    // den Füßen (y-1). Falling-Block-Säulen über dem Ziel werden mitgerechnet.

    DOWNWARD(0, -1, 0) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            if (mc.level == null) return MoveResult.IMPOSSIBLE;

            BlockPos headBlock = new BlockPos(x, y + 1, z); // Kopfhöhe – muss zuerst weg!
            BlockPos dest      = new BlockPos(x, y - 1, z); // direkt unter den Füßen
            BlockPos floor     = new BlockPos(x, y - 2, z); // neuer Boden

            // Kopfblock muss abbaubar sein
            double costHead = BlockHelper.miningCost(mc, headBlock);
            if (costHead == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

            // Block unter den Füßen muss abbaubar sein
            double mCost = BlockHelper.miningCost(mc, dest);
            if (mCost == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

            // Neuer Boden muss solid oder begehbare Flüssigkeit sein
            BlockState floorState = mc.level.getBlockState(floor);
            if (!floorState.isSolid() && !isWalkableFluid(floorState)) return MoveResult.IMPOSSIBLE;

            // Falling block check: würde Gravel/Sand über dem Kopfblock fallen?
            double fallingCostHead = BlockHelper.fallingBlockColumnCost(mc, headBlock);
            if (fallingCostHead == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

            double mineCost = 0;
            java.util.List<BlockPos> toBreak = new java.util.ArrayList<>();
            // Reihenfolge: erst Kopf, dann Füße
            if (costHead > 0) { toBreak.add(headBlock); mineCost += costHead; }
            if (mCost    > 0) { toBreak.add(dest);      mineCost += mCost;    }
            if (mineCost > 0)   mineCost += ActionCosts.MINING_OVERHEAD;
            mineCost += fallingCostHead;

            double cost = ActionCosts.FALL_1_25_BLOCKS_COST + mineCost;
            return new MoveResult(dest, cost, toBreak.toArray(new BlockPos[0]));
        }
    },

    // ─── Pillar up (senkrecht hoch, Block unter sich platzieren) ─────────────

    PILLAR_UP(0, +1, 0) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            if (mc.player == null || mc.level == null) return MoveResult.IMPOSSIBLE;

            BlockPos destFeet = new BlockPos(x, y + 1, z);
            // Kopffreiheit auf dem neuen Level
            BlockPos destHead = new BlockPos(x, y + 2, z);
            BlockPos floor    = new BlockPos(x, y - 1, z);
            BlockState floorState = mc.level.getBlockState(floor);
            if (!floorState.isSolid()) return MoveResult.IMPOSSIBLE;

            double costFeet = BlockHelper.miningCost(mc, destFeet);
            double costHead = BlockHelper.miningCost(mc, destHead);
            if (costFeet == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
            if (costHead == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

            // Falling block check über destHead
            double fallingCost = BlockHelper.fallingBlockColumnCost(mc, destHead);
            if (fallingCost == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

            // Spieler braucht einen platzierbaren Block im Inventar
            if (!hasPlaceableBlock(mc)) return MoveResult.IMPOSSIBLE;

            double mineCost = 0;
            java.util.List<BlockPos> toBreak = new java.util.ArrayList<>();
            if (costFeet > 0) { toBreak.add(destFeet); mineCost += costFeet; }
            if (costHead > 0) { toBreak.add(destHead); mineCost += costHead; }
            if (mineCost > 0) mineCost += ActionCosts.MINING_OVERHEAD;
            mineCost += fallingCost;

            double cost = ActionCosts.JUMP_ONE_BLOCK_COST + ActionCosts.WALK_ONE_BLOCK_COST + mineCost;
            return new MoveResult(destFeet, cost, toBreak.toArray(new BlockPos[0]));
        }
    };

    // ─── Fields ───────────────────────────────────────────────────────────────

    public final int dx, dy, dz;

    MoveType(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public abstract MoveResult apply(Minecraft mc, int x, int y, int z);

    // ─── Shared helpers ───────────────────────────────────────────────────────

    protected static boolean hasPlaceableBlock(Minecraft mc) {
        if (mc.player == null) return false;
        for (int slot = 0; slot < 9; slot++) {
            net.minecraft.world.item.ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            net.minecraft.world.item.Item item = stack.getItem();
            if (item instanceof net.minecraft.world.item.BlockItem) return true;
        }
        return false;
    }

    protected static MoveResult traverse(Minecraft mc, int sx, int sy, int sz, int dx, int dy, int dz) {
        BlockPos dest     = new BlockPos(dx, dy, dz);
        BlockPos destHead = new BlockPos(dx, dy + 1, dz);
        BlockPos floor    = new BlockPos(dx, dy - 1, dz);

        double costFeet = BlockHelper.miningCost(mc, dest);
        double costHead = BlockHelper.miningCost(mc, destHead);
        if (costFeet == ActionCosts.COST_INF || costHead == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

        BlockState floorState = mc.level.getBlockState(floor);
        if (!floorState.isSolid() && !isWalkableFluid(floorState)) return MoveResult.IMPOSSIBLE;

        // Falling block check: würde Gravel/Sand über dest oder destHead in den Weg fallen?
        double fallingCostFeet = BlockHelper.fallingBlockColumnCost(mc, dest);
        double fallingCostHead = BlockHelper.fallingBlockColumnCost(mc, destHead);
        if (fallingCostFeet == ActionCosts.COST_INF || fallingCostHead == ActionCosts.COST_INF)
            return MoveResult.IMPOSSIBLE;

        double moveCost = ActionCosts.WALK_ONE_BLOCK_COST;

        // Water penalty at destination
        if (isWater(mc.level.getBlockState(dest))) moveCost += ActionCosts.WATER_PENALTY;

        double mineCost = 0;
        java.util.List<BlockPos> toBreakList = new java.util.ArrayList<>();
        if (costFeet > 0) { toBreakList.add(dest);     mineCost += costFeet; }
        if (costHead > 0) { toBreakList.add(destHead); mineCost += costHead; }
        if (mineCost > 0) mineCost += ActionCosts.MINING_OVERHEAD;
        mineCost += fallingCostFeet + fallingCostHead;

        return new MoveResult(dest, moveCost + mineCost, toBreakList.toArray(new BlockPos[0]));
    }

    /**
     * Ascend: dest ist bei Y+1.
     * Spieler springt von src zu dest.
     * Braucht:
     * - src head+1 (y+2) frei (Sprungfreiheit über aktuelle Position)
     * - dest feet (y+1) abbaubar
     * - dest head (y+2) abbaubar
     * - soliden Boden unter dest (y+0 ist der Stufenblock = muss solid sein)
     */
    protected static MoveResult ascend(Minecraft mc, int sx, int sy, int sz, int tx, int ty, int tz) {
        int destY = sy + 1;
        BlockPos jumpClear = new BlockPos(sx, sy + 2, sz);
        BlockPos destFeet  = new BlockPos(tx, destY, tz);
        BlockPos destHead  = new BlockPos(tx, destY + 1, tz);
        BlockPos srcFloor  = new BlockPos(tx, sy, tz);

        BlockState srcFloorState = mc.level.getBlockState(srcFloor);
        if (!srcFloorState.isSolid()) return MoveResult.IMPOSSIBLE;

        double costJumpClear = BlockHelper.miningCost(mc, jumpClear);
        double costFeet      = BlockHelper.miningCost(mc, destFeet);
        double costHead      = BlockHelper.miningCost(mc, destHead);

        if (costJumpClear == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
        if (costFeet      == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
        if (costHead      == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

        // Falling block check über destFeet
        double fallingCost = BlockHelper.fallingBlockColumnCost(mc, destFeet);
        if (fallingCost == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

        double moveCost = ActionCosts.WALK_ONE_BLOCK_COST + ActionCosts.JUMP_ONE_BLOCK_COST;

        double mineCost = 0;
        java.util.List<BlockPos> toBreakList = new java.util.ArrayList<>();
        // Reihenfolge: erst Sprungfreiheit, dann Ziel
        if (costJumpClear > 0) { toBreakList.add(jumpClear); mineCost += costJumpClear; }
        if (costFeet      > 0) { toBreakList.add(destFeet);  mineCost += costFeet;      }
        if (costHead      > 0) { toBreakList.add(destHead);  mineCost += costHead;      }
        if (mineCost      > 0)   mineCost += ActionCosts.MINING_OVERHEAD;
        mineCost += fallingCost;

        return new MoveResult(new BlockPos(tx, destY, tz), moveCost + mineCost, toBreakList.toArray(new BlockPos[0]));
    }

    protected static MoveResult descend(Minecraft mc, int sx, int sy, int sz, int tx, int tz) {
        int destY = sy - 1;


        BlockPos srcHead  = new BlockPos(tx, sy + 1, tz); // Fallblock
        BlockPos destFeet = new BlockPos(tx, destY, tz);  // Fuesse am Ziel (sy-1)
        BlockPos destHead = new BlockPos(tx, sy, tz);     // Kopfhoehe am Ziel (sy)
        BlockPos floor    = new BlockPos(tx, destY - 1, tz);

        double costSrcHead = BlockHelper.miningCost(mc, srcHead);
        double costFeet    = BlockHelper.miningCost(mc, destFeet);
        double costHead    = BlockHelper.miningCost(mc, destHead);
        if (costSrcHead == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
        if (costFeet    == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
        if (costHead    == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

        BlockState floorState = mc.level.getBlockState(floor);
        if (!floorState.isSolid() && !isWalkableFluid(floorState)) return MoveResult.IMPOSSIBLE;
        double fallingCost = BlockHelper.fallingBlockColumnCost(mc, destHead);
        if (fallingCost == ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

        double moveCost = ActionCosts.WALK_ONE_BLOCK_COST + ActionCosts.FALL_1_25_BLOCKS_COST;

        double mineCost = 0;
        java.util.List<BlockPos> toBreakList = new java.util.ArrayList<>();
        // Reihenfolge: erst Kopfblock an Quelle, dann Zielbereich
        if (costSrcHead > 0) { toBreakList.add(srcHead);  mineCost += costSrcHead; }
        if (costHead    > 0) { toBreakList.add(destHead); mineCost += costHead;    }
        if (costFeet    > 0) { toBreakList.add(destFeet); mineCost += costFeet;    }
        if (mineCost    > 0)   mineCost += ActionCosts.MINING_OVERHEAD;
        mineCost += fallingCost;

        return new MoveResult(new BlockPos(tx, destY, tz), moveCost + mineCost, toBreakList.toArray(new BlockPos[0]));
    }

    protected static boolean isWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    protected static boolean isWalkableFluid(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }
}