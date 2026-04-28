package com.qdrppl.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;
import java.util.function.Consumer;

public class ModeButton extends Component {
    private final String label;
    private final List<String> modes;
    private int index;
    private final Consumer<String> onChange;

    public ModeButton(String label, List<String> modes, int startIndex, Consumer<String> onChange) {
        this.label = label;
        this.modes = modes;
        this.index = startIndex;
        this.onChange = onChange;
        this.width = 100;
        this.height = 14;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(x, y, x + width, y + height, 0x90202020);
        String text = label + ": §7" + modes.get(index);
        guiGraphics.drawString(Minecraft.getInstance().font, text, x + 4, y + 3, 0xFFFFFFFF, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) index = (index + 1) % modes.size();
            else if (button == 1) index = (index - 1 + modes.size()) % modes.size();
            onChange.accept(modes.get(index));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }
}