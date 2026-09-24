package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.util.function.Consumer;

public class Slider extends Component {

    private final String label;
    private final double min;
    private final double max;
    private final double defaultValue;
    private final double step;
    private double value;
    private final Consumer<Double> onChange;
    private boolean dragging = false;
    private boolean focused = false;

    private long lastClickMs = 0L;
    private static final long DOUBLE_CLICK_MS = 300L;

    private static final int BASE_HEIGHT = 12;
    private static final int TRACK_INSET_X = 3;
    private static final int TRACK_H = 1;

    // Animationszustände (weich interpoliert pro Frame)
    private float animPct = -1f;   // -1 = noch nicht initialisiert
    private float hoverAnim = 0f;

    public Slider(String label, double min, double max, double defaultValue,
                  Consumer<Double> onChange) {
        this(label, min, max, defaultValue, 0.0, onChange);
    }

    public Slider(String label, double min, double max, double defaultValue, double step,
                  Consumer<Double> onChange) {
        super(0, 0, 100, BASE_HEIGHT);
        this.label        = label;
        this.min          = min;
        this.max          = max;
        this.defaultValue = defaultValue;
        this.step         = step;
        this.value        = snap(defaultValue);
        this.onChange     = onChange;
        syncScaledSize(100, BASE_HEIGHT, 60, BASE_HEIGHT);
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

    private void applyValue(double raw) {
        double newValue = snap(raw);
        if (newValue != this.value) {
            this.value = newValue;
            onChange.accept(this.value);
            float pct = (float) ((this.value - min) / (max - min));
            Sounds.drag(pct);
        }
    }

    @Override
    public void render(Object graphics, int mouseX, int mouseY) {
        if (!(graphics instanceof GuiGraphicsExtractor guiGraphics)) return;

        syncHeight(BASE_HEIGHT, UISettings.scaled(BASE_HEIGHT));

        int insetX = Math.max(1, UISettings.scaled(TRACK_INSET_X));
        int trackH = Math.max(1, UISettings.scaled(TRACK_H));

        Theme.Palette p = Theme.getActive().palette;

        if (dragging) {
            double usable = width - insetX * 2;
            double diff   = Math.min(usable, Math.max(0, mouseX - x - insetX));
            applyValue(min + (diff / usable) * (max - min));
        }

        hoverAnim = approach(hoverAnim, isHovered(mouseX, mouseY) ? 1f : 0f, 0.3f);

        float targetPct = (max > min) ? (float) ((value - min) / (max - min)) : 0f;
        animPct = animPct < 0f ? targetPct : approach(animPct, targetPct, 0.4f);

        if (hoverAnim > 0.01f) {
            guiGraphics.fill(x, y, x + width, y + height,
                    withAlpha(p.bgHover, hoverAnim * 0.4f));
        }

        // "Label: value" – Label links, Wert rechts, mit Scissor gegen Überlappung
        String valStr = formatValue(value);
        int valW = UISettings.textWidth(Minecraft.getInstance().font, valStr);
        int textY = y + UISettings.scaled(1);
        int valX = x + width - insetX - valW;
        guiGraphics.enableScissor(x + insetX, y, Math.max(x + insetX, valX - UISettings.scaled(2)), y + height);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label,
                x + insetX, textY, lerpColor(p.textDim, p.text, hoverAnim), false);
        guiGraphics.disableScissor();
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, valStr,
                valX, textY, lerpColor(p.accent, p.text, hoverAnim * 0.4f), false);

        // Dünne Underline-Track + animierter Fill + kleiner Marker
        int trackX = x + insetX;
        int trackW = width - insetX * 2;
        int trackY = y + height - trackH - UISettings.scaled(2);
        guiGraphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, p.disabled);
        int fillW = Math.max(0, Math.round(animPct * trackW));
        if (fillW > 0) {
            guiGraphics.fill(trackX, trackY, trackX + fillW, trackY + trackH, p.accent);
        }
        int markerW = Math.max(1, UISettings.scaled(1));
        int markerH = trackH + UISettings.scaled(2);
        int mxPos = Math.max(trackX, Math.min(trackX + trackW - markerW, trackX + fillW - markerW / 2));
        guiGraphics.fill(mxPos, trackY - UISettings.scaled(1), mxPos + markerW, trackY - UISettings.scaled(1) + markerH, p.text);
    }

    private String formatValue(double v) {
        if (step >= 1.0 || (step == 0.0 && v == Math.floor(v) && Math.abs(v) < 1e7)) {
            return String.valueOf((long) Math.round(v));
        }
        return String.format("%.1f", v);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            long now = System.currentTimeMillis();
            boolean doubleClick = (now - lastClickMs) <= DOUBLE_CLICK_MS;
            lastClickMs = now;

            focused = true;
            this.dragging = true;

            if (doubleClick) {
                applyValue(defaultValue);
                Sounds.select();
                return true;
            }

            int insetX = Math.max(2, UISettings.scaled(TRACK_INSET_X));
            double usable = width - insetX * 2;
            double diff   = Math.min(usable, Math.max(0, mouseX - x - insetX));
            applyValue(min + (diff / usable) * (max - min));
            return true;
        }
        if (button == 0) {
            focused = false;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.dragging = false;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!focused || dragging) return false;
        int key = event.key();
        double nudge = step > 0 ? step : (max - min) / 100.0;

        if (key == 263) { // LEFT
            applyValue(value - nudge);
            return true;
        }
        if (key == 262) { // RIGHT
            applyValue(value + nudge);
            return true;
        }
        return false;
    }

    public String getLabel()     { return label; }
    public double getValue()     { return value; }
    public void setValue(double v) {
        this.value = snap(v);
        onChange.accept(this.value);
    }
}