package com.OsamaClient.newbridge.Hacks.Visual.minimap;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Farbe einer Blocksäule wie auf Vanilla-Karten (MapColor), mit Höhenschattierung gegenüber dem
 * nördlichen Nachbarn und dunklerem tiefem Wasser. Ohne Client-Abhängigkeit -> headless testbar.
 */
public final class MapSampler {

    private MapSampler() {}

    /** Oberster "sichtbarer" Block (MapColor != NONE) ab {@code fromY} abwärts, sonst minY - 1. */
    public static int scanTop(BlockGetter level, int x, int z, int fromY, int minY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = fromY; y >= minY; y--) {
            p.set(x, y, z);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.getMapColor(level, p) != MapColor.NONE) return y;
        }
        return minY - 1;
    }

    /**
     * Höhlenmodus (Nether/Höhlen): ab {@code fromY} erst durch festen Block nach unten bis Luft,
     * dann bis zum nächsten Boden.
     */
    public static int scanCave(BlockGetter level, int x, int z, int fromY, int minY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int y = fromY;
        // falls wir in einem Block starten: bis zur ersten Luft
        while (y >= minY && !level.getBlockState(p.set(x, y, z)).isAir()) y--;
        return scanTop(level, x, z, y, minY);
    }

    /** Biom-/Block-Tönung (RGB) für einen Block, oder -1 für keine (z.B. Gras, Laub, Wasser). */
    public interface Tint { int of(BlockState state, BlockGetter level, BlockPos pos); }

    /** ARGB-Farbe der Säule (nur Norden als Nachbar); 0 = nichts (transparent). */
    public static int columnColor(BlockGetter level, int x, int topY, int z, int northTopY, int minY) {
        return columnColor(level, x, topY, z, northTopY, northTopY, minY, null);
    }

    /**
     * ARGB-Farbe der Säule; 0 = nichts (transparent). Schattierung aus dem Höhenunterschied zu Norden UND
     * Westen (Licht von links oben, 7 Stufen), tiefes Wasser dunkler, optionale Biom-Tönung.
     */
    public static int columnColor(BlockGetter level, int x, int topY, int z, int northTopY, int westTopY, int minY, Tint tint) {
        if (topY < minY) return 0;
        BlockPos pos = new BlockPos(x, topY, z);
        BlockState s = level.getBlockState(pos);
        MapColor mc = s.getMapColor(level, pos);
        if (mc == MapColor.NONE) return 0;

        float shade;
        boolean water = s.getFluidState().is(FluidTags.WATER);
        if (water) {
            int depth = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int y = topY; y > topY - 16 && y >= minY; y--) {
                if (!level.getFluidState(p.set(x, y, z)).is(FluidTags.WATER)) break;
                depth++;
            }
            shade = Math.max(0.45f, 1.06f - 0.065f * depth);
        } else {
            int dn = northTopY < minY ? 0 : topY - northTopY;
            int dw = westTopY < minY ? 0 : topY - westTopY;
            int diff = Mth.clamp(dn + dw, -3, 3);
            shade = switch (diff) {
                case 3 -> 1.20f;
                case 2 -> 1.12f;
                case 1 -> 1.06f;
                case 0 -> 1.0f;
                case -1 -> 0.93f;
                case -2 -> 0.86f;
                default -> 0.78f;
            };
        }

        int base = mc.col;
        if (tint != null) {
            int t = tint.of(s, level, pos);
            if (t != -1 && (t & 0xFFFFFF) != 0xFFFFFF) base = t & 0xFFFFFF;
        }
        float k = 0.86f * shade;
        int r = Mth.clamp(Math.round(((base >> 16) & 0xFF) * k), 0, 255);
        int g = Mth.clamp(Math.round(((base >> 8) & 0xFF) * k), 0, 255);
        int b = Mth.clamp(Math.round((base & 0xFF) * k), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
