package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Static helpers for block evaluation.
 * Used by MoveType cost calculations and MovementExecutor.
 */
public final class BlockHelper {

    private static final double MAX_REACH_SQ = 4.5 * 4.5;

    private static final Set<net.minecraft.world.level.block.Block> FORBIDDEN = Set.of(
            Blocks.BEDROCK,
            Blocks.LAVA,
            Blocks.WATER // don't try to mine water source blocks
    );

    /** Blocks the bot must never walk into (feet/head cell), even though some have no collision. */
    private static final Set<net.minecraft.world.level.block.Block> DANGEROUS_CELL = Set.of(
            Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.COBWEB, Blocks.SWEET_BERRY_BUSH,
            Blocks.POWDER_SNOW, Blocks.WITHER_ROSE, Blocks.CACTUS, Blocks.MAGMA_BLOCK,
            Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.POINTED_DRIPSTONE, Blocks.END_PORTAL,
            Blocks.NETHER_PORTAL, Blocks.SCAFFOLDING
    );

    /** Blocks the bot must never stand on. */
    private static final Set<net.minecraft.world.level.block.Block> DANGEROUS_FLOOR = Set.of(
            Blocks.MAGMA_BLOCK, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.CACTUS,
            Blocks.POINTED_DRIPSTONE, Blocks.POWDER_SNOW, Blocks.SCAFFOLDING
    );

    /** Per-search cache: break time in ticks per BlockState (depends on hotbar tools). */
    private static final Map<BlockState, Double> BREAK_TICKS_CACHE = new IdentityHashMap<>();

    private BlockHelper() {}

    /** Must be called whenever a new path search starts (inventory/tools may have changed). */
    public static void beginSearch() {
        BREAK_TICKS_CACHE.clear();
    }

    /**
     * Returns the cost to clear this block so the player can stand in it, in ticks.
     * Returns COST_INF if the block must not be touched/entered.
     * Returns 0 if the block is already passable (air, grass, flowers, ...).
     *
     * Never-touch rules (all checked at PLAN time, not just while executing):
     * - fluids (water, lava, waterlogged blocks) and anything directly next to a fluid,
     *   because breaking it would let the fluid flow into the tunnel
     * - falling blocks (gravel, sand, concrete powder, ...)
     * - block entities (chests, shulkers, spawners, furnaces ...): never destroy containers
     * - dangerous blocks (fire, cobweb, berry bush, powder snow, magma, ...)
     */
    public static double miningCost(Minecraft mc, BlockPos pos) {
        if (!PathWorld.has(mc)) return ActionCosts.COST_INF;
        // Never invent air in an unloaded chunk. That was the source of paths
        // which looked valid locally but immediately disagreed with the server.
        if (!PathWorld.loaded(mc, pos)) return ActionCosts.COST_INF;

        BlockState state = PathWorld.level(mc).getBlockState(pos);

        if (state.isAir()) return 0;
        if (FORBIDDEN.contains(state.getBlock())) return ActionCosts.COST_INF;
        if (!state.getFluidState().isEmpty()) return ActionCosts.COST_INF; // water, lava, waterlogged
        if (DANGEROUS_CELL.contains(state.getBlock())) return ActionCosts.COST_INF;

        // Already passable (tall grass, flowers, torches, rails ...): nothing to mine
        if (state.getCollisionShape(PathWorld.level(mc), pos).isEmpty()) return 0;

        // Unbreakable
        float hardness = state.getDestroySpeed(PathWorld.level(mc), pos);
        if (hardness < 0) return ActionCosts.COST_INF;

        // Gravel/Sand/etc: nie abbauen -> drumherum laufen
        if (isFallingBlock(state)) return ActionCosts.COST_INF;

        // Container / Spawner / sonstige Block-Entities nie zerstoeren
        if (state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock) return ActionCosts.COST_INF;

        // Abbauen wuerde Wasser/Lava in den Gang laufen lassen
        if (touchesFluid(mc, pos)) return ActionCosts.COST_INF;

        return breakTicks(mc, state, hardness);
    }

