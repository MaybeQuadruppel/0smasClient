package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class
Component {
    public int x, y, width, height;

    /** Unskalierte Basisgröße, aus der width/height per {@link #syncScaledSize}
     *  neu berechnet werden. Ohne das würde eine Änderung von
     *  {@link UISettings#scale} zur Laufzeit bereits erzeugte Komponenten nicht
     *  mehr erreichen, da width/height sonst nur einmalig im Konstruktor
     *  berechnet würden. */
    protected int baseWidth, baseHeight;

    /** Internal hover animation state (0 = not hovered, 1 = fully hovered). */
    protected float hoverAnim = 0f;

    /** Optional tooltip text shown when hovering this component. */
    protected String description = null;

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

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** Fluent helper, e.g. {@code new Slider(...).withDescription("...")} */
    @SuppressWarnings("unchecked")
    public <T extends Component> T withDescription(String description) {
        this.description = description;
        return (T) this;
    }

    /**
     * Setzt {@link #baseWidth}/{@link #baseHeight} und berechnet width/height
     * anhand des AKTUELLEN {@link UISettings#scale} neu. Muss im Konstruktor
     * jeder Komponente mit fester Basisgröße aufgerufen werden UND erneut als
     * erste Zeile in {@code render(...)} - sonst übernimmt eine bereits
     * bestehende Komponente eine spätere Änderung des Scale-Reglers nie
     * (width/height blieben sonst für immer auf dem Wert beim Erzeugen
     * eingefroren, was zu falscher Hitbox-Größe und überlaufendem/
     * abgeschnittenem Text führt).
     */
    protected void syncScaledSize(int baseWidth, int baseHeight) {
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.width = UISettings.scaled(baseWidth);
        this.height = UISettings.scaled(baseHeight);
    }

    /**
     * Wie {@link #syncScaledSize(int, int)}, garantiert aber zusätzlich eine
     * Mindestgröße in echten Pixeln, unabhängig von {@link UISettings#scale}.
     * Ohne diese Grenze konnte eine Box bei sehr kleiner Scale so weit
     * schrumpfen, dass Label-Text und interne Elemente (Track, Toggle, ...)
     * sich gegenseitig überlappen bzw. der Text "hinter" ihnen verschwindet -
     * genau der gemeldete Clipping-Bug. Jede Komponente kennt ihre eigene
     * sinnvolle Mindestgröße (genug Platz für eine Zeile Text + ihre Regler).
     */
    protected void syncScaledSize(int baseWidth, int baseHeight, int minWidth, int minHeight) {
        syncScaledSize(baseWidth, baseHeight);
        this.width = Math.max(minWidth, this.width);
        this.height = Math.max(minHeight, this.height);
    }


    /** Filled rounded rectangle. Fällt auf ein scharfkantiges Rechteck zurück,
     *  wenn {@link UISettings#roundedCorners} deaktiviert ist. */
    public static void drawRoundedRect(GuiGraphicsExtractor g,
                                       int x, int y, int w, int h, int color) {
        if (!UISettings.roundedCorners || w < 4 || h < 4) { g.fill(x, y, x + w, y + h, color); return; }
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
        if (!UISettings.roundedCorners || w < 4 || h < 4) { drawOutline(g, x, y, w, h, color); return; }
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