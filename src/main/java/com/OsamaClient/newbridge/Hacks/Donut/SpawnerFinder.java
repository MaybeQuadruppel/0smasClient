package com.OsamaClient.newbridge.Hacks.Donut;

import com.OsamaClient.newbridge.Hacks.Misc.Waypoints;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Scans the loaded chunks for mob spawners (cheap: only looks at block entities) and reports every new one. */
public class SpawnerFinder extends Module {

    public static SpawnerFinder INSTANCE;

    public double radiusChunks = 12.0;
    public boolean chat = true;
    public boolean waypoint = false;

    private final Set<BlockPos> found = new LinkedHashSet<>();
    private net.minecraft.world.level.Level lastLevel;
    private int tick;

    public SpawnerFinder() {
        super("SpawnerFinder", "Finds mob spawners in loaded chunks and reports their coordinates (DonutSMP).", Category.Donut);
        INSTANCE = this;
        settings.add(new Slider("Radius (chunks)", 1.0, 32.0, radiusChunks, 1.0, v -> radiusChunks = v)
                .withDescription("How far around you chunks are scanned"));
        settings.add(new ToggleButton("Chat", chat, v -> chat = v).withDescription("Print every new spawner in the chat"));
        settings.add(new ToggleButton("Waypoint", waypoint, v -> waypoint = v).withDescription("Create a waypoint for every new spawner"));
    }

    public List<BlockPos> found() { return new ArrayList<>(found); }

    public void clear() { found.clear(); }

    @Override
    public void onEnable() { found.clear(); tick = 0; }

    @Override
    public void onTick(Minecraft mc) {
        var level = mc.level;
        var p = mc.player;
        if (level == null || p == null) return;
        if (level != lastLevel) { lastLevel = level; found.clear(); }
        if (++tick % 20 != 1) return;
        int r = (int) radiusChunks;
        int cx = p.chunkPosition().x(), cz = p.chunkPosition().z();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity)) continue;
                    BlockPos pos = be.getBlockPos();
                    if (found.add(pos)) announce(mc, pos);
                }
            }
        }
    }

    private void announce(Minecraft mc, BlockPos pos) {
        if (chat) mc.player.sendSystemMessage(Component.literal("§e[SpawnerFinder] §fSpawner at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
        if (waypoint && Waypoints.INSTANCE != null) {
            Waypoints.INSTANCE.handleCommand(new String[]{".wp", "add", "Spawner" + found.size(),
                    String.valueOf(pos.getX()), String.valueOf(pos.getY()), String.valueOf(pos.getZ())});
        }
    }
}
