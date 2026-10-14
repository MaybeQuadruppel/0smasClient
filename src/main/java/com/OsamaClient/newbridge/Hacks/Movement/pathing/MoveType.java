package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * All movement types the pathfinder can consider (modeled after Baritone's Moves).
 *
 * Coordinates are always the FEET position of the player.
 *
 * Moves:
 *  - TRAVERSE   : 1 block flat, may mine feet/head cell
 *  - DIAGONAL   : 1 block flat diagonal, only through already free space (no mining)
 *  - ASCEND     : jump onto a 1-block step (normal jump, NO block placing)
 *  - DESCEND    : walk off an edge, drop 1..3 blocks (no fall damage)
 *  - DOWNWARD   : dig straight down 1 block
 *  - PILLAR_UP  : jump + place block under yourself. Expensive, only used when no step exists.
 *
 * Safety rules applied to every move (at plan time):
 *  - never mine falling blocks or blocks with a falling block above them
 *  - never mine a block that touches any fluid (water/lava would flow into the tunnel)
 *  - never walk through fluids, never walk with feet/head next to lava
 *  - never stand on dangerous blocks (magma, campfire, cactus, ...)
 */
public enum MoveType {

    // ─── Flat movement ────────────────────────────────────────────────────────

    TRAVERSE_NORTH(0, 0, -1) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return traverse(mc, x, y, z, 0, -1); }
    },
    TRAVERSE_SOUTH(0, 0, +1) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return traverse(mc, x, y, z, 0, 1); }
    },
    TRAVERSE_EAST(+1, 0, 0) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return traverse(mc, x, y, z, 1, 0); }
    },
    TRAVERSE_WEST(-1, 0, 0) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return traverse(mc, x, y, z, -1, 0); }
    },

    DIAGONAL_NE(+1, 0, -1) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return diagonal(mc, x, y, z, 1, -1); }
    },
    DIAGONAL_NW(-1, 0, -1) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return diagonal(mc, x, y, z, -1, -1); }
    },
    DIAGONAL_SE(+1, 0, +1) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return diagonal(mc, x, y, z, 1, 1); }
    },
    DIAGONAL_SW(-1, 0, +1) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return diagonal(mc, x, y, z, -1, 1); }
    },

    // ─── Ascend: echter Sprung auf eine 1-Block-Stufe ─────────────────────────

    ASCEND_NORTH(0, +1, -1) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return ascend(mc, x, y, z, 0, -1); }
    },
    ASCEND_SOUTH(0, +1, +1) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return ascend(mc, x, y, z, 0, 1); }
    },
    ASCEND_EAST(+1, +1, 0) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return ascend(mc, x, y, z, 1, 0); }
    },
    ASCEND_WEST(-1, +1, 0) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return ascend(mc, x, y, z, -1, 0); }
    },

    // ─── Descend: Kante runter, 1..3 Bloecke ──────────────────────────────────

    DESCEND_NORTH(0, -1, -1) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return descend(mc, x, y, z, 0, -1); }
    },
    DESCEND_SOUTH(0, -1, +1) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return descend(mc, x, y, z, 0, 1); }
    },
    DESCEND_EAST(+1, -1, 0) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return descend(mc, x, y, z, 1, 0); }
    },
    DESCEND_WEST(-1, -1, 0) {
        @Override public MoveResult apply(Minecraft mc, int x, int y, int z) { return descend(mc, x, y, z, -1, 0); }
    },

    // ─── Downward (dig straight down) ─────────────────────────────────────────

    DOWNWARD(0, -1, 0) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            if (!PathWorld.has(mc)) return MoveResult.IMPOSSIBLE;

            BlockPos dest  = new BlockPos(x, y - 1, z); // direkt unter den Füßen
            BlockPos floor = new BlockPos(x, y - 2, z); // neuer Boden

            if (!BlockHelper.isLoaded(mc, dest) || !BlockHelper.isLoaded(mc, floor)) return MoveResult.IMPOSSIBLE;

            double mCost = BlockHelper.miningCost(mc, dest);
            if (mCost >= ActionCosts.COST_INF || mCost == 0) return MoveResult.IMPOSSIBLE; // 0 = schon Luft -> DESCEND/Fall
            if (!BlockHelper.canStandOn(mc, floor)) return MoveResult.IMPOSSIBLE;
            if (BlockHelper.nearLava(mc, dest)) return MoveResult.IMPOSSIBLE;

            double cost = ActionCosts.FALL_1_25_BLOCKS_COST + mCost + ActionCosts.MINING_OVERHEAD;
            return new MoveResult(dest, cost, new BlockPos[]{dest});
        }
    },

    // ─── Pillar up (Block unter sich platzieren) – nur wenn keine Stufe da ist ──

    PILLAR_UP(0, +1, 0) {
        @Override
        public MoveResult apply(Minecraft mc, int x, int y, int z) {
            if (!PathWorld.hasPlayer(mc) || !PathWorld.has(mc)) return MoveResult.IMPOSSIBLE;

            BlockPos placeAt  = new BlockPos(x, y, z);     // hier kommt der Block hin
            BlockPos destFeet = new BlockPos(x, y + 1, z);
            BlockPos destHead = new BlockPos(x, y + 2, z);
            BlockPos floor    = new BlockPos(x, y - 1, z);
            if (!BlockHelper.isLoaded(mc, destFeet) || !BlockHelper.isLoaded(mc, destHead)
                    || !BlockHelper.isLoaded(mc, floor)) return MoveResult.IMPOSSIBLE;

            // Boden darf Luft sein: in einer Pillar-Kette platzieren die vorherigen Schritte genau
            // diesen Block. Der Executor prüft zur Laufzeit, ob wirklich etwas zum Platzieren da ist.
            if (!BlockHelper.canStandOn(mc, floor) && !PathWorld.level(mc).getBlockState(floor).isAir()) return MoveResult.IMPOSSIBLE;
            // Nie in Flüssigkeiten bauen
            if (!PathWorld.level(mc).getFluidState(placeAt).isEmpty()) return MoveResult.IMPOSSIBLE;

            double costFeet = BlockHelper.miningCost(mc, destFeet);
            double costHead = BlockHelper.miningCost(mc, destHead);
            if (costFeet >= ActionCosts.COST_INF || costHead >= ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

            double fallingCost = BlockHelper.fallingBlockColumnCost(mc, destHead);
            if (fallingCost >= ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
            if (BlockHelper.nearLava(mc, destFeet) || BlockHelper.nearLava(mc, destHead)) return MoveResult.IMPOSSIBLE;

            if (!BlockHelper.hasPlaceableBlock(mc)) return MoveResult.IMPOSSIBLE;

            List<BlockPos> toBreak = new ArrayList<>(2);
            double mineCost = 0;
            if (costHead > 0) { toBreak.add(destHead); mineCost += costHead; }
            if (costFeet > 0) { toBreak.add(destFeet); mineCost += costFeet; }
            if (mineCost > 0) mineCost += ActionCosts.MINING_OVERHEAD;

            double cost = ActionCosts.JUMP_ONE_BLOCK_COST + ActionCosts.WALK_ONE_BLOCK_COST
                    + ActionCosts.PLACE_BLOCK_COST + mineCost;
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

    /** Kosten, um eine Zelle (Fuß- oder Kopfhöhe) begehbar zu machen; prüft auch Lava-Nähe und Fallblöcke darüber. */
    private static double clearCost(Minecraft mc, BlockPos cell) {
        double c = BlockHelper.miningCost(mc, cell);
        if (c >= ActionCosts.COST_INF) return ActionCosts.COST_INF;
        if (BlockHelper.nearLava(mc, cell)) return ActionCosts.COST_INF;
        if (c > 0 && BlockHelper.fallingBlockColumnCost(mc, cell) >= ActionCosts.COST_INF) return ActionCosts.COST_INF;
        return c;
    }

    protected static MoveResult traverse(Minecraft mc, int sx, int sy, int sz, int ox, int oz) {
        int tx = sx + ox, tz = sz + oz;
        BlockPos dest     = new BlockPos(tx, sy, tz);
        BlockPos destHead = new BlockPos(tx, sy + 1, tz);
        BlockPos floor    = new BlockPos(tx, sy - 1, tz);

        if (!BlockHelper.isLoaded(mc, dest) || !BlockHelper.isLoaded(mc, destHead)
                || !BlockHelper.isLoaded(mc, floor)) return MoveResult.IMPOSSIBLE;
        if (!BlockHelper.canStandOn(mc, floor)) return MoveResult.IMPOSSIBLE;

        // Kopfblock zuerst prüfen: über dem Kopfblock darf kein Kies liegen
        double costHead = clearCost(mc, destHead);
        if (costHead >= ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
        double costFeet = clearCost(mc, dest);
        if (costFeet >= ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

        Direction travelDir = ox != 0 ? (ox > 0 ? Direction.EAST : Direction.WEST)
                : (oz > 0 ? Direction.SOUTH : Direction.NORTH);
        double moveCost = ActionCosts.WALK_ONE_BLOCK_COST;
        // Nur bereits FREIE Gänge zählen als fremder Tunnel – massiver Stein, den wir selbst
        // aufgraben, darf nicht bestraft werden (sonst springt der Bot auf Wände statt durchzugraben).
        if (costFeet == 0 && costHead == 0 && BlockHelper.isCarvedTunnel(mc, dest, travelDir))
            moveCost += ActionCosts.CARVED_TUNNEL_PENALTY;

        List<BlockPos> toBreak = new ArrayList<>(2);
        double mineCost = 0;
        // Reihenfolge: erst Kopf, dann Füße (sonst fällt man evtl. nicht durch / sieht das Face nicht)
        if (costHead > 0) { toBreak.add(destHead); mineCost += costHead; }
        if (costFeet > 0) { toBreak.add(dest);     mineCost += costFeet; }
        if (mineCost > 0) mineCost += ActionCosts.MINING_OVERHEAD;

        return new MoveResult(dest, moveCost + mineCost, toBreak.toArray(new BlockPos[0]));
    }

    /** Diagonal nur durch bereits freien Raum – beide "Ecken" müssen frei sein, damit man nicht hängen bleibt. */
    protected static MoveResult diagonal(Minecraft mc, int sx, int sy, int sz, int ox, int oz) {
        int tx = sx + ox, tz = sz + oz;
        BlockPos dest  = new BlockPos(tx, sy, tz);
        BlockPos floor = new BlockPos(tx, sy - 1, tz);
        if (!BlockHelper.isLoaded(mc, dest) || !BlockHelper.isLoaded(mc, floor)) return MoveResult.IMPOSSIBLE;
        if (!BlockHelper.canStandOn(mc, floor)) return MoveResult.IMPOSSIBLE;

        BlockPos[] cells = {
                dest, dest.above(),
                new BlockPos(tx, sy, sz), new BlockPos(tx, sy + 1, sz),
                new BlockPos(sx, sy, tz), new BlockPos(sx, sy + 1, tz)
        };
        for (BlockPos c : cells) {
            if (!BlockHelper.isFreeCell(mc, c) || BlockHelper.nearLava(mc, c)) return MoveResult.IMPOSSIBLE;
        }
        return new MoveResult(dest, ActionCosts.WALK_DIAGONAL_COST, new BlockPos[0]);
    }

    /**
     * Ascend: Ziel ist eine Stufe höher. Der Spieler SPRINGT auf den Block vor ihm
     * (kein Block-Platzieren).
     * Braucht:
     *  - Stufe (tx, sy, tz) zum Draufstellen
     *  - Sprungfreiheit über dem Kopf (sx, sy+2, sz)
     *  - Ziel Füße (tx, sy+1) und Kopf (tx, sy+2) frei oder abbaubar
     */
    protected static MoveResult ascend(Minecraft mc, int sx, int sy, int sz, int ox, int oz) {
        int tx = sx + ox, tz = sz + oz;
        BlockPos step      = new BlockPos(tx, sy, tz);
        BlockPos jumpClear = new BlockPos(sx, sy + 2, sz);
        BlockPos destFeet  = new BlockPos(tx, sy + 1, tz);
        BlockPos destHead  = new BlockPos(tx, sy + 2, tz);

        if (!BlockHelper.isLoaded(mc, jumpClear) || !BlockHelper.isLoaded(mc, destFeet)
                || !BlockHelper.isLoaded(mc, destHead) || !BlockHelper.isLoaded(mc, step)) {
            return MoveResult.IMPOSSIBLE;
        }
        if (!BlockHelper.canStandOn(mc, step)) return MoveResult.IMPOSSIBLE;

        double costJumpClear = clearCost(mc, jumpClear);
        if (costJumpClear >= ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
        double costHead = clearCost(mc, destHead);
        if (costHead >= ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
        double costFeet = clearCost(mc, destFeet);
        if (costFeet >= ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

        double moveCost = ActionCosts.WALK_ONE_BLOCK_COST + ActionCosts.JUMP_ONE_BLOCK_COST;

        List<BlockPos> toBreak = new ArrayList<>(3);
        double mineCost = 0;
        // Reihenfolge: erst Sprungfreiheit, dann Ziel von oben nach unten
        if (costJumpClear > 0) { toBreak.add(jumpClear); mineCost += costJumpClear; }
        if (costHead      > 0) { toBreak.add(destHead);  mineCost += costHead;      }
        if (costFeet      > 0) { toBreak.add(destFeet);  mineCost += costFeet;      }
        if (mineCost > 0) mineCost += ActionCosts.MINING_OVERHEAD;

        return new MoveResult(destFeet, moveCost + mineCost, toBreak.toArray(new BlockPos[0]));
    }

    /**
     * Descend: über die Kante laufen und 1..MAX_FALL_HEIGHT Blöcke fallen.
     * Bei genau 1 Block darf der Weg freigegraben werden (Treppe nach unten),
     * bei tieferen Fällen nur durch bereits freien Raum.
     */
    protected static MoveResult descend(Minecraft mc, int sx, int sy, int sz, int ox, int oz) {
        int tx = sx + ox, tz = sz + oz;

        BlockPos overHead = new BlockPos(tx, sy + 1, tz); // Kopfhöhe beim Rüberlaufen
        BlockPos overFeet = new BlockPos(tx, sy, tz);     // Fußhöhe beim Rüberlaufen
        BlockPos land1    = new BlockPos(tx, sy - 1, tz); // Füße nach 1 Block Fall

        if (!BlockHelper.isLoaded(mc, overHead) || !BlockHelper.isLoaded(mc, overFeet)
                || !BlockHelper.isLoaded(mc, land1)) return MoveResult.IMPOSSIBLE;

        BlockPos floor1 = land1.below();
        if (!BlockHelper.isLoaded(mc, floor1)) return MoveResult.IMPOSSIBLE;

        // ── 1 Block runter (darf graben) ──
        if (BlockHelper.canStandOn(mc, floor1)) {
            double costOverHead = clearCost(mc, overHead);
            if (costOverHead >= ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
            double costOverFeet = clearCost(mc, overFeet);
            if (costOverFeet >= ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;
            double costLand = clearCost(mc, land1);
            if (costLand >= ActionCosts.COST_INF) return MoveResult.IMPOSSIBLE;

            List<BlockPos> toBreak = new ArrayList<>(3);
            double mineCost = 0;
            if (costOverHead > 0) { toBreak.add(overHead); mineCost += costOverHead; }
            if (costOverFeet > 0) { toBreak.add(overFeet); mineCost += costOverFeet; }
            if (costLand     > 0) { toBreak.add(land1);    mineCost += costLand;     }
            if (mineCost > 0) mineCost += ActionCosts.MINING_OVERHEAD;

            double cost = ActionCosts.WALK_ONE_BLOCK_COST + ActionCosts.FALL_1_25_BLOCKS_COST + mineCost;
            return new MoveResult(land1, cost, toBreak.toArray(new BlockPos[0]));
        }

        // ── 2..3 Blöcke runter: nur durch freien Raum, Landung muss sicher sein ──
        if (!BlockHelper.isFreeCell(mc, overHead) || !BlockHelper.isFreeCell(mc, overFeet)
                || !BlockHelper.isFreeCell(mc, land1)) return MoveResult.IMPOSSIBLE;
        if (BlockHelper.nearLava(mc, overHead) || BlockHelper.nearLava(mc, overFeet)) return MoveResult.IMPOSSIBLE;

        for (int fall = 2; fall <= ActionCosts.MAX_FALL_HEIGHT; fall++) {
            BlockPos landFeet = new BlockPos(tx, sy - fall, tz);
            BlockPos floor    = landFeet.below();
            if (!BlockHelper.isLoaded(mc, floor)) return MoveResult.IMPOSSIBLE;
            if (!BlockHelper.isFreeCell(mc, landFeet)) return MoveResult.IMPOSSIBLE; // Wasser/Lava/Block im Weg
            if (BlockHelper.canStandOn(mc, floor)) {
                if (BlockHelper.nearLava(mc, landFeet) || BlockHelper.nearLava(mc, landFeet.above()))
                    return MoveResult.IMPOSSIBLE;
                double cost = ActionCosts.WALK_ONE_BLOCK_COST + ActionCosts.distanceToTicks(fall + 0.25);
                return new MoveResult(landFeet, cost, new BlockPos[0]);
            }
            // Boden ist keine sichere Standfläche (Lava, Magma, Wasser...) und nicht frei -> abbrechen
            if (!BlockHelper.isFreeCell(mc, floor)) return MoveResult.IMPOSSIBLE;
        }
        return MoveResult.IMPOSSIBLE; // würde tiefer fallen -> Fallschaden
    }
}
