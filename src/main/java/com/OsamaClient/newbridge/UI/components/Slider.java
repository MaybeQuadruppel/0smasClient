package com.OsamaClient.newbridge.UI.components;

import java.util.function.Consumer;

/** Datenhülle für einen Zahlen-Slider (kein GUI mehr). */
public class Slider extends Component {

    private final String label;
    private final double min;
    private final double max;
    private final double defaultValue;
    private final double step;
    private double value;
    private final Consumer<Double> onChange;

    public Slider(String label, double min, double max, double defaultValue,
                  Consumer<Double> onChange) {
        this(label, min, max, defaultValue, 0.0, onChange);
    }

    public Slider(String label, double min, double max, double defaultValue, double step,
                  Consumer<Double> onChange) {
        super(0, 0, 100, 20);
        this.label = label;
        this.min = min;
        this.max = max;
        this.defaultValue = defaultValue;
        this.step = step;
        this.value = snap(defaultValue);
        this.onChange = onChange;
    }

    public Slider withDescription(String description) {
        this.description = description;
        return this;
    }

    private double snap(double raw) {
        double clamped = Math.min(max, Math.max(min, raw));
        if (step <= 0) return clamped;
        double steps = Math.round((clamped - min) / step);
        return Math.min(max, Math.max(min, min + steps * step));
    }

    public String getLabel() { return label; }
    public double getValue() { return value; }
    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
    public double getDefaultValue() { return defaultValue; }

    public void setValue(double v) {
        this.value = snap(v);
        if (onChange != null) onChange.accept(this.value);
    }
}
