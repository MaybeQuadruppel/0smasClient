package com.OsamaClient.newbridge.UI.components;

import net.minecraft.world.level.block.Block;

import java.util.HashSet;
import java.util.Set;

/** Datenhülle für die Block-Auswahl (kein GUI mehr). */
public class BlockPicker extends Component {

    private final String label;
    public final Set<Block> selectedBlocks = new HashSet<>();

    public BlockPicker(String label) {
        super(0, 0, 100, 28);
        this.label = label;
    }

    public String getLabel() { return this.label; }

    public BlockPicker withDescription(String description) {
        this.description = description;
        return this;
    }
}
