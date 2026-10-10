package com.OsamaClient.newbridge.UI.components;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Datenhülle für die Block-Auswahl (kein GUI mehr). Jeder ausgewählte Block/jede Familie hat eine eigene ESP-Farbe. */
public class BlockPicker extends Component {

    /** Default ESP color for newly selected blocks (same value the old hardcoded fallback used). */
    public static final int DEFAULT_COLOR = 0x6600FFFF;

    private final String label;
    /** Ausgewählte Blöcke -> individuelle ESP-Farbe (ARGB). */
    public final Map<Block, Integer> selectedBlocks = new LinkedHashMap<>();

    /**
     * Blöcke, die als EIN Setting behandelt werden sollen: alle 17 Shulker-Box-Farben (das ungefärbte
     * {@link Blocks#SHULKER_BOX} + die 16 Farben aus {@link Blocks#DYED_SHULKER_BOX}) zusammen, alle
     * 8 Kupfertruhen-Varianten aus {@link Blocks#COPPER_CHEST} (4 Oxidationsstufen, jeweils normal
     * + gewachst) zusammen - toggeln eines Mitglieds selektiert/deselektiert die ganze Gruppe, statt
     * dass man jede Farbe/Stufe einzeln anhaken muss.
     */
    private static final List<Set<Block>> GROUPS = List.of(shulkerFamily(), new HashSet<>(Blocks.COPPER_CHEST.asList()));

    private static Set<Block> shulkerFamily() {
        Set<Block> family = new HashSet<>(Blocks.DYED_SHULKER_BOX.asList());
        family.add(Blocks.SHULKER_BOX);
        return family;
    }

    public BlockPicker(String label) {
        super(0, 0, 100, 28);
        this.label = label;
    }

    public String getLabel() { return this.label; }

    public BlockPicker withDescription(String description) {
        this.description = description;
        return this;
    }

    /** The full family a block belongs to (Shulker-Box-Farben, Kupfertruhen-Stufen), or just itself. */
    public static Set<Block> familyOf(Block block) {
        for (Set<Block> group : GROUPS) if (group.contains(block)) return group;
        return Set.of(block);
    }

    /**
     * An- oder abwählen ganzer Familie statt einzelnem Block: ist die Familie bereits komplett
     * selektiert, wird sie komplett entfernt, sonst komplett hinzugefügt (auch wenn vorher nur
     * ein Teil davon ausgewählt war).
     */
    public void toggle(Block block) {
        Set<Block> family = familyOf(block);
        if (selectedBlocks.keySet().containsAll(family)) {
            for (Block b : family) selectedBlocks.remove(b);
        } else {
            for (Block b : family) selectedBlocks.putIfAbsent(b, DEFAULT_COLOR);
        }
    }

    /** True if this block (or any member of its family) is currently selected. */
    public boolean isSelected(Block block) {
        return selectedBlocks.containsKey(block);
    }

    /** Current ESP color for a block; falls back to the default if it isn't selected. */
    public int colorOf(Block block) {
        return selectedBlocks.getOrDefault(block, DEFAULT_COLOR);
    }

    /** Sets the color for every member of block's family that is currently selected. */
    public void setColor(Block block, int color) {
        for (Block b : familyOf(block)) {
            if (selectedBlocks.containsKey(b)) selectedBlocks.put(b, color);
        }
    }
}