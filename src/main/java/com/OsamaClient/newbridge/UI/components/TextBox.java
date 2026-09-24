package com.OsamaClient.newbridge.UI.components;

import java.util.function.Consumer;

/** Datenhülle für ein Textfeld (kein GUI mehr). */
public class TextBox extends Component {

    private final String label;
    private String text;
    private final Consumer<String> onResponder;
    private int maxLength = -1;
    private boolean numericOnly = false;

    public TextBox(String label, String defaultText, Consumer<String> onResponder) {
        super(0, 0, 92, 28);
        this.label = label;
        this.text = defaultText != null ? defaultText : "";
        this.onResponder = onResponder;
    }

    public String getText() { return text; }

    public void setText(String text) {
        this.text = clampLength(text != null ? text : "");
        if (onResponder != null) onResponder.accept(this.text);
    }

    public TextBox withDescription(String description) {
        this.description = description;
        return this;
    }

    public TextBox withMaxLength(int maxLength) {
        this.maxLength = maxLength;
        this.text = clampLength(this.text);
        return this;
    }

    public TextBox numericOnly() {
        this.numericOnly = true;
        return this;
    }

    public String getLabel() { return label; }

    private String clampLength(String value) {
        return (maxLength >= 0 && value.length() > maxLength) ? value.substring(0, maxLength) : value;
    }
}
