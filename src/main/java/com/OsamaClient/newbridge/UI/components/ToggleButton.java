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

    private static final int BASE_HEIGHT = 16;

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

        syncScaledSize(baseWidth, BASE_HEIGHT, 60, BASE_HEIGHT);

        Theme.Palette p = Theme.getActive().palette;

        float hover = stepHover(mouseX, mouseY);
        boolean nowHovered = isHovered(mouseX, mouseY);
        hoverAnim = nowHovered
                ? Math.min(1f, hoverAnim + UISettings.animStep(0.2f))
                : Math.max(0f, hoverAnim - UISettings.animStep(0.2f));

        toggleAnim = enabled
                ? Math.min(1f, toggleAnim + UISettings.animStep(0.15f))
                : Math.max(0f, toggleAnim - UISettings.animStep(0.15f));

        int bg  = lerpColor(p.bg, p.bgHover, hover);
        int bdr = lerpColor(p.border,
                lerpColor(p.borderHover, p.enabled, toggleAnim),
                hover * 0.6f + toggleAnim * 0.4f);

        drawRoundedRect(guiGraphics, x, y, width, height, UISettings.withPanelAlpha(bg));
        drawRoundedOutline(guiGraphics, x, y, width, height, bdr);

        final int TRACK_W = Math.max(16, UISettings.scaled(20));
        final int TRACK_H = Math.max(6, UISettings.scaled(8));
        int trackX = x + width - TRACK_W - UISettings.scaled(6);
        int trackY = y + (height - TRACK_H) / 2;

        guiGraphics.enableScissor(x + 1, y + 1, Math.max(x + 1, trackX - 3), y + height - 1);
        int labelColor = lerpColor(p.textDim, p.text, hover);
        int textOffsetY = UISettings.scaled(4);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label,
                x + UISettings.scaled(6), y + (height / 2) - textOffsetY, labelColor, false);
        guiGraphics.disableScissor();

        int trackColor = lerpColor(p.disabled, p.accent, toggleAnim * 0.7f);
        drawRoundedRect(guiGraphics, trackX, trackY, TRACK_W, TRACK_H, trackColor);

        final int THUMB_W = Math.max(6, TRACK_H);
        int thumbX = trackX + 1 + (int) (toggleAnim * (TRACK_W - THUMB_W - 2));
        int thumbColor = lerpColor(p.textDim, p.enabled, toggleAnim);
        drawRoundedRect(guiGraphics, thumbX, trackY + 1, THUMB_W, TRACK_H - 2, thumbColor);
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