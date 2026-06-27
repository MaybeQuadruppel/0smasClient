package com.OsamaClient.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Consumer;

public class Slider extends Component {

    private final String label;
    private final double min;
    private final double max;
    private double value;
    private final Consumer<Double> onChange;
    private boolean dragging = false;

    // Palette (Anpassung an Schwarz-Weiß-Vorgabe)
    private static final int C_BG        = 0xF20A0A0A; // C_PANEL_BG
    private static final int C_BG_HOV    = 0xFF181818; // C_PANEL_HEADER
    private static final int C_BORDER    = 0xFF2C2C2C; // C_SEPARATOR
    private static final int C_FILL      = 0xFFFFFFFF; // C_ACCENT (Weiß)
    private static final int C_FILL_HOV  = 0xFFBBBBBB; // C_KEYBIND (Helles Grau für Hover)
    private static final int C_TRACK     = 0xFF3A3A3A; // C_DISABLED (Dunkles Grau für die Schiene)
    private static final int C_THUMB     = 0xFFFFFFFF; // C_ACCENT (Der Griff ist Weiß)
    private static final int C_LABEL     = 0xFFEEEEEE; // C_TEXT (Fast Weiß)
    private static final int C_LABEL_DIM = 0xFF666666; // C_TEXT_DIM (Gedimmtes Grau)

    private static final int TRACK_INSET_X = 4;
    private static final int TRACK_H       = 4;

    public Slider(String label, double min, double max, double defaultValue,
                  Consumer<Double> onChange) {
        this.label    = label;
        this.min      = min;
        this.max      = max;
        this.value    = defaultValue;
        this.onChange = onChange;
        this.width    = 110;
        this.height   = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (dragging) {
            double usable = width - TRACK_INSET_X * 2;
            double diff   = Math.min(usable, Math.max(0, mouseX - x - TRACK_INSET_X));
            this.value    = min + (diff / usable) * (max - min);
            onChange.accept(this.value);
        }

        float hover = stepHover(mouseX, mouseY);

        // Hintergrund (Panel-Farben)
        drawRoundedRect(   guiGraphics, x, y, width, height,
                lerpColor(C_BG, C_BG_HOV, hover));

        // Umrandung (Akzentuierung bei Hover)
        drawRoundedOutline(guiGraphics, x, y, width, height,
                lerpColor(C_BORDER, C_FILL, hover * 0.6f));

        // Label + Wert
        String display = String.format("%s: %.1f", label, value);
        guiGraphics.text(Minecraft.getInstance().font, display,
                x + 5, y + 2, lerpColor(C_LABEL_DIM, C_LABEL, hover), false);

        // Track (Die "Rille" des Sliders)
        int trackX = x + TRACK_INSET_X;
        int trackY = y + height - TRACK_H - 2;
        int trackW = width - TRACK_INSET_X * 2;
        drawRoundedRect(guiGraphics, trackX, trackY, trackW, TRACK_H, C_TRACK);

        // Fill (Der ausgefüllte Bereich - Weiß/Hellgrau)
        int fillW = Math.max(0, (int)(((value - min) / (max - min)) * trackW));
        if (fillW > 0) {
            drawRoundedRect(guiGraphics, trackX, trackY, fillW, TRACK_H,
                    lerpColor(C_FILL, C_FILL_HOV, hover));
        }

        // Thumb (Der Zeiger)
        int thumbX = Math.max(trackX, Math.min(trackX + trackW - 3, trackX + fillW - 1));
        guiGraphics.fill(thumbX, trackY - 1, thumbX + 3, trackY + TRACK_H + 1, C_THUMB);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) { this.dragging = true; return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return true;
    }

    public String getLabel()     { return label; }
    public double getValue()     { return value; }
    public void setValue(double v) {
        this.value = Math.min(max, Math.max(min, v));
        onChange.accept(this.value);
    }
}