package com.qdrppl.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.function.Consumer;

public class ToggleButton extends Component {
    public boolean enabled;
    private final String label;
    private final Consumer<Boolean> callback;

    public ToggleButton(String label, boolean startValue, Consumer<Boolean> callback) {
        this.label = label;
        this.enabled = startValue;
        this.callback = callback;
        this.width = 100;
        this.height = 14;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(x, y, x + width, y + height, 0x90202020);
        int accentColor = enabled ? 0xFF55FF55 : 0xFFFF5555;
        guiGraphics.fill(x, y, x + 2, y + height, accentColor);

        String statusText = label + ": " + (enabled ? "§aON" : "§cOFF");
        guiGraphics.drawString(Minecraft.getInstance().font, statusText, x + 6, y + 3, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            enabled = !enabled;
            callback.accept(enabled);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }
}