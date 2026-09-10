package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

public abstract class Component {
    public int x;
    public int y;
    public int width;
    public int height;

    protected int baseWidth;
    protected int baseHeight;
    protected String description = "";
    protected float hoverState = 0.0f;

    public Component(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.baseWidth = width;
        this.baseHeight = height;
    }

    public abstract void render(Object graphics, int mouseX, int mouseY);

    /**
     * Synchronisiert die physische Größe mit der aktuellen UI-Skalierung.
     */
    public void syncScaledSize(int baseW, int baseH, int minW, int minH) {
        this.baseWidth = baseW;
        this.baseHeight = baseH;
        this.width = Math.max(minW, UISettings.scaled(baseW));
        this.height = Math.max(minH, UISettings.scaled(baseH));
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public float stepHover(int mouseX, int mouseY) {
        boolean hov = isHovered(mouseX, mouseY);
        hoverState = hov
                ? Math.min(1.0f, hoverState + UISettings.animStep(0.2f))
                : Math.max(0.0f, hoverState - UISettings.animStep(0.2f));
        return hoverState;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @SuppressWarnings("unchecked")
    public <T extends Component> T withDescription(String description) {
        this.description = description;
        return (T) this;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }

    // ========================================================================
    // STATIC UTILITIES (Rendering & Color Math)
    // ========================================================================

    public static void drawShadow(Object g, int x, int y, int w, int h, int color) {
        if (g instanceof GuiGraphicsExtractor extractor) {
            extractor.fill(x - 2, y - 2, x + w + 2, y + h + 2, color);
        }
    }

    public static void drawShadow(Object g, int x, int y, int w, int h) {
        drawShadow(g, x, y, w, h, 0x66000000);
    }

    public static void drawRoundedRect(Object g, int x, int y, int w, int h, int color) {
        if (g instanceof GuiGraphicsExtractor extractor) {
            extractor.fill(x, y, x + w, y + h, color);
        }
    }

    public static void drawRoundedOutline(Object g, int x, int y, int w, int h, int color) {
        if (g instanceof GuiGraphicsExtractor extractor) {
            extractor.fill(x, y, x + w, y + 1, color);           // Oben
            extractor.fill(x, y + h - 1, x + w, y + h, color);   // Unten
            extractor.fill(x, y, x + 1, y + h, color);           // Links
            extractor.fill(x + w - 1, y, x + w, y + h, color);   // Rechts
        }
    }

    public static int lerpColor(int color1, int color2, float factor) {
        factor = Math.max(0.0f, Math.min(1.0f, factor));
        int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.min(255, Math.max(0, (int) (alpha * 255.0f)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }
}