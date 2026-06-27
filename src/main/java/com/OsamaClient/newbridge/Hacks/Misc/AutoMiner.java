package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.Hacks.Movement.pathing.BlockHelper;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.GoalBlock;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.MiningAction;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Navigator;
import com.OsamaClient.newbridge.UI.components.BlockPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// TODO: Importiere hier deine MiningAction-Klasse, falls sie in einem anderen Package liegt
// import com.qdrppl.newbridge.Hacks.Mining.MiningAction;

public class AutoMiner extends Module {

    private final BlockPicker blockPicker = new BlockPicker("Targets");
    private final Slider scanRadius = new Slider("Scan Radius", 10.0, 100.0, 40.0, val -> {});

    private int tickDelay = 0;
    private BlockPos currentTargetPos = null;
    private Block currentTargetBlock = null;
    private boolean isMining = false;

    public AutoMiner() {
        super("AutoMiner", "Farms for you", Category.MISC);
        this.settings.add(blockPicker);
        this.settings.add(scanRadius);
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        Navigator.INSTANCE.stop(mc);
        currentTargetPos = null;
        currentTargetBlock = null;
        isMining = false;
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null) return;

        // 1. PHASE: Der Navigator läuft aktiv zum Erz
        if (Navigator.INSTANCE.isNavigating()) {
            Navigator.INSTANCE.onTick(client);
            return;
        }

        // 2. PHASE: Der Navigator ist angekommen -> Starte das Vein Mining auf dem Zielblock
        if (currentTargetPos != null && currentTargetBlock != null && !isMining) {
            client.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("§a[AutoMiner] Target reached starting Veinmine"));
            isMining = true;

            MiningAction.mineVein(client, currentTargetPos, currentTargetBlock);

            currentTargetPos = null;
            currentTargetBlock = null;
            isMining = false;
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
            currentTargetPos = target;
            currentTargetBlock = state.getBlock();

            client.gui.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("§e[AutoMiner] Target found at: " + target.toShortString()));

            Navigator.INSTANCE.goTo(client, new GoalBlock(target));
            if (!Navigator.INSTANCE.isNavigating()) {
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

                    if (blockPicker.selectedBlocks.contains(block)) {
                        if (BlockHelper.isEncasedInBedrock(client.level, currentPos)) {
                            continue;
                        }
                        if (BlockHelper.isBedrockAbove(client.level, currentPos)){
                            continue;
                        }
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
}