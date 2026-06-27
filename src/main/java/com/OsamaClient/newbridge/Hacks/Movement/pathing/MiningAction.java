package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Mining utilities used by external hacks (e.g. AutoMiner).
 *
 * Navigation-internal mining is handled by MovementExecutor directly.
 * This class is only for post-navigation actions like vein mining.
 */
public final class MiningAction {

    private static final double MAX_REACH_SQ = 4.5 * 4.5;
    private static final int MAX_VEIN_SIZE = 32;

    // ─── Mining state (for continueDestroyBlock logic) ────────────────────────

    private static BlockPos lastMinedPos = null;

    public static void resetMiningState() {
        lastMinedPos = null;
    }

    // ─── Single block mining ──────────────────────────────────────────────────

    /**
     * Mine a single block. Call every tick until the block is gone.
     * Handles start/continue automatically.
     */
    public static boolean mineBlock(Minecraft mc, BlockPos pos) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return false;

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) { lastMinedPos = null; return true; }
        if (BlockHelper.isForbidden(state)) return false;

        Vec3 eyes = mc.player.getEyePosition();
        if (eyes.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_REACH_SQ) return false;

        BlockHelper.lookAt(mc, pos);

        if (!pos.equals(lastMinedPos)) {
            mc.gameMode.startDestroyBlock(pos, Direction.UP);
            lastMinedPos = pos;
        } else {
            mc.gameMode.continueDestroyBlock(pos, Direction.UP);
        }
        return true;
    }

    // ─── Vein mining ──────────────────────────────────────────────────────────

    /**
     * BFS vein mine: mines all connected blocks of the same type within reach.
     * Call this AFTER navigation has reached the ore.
     */
    public static List<BlockPos> mineVein(Minecraft mc, BlockPos origin, Block targetBlock) {
        List<BlockPos> mined = new ArrayList<>();
        if (mc.player == null || mc.level == null) return mined;

        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);

        Vec3 eyes = mc.player.getEyePosition();

        while (!queue.isEmpty() && mined.size() < MAX_VEIN_SIZE) {
            BlockPos current = queue.poll();
            BlockState state = mc.level.getBlockState(current);

            if (!state.is(targetBlock)) continue;
            if (eyes.distanceToSqr(Vec3.atCenterOf(current)) > MAX_REACH_SQ) continue;

            mineBlock(mc, current);
            mined.add(current);

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    if (mc.level.getBlockState(neighbor).is(targetBlock)) {
                        queue.add(neighbor);
                    }
                }
            }
        }
        return mined;
    }
}
