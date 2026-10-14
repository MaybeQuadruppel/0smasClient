package com.OsamaClient.newbridge.UI.gui.window;

import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.setting.TextField;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import com.OsamaClient.newbridge.UI.gui.input.Keys;

/**
 * Basis für eigene Fenster im ClickGUI-Look. Immediate-Mode: Klicks werden gesammelt und beim
 * nächsten Zeichnen von dem Widget verbraucht, unter dem sie liegen ({@link #button} usw. geben
 * {@code true} zurück, wenn sie geklickt wurden).
 */
public abstract class UiWindow {

    /** Farbpalette für Farbauswahl (Xaero-ähnlich). */
    public static final int[] PALETTE = {
            0xFFFF4D4D, 0xFFFF9F43, 0xFFFFD43B, 0xFF4ADE80, 0xFF2DD4BF, 0xFF38BDF8,
            0xFF6C8CFF, 0xFFA78BFA, 0xFFF472B6, 0xFFFFFFFF, 0xFF9CA3AF, 0xFF1F2937};

    protected Ui ui;
    protected float wx, wy, ww, wh;          // Fensterrechteck (Units)
    private int clickButton = -1;
    private int clickMods;
    private float clickX, clickY;
    private double scrollQueued;
    protected TextField focused;
    private String tooltip;

    /** Gewünschte Größe in GUI-Units (wird an den Bildschirm angepasst). */
    protected abstract float prefWidth();
    protected abstract float prefHeight();
    protected abstract String title();

    /** Inhalt zeichnen; Bereich (x, y, w, h) liegt unter der Titelleiste. */
    protected abstract void drawContent(float x, float y, float w, float h);

    public void onOpen() {}
    public void onClose() {
        if (focused != null) focused.end();
        focused = null;
    }

    // ------------------------------------------------------------------ frame

    final void frame(Ui ui, float dt) {
        this.ui = ui;
        tooltip = null;
        ww = Math.min(prefWidth(), ui.width() - 16f);
        wh = Math.min(prefHeight(), ui.height() - 16f);
        wx = (ui.width() - ww) / 2f;
        wy = (ui.height() - wh) / 2f;
        float r = Theme.radius();
        float g = Theme.glow();
        if (g > 0) ui.glow(wx, wy, ww, wh, r, 9f, Theme.accent(0.18f * g));
        ui.round(wx, wy, ww, wh, r, Theme.panelBg());
        float hh = 16f;
        ui.text(title(), wx + 6f, wy, hh, Theme.FONT, Theme.TEXT);
        ui.gradientH(wx + 4f, wy + hh - 1f, ww * 0.5f, 1f / ui.scale, 0, Theme.accent(0.9f), Theme.accent(0.05f));
        if (button("x", wx + ww - 14f, wy + 3f, 10f, 10f, true, false)) {
            consumeClick();
            WindowManager.INSTANCE.close();
            return;
        }
        if (Theme.outline()) ui.outline(wx, wy, ww, wh, r, 1f, Theme.OUTLINE);

        drawContent(wx + 6f, wy + hh + 4f, ww - 12f, wh - hh - 10f);

        // Klick außerhalb aller Widgets: Fokus weg
        if (clickButton != -1 && focused != null) {
            focused.end();
            focused = null;
        }
        clickButton = -1;
        scrollQueued = 0;
        drawTooltip();
    }

    // ------------------------------------------------------------------ input

    void queueClick(int button, int mods) {
        clickButton = button;
        clickMods = mods;
        clickX = ui == null ? 0 : ui.mouseX;
        clickY = ui == null ? 0 : ui.mouseY;
    }

    void queueScroll(double amount) { scrollQueued += amount; }

    void mouseReleased(int button) {}

    /** @return true wenn verarbeitet (ESC schließt sonst das Fenster) */
    boolean keyPressed(int key, int mods) {
        if (focused != null) {
            if (key == Keys.KEY_ESCAPE || key == Keys.KEY_ENTER || key == Keys.KEY_KP_ENTER || key == Keys.KEY_TAB) {
                TextField f = focused;
                focused.end();
                focused = null;
                onFieldCommitted(f, key);
                return true;
            }
            focused.keyPressed(key, mods);
            return true;
        }
        return onKey(key, mods);
    }

    void charTyped(int cp) {
        if (focused != null) focused.charTyped(cp);
    }

    /** Fenster-spezifische Tasten (kein Textfeld fokussiert). */
    protected boolean onKey(int key, int mods) { return false; }

    /** Enter/Tab/ESC in einem Textfeld. */
    protected void onFieldCommitted(TextField field, int key) {}

    // ------------------------------------------------------------------ widgets

    protected boolean clicked(float x, float y, float w, float h, int button) {
        return clickButton == button && clickX >= x && clickX < x + w && clickY >= y && clickY < y + h
                && ui.r.insideClip(clickX * ui.scale, clickY * ui.scale);
    }

    protected void consumeClick() { clickButton = -1; }

    protected boolean shiftClick() { return (clickMods & Keys.MOD_SHIFT) != 0; }

    /** Scrollmenge, wenn die Maus über dem Bereich ist (wird dabei verbraucht). */
    protected double scroll(float x, float y, float w, float h) {
        if (scrollQueued != 0 && ui.hovered(x, y, w, h)) {
            double s = scrollQueued;
            scrollQueued = 0;
            return s;
        }
        return 0;
    }

    protected void tooltip(String text) { tooltip = text; }