    /** Ticks needed to break {@code state} with the best tool in the hotbar (cached per search). */
    private static double breakTicks(Minecraft mc, BlockState state, float hardness) {
        Double cached = BREAK_TICKS_CACHE.get(state);
        if (cached != null) return cached;

        double ticks;
        if (hardness == 0f) {
            ticks = 1.0;
        } else {
            double bestProgress = 0;
            boolean needsTool = state.requiresCorrectToolForDrops();
            if (PathWorld.hasPlayer(mc)) {
                for (int slot = 0; slot < 9; slot++) {
                    net.minecraft.world.item.ItemStack stack = PathWorld.hotbar(mc, slot);
                    float speed = stack.isEmpty() ? 1f : stack.getDestroySpeed(state);
                    boolean canHarvest = !needsTool || (!stack.isEmpty() && stack.isCorrectToolForDrops(state));
                    double progress = speed / hardness / (canHarvest ? 30.0 : 100.0);
                    if (progress > bestProgress) bestProgress = progress;
                }
            }
            if (bestProgress <= 0) bestProgress = 1.0 / hardness / 100.0;
            ticks = Math.ceil(1.0 / bestProgress) + 2.0; // +2: aim/start latency
        }
        BREAK_TICKS_CACHE.put(state, ticks);
        return ticks;
    }

    /**
     * True if the player can stand ON this block: has collision, top surface roughly at
     * full height (no fences/walls 1.5, no bottom slabs 0.5, no carpets) and is not
     * dangerous (magma, campfire, cactus, powder snow ...).
     */
    public static boolean canStandOn(Minecraft mc, BlockPos pos) {
        if (!PathWorld.loaded(mc, pos)) return false;
        BlockState state = PathWorld.level(mc).getBlockState(pos);
        if (state.isAir()) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (DANGEROUS_FLOOR.contains(state.getBlock())) return false;
        net.minecraft.world.phys.shapes.VoxelShape shape = state.getCollisionShape(PathWorld.level(mc), pos);
        if (shape.isEmpty()) return false;
        double top = shape.max(Direction.Axis.Y);
        return top >= 0.8 && top <= 1.0;
    }

    /**
     * True if the player can occupy this cell WITHOUT mining (air / no-collision blocks),
     * and it is neither a fluid nor dangerous.
     */
    public static boolean isFreeCell(Minecraft mc, BlockPos pos) {
        if (!PathWorld.loaded(mc, pos)) return false;
        BlockState state = PathWorld.level(mc).getBlockState(pos);
        if (state.isAir()) return true;
        if (!state.getFluidState().isEmpty()) return false;
        if (DANGEROUS_CELL.contains(state.getBlock())) return false;
        return state.getCollisionShape(PathWorld.level(mc), pos).isEmpty();
    }

