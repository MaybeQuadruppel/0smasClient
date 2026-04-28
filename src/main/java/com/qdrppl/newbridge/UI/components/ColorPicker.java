package com.qdrppl.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.function.Consumer;

public class ColorPicker extends Component {
    private final String label;
    private float hue = 0f;
    private float saturation = 1f;
    private float brightness = 1f;
    private int color;
    private final Consumer<Integer> onChange;
    private boolean dragging = false;

    public ColorPicker(String label, int defaultColor, Consumer<Integer> onChange) {
        this.label = label;
        this.color = defaultColor;
        this.onChange = onChange;
        this.width = 100;
        this.height = 24;

        float[] hsb = new float[3];
        java.awt.Color c = new java.awt.Color(defaultColor);
        java.awt.Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), hsb);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(x, y, x + width, y + height, 0x90202020);

        guiGraphics.drawString(Minecraft.getInstance().font, label, x + 4, y + 2, 0xFFFFFF, true);

        int sliderX = x + 4;
        int sliderY = y + 12;
        int sliderWidth = width - 20;
        int sliderHeight = 8;

        for (int i = 0; i < sliderWidth; i++) {
            float h = i / (float) sliderWidth;
            int c = java.awt.Color.HSBtoRGB(h, 1f, 1f);
            guiGraphics.fill(sliderX + i, sliderY, sliderX + i + 1, sliderY + sliderHeight, c | 0xFF000000);
        }

        int indicatorX = sliderX + (int) (hue * sliderWidth);
        guiGraphics.fill(indicatorX - 1, sliderY - 1, indicatorX + 1, sliderY + sliderHeight + 1, 0xFFFFFFFF);

        guiGraphics.fill(x + width - 14, y + 10, x + width - 4, y + 20, color | 0xFF000000);

        if (dragging) {
            updateColor(mouseX, sliderX, sliderWidth);
        }
    }

    private void updateColor(int mouseX, int sliderX, int sliderWidth) {
        float diff = Math.min(sliderWidth, Math.max(0, mouseX - sliderX));
        this.hue = diff / (float) sliderWidth;
        this.color = java.awt.Color.HSBtoRGB(hue, saturation, brightness);
        onChange.accept(this.color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            int sliderX = x + 4;
            int sliderWidth = width - 20;

            if (mouseY >= y + 12 && mouseY <= y + 20 && mouseX >= sliderX && mouseX <= sliderX + sliderWidth) {
                dragging = true;
                updateColor((int) mouseX, sliderX, sliderWidth);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return false;
    }
}