package com.OsamaClient.newbridge.Hacks.Visual.ESP;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.BlockPicker;
import com.OsamaClient.newbridge.Utils.Render.Color;
import com.OsamaClient.newbridge.Utils.Render.Events.EventHandler;
import com.OsamaClient.newbridge.Utils.Render.Events.Render3DEvent;
import com.OsamaClient.newbridge.Utils.Render.Renderer3D;
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
        super("BlockESP", "Look-Up Blocks", Category.VISUAL);

        this.blockPicker = new BlockPicker("Block List").withDescription("Selects which blocks to highlight with ESP.");
        this.settings.add(this.blockPicker);

        this.settings.add(new Slider("Chunk Range", 1.0, 16.0, rangeInChunks, val -> rangeInChunks = val).withDescription("Sets the chunk render and scan range for block ESP."));
        this.settings.add(new Slider("Scan Delay (s)", 0.5, 10.0, scanDelay, val -> scanDelay = val).withDescription("Sets the delay in seconds between block scans."));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.level == null || client.player == null) return;
        if (!this.enabled) return;
        if (tickCounter++ % (int)(scanDelay * 20) != 0) return;
        if (isScanning || blockPicker.selectedBlocks.isEmpty()) return;

        new Thread(() -> {
            isScanning = true;
            try {
                List<RenderUtils.ESPBlockData> found = new ArrayList<>();
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

    private void scanChunkSection(LevelChunk chunk, int yBase, List<RenderUtils.ESPBlockData> found) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 16; y++) {
                    BlockPos pos = chunk.getPos().getBlockAt(x, yBase + y, z);
                    Block block = chunk.getBlockState(pos).getBlock();

                    if (blockPicker.selectedBlocks.contains(block)) {
                        int color = RenderUtils.BLOCK_COLORS.getOrDefault(block, 0xFF00FFFF); // Default: Cyan
                        found.add(new RenderUtils.ESPBlockData(pos.immutable(), color));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled || RenderUtils.BLOCKS_TO_RENDER.isEmpty()) return;

        for (RenderUtils.ESPBlockData data : RenderUtils.BLOCKS_TO_RENDER) {
            BlockPos pos = data.pos();
            int argb = data.color();

            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            int a = (argb >> 24) & 0xFF;
            if (a == 0) a = 255;

            // Weltposition - Kameraposition = relative Position für den Renderer
            double x = pos.getX() - event.offsetX;
            double y = pos.getY() - event.offsetY;
            double z = pos.getZ() - event.offsetZ;

            // 1x1x1 Block-Box zeichnen
            event.renderer.boxNoDepth(
                    x, y, z,
                    x + 1.0, y + 1.0, z + 1.0,
                    new Color(r, g, b, 50),  // Dezente Füllung
                    new Color(r, g, b, 255), // Fester Rand
                    Renderer3D.ShapeMode.BOTH,
                    0
            );
        }
    }

    @Override
    public void onDisable() {
        RenderUtils.BLOCKS_TO_RENDER.clear();
        super.onDisable();
    }
}