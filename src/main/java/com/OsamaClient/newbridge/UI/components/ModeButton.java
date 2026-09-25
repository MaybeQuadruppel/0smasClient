package com.OsamaClient.newbridge.UI.components;

import java.util.List;
import java.util.function.Consumer;

/** Datenhülle für eine Modus-Auswahl (kein GUI mehr). */
public class ModeButton extends Component {

    private final String label;
    private final List<String> modes;
    private int index;
    private final Consumer<String> onChange;

    public ModeButton(String label, List<String> modes, int startIndex, Consumer<String> onChange) {
        super(0, 0, 100, 16);
        this.label = label;
        this.modes = modes;
        this.index = startIndex;
        this.onChange = onChange;
    }

    public ModeButton withDescription(String description) {
        this.description = description;
        return this;
    }

    public String getLabel() { return label; }
    public int getIndex() { return index; }
    public List<String> getModes() { return modes; }
    public String getMode() { return modes.get(index); }

    public void setIndex(int i) {
        this.index = Math.min(modes.size() - 1, Math.max(0, i));
        if (onChange != null) onChange.accept(modes.get(this.index));
    }
}
