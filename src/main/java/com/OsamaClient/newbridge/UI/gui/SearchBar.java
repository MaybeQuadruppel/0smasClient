package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.setting.TextField;
import com.OsamaClient.newbridge.UI.gui.input.Keys;

import java.util.Locale;

/**
 * Always visible search bar at the bottom center. Click it or press Ctrl+F.
 * Filters module rows across all panels by name (case-insensitive); next to it sit the "Enabled" filter chip
 * and the collapse / expand all button, and the bar shows the number of matches.
 */
final class SearchBar {

    private static final float W = 120f, H = 11f, CHIP_GAP = 2f;

    private final TextField field = new TextField();
    private final Anim focusA = new Anim(0f, 16f);
    private float x, y;
    private boolean hovered;
    // hit rects of the chips / clear button: x, y, w, h
    /** Tag suggestions above the bar while typing a #word: hit rects and the tag of each. */
    private final float[][] tagRects = new float[10][4];
    private final String[] tagNames = new String[10];
    private int tagCount;
    private final float[] enabledRect = new float[4], favRect = new float[4], recentRect = new float[4], collapseRect = new float[4], clearRect = new float[4];

    SearchBar() {
        field.maxLength = 48;
    }

    /** Current filter, lower case ("" = no filter). */
    String query() { return field.text.toLowerCase(Locale.ROOT).trim(); }

    boolean editing() { return field.editing; }

    void setText(String t) {
        field.text = t == null ? "" : t;
        field.cursor = field.text.length();
    }

    void open() {
        if (!field.editing) field.begin(field.text);
    }

    void close() {
        field.end();
        field.text = "";
        field.cursor = 0;
    }

    void unfocus() { field.end(); }

