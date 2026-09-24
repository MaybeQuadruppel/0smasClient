package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Consumer;

public class ModeButton extends Component {

    private final String label;
    private final List<String> modes;
    private int index;
    private final Consumer<String> onChange;

    private float hoverAnim = 0f;
    private float changeFlash = 0f; // kurzes Aufblitzen beim Wechsel

    private static final int BASE_HEIGHT = 14;

    public ModeButton(String label, List<String> modes, int startIndex, Consumer<String> onChange) {
        super(0, 0, 100, BASE_HEIGHT);
        this.label    = label;
        this.modes    = modes;
        this.index    = startIndex;
        this.onChange = onChange;
        syncScaledSize(100, BASE_HEIGHT, 60, BASE_HEIGHT);
    }

    public ModeButton withDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public void render(Object graphics, int mouseX, int mouseY) {
        if (!(graphics instanceof GuiGraphicsExtractor guiGraphics)) return;

        syncHeight(BASE_HEIGHT, UISettings.scaled(BASE_HEIGHT));

        Theme.Palette p = Theme.getActive().palette;
        hoverAnim   = approach(hoverAnim, isHovered(mouseX, mouseY) ? 1f : 0f, 0.25f);
        changeFlash = approach(changeFlash, 0f, 0.12f);

        drawRoundedRect(guiGraphics, x, y, width, height,
                UISettings.withPanelAlpha(lerpColor(p.bg, p.bgHover, hoverAnim * 0.8f)));

        int textOffsetY = UISettings.scaled(4);
        String valText = modes.get(index) + " \u203A";
        int valWidth = UISettings.textWidth(Minecraft.getInstance().font, valText);
        int valStartX = x + width - valWidth - UISettings.scaled(6);

        // Label links
        guiGraphics.enableScissor(x + 1, y + 1, Math.max(x + 1, valStartX - 2), y + height - 1);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label,
                x + UISettings.scaled(6), y + (height / 2) - textOffsetY,
                lerpColor(p.textDim, p.text, hoverAnim), false);
        guiGraphics.disableScissor();

        // Aktueller Modus rechts (Akzent, blitzt beim Wechsel kurz auf)
        guiGraphics.enableScissor(Math.max(x + 1, valStartX - 2), y + 1, x + width - 1, y + height - 1);
        int valColor = lerpColor(lerpColor(p.textDim, p.accent, 0.5f + hoverAnim * 0.5f), p.text, changeFlash);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, valText,
                valStartX, y + (height / 2) - textOffsetY, valColor, false);
        guiGraphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) {
                index = (index + 1) % modes.size();
                Sounds.select();
            } else if (button == 1) {
                index = (index - 1 + modes.size()) % modes.size();
                Sounds.deselect();
            } else {
                return false;
            }
            changeFlash = 1f;
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