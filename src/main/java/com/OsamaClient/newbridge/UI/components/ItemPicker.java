package com.OsamaClient.newbridge.UI.components;

import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

/** Datenhülle für die Item-Auswahl (kein GUI mehr). */
public class ItemPicker extends Component {

    private final String label;
    public final Map<Item, Integer> selectedItems = new HashMap<>();

    public ItemPicker(String label) {
        super(0, 0, 100, 28);
        this.label = label;
    }

    public String getLabel() { return this.label; }

    public ItemPicker withDescription(String description) {
        this.description = description;
        return this;
    }
}
