package com.OsamaClient.newbridge.UI.components;

import java.util.LinkedHashMap;
import java.util.Map;

/** Datenhülle für den Entity-Filter (kein GUI mehr). */
public class EntityFilterPicker extends Component {

    private final String label;
    public final Map<String, Boolean> filters = new LinkedHashMap<>();
    public final Map<String, Integer> colors = new LinkedHashMap<>();

    private static final int DEFAULT_COLOR = 0x6600FFFF;

    public EntityFilterPicker(String label) {
        super(0, 0, 100, 28);
        this.label = label;

        filters.put("Players", true);
        filters.put("Hostiles", true);
        filters.put("Animals", false);
        filters.put("NPCs", false);
        filters.put("ArmorStands", false);
    }

    public String getLabel() { return this.label; }

    public EntityFilterPicker withDescription(String description) {
        this.description = description;
        return this;
    }

    public boolean isFilterEnabled(String key) {
        return filters.getOrDefault(key, false);
    }

    public int getColor(String key) {
        return colors.getOrDefault(key, DEFAULT_COLOR);
    }
}
