package com.OsamaClient.newbridge.UI.components;

import net.minecraft.world.entity.EntityType;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Datenhülle für den Entity-Filter (kein GUI mehr). */
public class EntityFilterPicker extends Component {

    private final String label;
    public final Map<String, Boolean> filters = new LinkedHashMap<>();
    public final Map<String, Integer> colors = new LinkedHashMap<>();

    /** Individually selected entity types (arrow, snowball, zombie, ...) -> own color. Added on top of the groups. */
    public final Map<EntityType<?>, Integer> selectedEntities = new LinkedHashMap<>();

    /** Entity types that are never shown, even if their group is on or they were picked. Exclusion wins. */
    public final Set<EntityType<?>> excludedEntities = new LinkedHashSet<>();

    public static final int DEFAULT_COLOR = 0x6600FFFF;

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

    public boolean isEntityExcluded(EntityType<?> type) {
        return excludedEntities.contains(type);
    }

    public boolean isEntitySelected(EntityType<?> type) {
        return selectedEntities.containsKey(type);
    }

    /** Own color of the entity type if it was picked individually, otherwise the color of its group. */
    public int getColor(EntityType<?> type, String groupKey) {
        Integer own = selectedEntities.get(type);
        return own != null ? own : getColor(groupKey);
    }
}