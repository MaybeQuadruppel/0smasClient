package com.qdrppl.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Consumer;

public class ToggleButton extends Component {

    public boolean enabled;
    private final String label;
    private final Consumer<Boolean> callback;

    /** Smooth 0 (off) → 1 (on) animation for the toggle thumb. */
    private float toggleAnim;

    // Palette (Schwarz-Weiß Theme)
    private static final int C_BG         = 0xF20A0A0A; // C_PANEL_BG
    private static final int C_BG_HOV     = 0xFF181818; // C_PANEL_HEADER
    private static final int C_BORDER     = 0xFF2C2C2C; // C_SEPARATOR
    private static final int C_BORDER_HOV = 0xFF999999; // C_ACCENT_DIM
    private static final int C_TRACK_OFF  = 0xFF3A3A3A; // C_DISABLED
    private static final int C_TRACK_ON   = 0xFF666666; // C_TEXT_DIM (etwas heller für "An")
    private static final int C_THUMB_OFF  = 0xFF666666; // C_TEXT_DIM
    private static final int C_THUMB_ON   = 0xFFFFFFFF; // C_ENABLED (Weiß)
    private static final int C_LABEL      = 0xFFEEEEEE; // C_TEXT
    private static final int C_LABEL_DIM  = 0xFF666666; // C_TEXT_DIM

    public ToggleButton(String label, boolean startValue, Consumer<Boolean> callback) {
        this.label      = label;
        this.enabled    = startValue;
        this.callback   = callback;
        this.toggleAnim = startValue ? 1f : 0f;
        this.width      = 110;
        this.height     = 16;
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        float hover = stepHover(mouseX, mouseY);

        toggleAnim = enabled
                ? Math.min(1f, toggleAnim + 0.15f)
                : Math.max(0f, toggleAnim - 0.15f);

        // Background & Border Lerp
        int bg  = lerpColor(C_BG, C_BG_HOV, hover);
        int bdr = lerpColor(C_BORDER,
                lerpColor(C_BORDER_HOV, C_THUMB_ON, toggleAnim),
                hover * 0.6f + toggleAnim * 0.4f);

        drawRoundedRect(   guiGraphics, x, y, width, height, bg);
        drawRoundedOutline(guiGraphics, x, y, width, height, bdr);

        // Label
        int labelColor = lerpColor(C_LABEL_DIM, C_LABEL, hover);
        guiGraphics.text(Minecraft.getInstance().font, label,
                x + 6, y + (height / 2) - 4, labelColor, false);

        // Switch Track
        final int TRACK_W = 22, TRACK_H = 8;
        int trackX = x + width - TRACK_W - 5;
        int trackY = y + (height - TRACK_H) / 2;

        int trackColor = lerpColor(C_TRACK_OFF, C_TRACK_ON, toggleAnim);
        drawRoundedRect(guiGraphics, trackX, trackY, TRACK_W, TRACK_H, trackColor);

        // Thumb (Der bewegliche Teil)
        final int THUMB_W = 8;
        int thumbX = trackX + 1 + (int)(toggleAnim * (TRACK_W - THUMB_W - 2));
        int thumbColor = lerpColor(C_THUMB_OFF, C_THUMB_ON, toggleAnim);
        drawRoundedRect(guiGraphics, thumbX, trackY + 1, THUMB_W, TRACK_H - 2, thumbColor);
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

    public String getLabel()         { return label; }
    public void setValue(boolean v)  { this.enabled = v; callback.accept(v); }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
}