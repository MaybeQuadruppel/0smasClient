package com.qdrppl.newbridge.Hacks.Visual;

import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.HashSet;
import java.util.Set;

public class XRay extends Module {

    public static boolean isActive = false;
    private static final Set<Block> WHITELIST = new HashSet<>();

    public XRay() {
        super("XRay", "Lets you see Ores & BlockEntities throught walls", Category.VISUAL);
        initWhitelist();
    }

    private void initWhitelist() {
        // --- ERZE (Normale Varianten) ---
        WHITELIST.add(Blocks.COAL_ORE);
        WHITELIST.add(Blocks.IRON_ORE);
        WHITELIST.add(Blocks.GOLD_ORE);
        WHITELIST.add(Blocks.REDSTONE_ORE);
        WHITELIST.add(Blocks.LAPIS_ORE);
        WHITELIST.add(Blocks.DIAMOND_ORE);
        WHITELIST.add(Blocks.EMERALD_ORE);
        WHITELIST.add(Blocks.NETHER_QUARTZ_ORE);
        WHITELIST.add(Blocks.NETHER_GOLD_ORE);
        WHITELIST.add(Blocks.ANCIENT_DEBRIS); // Ancient Debris / Antiker Schrott

        // --- ERZE (Deepslate / Tiefenschiefer Varianten) ---
        WHITELIST.add(Blocks.DEEPSLATE_COAL_ORE);
        WHITELIST.add(Blocks.DEEPSLATE_IRON_ORE);
        WHITELIST.add(Blocks.DEEPSLATE_GOLD_ORE);
        WHITELIST.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        WHITELIST.add(Blocks.DEEPSLATE_LAPIS_ORE);
        WHITELIST.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        WHITELIST.add(Blocks.DEEPSLATE_EMERALD_ORE);

        // --- FLÜSSIGKEITEN ---
        WHITELIST.add(Blocks.WATER);
        WHITELIST.add(Blocks.LAVA);

        // --- CONTAINER & FUNKTIONSBLÖCKE (Normale Blöcke) ---
        WHITELIST.add(Blocks.CHEST);
        WHITELIST.add(Blocks.TRAPPED_CHEST);
        WHITELIST.add(Blocks.ENDER_CHEST);
        WHITELIST.add(Blocks.BARREL);
        WHITELIST.add(Blocks.HOPPER);
        WHITELIST.add(Blocks.SPAWNER);

        // --- SHULKER KISTEN (Alle Farben) ---
        WHITELIST.add(Blocks.SHULKER_BOX);
        WHITELIST.add(Blocks.WHITE_SHULKER_BOX);
        WHITELIST.add(Blocks.ORANGE_SHULKER_BOX);
        WHITELIST.add(Blocks.MAGENTA_SHULKER_BOX);
        WHITELIST.add(Blocks.LIGHT_BLUE_SHULKER_BOX);
        WHITELIST.add(Blocks.YELLOW_SHULKER_BOX);
        WHITELIST.add(Blocks.LIME_SHULKER_BOX);
        WHITELIST.add(Blocks.PINK_SHULKER_BOX);
        WHITELIST.add(Blocks.GRAY_SHULKER_BOX);
        WHITELIST.add(Blocks.LIGHT_GRAY_SHULKER_BOX);
        WHITELIST.add(Blocks.CYAN_SHULKER_BOX);
        WHITELIST.add(Blocks.PURPLE_SHULKER_BOX);
        WHITELIST.add(Blocks.BLUE_SHULKER_BOX);
        WHITELIST.add(Blocks.BROWN_SHULKER_BOX);
        WHITELIST.add(Blocks.GREEN_SHULKER_BOX);
        WHITELIST.add(Blocks.RED_SHULKER_BOX);
        WHITELIST.add(Blocks.BLACK_SHULKER_BOX);
    }

    public static boolean isSupported(Block block) {
        return WHITELIST.contains(block);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        isActive = true;
        reloadChunks();
    }

    @Override
    public void onDisable() {
        isActive = false;
        reloadChunks();
        super.onDisable();
    }

    private void reloadChunks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            mc.levelRenderer.allChanged();
        }
    }
}