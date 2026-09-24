package com.OsamaClient.newbridge.UI.components;

import java.util.function.Consumer;

/** Datenhülle für eine Farb-Einstellung (kein GUI mehr). */
public class ColorPicker extends Component {

    private final String label;
    private final Consumer<Integer> onChange;
    private int color;

    public ColorPicker(String label, int defaultColor, Consumer<Integer> onChange) {
        super(0, 0, 110, 14);
        this.label = label;
        this.onChange = onChange;
        this.color = defaultColor;
    }

    public ColorPicker withDescription(String description) {
        this.description = description;
        return this;
    }

    public String getLabel() { return label; }
    public int getColor() { return color; }

    public void setColor(int newColor) {
        this.color = newColor;
        if (onChange != null) onChange.accept(newColor);
    }
}
