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

    private static final int BASE_HEIGHT = 17;
    private static final int TRACK_INSET_X = 6;
    private static final int TRACK_H = 3;

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

        int insetX = Math.max(2, UISettings.scaled(TRACK_INSET_X));
        int trackH = Math.max(2, UISettings.scaled(TRACK_H));
        int textPadX = Math.max(2, UISettings.scaled(6));
        int textPadY = Math.max(1, UISettings.scaled(4));

        Theme.Palette p = Theme.getActive().palette;

        if (dragging) {
            double usable = width - insetX * 2;
            double diff   = Math.min(usable, Math.max(0, mouseX - x - insetX));
            applyValue(min + (diff / usable) * (max - min));
        }

        hoverAnim = approach(hoverAnim, isHovered(mouseX, mouseY) ? 1f : 0f, 0.25f);

        float targetPct = (max > min) ? (float) ((value - min) / (max - min)) : 0f;
        animPct = animPct < 0f ? targetPct : approach(animPct, targetPct, 0.35f);

        // Hintergrund (dezent, nur bei Hover leicht heller) – kein Rahmen für cleanen Look
        drawRoundedRect(guiGraphics, x, y, width, height,
                UISettings.withPanelAlpha(lerpColor(p.bg, p.bgHover, hoverAnim * 0.8f)));
        if (focused) drawRoundedOutline(guiGraphics, x, y, width, height,
                withAlpha(p.accent, 0.7f));

        int trackX = x + insetX;
        int trackW = width - insetX * 2;
        int trackY = y + height - trackH - Math.max(2, UISettings.scaled(4));

        // Label links, Wert rechts (rechtsbündig)
        String valStr = formatValue(value);
        int valW = UISettings.textWidth(Minecraft.getInstance().font, valStr);
        guiGraphics.enableScissor(x + 1, y + 1, x + width - 1, Math.max(y + 1, trackY));
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label,
                x + textPadX, y + textPadY, lerpColor(p.textDim, p.text, hoverAnim), false);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, valStr,
                x + width - textPadX - valW, y + textPadY,
                lerpColor(p.textDim, p.accent, 0.4f + hoverAnim * 0.6f), false);
        guiGraphics.disableScissor();

        // Track + animierter Fill
        roundRect(guiGraphics, trackX, trackY, trackW, trackH, trackH / 2, p.disabled);
        int fillW = Math.max(0, Math.round(animPct * trackW));
        if (fillW > 0) {
            roundRect(guiGraphics, trackX, trackY, fillW, trackH, trackH / 2,
                    lerpColor(p.accent, p.text, hoverAnim * 0.35f));
        }

        // Kreisförmiger Thumb, wächst leicht bei Hover/Drag
        int baseR = Math.max(2, UISettings.scaled(3));
        int grow = Math.round((hoverAnim + (dragging ? 1f : 0f)) * UISettings.scaled(1));
        int r = baseR + Math.min(UISettings.scaled(2), grow);
        int cx = trackX + fillW;
        int cy = trackY + trackH / 2;
        cx = Math.max(trackX + r, Math.min(trackX + trackW - r, cx));
        roundRect(guiGraphics, cx - r, cy - r, r * 2, r * 2, r, p.text);
        roundRect(guiGraphics, cx - r + 1, cy - r + 1, (r - 1) * 2, (r - 1) * 2, r - 1, p.accent);
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