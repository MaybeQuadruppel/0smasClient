package com.qdrppl.newbridge.Hacks.Visual.ESP;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;
import com.qdrppl.newbridge.UI.components.BlockPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.ArrayList;
import java.util.List;

public class BlockESP extends Module {
    public double rangeInChunks = 4.0;
    public double scanDelay = 2.0;
    private BlockPicker blockPicker;
    private int tickCounter = 0;
    private boolean isScanning = false;

    public BlockESP() {
        super("BlockESP","(LookUP Blocks)", Category.VISUAL);

        this.blockPicker = new BlockPicker("Block List");
        this.settings.add(this.blockPicker);

        this.settings.add(new Slider("Chunk Range", 1.0, 16.0, rangeInChunks, val -> rangeInChunks = val));
        this.settings.add(new Slider("Scan Delay (s)", 0.5, 10.0, scanDelay, val -> scanDelay = val));

        this.settings.add(new Slider("Red", 0.0, 1.0, 0.0, val -> RenderUtils.espRed = val.floatValue()));
        this.settings.add(new Slider("Green", 0.0, 1.0, 1.0, val -> RenderUtils.espGreen = val.floatValue()));
        this.settings.add(new Slider("Blue", 0.0, 1.0, 1.0, val -> RenderUtils.espBlue = val.floatValue()));
        this.settings.add(new Slider("Alpha", 0.0, 1.0, 0.4, val -> RenderUtils.espAlpha = val.floatValue()));
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.level == null || client.player == null) return;
        if (tickCounter++ % (int)(scanDelay * 20) != 0) return;
        if (isScanning || blockPicker.selectedBlocks.isEmpty()) return;

        new Thread(() -> {
            isScanning = true;
            try {
                List<BlockPos> found = new ArrayList<>();
                int radius = (int) rangeInChunks;
                int pX = client.player.chunkPosition().x();
                int pZ = client.player.chunkPosition().z();

                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (client.level == null) return;
                        LevelChunk chunk = client.level.getChunk(pX + x, pZ + z);
                        for (int y = client.level.getMinY(); y < client.level.getMaxY(); y += 16) {
                            scanChunkSection(chunk, y, found);
                        }
                    }
                }
                RenderUtils.BLOCKS_TO_RENDER.clear();
                RenderUtils.BLOCKS_TO_RENDER.addAll(found);
            } finally {
                isScanning = false;
            }
        }).start();
    }

    private void scanChunkSection(LevelChunk chunk, int yBase, List<BlockPos> found) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 16; y++) {
                    BlockPos pos = chunk.getPos().getBlockAt(x, yBase + y, z);
                    Block block = chunk.getBlockState(pos).getBlock();
                    if (blockPicker.selectedBlocks.contains(block)) {
                        found.add(pos.immutable());
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        RenderUtils.BLOCKS_TO_RENDER.clear();
        super.onDisable();
    }
}