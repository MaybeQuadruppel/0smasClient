package com.OsamaClient.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.function.Consumer;

public class ColorPicker extends Component {

    private final String label;
    private float hue = 0f;
    private int color;
    private final Consumer<Integer> onChange;
    private boolean dragging = false;

    // Schwarz-Weiß Palette & Abstände
    private static final int C_BG       = 0xF20A0A0A;
    private static final int C_BORDER   = 0xFF2C2C2C;
    private static final int C_TEXT_DIM = 0xFF666666;
    private static final int C_TEXT     = 0xFFEEEEEE;

    private static final int TRACK_H = 4;
    private static final int PAD     = 4;

    public ColorPicker(String label, int defaultColor, Consumer<Integer> onChange) {
        this.label    = label;
        this.color    = defaultColor;
        this.onChange = onChange;
        this.width    = 110;
        this.height   = 24; // Deutlich flacher, da nur noch ein Slider
        decomposeColor(defaultColor);
    }

    public String getLabel() { return this.label; }
    public int getColor() { return this.color; }

    public void setColor(int newColor) {
        this.color = newColor;
        decomposeColor(newColor);
        if (this.onChange != null) this.onChange.accept(newColor);
    }

    private void decomposeColor(int argb) {
        float[] hsb = new float[3];
        java.awt.Color awt = new java.awt.Color(argb);
        java.awt.Color.RGBtoHSB(awt.getRed(), awt.getGreen(), awt.getBlue(), hsb);
        this.hue = hsb[0];
    }

    private void updateColorFromHue() {
        this.color = java.awt.Color.HSBtoRGB(hue, 1.0f, 1.0f);
        if (this.onChange != null) this.onChange.accept(this.color);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int trackX = x + PAD + 8;
        int trackW = width - (PAD * 2) - 22; // Platz für Vorschau rechts
        int trackY = y + height - TRACK_H - 5;

        if (dragging) {
            this.hue = Math.min(1f, Math.max(0f, (mouseX - trackX) / (float) trackW));
            updateColorFromHue();
        }

        drawRoundedRect(guiGraphics, x, y, width, height, C_BG);
        drawRoundedOutline(guiGraphics, x, y, width, height, C_BORDER);


        guiGraphics.text(Minecraft.getInstance().font, label, x + PAD, y + 3, C_TEXT_DIM, false);


        for (int i = 0; i < trackW; i++) {
            int col = java.awt.Color.HSBtoRGB(i / (float) trackW, 1f, 1f) | 0xFF000000;
            guiGraphics.fill(trackX + i, trackY, trackX + i + 1, trackY + TRACK_H, col);
        }

        int thumbX = trackX + (int)(hue * trackW);
        thumbX = Math.max(trackX, Math.min(trackX + trackW - 2, thumbX));
        guiGraphics.fill(thumbX - 1, trackY - 1, thumbX + 2, trackY + TRACK_H + 1, 0xFFFFFFFF);


        int previewX = x + width - 14;
        int previewY = y + 5;
        drawRoundedRect(guiGraphics, previewX, previewY, 10, 14, color | 0xFF000000);
        drawRoundedOutline(guiGraphics, previewX, previewY, 10, 14, C_BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            this.dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.dragging = false;
        return false;
    }
}