    /**
     * True if lava is directly next to (4 horizontal sides) or above this cell. The bot
     * never walks with its feet/head through such a cell.
     */
    public static boolean nearLava(Minecraft mc, BlockPos pos) {
        if (!PathWorld.has(mc)) return false;
        if (isLava(mc, pos.above())) return true;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (isLava(mc, pos.relative(d))) return true;
        }
        return false;
    }

    private static boolean isLava(Minecraft mc, BlockPos pos) {
        return PathWorld.loaded(mc, pos) && PathWorld.level(mc).getFluidState(pos).is(FluidTags.LAVA);
    }

    /**
     * Checks if mining the block at `pos` would cause falling blocks above it
     * to drop down and block the path.
     *
     * Returns COST_INF if an unminable falling block column is above,
     * otherwise returns the extra cost to also mine the falling blocks.
     *
     * Call this in addition to miningCost() when clearing a block that has
     * something above it (e.g. in traverse/ascend for destFeet and destHead).
     */
    public static double fallingBlockColumnCost(Minecraft mc, BlockPos pos) {
        if (!PathWorld.has(mc)) return 0;

        BlockPos check = pos.above();

        // Scan upward until we hit air or a non-falling solid block.
        // Sobald irgendwo Kies/Sand in der Säule steckt: kompletter Move wird
        // als unmöglich markiert (COST_INF), statt ihn "mitzurechnen" und
        // trotzdem abzubauen -> der Bot meidet Kies/Sand jetzt vollständig.
        for (int i = 0; i < 32; i++) {
            if (!PathWorld.loaded(mc, check)) return ActionCosts.COST_INF;

            BlockState above = PathWorld.level(mc).getBlockState(check);

            if (above.isAir()) break; // Säule endet, nichts fällt nach

            if (isFallingBlock(above)) {
                return ActionCosts.COST_INF;
            }
            break; // erster nicht-Luft-Block ist kein Fallblock -> sicher
        }

        return 0;
    }

    public static boolean isBedrockAbove(net.minecraft.world.level.Level level, BlockPos pos) {
        for (int i = 1; i <= 3; i++) {
            if (level.getBlockState(pos.above(i)).is(Blocks.BEDROCK)) {
                return true;
            }
        }
        BlockPos posBelow = pos.below();
        if (Minecraft.getInstance().player.blockPosition().below().equals(pos) && level.getBlockState(posBelow).isAir()){ return true;}
        return false;
    }

    public static boolean isLoaded(Minecraft mc, BlockPos pos) {
        return PathWorld.loaded(mc, pos);
    }

    public static boolean isEncasedInBedrock(net.minecraft.world.level.Level level, BlockPos pos) {
        int bedrockCount = 0;
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(Blocks.BEDROCK)) {
                bedrockCount++;
            }
        }
        return bedrockCount >= 5;
    }
    /**
     * True if the player can walk through this block without mining
     * (air, water, most plants, etc.)
     */
    public static boolean fullyPassable(Minecraft mc, BlockPos pos) {
        return miningCost(mc, pos) == 0;
    }

    /**
     * Returns true if this block is a gravity-affected falling block
     * (Gravel, Sand, Red Sand, Concrete Powder, Anvil, etc.)
     */
    public static boolean isFallingBlock(BlockState state) {
        return state.getBlock() instanceof FallingBlock;
    }

    /**
     * True if mining the block at pos is safe – i.e. no falling block column
     * above it would drop into an unminable position.
     */
    public static boolean isSafeToMine(Minecraft mc, BlockPos pos) {
        return fallingBlockColumnCost(mc, pos) < ActionCosts.COST_INF;
    }

    /**
     * Turns the player to look at a block's center.
     */
    public static void lookAt(Minecraft mc, BlockPos target) {
        if (mc.player == null) return;
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(target);

        double dx = center.x - eyes.x;
        double dy = center.y - eyes.y;
        double dz = center.z - eyes.z;
        double dxz = Math.sqrt(dx * dx + dz * dz);

        float yaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, dxz)));

        mc.player.setYRot(yaw);
        mc.player.setXRot(net.minecraft.util.Mth.clamp(pitch, -90, 90));
    }

    /**
     * Returns true if the block is within mining reach of the player.
     */
    public static boolean canReach(Minecraft mc, BlockPos pos) {
        if (mc.player == null) return false;
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        return eyes.distanceToSqr(center) <= MAX_REACH_SQ;
    }

    public static boolean isForbidden(BlockState state) {
        return FORBIDDEN.contains(state.getBlock())
                || state.getFluidState().is(FluidTags.LAVA);
    }

    // ─── Placement / safety helpers ─────────────────────────────────────────

    /**
     * Ein Block-Item, das sich zum Pillarn eignet: vollwertiger fester Block,
     * KEIN Fallblock (Sand/Kies), keine Kiste/Ofen/etc.
     */
    public static boolean isPlacementItem(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) return false;
        net.minecraft.world.level.block.Block block = blockItem.getBlock();
        if (block instanceof FallingBlock) return false;
        if (block instanceof net.minecraft.world.level.block.EntityBlock) return false;
        return block.defaultBlockState().isSolid();
    }

    /** Hotbar-Slot mit einem platzierbaren Block, sonst -1. */
    public static int findPlaceableSlot(Minecraft mc) {
        if (!PathWorld.hasPlayer(mc)) return -1;
        for (int slot = 0; slot < 9; slot++) {
            if (isPlacementItem(PathWorld.hotbar(mc, slot))) return slot;
        }
        return -1;
    }

    public static boolean hasPlaceableBlock(Minecraft mc) {
        return findPlaceableSlot(mc) != -1;
    }

    /**
     * true wenn direkt neben {@code pos} Lava ist: Abbauen von pos würde die Lava
     * in den Gang laufen lassen.
     */
    public static boolean touchesLava(Minecraft mc, BlockPos pos) {
        if (!PathWorld.has(mc)) return false;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!PathWorld.loaded(mc, n)) continue;
            if (PathWorld.level(mc).getFluidState(n).is(FluidTags.LAVA)) return true;
        }
        return false;
    }

    /**
     * true wenn direkt neben {@code pos} (alle 6 Seiten) irgendeine Flüssigkeit ist:
     * Abbauen von pos würde Wasser/Lava in den Gang laufen lassen.
     */
    public static boolean touchesFluid(Minecraft mc, BlockPos pos) {
        if (!PathWorld.has(mc)) return false;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!PathWorld.loaded(mc, n)) continue;
            if (!PathWorld.level(mc).getFluidState(n).isEmpty()) return true;
        }
        return false;
    }

    // ─── Player-dug tunnel detection ────────────────────────────────────────

    /**
     * Heuristic detection of player-dug tunnels (1x1 crawl, 1x2 standard walking
     * tunnel, 3x3 highway), fully enclosed by solid stone-like walls. Used by the
     * pathfinder so the bot digs its own route instead of following (possibly
     * already explored/trapped) tunnels other players made.
     */
    public static boolean isCarvedTunnel(Minecraft mc, BlockPos feet, Direction travelDir) {
        if (!PathWorld.has(mc) || travelDir.getAxis() == Direction.Axis.Y) return false;
        Direction perp = travelDir.getClockWise();

        if (is1x2Tunnel(mc, feet, perp)) return true;
        if (is1x1Tunnel(mc, feet, perp)) return true;
        return is3x3Tunnel(mc, feet, perp);
    }

    private static boolean isWallLike(Minecraft mc, BlockPos pos) {
        BlockState state = PathWorld.level(mc).getBlockState(pos);
        return !state.getCollisionShape(PathWorld.level(mc), pos).isEmpty() && !isFallingBlock(state);
    }

    private static boolean is1x2Tunnel(Minecraft mc, BlockPos feet, Direction perp) {
        BlockPos head = feet.above();
        boolean leftWall  = isWallLike(mc, feet.relative(perp)) && isWallLike(mc, head.relative(perp));
        boolean rightWall = isWallLike(mc, feet.relative(perp.getOpposite())) && isWallLike(mc, head.relative(perp.getOpposite()));
        boolean ceiling   = isWallLike(mc, head.above());
        boolean floor     = isWallLike(mc, feet.below());
        return leftWall && rightWall && ceiling && floor;
    }

    private static boolean is1x1Tunnel(Minecraft mc, BlockPos feet, Direction perp) {
        boolean leftWall  = isWallLike(mc, feet.relative(perp));
        boolean rightWall = isWallLike(mc, feet.relative(perp.getOpposite()));
        boolean ceiling   = isWallLike(mc, feet.above());
        boolean floor     = isWallLike(mc, feet.below());
        return leftWall && rightWall && ceiling && floor;
    }

    private static boolean is3x3Tunnel(Minecraft mc, BlockPos feet, Direction perp) {
        // Inneres 3x3 muss komplett frei begehbar sein
        for (int side = -1; side <= 1; side++) {
            for (int up = 0; up <= 2; up++) {
                BlockPos p = feet.relative(perp, side).above(up);
                if (!PathWorld.level(mc).getBlockState(p).getCollisionShape(PathWorld.level(mc), p).isEmpty()) return false;
            }
        }
        // Boden, Decke und beide äußeren Wände müssen solide sein
        for (int side = -1; side <= 1; side++) {
            if (!isWallLike(mc, feet.relative(perp, side).below())) return false;
            if (!isWallLike(mc, feet.relative(perp, side).above(3))) return false;
        }
        return isWallLike(mc, feet.relative(perp, -2)) && isWallLike(mc, feet.relative(perp, 2));
    }
}
