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
    /** Ziele, die nicht erreichbar/abbaubar waren: Position -> Tick (Weltzeit-Zähler), bis zu dem sie ignoriert werden. */
    private final java.util.Map<BlockPos, Integer> skipped = new java.util.HashMap<>();
    private int tickCounter = 0;
    private Object lastLevel, lastPlayer;
    private int pathColor = 0xFF4ADE80;
    private int targetColor = 0xFFFFD43B;
    private final com.OsamaClient.newbridge.Hacks.Movement.pathing.PathRenderer pathRenderer =
            new com.OsamaClient.newbridge.Hacks.Movement.pathing.PathRenderer();

    /** Für Tests: die Zielblock-Auswahl. */
    public BlockPicker blockPicker() { return blockPicker; }

    public AutoMiner() {
        super("AutoMiner", "Farms for you", Category.MISC);
        this.settings.add(blockPicker.withDescription("Selects which blocks to automatically mine."));
        this.settings.add(scanRadius.withDescription("Sets the radius to scan for target blocks."));
        this.settings.add(new ColorPicker("Path Color", pathColor, color -> pathColor = color));
        this.settings.add(new ColorPicker("Target Color", targetColor, color -> targetColor = color));
        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onEnable() {
        resetState(Minecraft.getInstance());
    }

    @Override
    public void onDisable() {
        resetState(Minecraft.getInstance());
    }

    /** Verwirft Ziel, Navigation und Wartezeiten (beim Ein-/Ausschalten und beim Welt-/Server-Wechsel). */
    private void resetState(Minecraft mc) {
        Navigator.INSTANCE.cancel(mc, Navigator.OWNER_AUTO_MINER);
        MiningAction.resetMiningState();
        currentTargetPos = null;
        currentTargetBlock = null;
        isMining = false;
        miningNoProgressTicks = 0;
        tickDelay = 0;
        skipped.clear();
        lastLevel = mc.level;
        lastPlayer = mc.player;
    }

    private void skip(BlockPos pos) {
        if (pos != null) skipped.put(pos, tickCounter + 600);
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return;
        tickCounter++;
        if (client.level != lastLevel || client.player != lastPlayer) resetState(client);

        // The request may be queued behind another module, but it remains ours.
        if (Navigator.INSTANCE.isNavigating(Navigator.OWNER_AUTO_MINER)) return;

        // Keep sending the vanilla break progression until the world confirms the block change.
        if (currentTargetPos != null && currentTargetBlock != null && !isMining) {
            if (client.player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(currentTargetPos))
                    > BlockInteraction.REACH * BlockInteraction.REACH) {
                skip(currentTargetPos);
                currentTargetPos = null;
                currentTargetBlock = null;
                tickDelay = 10;
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
            if (++miningNoProgressTicks < 8) return;
            // nichts mehr abbaubar: war der Ziel-Block selbst noch da, nicht wieder sofort anlaufen
            if (client.level.getBlockState(currentTargetPos).is(currentTargetBlock)) skip(currentTargetPos);
            MiningAction.resetMiningState();
            currentTargetPos = null;
            currentTargetBlock = null;
            isMining = false;
            miningNoProgressTicks = 0;
            tickDelay = 0; // sofort das nächste Ziel suchen
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
                skip(target);
                tickDelay = 2;
                return;
            }
            currentTargetPos = target;
            currentTargetBlock = state.getBlock();

            client.player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§e[AutoMiner] Target found at: " + target.toShortString())
            );

            Navigator.INSTANCE.goTo(client, Navigator.OWNER_AUTO_MINER,
                    Navigator.PRIORITY_AUTO_MINER, new GoalBlock(miningStand));
            if (!Navigator.INSTANCE.isNavigating(Navigator.OWNER_AUTO_MINER)
                    && client.player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))
                    > BlockInteraction.REACH * BlockInteraction.REACH) {
                skip(target);
                currentTargetPos = null;
                currentTargetBlock = null;
                tickDelay = 5;
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
                        Integer until = skipped.get(currentPos);
                        if (until != null) {
                            if (until > tickCounter) continue;
                            skipped.remove(currentPos);
                        }
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
            // gleiche Regeln wie der Pathfinder: freie Zellen, sicherer Boden, keine Lava daneben
            if (!BlockHelper.isFreeCell(client, stand) || !BlockHelper.isFreeCell(client, head)) continue;
            if (!BlockHelper.canStandOn(client, floor)) continue;
            if (BlockHelper.nearLava(client, stand) || BlockHelper.nearLava(client, head)) continue;

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
