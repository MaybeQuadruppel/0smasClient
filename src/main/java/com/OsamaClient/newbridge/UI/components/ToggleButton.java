package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Consumer;

public class ToggleButton extends Component {

    public boolean enabled;
    private final String label;
    private final Consumer<Boolean> callback;

    private float toggleAnim;
    private float hoverAnim;

    private static final int BASE_HEIGHT = 11;

    public ToggleButton(String label, boolean startValue, Consumer<Boolean> callback) {
        super(0, 0, 100, BASE_HEIGHT);
        this.label      = label;
        this.enabled    = startValue;
        this.callback   = callback;
        this.toggleAnim = startValue ? 1f : 0f;
        syncScaledSize(100, BASE_HEIGHT, 60, BASE_HEIGHT);
    }

    public ToggleButton withDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public void render(Object graphics, int mouseX, int mouseY) {
        if (!(graphics instanceof GuiGraphicsExtractor guiGraphics)) return;

        syncHeight(BASE_HEIGHT, UISettings.scaled(BASE_HEIGHT));

        Theme.Palette p = Theme.getActive().palette;
        hoverAnim  = approach(hoverAnim, isHovered(mouseX, mouseY) ? 1f : 0f, 0.3f);
        toggleAnim = approach(toggleAnim, enabled ? 1f : 0f, 0.3f);

        if (hoverAnim > 0.01f) {
            guiGraphics.fill(x, y, x + width, y + height,
                    withAlpha(p.bgHover, hoverAnim * 0.5f));
        }

        // Kleine Checkbox links
        int box = Math.max(5, UISettings.scaled(7));
        int bx = x + UISettings.scaled(3);
        int by = y + (height - box) / 2;
        int frame = lerpColor(p.border, p.accent, toggleAnim);
        drawRoundedOutline(guiGraphics, bx, by, box, box, frame);
        if (toggleAnim > 0.01f) {
            int inset = Math.round((1f - toggleAnim) * ((box - 2) / 2f));
            guiGraphics.fill(bx + 1 + inset, by + 1 + inset,
                    bx + box - 1 - inset, by + box - 1 - inset, p.accent);
        }

        // Label rechts daneben (geclippt, damit nichts überlappt)
        int textX = bx + box + UISettings.scaled(4);
        guiGraphics.enableScissor(textX, y, x + width - UISettings.scaled(2), y + height);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label,
                textX, y + (height - UISettings.scaled(7)) / 2,
                lerpColor(p.textDim, p.text, Math.max(hoverAnim, toggleAnim)), false);
        guiGraphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            enabled = !enabled;
            callback.accept(enabled);
            if (enabled) Sounds.toggleOn(); else Sounds.toggleOff();
            return true;
        }
        return false;
    }

    public String getLabel()         { return label; }
    public void setValue(boolean v)  { this.enabled = v; callback.accept(v); }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
}