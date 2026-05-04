package com.qdrppl.newbridge.UI.components;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class Component {
    public int x, y, width, height;

    /** Internal hover animation state (0 = not hovered, 1 = fully hovered). */
    protected float hoverAnim = 0f;

    public Component() {}

    public abstract void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY);
    public abstract boolean mouseClicked(double mouseX, double mouseY, int button);
    public abstract boolean mouseReleased(double mouseX, double mouseY, int button);

    /**
     * Called every frame while a mouse button is held and the cursor moves.
     * @param mouseX   current cursor X
     * @param mouseY   current cursor Y
     * @param button   held mouse button (0=left, 1=right, 2=middle)
     * @param dragX    delta X since last frame
     * @param dragY    delta Y since last frame
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) { return false; }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) { return false; }
    public boolean handleKeyboard(net.minecraft.client.input.KeyEvent event)  { return false; }
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) { return false; }
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event)      { return false; }


    /** Filled rounded rectangle. */
    public static void drawRoundedRect(GuiGraphicsExtractor g,
                                       int x, int y, int w, int h, int color) {
        if (w < 4 || h < 4) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x + 2,     y,         x + w - 2, y + h,     color); // centre column
        g.fill(x,         y + 2,     x + 2,     y + h - 2, color); // left strip
        g.fill(x + w - 2, y + 2,     x + w,     y + h - 2, color); // right strip
        // single-pixel diagonal corners
        g.fill(x + 1,     y + 1,     x + 2,     y + 2,     color); // TL
        g.fill(x + w - 2, y + 1,     x + w - 1, y + 2,     color); // TR
        g.fill(x + 1,     y + h - 2, x + 2,     y + h - 1, color); // BL
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color); // BR
    }

    public static void drawRoundedOutline(GuiGraphicsExtractor g,
                                          int x, int y, int w, int h, int color) {
        if (w < 4 || h < 4) { drawOutline(g, x, y, w, h, color); return; }
        g.fill(x + 2,     y,         x + w - 2, y + 1,     color); // top
        g.fill(x + 2,     y + h - 1, x + w - 2, y + h,     color); // bottom
        g.fill(x,         y + 2,     x + 1,     y + h - 2, color); // left
        g.fill(x + w - 1, y + 2,     x + w,     y + h - 2, color); // right
        g.fill(x + 1,     y + 1,     x + 2,     y + 2,     color); // TL
        g.fill(x + w - 2, y + 1,     x + w - 1, y + 2,     color); // TR
        g.fill(x + 1,     y + h - 2, x + 2,     y + h - 1, color); // BL
        g.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color); // BR
    }

    public static void drawShadow(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 3, y + h,     x + w + 3, y + h + 4, 0x35000000); // bottom
        g.fill(x + w, y + 3,     x + w + 3, y + h,     0x35000000); // right
        g.fill(x + 4, y + h + 4, x + w + 3, y + h + 5, 0x15000000); // bottom fade
    }




    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int fA = (from >> 24) & 0xFF, tA = (to >> 24) & 0xFF;
        int fR = (from >> 16) & 0xFF, tR = (to >> 16) & 0xFF;
        int fG = (from >>  8) & 0xFF, tG = (to >>  8) & 0xFF;
        int fB =  from        & 0xFF, tB =  to        & 0xFF;
        return ((int)(fA + (tA - fA) * t) << 24)
                | ((int)(fR + (tR - fR) * t) << 16)
                | ((int)(fG + (tG - fG) * t) <<  8)
                |  (int)(fB + (tB - fB) * t);
    }


    public static int withAlpha(int color, float t) {
        int a = (int)(((color >> 24) & 0xFF) * Math.max(0f, Math.min(1f, t)));
        return (color & 0x00FFFFFF) | (a << 24);
    }


    protected float stepHover(int mouseX, int mouseY) {
        boolean hovered = isHovered(mouseX, mouseY);
        hoverAnim = hovered
                ? Math.min(1f, hoverAnim + 0.15f)
                : Math.max(0f, hoverAnim - 0.15f);
        return hoverAnim;
    }


    public static void drawOutline(GuiGraphicsExtractor guiGraphics,
                                   int x, int y, int width, int height, int color) {
        guiGraphics.fill(x,             y,              x + width,     y + 1,          color);
        guiGraphics.fill(x,             y + height - 1, x + width,     y + height,     color);
        guiGraphics.fill(x,             y,              x + 1,         y + height,     color);
        guiGraphics.fill(x + width - 1, y,              x + width,     y + height,     color);
    }

    protected boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}