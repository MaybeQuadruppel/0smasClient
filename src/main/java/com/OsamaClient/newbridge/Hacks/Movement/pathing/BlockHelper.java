package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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

    private BlockHelper() {}

    /**
     * Returns the cost to mine this block in ticks.
     * Returns COST_INF if the block is impassable/forbidden.
     * Returns 0 if the block is already air/passable.
     *
     * Falling blocks (Gravel, Sand, etc.) get extra cost because:
     * - After mining the block below them they fall down
     * - This can block the path or hurt the player
     */
    public static double miningCost(Minecraft mc, BlockPos pos) {
        if (mc.level == null) return ActionCosts.COST_INF;
        // Never invent air in an unloaded chunk. That was the source of paths
        // which looked valid locally but immediately disagreed with the server.
        if (!mc.level.isLoaded(pos)) return ActionCosts.COST_INF;

        BlockState state = mc.level.getBlockState(pos);

        if (state.isAir()) return 0;
        if (FORBIDDEN.contains(state.getBlock())) return ActionCosts.COST_INF;

        // Lava check via fluid state
        if (state.getFluidState().is(FluidTags.LAVA)) return ActionCosts.COST_INF;

        // Unbreakable
        float hardness = state.getDestroySpeed(mc.level, pos);
        if (hardness < 0) return ActionCosts.COST_INF;

        // Wasser: komplett meiden statt nur durchqueren (schon vorher via FORBIDDEN
        // abgedeckt, hier nur zur Klarheit/Robustheit falls jemand FORBIDDEN ändert)
        if (state.getFluidState().is(FluidTags.WATER)) return ActionCosts.COST_INF;

        // Gravel/Sand/etc: NICHT mehr abbauen -> der Pathfinder soll drumherum laufen,
        // statt hindurchzugraben (kein Fallschaden/keine Blockade durch nachrutschenden Kies).
        if (isFallingBlock(state)) {
            return ActionCosts.COST_INF;
        }

        // Hardness-based cost (calibrated: stone with iron pickaxe ≈ 7.5 ticks)
        return hardness * 30.0 + ActionCosts.MINING_OVERHEAD;
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
        if (mc.level == null) return 0;

        BlockPos check = pos.above();

        // Scan upward until we hit air or a non-falling solid block.
        // Sobald irgendwo Kies/Sand in der Säule steckt: kompletter Move wird
        // als unmöglich markiert (COST_INF), statt ihn "mitzurechnen" und
        // trotzdem abzubauen -> der Bot meidet Kies/Sand jetzt vollständig.
        for (int i = 0; i < 32; i++) {
            if (!mc.level.isLoaded(check)) return ActionCosts.COST_INF;

            BlockState above = mc.level.getBlockState(check);

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
        return mc.level != null && mc.level.isLoaded(pos);
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
        if (mc.player == null) return -1;
        for (int slot = 0; slot < 9; slot++) {
            if (isPlacementItem(mc.player.getInventory().getItem(slot))) return slot;
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
        if (mc.level == null) return false;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!mc.level.isLoaded(n)) continue;
            if (mc.level.getFluidState(n).is(FluidTags.LAVA)) return true;
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
        if (mc.level == null || travelDir.getAxis() == Direction.Axis.Y) return false;
        Direction perp = travelDir.getClockWise();

        if (is1x2Tunnel(mc, feet, perp)) return true;
        if (is1x1Tunnel(mc, feet, perp)) return true;
        return is3x3Tunnel(mc, feet, perp);
    }

    private static boolean isWallLike(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        return state.isSolid() && !isFallingBlock(state);
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
                if (!mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty()) return false;
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
