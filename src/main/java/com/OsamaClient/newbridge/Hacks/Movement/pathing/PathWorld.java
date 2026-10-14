package com.OsamaClient.newbridge.Hacks.Movement.pathing;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;

import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * Weltzugriff für Pathfinder/BlockHelper. Im Spiel ist das einfach {@code mc.level} / {@code mc.player};
 * Tests können eine eigene Welt (beliebiger {@link BlockGetter}) und ein Hotbar-Modell einsetzen,
 * damit A*, Kosten und Sicherheitsregeln ohne laufenden Client geprüft werden können.
 */
public final class PathWorld {

    private static BlockGetter testLevel;
    private static Predicate<BlockPos> testLoaded;
    private static IntFunction<ItemStack> testHotbar;

    private PathWorld() {}

    /** Nur für Tests: eigene Welt einsetzen ({@code null} = wieder das echte Spiel benutzen). */
    public static void useTestWorld(BlockGetter level, Predicate<BlockPos> loaded, IntFunction<ItemStack> hotbar) {
        testLevel = level;
        testLoaded = loaded;
        testHotbar = hotbar;
    }

    public static boolean has(Minecraft mc) {
        return testLevel != null || (mc != null && mc.level != null);
    }

    public static BlockGetter level(Minecraft mc) {
        return testLevel != null ? testLevel : mc.level;
    }

    public static boolean loaded(Minecraft mc, BlockPos pos) {
        if (testLevel != null) return testLoaded == null || testLoaded.test(pos);
        return mc != null && mc.level != null && mc.level.isLoaded(pos);
    }

    public static boolean hasPlayer(Minecraft mc) {
        return testLevel != null ? testHotbar != null : mc != null && mc.player != null;
    }

    /** Hotbar-Slot 0..8 (leer, wenn es keinen Spieler gibt). */
    public static ItemStack hotbar(Minecraft mc, int slot) {
        if (testLevel != null) return testHotbar == null ? ItemStack.EMPTY : testHotbar.apply(slot);
        return mc == null || mc.player == null ? ItemStack.EMPTY : mc.player.getInventory().getItem(slot);
    }
}
