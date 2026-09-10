package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Consumer;

public class ColorPicker extends Component {

    private final String label;
    private float hue = 0f;
    private float saturation = 1f;
    private float brightness = 1f;
    private int color;
    private final Consumer<Integer> onChange;

    /** 0 = kein Drag, 1 = Hue, 2 = Saturation, 3 = Brightness. */
    private int draggingBar = 0;

    private static final int TRACK_H = 4;
    private static final int PAD = 6;
    private static final int BAR_STEP = 11;

    public ColorPicker(String label, int defaultColor, Consumer<Integer> onChange) {
        super(0, 0, 110, 48);
        this.label    = label;
        this.color    = defaultColor;
        this.onChange = onChange;
        syncScaledSize(110, 48, 90, 44);
        decomposeColor(defaultColor);
    }

    public String getLabel() { return this.label; }
    public int getColor() { return this.color; }

    public ColorPicker withDescription(String description) {
        this.description = description;
        return this;
    }

    public void setColor(int newColor) {
        this.color = newColor;
        decomposeColor(newColor);
        if (this.onChange != null) this.onChange.accept(newColor);
    }

    private void decomposeColor(int argb) {
        float[] hsb = new float[3];
        java.awt.Color awt = new java.awt.Color(argb);
        java.awt.Color.RGBtoHSB(awt.getRed(), awt.getGreen(), awt.getBlue(), hsb);
        this.hue        = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    private void updateColorFromHSB() {
        this.color = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        if (this.onChange != null) this.onChange.accept(this.color);
    }

    private int trackX() { return x + UISettings.scaled(PAD) + UISettings.scaled(12); }
    private int trackW() { return width - (UISettings.scaled(PAD) * 2) - UISettings.scaled(14); }

    private int hueY() { return y + UISettings.scaled(16); }
    private int satY() { return hueY() + UISettings.scaled(BAR_STEP); }
    private int briY() { return satY() + UISettings.scaled(BAR_STEP); }

    private void applyDrag(int barTrackY, double mouseX, double mouseY, int which) {
        int tX = trackX(), tW = trackW();
        int barH = UISettings.scaled(TRACK_H);
        if (mouseY < barTrackY - 4 || mouseY > barTrackY + barH + 4) return;
        float pct = (float) Math.min(1.0, Math.max(0.0, (mouseX - tX) / (double) tW));
        switch (which) {
            case 1 -> hue = pct;
            case 2 -> saturation = pct;
            case 3 -> brightness = pct;
        }
        updateColorFromHSB();
    }

    @Override
    public void render(Object graphics, int mouseX, int mouseY) {
        if (!(graphics instanceof GuiGraphicsExtractor guiGraphics)) return;

        syncScaledSize(baseWidth, baseHeight, 90, 44);
        Theme.Palette p = Theme.getActive().palette;

        int tX = trackX(), tW = trackW();
        int barH = UISettings.scaled(TRACK_H);
        int hueY = hueY(), satY = satY(), briY = briY();

        if (draggingBar == 1) applyDrag(hueY, mouseX, mouseY, 1);
        else if (draggingBar == 2) applyDrag(satY, mouseX, mouseY, 2);
        else if (draggingBar == 3) applyDrag(briY, mouseX, mouseY, 3);

        // Panel Alpha Transparenz
        drawRoundedRect(guiGraphics, x, y, width, height, UISettings.withPanelAlpha(p.bg));
        drawRoundedOutline(guiGraphics, x, y, width, height, p.border);

        // Label
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label,
                x + UISettings.scaled(PAD), y + UISettings.scaled(4), p.accent, false);

        // Farbmodus-Indikatoren (H, S, B)
        int lblX = x + UISettings.scaled(PAD);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, "H", lblX, hueY - 1, p.textDim, false);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, "S", lblX, satY - 1, p.textDim, false);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, "B", lblX, briY - 1, p.textDim, false);

        // Hue-Regler
        for (int i = 0; i < tW; i++) {
            int col = java.awt.Color.HSBtoRGB(i / (float) tW, 1f, 1f) | 0xFF000000;
            guiGraphics.fill(tX + i, hueY, tX + i + 1, hueY + barH, col);
        }
        drawThumb(guiGraphics, tX, tW, hueY, barH, hue, p.accent);

        // Saturation-Regler
        for (int i = 0; i < tW; i++) {
            int col = java.awt.Color.HSBtoRGB(hue, i / (float) tW, 1f) | 0xFF000000;
            guiGraphics.fill(tX + i, satY, tX + i + 1, satY + barH, col);
        }
        drawThumb(guiGraphics, tX, tW, satY, barH, saturation, p.accent);

        // Brightness-Regler
        for (int i = 0; i < tW; i++) {
            int col = java.awt.Color.HSBtoRGB(hue, saturation, i / (float) tW) | 0xFF000000;
            guiGraphics.fill(tX + i, briY, tX + i + 1, briY + barH, col);
        }
        drawThumb(guiGraphics, tX, tW, briY, barH, brightness, p.accent);

        // Vorschaufeld oben rechts
        int previewW = UISettings.scaled(12);
        int previewH = UISettings.scaled(10);
        int previewX = x + width - previewW - UISettings.scaled(PAD);
        int previewY = y + UISettings.scaled(4);
        drawRoundedRect(guiGraphics, previewX, previewY, previewW, previewH, color | 0xFF000000);
        drawRoundedOutline(guiGraphics, previewX, previewY, previewW, previewH, p.border);
    }

    private void drawThumb(GuiGraphicsExtractor g, int trackX, int trackW, int trackY, int barH, float value, int accentColor) {
        int thumbX = trackX + (int) (value * trackW);
        thumbX = Math.max(trackX, Math.min(trackX + trackW - 2, thumbX));
        g.fill(thumbX - 1, trackY - 1, thumbX + 2, trackY + barH + 1, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) return false;

        int barH = UISettings.scaled(TRACK_H);
        int hueY = hueY(), satY = satY(), briY = briY();

        int which;
        if (mouseY <= hueY + barH + (BAR_STEP / 2.0)) which = 1;
        else if (mouseY <= satY + barH + (BAR_STEP / 2.0)) which = 2;
        else which = 3;

        draggingBar = which;
        int barTrackY = which == 1 ? hueY : which == 2 ? satY : briY;
        applyDrag(barTrackY, mouseX, mouseY, which);

        Sounds.select();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingBar = 0;
        return false;
    }
}