    /** Button im ClickGUI-Stil; {@code active} = hervorgehoben (z.B. Toggle an). */
    protected boolean button(String label, float x, float y, float w, float h, boolean enabled, boolean active) {
        boolean hover = enabled && ui.hovered(x, y, w, h);
        int bg = !enabled ? 0x2218181D : active ? Theme.accent(0.30f) : hover ? Theme.ROW_HOVER : Theme.ROW;
        int border = !enabled ? 0x10FFFFFF : active || hover ? Theme.accent(0.85f) : 0x18FFFFFF;
        ui.round(x, y, w, h, 1.5f, bg);
        ui.outline(x, y, w, h, 1.5f, 1f, border);
        ui.textCentered(ui.ellipsize(label, w - 3f, Theme.FONT_SMALL), x + w / 2f, y, h, Theme.FONT_SMALL,
                enabled ? Theme.TEXT : Theme.TEXT_DIM);
        if (enabled && clicked(x, y, w, h, Keys.MOUSE_BUTTON_LEFT)) {
            consumeClick();
            return true;
        }
        return false;
    }

    /** Textfeld; Klick fokussiert es. */
    protected void field(TextField f, float x, float y, float w, float h, String placeholder) {
        if (clicked(x, y, w, h, Keys.MOUSE_BUTTON_LEFT)) {
            consumeClick();
            if (focused != null && focused != f) focused.end();
            if (!f.editing) f.begin(f.text);
            focused = f;
        }
        f.draw(ui, x, y, w, h, Theme.FONT_SMALL, placeholder, false);
    }

    /** Checkbox mit Label; gibt den neuen Wert zurück. */
    protected boolean checkbox(String label, float x, float y, boolean value) {
        float s = 8f;
        boolean hover = ui.hovered(x, y, s + 4f + ui.textWidth(label, Theme.FONT_SMALL), s);
        ui.round(x, y, s, s, 1.5f, value ? Theme.accent(0.85f) : Theme.ROW);
        ui.outline(x, y, s, s, 1.5f, 1f, hover ? Theme.accent() : 0x30FFFFFF);
        if (value) ui.check(x, y, s, 1f, 0xFFFFFFFF);
        ui.text(label, x + s + 3f, y, s, Theme.FONT_SMALL, Theme.TEXT);
        if (clicked(x, y, s + 4f + ui.textWidth(label, Theme.FONT_SMALL), s, Keys.MOUSE_BUTTON_LEFT)) {
            consumeClick();
            return !value;
        }
        return value;
    }

    /** Farbfelder der Palette; gibt die (evtl. neue) Farbe zurück. */
    protected int palette(float x, float y, float size, int current) {
        int out = current;
        for (int i = 0; i < PALETTE.length; i++) {
            float px = x + i * (size + 2f);
            int c = PALETTE[i];
            boolean sel = (c & 0xFFFFFF) == (current & 0xFFFFFF);
            ui.rect(px - 0.5f, y - 0.5f, size + 1f, size + 1f, sel ? 0xFFFFFFFF : 0x80000000);
            ui.rect(px, y, size, size, c);
            if (clicked(px, y, size, size, Keys.MOUSE_BUTTON_LEFT)) {
                consumeClick();
                out = c;
            }
        }
        return out;
    }

    /** Kleines Xaero-artiges Waypoint-Icon. */
    protected void icon(float x, float y, float s, int color, String letter) {
        ui.rect(x - 0.6f, y - 0.6f, s + 1.2f, s + 1.2f, 0xC0000000);
        ui.rect(x, y, s, s, color | 0xFF000000);
        float lum = (((color >> 16) & 0xFF) * 0.299f + ((color >> 8) & 0xFF) * 0.587f + (color & 0xFF) * 0.114f) / 255f;
        ui.textCentered(letter, x + s / 2f, y, s, Theme.FONT_SMALL, lum > 0.6f ? 0xFF111111 : 0xFFFFFFFF);
    }

    protected void label(String text, float x, float y, float h, int color) {
        ui.text(text, x, y, h, Theme.FONT_SMALL, color);
    }

    protected void separator(float x, float y, float w) {
        ui.rect(x, y, w, 1f / ui.scale, 0x18FFFFFF);
    }

    private void drawTooltip() {
        if (tooltip == null || tooltip.isEmpty()) return;
        float size = Theme.FONT_SMALL;
        float w = ui.textWidth(tooltip, size) + 2 * Theme.PAD, h = 9f;
        float x = ui.mouseX + 7f, y = ui.mouseY + 7f;
        if (x + w > ui.width() - 2) x = ui.mouseX - w - 3f;
        if (y + h > ui.height() - 2) y = ui.mouseY - h - 3f;
        ui.round(x, y, w, h, 2f, 0xF2101014);
        ui.outline(x, y, w, h, 2f, 1f, Theme.accent(0.35f));
        ui.text(tooltip, x + Theme.PAD, y, h, size, Theme.TEXT);
    }

    /** Scrollbarer Listenbereich: verwaltet Offset, Clip und Scrollbalken. */
    public final class ScrollList {
        public ScrollList() {}
        public float offset, target;

        /** Vor dem Zeichnen der Einträge; gibt den Startwert für y zurück. */
        public float begin(float x, float y, float w, float h, float contentH) {
            float max = Math.max(0f, contentH - h);
            double s = scroll(x, y, w, h);
            if (s != 0) target -= (float) s * 18f;
            target = Math.max(0f, Math.min(max, target));
            offset += (target - offset) * Math.min(1f, ui.dt * 18f);
            if (Math.abs(target - offset) < 0.05f) offset = target;
            ui.pushClip(x, y, w, h);
            if (max > 0) {
                float barH = Math.max(10f, h * h / contentH);
                float barY = y + (h - barH) * (offset / max);
                ui.round(x + w - 1.5f, barY, 1f, barH, 0.5f, Theme.accent(0.6f));
            }
            return y - offset;
        }

        public void end() { ui.popClip(); }
    }

    protected static int lighten(int c, float t) { return ColorUtil.lerp(c, 0xFFFFFFFF, t); }
}
