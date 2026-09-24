package com.OsamaClient.newbridge.UI.components;

import java.util.HashMap;
import java.util.Map;

/** Datenhülle für die Verzauberungs-Auswahl (kein GUI mehr). */
public class EnchantmentPicker extends Component {

    private final String label;
    public final Map<String, Integer> selectedEnchantments = new HashMap<>();

    public EnchantmentPicker(String label) {
        super(0, 0, 100, 28);
        this.label = label;
    }

    public String getLabel() { return this.label; }

    public EnchantmentPicker withDescription(String description) {
        this.description = description;
        return this;
    }
}
