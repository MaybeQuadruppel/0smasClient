package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Consumer;

/**
 * Farb-Auswahl mit drei Reglern (Hue / Saturation / Brightness) statt nur
 * Hue. Mit reinem Hue (S=1, V=1 fix) waren Schwarz, Grau und Weiß nicht
 * erreichbar, da diese ausschließlich über Sättigung bzw. Helligkeit
 * entstehen - deshalb jetzt vollwertiges HSB.
 */
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
    private static final int PAD = 4;
    private static final int BAR_STEP = 9; // vertikaler Abstand zwischen den drei Reglern

    public ColorPicker(String label, int defaultColor, Consumer<Integer> onChange) {
        this.label    = label;
        this.color    = defaultColor;
        this.onChange = onChange;
        // Mindestgröße: genug Platz für Label + drei Regler (Hue/Sat/Bri),
        // sonst überlappen sich die Balken bei kleiner Scale.
        syncScaledSize(110, 42, 90, 38);
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
        this.hue        = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    private void updateColorFromHSB() {
        this.color = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        if (this.onChange != null) this.onChange.accept(this.color);
    }

    private int trackX() { return x + UISettings.scaled(PAD) + UISettings.scaled(8); }
    private int trackW() { return width - (UISettings.scaled(PAD) * 2) - UISettings.scaled(22); }

    private int hueY() { return y + UISettings.scaled(14); }
    private int satY() { return hueY() + UISettings.scaled(BAR_STEP); }
    private int briY() { return satY() + UISettings.scaled(BAR_STEP); }

    private void applyDrag(int barTrackY, double mouseX, double mouseY, int which) {
        int tX = trackX(), tW = trackW();
        int barH = UISettings.scaled(TRACK_H);
        if (mouseY < barTrackY - 2 || mouseY > barTrackY + barH + 2) return;
        float pct = (float) Math.min(1.0, Math.max(0.0, (mouseX - tX) / (double) tW));
        switch (which) {
            case 1 -> hue = pct;
            case 2 -> saturation = pct;
            case 3 -> brightness = pct;
        }
        updateColorFromHSB();
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // Größe bei jedem Frame neu aus UISettings.scale ableiten - trackX()/
        // trackW() usw. hängen direkt von width/height ab, die sonst auf dem
        // Stand beim Erzeugen eingefroren blieben und nicht mehr zu den live
        // per UISettings.scaled(...) berechneten Innenabständen passen würden.
        syncScaledSize(baseWidth, baseHeight, 90, 38);

        Theme.Palette p = Theme.getActive().palette;

        int tX = trackX(), tW = trackW();
        int barH = UISettings.scaled(TRACK_H);
        int hueY = hueY(), satY = satY(), briY = briY();

        if (draggingBar == 1) applyDrag(hueY, mouseX, mouseY, 1);
        else if (draggingBar == 2) applyDrag(satY, mouseX, mouseY, 2);
        else if (draggingBar == 3) applyDrag(briY, mouseX, mouseY, 3);

        drawRoundedRect(guiGraphics, x, y, width, height, UISettings.withPanelAlpha(p.bg));
        drawRoundedOutline(guiGraphics, x, y, width, height, p.border);

        // Scissor gegen Überlauf des Labels bei großer Font-Size/kleiner Scale.
        guiGraphics.enableScissor(x + 1, y + 1, x + width - 1, hueY - 1);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label,
                x + UISettings.scaled(PAD), y + UISettings.scaled(3), p.textDim, false);
        guiGraphics.disableScissor();

        // Hue-Regler (Regenbogen, S=1/V=1)
        for (int i = 0; i < tW; i++) {
            int col = java.awt.Color.HSBtoRGB(i / (float) tW, 1f, 1f) | 0xFF000000;
            guiGraphics.fill(tX + i, hueY, tX + i + 1, hueY + barH, col);
        }
        drawThumb(guiGraphics, tX, tW, hueY, barH, hue);

        // Saturation-Regler (aktueller Hue, 0 = grau -> 1 = volle Sättigung)
        for (int i = 0; i < tW; i++) {
            int col = java.awt.Color.HSBtoRGB(hue, i / (float) tW, 1f) | 0xFF000000;
            guiGraphics.fill(tX + i, satY, tX + i + 1, satY + barH, col);
        }
        drawThumb(guiGraphics, tX, tW, satY, barH, saturation);

        // Brightness-Regler (aktueller Hue+Sat, 0 = schwarz -> 1 = hell)
        for (int i = 0; i < tW; i++) {
            int col = java.awt.Color.HSBtoRGB(hue, saturation, i / (float) tW) | 0xFF000000;
            guiGraphics.fill(tX + i, briY, tX + i + 1, briY + barH, col);
        }
        drawThumb(guiGraphics, tX, tW, briY, barH, brightness);

        int previewX = x + width - UISettings.scaled(14);
        int previewY = y + UISettings.scaled(5);
        drawRoundedRect(guiGraphics, previewX, previewY, UISettings.scaled(10), UISettings.scaled(14), color | 0xFF000000);
        drawRoundedOutline(guiGraphics, previewX, previewY, UISettings.scaled(10), UISettings.scaled(14), p.border);
    }

    private void drawThumb(GuiGraphicsExtractor g, int trackX, int trackW, int trackY, int barH, float value) {
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