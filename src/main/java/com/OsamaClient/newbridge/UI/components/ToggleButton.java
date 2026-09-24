package com.OsamaClient.newbridge.UI.components;

import java.util.function.Consumer;

/** Datenhülle für einen Boolean-Schalter (kein GUI mehr). */
public class ToggleButton extends Component {

    public boolean enabled;
    private final String label;
    private final Consumer<Boolean> callback;

    public ToggleButton(String label, boolean startValue, Consumer<Boolean> callback) {
        super(0, 0, 100, 16);
        this.label = label;
        this.enabled = startValue;
        this.callback = callback;
    }

    public ToggleButton withDescription(String description) {
        this.description = description;
        return this;
    }

    public String getLabel() { return label; }

    public void setValue(boolean v) {
        this.enabled = v;
        if (callback != null) callback.accept(v);
    }
}
