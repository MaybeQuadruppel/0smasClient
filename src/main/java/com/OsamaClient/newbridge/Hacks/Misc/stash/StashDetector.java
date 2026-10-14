package com.OsamaClient.newbridge.Hacks.Misc.stash;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

/**
 * Entscheidet aus den Container-Blöcken eines Bereichs (z.B. Chunk), ob es ein Stash ist.
 * Natürliche Strukturen (Trial Chambers, Dungeons) werden ausgeschlossen. Headless testbar.
 */
public final class StashDetector {

    public record Found(BlockPos pos, Block block) {}

    private StashDetector() {}

    public static boolean isContainer(Block b) {
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL || b instanceof ShulkerBoxBlock
                || b == Blocks.HOPPER || b == Blocks.DISPENSER || b == Blocks.DROPPER;
    }

    /** Hinweise auf natürliche Strukturen im selben Bereich. */
    public static boolean isNaturalMarker(Block b) {
        return b == Blocks.TRIAL_SPAWNER || b == Blocks.VAULT || b == Blocks.SPAWNER;
    }

    /**
     * @param minWeight Mindestwert (Shulker zählen dreifach)
     * @return Stash (ohne Server/Zeit) oder null
     */
    public static StashStore.Stash evaluate(List<Found> found, String dimension, int minWeight) {
        int chests = 0, barrels = 0, shulkers = 0, hoppers = 0, other = 0;
        boolean natural = false;
        long sx = 0, sy = 0, sz = 0;
        int n = 0;
        for (Found f : found) {
            Block b = f.block();
            if (isNaturalMarker(b)) { natural = true; continue; }
            if (!isContainer(b)) continue;
            if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST) chests++;
            else if (b == Blocks.BARREL) barrels++;
            else if (b instanceof ShulkerBoxBlock) shulkers++;
            else if (b == Blocks.HOPPER) hoppers++;
            else other++;
            sx += f.pos().getX(); sy += f.pos().getY(); sz += f.pos().getZ();
            n++;
        }
        if (n == 0) return null;
        // Spawner/Trial-Kammer + ein paar Kisten = Struktur. Viele Shulker/Hopper sind aber nie natürlich.
        if (natural && shulkers == 0 && hoppers == 0 && chests + barrels <= 6) return null;
        StashStore.Stash s = new StashStore.Stash();
        s.chests = chests; s.barrels = barrels; s.shulkers = shulkers; s.hoppers = hoppers; s.other = other;
        if (s.weight() < minWeight) return null;
        s.x = (int) (sx / n); s.y = (int) (sy / n); s.z = (int) (sz / n);
        s.dimension = dimension;
        return s;
    }
}
