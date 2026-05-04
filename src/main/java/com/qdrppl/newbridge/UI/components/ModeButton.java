package com.qdrppl.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Consumer;

public class ModeButton extends Component {

    private final String label;
    private final List<String> modes;
    private int index;
    private final Consumer<String> onChange;

    // Schwarz-Weiß Palette
    private static final int C_BG         = 0xF20A0A0A; // C_PANEL_BG
    private static final int C_BG_HOV     = 0xFF181818; // C_PANEL_HEADER
    private static final int C_BORDER     = 0xFF2C2C2C; // C_SEPARATOR
    private static final int C_BORDER_HOV = 0xFF999999; // C_ACCENT_DIM
    private static final int C_ACCENT     = 0xFFFFFFFF; // C_ACCENT
    private static final int C_ACCENT_HOV = 0xFFEEEEEE; // C_TEXT
    private static final int C_LABEL      = 0xFFEEEEEE; // C_TEXT
    private static final int C_LABEL_DIM  = 0xFF666666; // C_TEXT_DIM

    public ModeButton(String label, List<String> modes, int startIndex,
                      Consumer<String> onChange) {
        this.label    = label;
        this.modes    = modes;
        this.index    = startIndex;
        this.onChange = onChange;
        this.width    = 110;
        this.height   = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        float hover = stepHover(mouseX, mouseY);

        drawRoundedRect(   guiGraphics, x, y, width, height,
                lerpColor(C_BG, C_BG_HOV, hover));
        drawRoundedOutline(guiGraphics, x, y, width, height,
                lerpColor(C_BORDER, C_BORDER_HOV, hover * 0.7f));

        guiGraphics.text(Minecraft.getInstance().font, label,
                x + 6, y + (height / 2) - 4,
                lerpColor(C_LABEL_DIM, C_LABEL, hover), false);

        String valText  = "\u25C4 " + modes.get(index) + " \u25BA";
        int    valWidth = Minecraft.getInstance().font.width(valText);
        guiGraphics.text(Minecraft.getInstance().font, valText,
                x + width - valWidth - 5,
                y + (height / 2) - 4,
                lerpColor(C_LABEL_DIM, lerpColor(C_ACCENT, C_ACCENT_HOV, hover), hover),
                false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if      (button == 0) index = (index + 1) % modes.size();
            else if (button == 1) index = (index - 1 + modes.size()) % modes.size();
            onChange.accept(modes.get(index));
            return true;
        }
        return false;
    }

    public String getLabel()  { return label; }
    public int    getIndex()  { return index; }
    public void setIndex(int i) {
        this.index = Math.min(modes.size() - 1, Math.max(0, i));
        onChange.accept(modes.get(this.index));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
}