    private static boolean in(float[] r, float mx, float my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private static void set(float[] r, float x, float y, float w, float h) { r[0] = x; r[1] = y; r[2] = w; r[3] = h; }

    private float chip(Ui ui, String label, float cx, boolean active, float[] rect) {
        float tw = ui.textWidth(label, Theme.FONT_SMALL), w = tw + 8f;
        set(rect, cx, y, w, H);
        boolean hv = ui.hovered(cx, y, w, H);
        ui.glow(cx, y, w, H, 2.5f, 6f, Theme.accent((active ? 0.28f : 0.08f) * Theme.glow()));
        ui.round(cx, y, w, H, 2.5f, active ? Theme.accent(0.35f) : (hv ? Theme.ROW_HOVER : Theme.PANEL_BG));
        ui.outline(cx, y, w, H, 2.5f, 1f, active ? Theme.accent(0.7f) : Theme.OUTLINE);
        ui.textCentered(label, cx + w / 2f, y, H, Theme.FONT_SMALL, active || hv ? Theme.TEXT : Theme.TEXT_DIM);
        return w;
    }

    void draw(Ui ui, int matches) {
        focusA.setTarget(field.editing ? 1f : 0f);
        focusA.update(ui.dt, Theme.animSpeed());
        float f = focusA.value();
        ClickGui gui = ClickGui.INSTANCE;
        String collapseLabel = gui.allCollapsed() ? "Expand all" : "Collapse all";
        float enW = ui.textWidth("Enabled", Theme.FONT_SMALL) + 8f;
        float coW = ui.textWidth(collapseLabel, Theme.FONT_SMALL) + 8f;
        float favW = ui.textWidth("Favorites", Theme.FONT_SMALL) + 8f;
        float reW = ui.textWidth("Recent", Theme.FONT_SMALL) + 8f;
        float total = W + CHIP_GAP + enW + CHIP_GAP + favW + CHIP_GAP + reW + CHIP_GAP + coW;
        x = (ui.width() - total) * 0.5f;
        y = ui.height() - H - 26f;
        hovered = ui.hovered(x, y, W, H);

        ui.glow(x, y, W, H, 2.5f, 8f, Theme.accent((0.10f + 0.16f * f) * Theme.glow()));
        ui.round(x, y, W, H, 2.5f, Theme.PANEL_BG);
        ui.outline(x, y, W, H, 2.5f, 1f, f > 0.01f ? Theme.accent(0.35f + 0.5f * f) : Theme.OUTLINE);
        // magnifier icon
        float ix = x + 6f, iy = y + H * 0.5f - 0.5f;
        int iconCol = f > 0.5f ? Theme.accent() : Theme.TEXT_DIM;
        ui.outline(ix - 2.2f, iy - 2.2f, 4.4f, 4.4f, 2.2f, Math.max(1f, ui.scale * 0.7f), iconCol);
        ui.line(ix + 1.6f, iy + 1.6f, ix + 3.2f, iy + 3.2f, Math.max(1f, ui.scale * 0.7f), iconCol);
        boolean has = !field.text.isEmpty();
        float right = x + W - 3f;
        set(clearRect, 0, 0, 0, 0);
        if (has) {
            // clear button
            float cs = 6f, cx = right - cs, cy = y + (H - cs) * 0.5f;
            set(clearRect, cx, cy, cs, cs);
            boolean hv = ui.hovered(cx, cy, cs, cs);
            ui.round(cx, cy, cs, cs, cs / 2f, hv ? Theme.accent(0.5f) : Theme.TRACK);
            float m = cs / 2f, d = 1.4f;
            ui.line(cx + m - d, cy + m - d, cx + m + d, cy + m + d, Math.max(1f, ui.scale * 0.6f), Theme.TEXT);
            ui.line(cx + m + d, cy + m - d, cx + m - d, cy + m + d, Math.max(1f, ui.scale * 0.6f), Theme.TEXT);
            right = cx - 2f;
            String cnt = matches + (matches == 1 ? " match" : " matches");
            float cw = ui.textWidth(cnt, 5.5f);
            ui.textRight(cnt, right, y, H, 5.5f, matches == 0 ? 0xFFFF7A7A : Theme.TEXT_DIM);
            right -= cw + 2f;
        }
        field.draw(ui, x + 11f, y + 1.5f, Math.max(10f, right - (x + 11f)), H - 3f, Theme.FONT_SMALL, "Search modules…  (try #sword)", false);

        float cx = x + W + CHIP_GAP;
        cx += chip(ui, "Enabled", cx, ClickGui.onlyEnabled, enabledRect) + CHIP_GAP;
        cx += chip(ui, "Favorites", cx, ClickGui.onlyFavorites, favRect) + CHIP_GAP;
        cx += chip(ui, "Recent", cx, ClickGui.onlyRecent, recentRect) + CHIP_GAP;
        chip(ui, collapseLabel, cx, false, collapseRect);
        if (ui.hovered(enabledRect[0], enabledRect[1], enabledRect[2], enabledRect[3]))
            gui.hint("Show only enabled modules");
        else if (ui.hovered(favRect[0], favRect[1], favRect[2], favRect[3]))
            gui.hint("Show only favorite modules (Ctrl+click a module to favorite it)");
        else if (ui.hovered(recentRect[0], recentRect[1], recentRect[2], recentRect[3]))
            gui.hint("Show only the modules you toggled recently");
        else if (ui.hovered(collapseRect[0], collapseRect[1], collapseRect[2], collapseRect[3]))
            gui.hint("Collapse or expand all panels");
        else if (hovered) gui.hint("Search by name, or by #tag: #sword #crystalpvp #ohc #undetectable #mobs ...");
        drawTagStrip(ui);
    }

    private void drawTagStrip(Ui ui) {
        tagCount = 0;
        if (!field.editing && !field.text.contains("#")) return;
        java.util.List<String> sug = com.OsamaClient.newbridge.UI.components.Tags.suggestions(field.text);
        if (sug.isEmpty()) return;
        float cx = x, cy = y - H - 3f;
        for (String t : sug) {
            if (tagCount >= tagRects.length) break;
            String label = "#" + t;
            float w = ui.textWidth(label, Theme.FONT_SMALL) + 8f;
            if (cx + w > x + W + 150f) break;
            boolean hv = ui.hovered(cx, cy, w, H);
            int col = t.equals("undetectable") ? 0xFF6EDC8C : t.equals("risky") ? 0xFFFF7A7A : Theme.accent();
            ui.round(cx, cy, w, H, 2.5f, hv ? Theme.ROW_HOVER : Theme.PANEL_BG);
            ui.outline(cx, cy, w, H, 2.5f, 1f, hv ? col : Theme.OUTLINE);
            ui.textCentered(label, cx + w / 2f, cy, H, Theme.FONT_SMALL, col);
            set(tagRects[tagCount], cx, cy, w, H);
            tagNames[tagCount++] = t;
            if (hv) ClickGui.INSTANCE.hint("#" + t + " (" + com.OsamaClient.newbridge.UI.components.Tags.count(t) + " modules): " + com.OsamaClient.newbridge.UI.components.Tags.INFO.get(t));
            cx += w + CHIP_GAP;
        }
    }

    /** Test helper: tag chips currently drawn. */
    int tagChips() { return tagCount; }
    String tagChip(int i) { return tagNames[i]; }

    /** @return true if the click hit the bar or one of its chips */
    boolean mouseClicked(int button) {
        ClickGui gui = ClickGui.INSTANCE;
        float mx = gui.mouseUnitsX(), my = gui.mouseUnitsY();
        if (button == Keys.MOUSE_BUTTON_LEFT) {
            for (int i = 0; i < tagCount; i++) {
                if (in(tagRects[i], mx, my)) {
                    setText(com.OsamaClient.newbridge.UI.components.Tags.complete(field.text, tagNames[i]));
                    if (!field.editing) field.begin(field.text);
                    return true;
                }
            }
            if (in(enabledRect, mx, my)) { gui.toggleOnlyEnabled(); return true; }
            if (in(favRect, mx, my)) { gui.toggleOnlyFavorites(); return true; }
            if (in(recentRect, mx, my)) { gui.toggleOnlyRecent(); return true; }
            if (in(collapseRect, mx, my)) { gui.toggleCollapseAll(); return true; }
            if (!field.text.isEmpty() && in(clearRect, mx, my)) { close(); return true; }
        }
        if (!hovered) return false;
        if (button == Keys.MOUSE_BUTTON_RIGHT) close();
        else if (!field.editing) field.begin(field.text);
        return true;
    }

    /** Keys while editing. @return true if consumed */
    boolean keyPressed(int key, int mods) {
        if (!editing()) return false;
        if (key == Keys.KEY_ESCAPE) {
            if (field.text.isEmpty()) field.end();
            else close();
            return true;
        }
        if (key == Keys.KEY_ENTER || key == Keys.KEY_KP_ENTER) {
            field.end();
            return true;
        }
        field.keyPressed(key, mods);
        return true;
    }

    boolean charTyped(int cp) { return editing() && field.charTyped(cp); }
}
