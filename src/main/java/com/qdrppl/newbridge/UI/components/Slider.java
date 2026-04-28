package com.qdrppl.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.function.Consumer;

public class Slider extends Component {
    private final String label;
    private final double min, max;
    private double value;
    private final Consumer<Double> onChange;
    private boolean dragging = false;

    public Slider(String label, double min, double max, double defaultValue, Consumer<Double> onChange) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.value = defaultValue;
        this.onChange = onChange;
        this.width = 100;
        this.height = 14;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (dragging) {
            double diff = Math.min(width, Math.max(0, mouseX - x));
            this.value = min + (diff / width) * (max - min);
            onChange.accept(this.value);
        }

        guiGraphics.fill(x, y, x + width, y + height, 0x90202020);
        double renderWidth = ((value - min) / (max - min)) * width;
        guiGraphics.fill(x, y, x + (int)renderWidth, y + height, 0xFF5555FF);

        String displayString = String.format("%s: %.1f", label, value);
        guiGraphics.drawString(Minecraft.getInstance().font, displayString, x + 4, y + 3, 0xFFFFFFFF, true);
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
        return true;
    }
}