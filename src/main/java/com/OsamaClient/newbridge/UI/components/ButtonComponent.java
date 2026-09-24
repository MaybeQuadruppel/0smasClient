package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Schlichter, animierter Aktions-Button (klicken löst {@code onClick} aus).
 * Zwei Stile: dezent (Standard) oder gefüllt/Akzent ({@link #accent()}).
 * Nutzt dieselben Render-Helfer wie die übrigen Komponenten, damit der Look
 * in beiden GUIs identisch ist.
 */
public class ButtonComponent extends Component {

    private final String label;
    private final Runnable onClick;
    private boolean accent = false;
    private boolean centered = true;

    private float hoverAnim = 0f;
    private float pressAnim = 0f;

    private static final int BASE_HEIGHT = 14;

    public ButtonComponent(String label, Runnable onClick) {
        super(0, 0, 100, BASE_HEIGHT);
        this.label = label;
        this.onClick = onClick;
    }

    public ButtonComponent accent()   { this.accent = true; return this; }
    public ButtonComponent leftAlign() { this.centered = false; return this; }
    public ButtonComponent withDescription(String d) { this.description = d; return this; }

    public String getLabel() { return label; }

    @Override
    public void render(Object graphics, int mouseX, int mouseY) {
        if (!(graphics instanceof GuiGraphicsExtractor g)) return;

        syncHeight(BASE_HEIGHT, UISettings.scaled(BASE_HEIGHT));
        Theme.Palette p = Theme.getActive().palette;

        hoverAnim = approach(hoverAnim, isHovered(mouseX, mouseY) ? 1f : 0f, 0.25f);
        pressAnim = approach(pressAnim, 0f, 0.14f);

        int bg;
        if (accent) {
            bg = lerpColor(withAlpha(p.accent, 0.75f), p.accent, hoverAnim);
        } else {
            bg = UISettings.withPanelAlpha(lerpColor(p.bg, p.bgHover, 0.4f + hoverAnim * 0.6f));
        }
        // leichter Press-"Dip"
        bg = lerpColor(bg, 0xFF000000, pressAnim * 0.12f);

        drawRoundedRect(g, x, y, width, height, bg);
        drawRoundedOutline(g, x, y, width, height,
                accent ? withAlpha(p.accent, 0.9f) : lerpColor(p.border, p.accent, hoverAnim * 0.6f));

        int textColor = accent ? p.text : lerpColor(p.textDim, p.text, hoverAnim);
        int ty = y + (height / 2) - UISettings.scaled(4);
        if (centered) {
            int tw = UISettings.textWidth(Minecraft.getInstance().font, label);
            UISettings.drawText(g, Minecraft.getInstance().font, label,
                    x + (width - tw) / 2, ty, textColor, false);
        } else {
            UISettings.drawText(g, Minecraft.getInstance().font, label,
                    x + UISettings.scaled(6), ty, textColor, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY)) {
            pressAnim = 1f;
            Sounds.select();
            if (onClick != null) onClick.run();
            return true;
        }
        return false;
    }
}
