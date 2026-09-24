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

    private static final int BASE_HEIGHT = 14;

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

        hoverAnim  = approach(hoverAnim, isHovered(mouseX, mouseY) ? 1f : 0f, 0.25f);
        toggleAnim = approach(toggleAnim, enabled ? 1f : 0f, 0.22f);

        drawRoundedRect(guiGraphics, x, y, width, height,
                UISettings.withPanelAlpha(lerpColor(p.bg, p.bgHover, hoverAnim * 0.8f)));

        final int TRACK_W = Math.max(14, UISettings.scaled(18));
        final int TRACK_H = Math.max(6, UISettings.scaled(8));
        int trackX = x + width - TRACK_W - UISettings.scaled(6);
        int trackY = y + (height - TRACK_H) / 2;

        guiGraphics.enableScissor(x + 1, y + 1, Math.max(x + 1, trackX - 3), y + height - 1);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label,
                x + UISettings.scaled(6), y + (height / 2) - UISettings.scaled(4),
                lerpColor(p.textDim, p.text, Math.max(hoverAnim, toggleAnim * 0.8f)), false);
        guiGraphics.disableScissor();

        // Pillen-Track (voll rund), Farbe interpoliert aus/ein
        int trackColor = lerpColor(UISettings.withPanelAlpha(p.disabled), p.accent, toggleAnim);
        roundRect(guiGraphics, trackX, trackY, TRACK_W, TRACK_H, TRACK_H / 2, trackColor);

        // Runder Knopf, gleitet weich
        int thumbR = TRACK_H / 2 - 1;
        int travel = TRACK_W - (thumbR * 2) - 2;
        int cx = trackX + 1 + thumbR + Math.round(toggleAnim * travel);
        int cy = trackY + TRACK_H / 2;
        roundRect(guiGraphics, cx - thumbR, cy - thumbR, thumbR * 2, thumbR * 2, thumbR, p.text);
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