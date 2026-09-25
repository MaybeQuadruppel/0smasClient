package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.setting.TextField;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Ctrl+F module search, floating at the bottom center (the top is taken by the panel headers).
 * Filters module rows across all panels by name, case-insensitive.
 */
final class SearchBar {

    private static final float W = 120f, H = 11f;

    private final TextField field = new TextField();
    private final Anim showA = new Anim(0f, 16f);
    private boolean shown;
    private float x, y;
    private boolean hovered;

    SearchBar() {
        field.maxLength = 32;
    }

    /** Current filter, lower case ("" = no filter). */
    String query() { return shown ? field.text.toLowerCase(Locale.ROOT).trim() : ""; }

    boolean editing() { return shown && field.editing; }

    void open() {
        shown = true;
        showA.setTarget(1f);
        if (!field.editing) field.begin(field.text);
    }

    void close() {
        shown = false;
        field.end();
        field.text = "";
        field.cursor = 0;
        showA.setTarget(0f);
    }

    void unfocus() { field.end(); }

    void draw(Ui ui) {
        showA.update(ui.dt, Theme.animSpeed());
        float a = showA.value();
        if (a < 0.01f) {
            hovered = false;
            return;
        }
        x = (ui.width() - W) * 0.5f;
        y = ui.height() - H - 26f + (1f - a) * 8f;
        hovered = shown && ui.hovered(x, y, W, H);
        float old = ui.alpha;
        ui.alpha = old * a;
        ui.glow(x, y, W, H, 2.5f, 8f, Theme.accent(0.22f * Theme.glow()));
        ui.round(x, y, W, H, 2.5f, Theme.PANEL_BG);
        // magnifier icon
        float ix = x + 6f, iy = y + H * 0.5f - 0.5f;
        ui.outline(ix - 2.2f, iy - 2.2f, 4.4f, 4.4f, 2.2f, Math.max(1f, ui.scale * 0.7f), Theme.TEXT_DIM);
        ui.line(ix + 1.6f, iy + 1.6f, ix + 3.2f, iy + 3.2f, Math.max(1f, ui.scale * 0.7f), Theme.TEXT_DIM);
        field.draw(ui, x + 11f, y + 1.5f, W - 13f, H - 3f, Theme.FONT_SMALL, "Search modules…", false);
        ui.alpha = old;
    }

    /** @return true if the click hit the bar */
    boolean mouseClicked(int button) {
        if (!hovered) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) close();
        else if (!field.editing) field.begin(field.text);
        return true;
    }

    /** Keys while editing. @return true if consumed */
    boolean keyPressed(int key, int mods) {
        if (!editing()) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            field.end();
            if (field.text.isEmpty()) close();
            return true;
        }
        field.keyPressed(key, mods);
        return true;
    }

    boolean charTyped(int cp) { return editing() && field.charTyped(cp); }
}
