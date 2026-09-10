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
    /** 0 = stufenlos, sonst Schrittweite (z. B. 1.0, 0.5, 5.0). */
    private final double step;
    private double value;
    private final Consumer<Double> onChange;
    private boolean dragging = false;
    private boolean focused = false;

    private long lastClickMs = 0L;
    private static final long DOUBLE_CLICK_MS = 300L;

    private static final int TRACK_INSET_X = 4;
    private static final int TRACK_H       = 4;

    public Slider(String label, double min, double max, double defaultValue,
                  Consumer<Double> onChange) {
        this(label, min, max, defaultValue, 0.0, onChange);
    }

    /** Variante mit fester Schrittweite, z. B. für ganzzahlige oder gerasterte Werte. */
    public Slider(String label, double min, double max, double defaultValue, double step,
                  Consumer<Double> onChange) {
        this.label        = label;
        this.min          = min;
        this.max          = max;
        this.defaultValue = defaultValue;
        this.step         = step;
        this.value        = snap(defaultValue);
        this.onChange     = onChange;
        // Mindestgröße: verhindert, dass Label-Text und Track bei sehr
        // kleiner UI-Scale übereinander gerendert werden ("Text clipped
        // hinter dem Slider").
        syncScaledSize(100, 15, 60, 16);
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
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // Größe bei jedem Frame neu aus UISettings.scale ableiten (mit
        // Mindestgröße), sonst bleiben Track/Thumb-Positionen auf dem Stand
        // beim Erzeugen eingefroren, während der Rest der GUI schon
        // umskaliert ist.
        syncScaledSize(baseWidth, baseHeight, 60, 16);

        // Alle inneren Maße MITSKALIEREN statt fixer Pixelwerte - sonst nimmt
        // z. B. der Track bei kleiner Scale relativ mehr Platz in der (dann
        // kleineren) Box ein und überlappt mit dem Label-Text darüber.
        int insetX = Math.max(2, UISettings.scaled(TRACK_INSET_X));
        int trackH = Math.max(2, UISettings.scaled(TRACK_H));
        int textPadX = Math.max(2, UISettings.scaled(5));
        int textPadY = Math.max(1, UISettings.scaled(2));
        int bottomGap = Math.max(1, UISettings.scaled(2));

        Theme.Palette p = Theme.getActive().palette;

        if (dragging) {
            double usable = width - insetX * 2;
            double diff   = Math.min(usable, Math.max(0, mouseX - x - insetX));
            applyValue(min + (diff / usable) * (max - min));
        }

        float hover = stepHover(mouseX, mouseY);

        drawRoundedRect(guiGraphics, x, y, width, height,
                UISettings.withPanelAlpha(lerpColor(p.bg, p.bgHover, hover)));
        drawRoundedOutline(guiGraphics, x, y, width, height,
                focused ? p.accent : lerpColor(p.border, p.accent, hover * 0.6f));

        int trackX = x + insetX;
        int trackY = y + height - trackH - bottomGap;
        int trackW = width - insetX * 2;

        // Scissor verhindert, dass der Label/Value-Text bei großer Font-Size
        // oder kleiner Scale über den Rand der Box hinausläuft - UND begrenzt
        // ihn zusätzlich nach unten auf den Bereich oberhalb des Tracks, damit
        // Text und Track sich nie gegenseitig überzeichnen ("Text clipped
        // hinter dem Slider").
        guiGraphics.enableScissor(x + 1, y + 1, x + width - 1, Math.max(y + 1, trackY - 1));
        String display = String.format("%s: %.1f", label, value);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, display,
                x + textPadX, y + textPadY, lerpColor(p.textDim, p.text, hover), false);
        guiGraphics.disableScissor();

        drawRoundedRect(guiGraphics, trackX, trackY, trackW, trackH, p.disabled);

        int fillW = Math.max(0, (int)(((value - min) / (max - min)) * trackW));
        if (fillW > 0) {
            drawRoundedRect(guiGraphics, trackX, trackY, fillW, trackH,
                    lerpColor(p.accent, p.text, hover));
        }

        int thumbX = Math.max(trackX, Math.min(trackX + trackW - 3, trackX + fillW - 1));
        guiGraphics.fill(thumbX, trackY - 1, thumbX + 3, trackY + trackH + 1, p.accent);
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
                // Doppelklick setzt den Wert auf den ursprünglichen Default zurück.
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