package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.BlockHelper;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.BlockInteraction;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.GoalBlock;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.MiningAction;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Navigator;
import com.OsamaClient.newbridge.UI.components.BlockPicker;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

public class AutoMiner extends Module {

    private final BlockPicker blockPicker = new BlockPicker("Targets");
    private final Slider scanRadius = new Slider("Scan Radius", 10.0, 100.0, 40.0, val -> {});

    private int tickDelay = 0;
    private BlockPos currentTargetPos = null;
    private Block currentTargetBlock = null;
    private boolean isMining = false;
    private int miningNoProgressTicks = 0;
    private int pathColor = 0xFF4ADE80;
    private int targetColor = 0xFFFFD43B;
    private final com.OsamaClient.newbridge.Hacks.Movement.pathing.PathRenderer pathRenderer =
            new com.OsamaClient.newbridge.Hacks.Movement.pathing.PathRenderer();

    public AutoMiner() {
        super("AutoMiner", "Farms for you", Category.MISC);
        this.settings.add(blockPicker.withDescription("Selects which blocks to automatically mine."));
        this.settings.add(scanRadius.withDescription("Sets the radius to scan for target blocks."));
        this.settings.add(new ColorPicker("Path Color", pathColor, color -> pathColor = color));
        this.settings.add(new ColorPicker("Target Color", targetColor, color -> targetColor = color));
        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        Navigator.INSTANCE.cancel(mc, Navigator.OWNER_AUTO_MINER);
        MiningAction.resetMiningState();
        currentTargetPos = null;
        currentTargetBlock = null;
        isMining = false;
        miningNoProgressTicks = 0;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return;

        // The request may be queued behind another module, but it remains ours.
        if (Navigator.INSTANCE.isNavigating(Navigator.OWNER_AUTO_MINER)) return;

        // Keep sending the vanilla break progression until the world confirms the block change.
        if (currentTargetPos != null && currentTargetBlock != null && !isMining) {
            if (client.player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(currentTargetPos))
                    > BlockInteraction.REACH * BlockInteraction.REACH) {
                currentTargetPos = null;
                currentTargetBlock = null;
                tickDelay = 40;
                return;
            }
            client.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§a[AutoMiner] Target reached starting Veinmine")
            );
            isMining = true;
            miningNoProgressTicks = 0;
            MiningAction.resetMiningState();
        }

        if (isMining) {
            if (Navigator.INSTANCE.isNavigating()) return;
            if (!MiningAction.mineVein(client, currentTargetPos, currentTargetBlock).isEmpty()) {
                miningNoProgressTicks = 0;
                return;
            }
            if (++miningNoProgressTicks < 40) return;
            MiningAction.resetMiningState();
            currentTargetPos = null;
            currentTargetBlock = null;
            isMining = false;
            miningNoProgressTicks = 0;
            tickDelay = 20;
            return;
        }

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }
        if (blockPicker.selectedBlocks.isEmpty()) {
            tickDelay = 40;
            return;
        }

        BlockPos target = findClosestSelectedBlock(client);

        if (target != null) {
            BlockState state = client.level.getBlockState(target);
            BlockPos miningStand = findMiningStand(client, target);
            if (miningStand == null) {
                tickDelay = 40;
                return;
            }
            currentTargetPos = target;
            currentTargetBlock = state.getBlock();

            client.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§e[AutoMiner] Target found at: " + target.toShortString())
            );

            Navigator.INSTANCE.goTo(client, Navigator.OWNER_AUTO_MINER,
                    Navigator.PRIORITY_AUTO_MINER, new GoalBlock(miningStand));
            if (!Navigator.INSTANCE.isNavigating(Navigator.OWNER_AUTO_MINER)) {
                currentTargetPos = null;
                currentTargetBlock = null;
                tickDelay = 100;
            }
        } else {
            tickDelay = 60;
        }
    }

    private BlockPos findClosestSelectedBlock(Minecraft client) {
        BlockPos playerPos = client.player.blockPosition();
        int radius = (int) scanRadius.getValue();

        BlockPos bestPos = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos currentPos = playerPos.offset(x, y, z);
                    if (currentPos.getY() < client.level.getMinY() || currentPos.getY() >= client.level.getMaxY()) {
                        continue;
                    }
                    BlockState state = client.level.getBlockState(currentPos);
                    Block block = state.getBlock();

                    if (blockPicker.selectedBlocks.containsKey(block)) {
                        if (BlockHelper.isEncasedInBedrock(client.level, currentPos)) continue;
                        if (BlockHelper.isBedrockAbove(client.level, currentPos)) continue;

                        // NEUER CHECK: Ignoriere den Block, wenn er an Lava/Wasser grenzt!
                        if (isExposedToLiquid(client, currentPos)) continue;

                        double dist = currentPos.distSqr(playerPos);
                        if (dist < bestDist) {
                            bestDist = dist;
                            bestPos = currentPos;
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    /** Finds a real walking position beside the block, not merely a loose radius around it. */
    private BlockPos findMiningStand(Minecraft client, BlockPos target) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Direction direction : new Direction[]{
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
        }) {
            BlockPos stand = target.relative(direction);
            BlockPos head = stand.above();
            BlockPos floor = stand.below();
            if (!client.level.isLoaded(stand) || !client.level.isLoaded(head) || !client.level.isLoaded(floor)) continue;
            if (!client.level.getFluidState(stand).isEmpty() || !client.level.getFluidState(head).isEmpty()) continue;
            if (!client.level.getBlockState(stand).getCollisionShape(client.level, stand).isEmpty()) continue;
            if (!client.level.getBlockState(head).getCollisionShape(client.level, head).isEmpty()) continue;
            if (client.level.getBlockState(floor).getCollisionShape(client.level, floor).isEmpty()) continue;

            double distance = stand.distSqr(client.player.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = stand;
            }
        }
        return best;
    }

    /**
     * Prüft alle 6 angrenzenden Blöcke, um zu sehen, ob eine Flüssigkeit (wie Lava) vorhanden ist.
     */
    private boolean isExposedToLiquid(Minecraft client, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos adjacent = pos.relative(dir);
            FluidState fluidState = client.level.getFluidState(adjacent);

            // Wenn der angrenzende Block eine Flüssigkeit enthält, gib true zurück
            if (!fluidState.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (enabled && currentTargetPos != null) {
            pathRenderer.render(event, Navigator.INSTANCE.getRemainingPath(Navigator.OWNER_AUTO_MINER),
                    currentTargetPos, pathColor, targetColor);
        }
    }
}
