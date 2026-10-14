package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class MiningAction {

    private static final int MAX_VEIN_SIZE = 32;

    private static BlockPos lastMinedPos = null;

    public static void resetMiningState() {
        lastMinedPos = null;
    }

    public static boolean mineBlock(Minecraft mc, BlockPos pos) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return false;

        BlockState state = mc.level.getBlockState(pos);

        if (state.isAir()) {
            if (lastMinedPos != null && lastMinedPos.equals(pos)) {
                mc.gameMode.stopDestroyBlock();
                lastMinedPos = null;
            }
            return true;
        }
        if (BlockHelper.isEncasedInBedrock(mc.level, pos)) return false;
        if (BlockHelper.isForbidden(state)) return false;
        if (BlockHelper.touchesLava(mc, pos)) return false;
        // Kein Abbauen, wenn Kies/Sand direkt darüber nachrutschen würde
        if (BlockHelper.isFallingBlock(mc.level.getBlockState(pos.above()))) return false;

        int toolSlot = ToolSelector.selectBestTool(mc, state);
        if (toolSlot < 0) return false;
        mc.player.getInventory().setSelectedSlot(toolSlot);

        BlockHitResult ray = BlockInteraction.rayFromLook(mc);
        if (ray != null && ray.getBlockPos().equals(pos)) {
            Direction face = ray.getDirection();
            if (!pos.equals(lastMinedPos)) {
                mc.gameMode.startDestroyBlock(pos, face);
                lastMinedPos = pos;
            } else {
                mc.gameMode.continueDestroyBlock(pos, face);
            }
            // WICHTIG: Hand schwengen, damit Anti-Cheats auf Servern den Abbau nicht verwerfen
            mc.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }

        BlockInteraction.AimTarget aim = BlockInteraction.findMineFace(mc, pos);
        if (aim == null) return false;

        BlockInteraction.smoothLookAt(mc, aim.point());
        return true;
    }

    public static List<BlockPos> mineVein(Minecraft mc, BlockPos origin, Block targetBlock) {
        List<BlockPos> mined = new ArrayList<>();
        if (mc.player == null || mc.level == null) return mined;
        List<BlockPos> veinBlocks = new ArrayList<>();
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(origin);
        visited.add(origin);
        // The first ore may already be confirmed as air. Seed its neighbors so
        // the remainder of the vein is still discovered on the next tick.
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = origin.relative(direction);
            if (visited.add(neighbor)) queue.add(neighbor);
        }
        while (!queue.isEmpty() && veinBlocks.size() < MAX_VEIN_SIZE) {
            BlockPos current = queue.poll();
            if (mc.level.getBlockState(current).is(targetBlock)) {
                veinBlocks.add(current);
                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.relative(dir);
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        Vec3 eyes = mc.player.getEyePosition();
        veinBlocks.sort((a, b) -> Double.compare(
                eyes.distanceToSqr(Vec3.atCenterOf(a)), eyes.distanceToSqr(Vec3.atCenterOf(b))));

        for (BlockPos pos : veinBlocks) {
            if (eyes.distanceToSqr(Vec3.atCenterOf(pos)) > BlockInteraction.REACH * BlockInteraction.REACH) continue;
            if (mineBlock(mc, pos)) {
                mined.add(pos);
                break;
            }
        }
        return mined;
    }
}
