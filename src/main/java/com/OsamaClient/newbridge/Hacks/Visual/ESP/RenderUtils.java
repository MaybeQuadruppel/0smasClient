package com.OsamaClient.newbridge.Hacks.Visual.ESP;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class RenderUtils {
    public static final Map<Block, Integer> BLOCK_COLORS = new ConcurrentHashMap<>();
    public static final List<ESPBlockData> BLOCKS_TO_RENDER = new CopyOnWriteArrayList<>();

    public record ESPBlockData(BlockPos pos, int color) {}
}