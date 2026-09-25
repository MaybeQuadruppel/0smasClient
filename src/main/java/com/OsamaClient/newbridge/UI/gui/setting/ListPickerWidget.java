package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.Component;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registry list picker (blocks / items / enchantments): "Label  3 selected"; expands inline to a search field
 * and a small scrollable list. Click toggles an entry; selected entries sort first and use the accent color.
 * RMB on a selected entry opens its per-entry editor (item color, enchantment price) below the list.
 */
public class ListPickerWidget extends SettingWidget {

    public record Entry(Object key, String id, String name) {}

    public interface Source {
        String label();
        List<Entry> entries();
        boolean selected(Object key);
        void toggle(Object key);
        int selectedCount();
        /** Color dot shown next to a selected entry, or 0 for none. */
        default int dot(Object key) { return 0; }
        /** Editor for a selected entry (RMB), or null. */
        default SettingWidget editor(Entry e) { return null; }
    }

    private static final int VISIBLE = 6;
    private static final float ITEM_H = 8f, SEARCH_H = 8f;

    private final Source source;
    private final Anim openA = new Anim(0f, 14f), scrollA = new Anim(0f, 18f);
    private boolean open;
    private final TextField search = new TextField();
    private String lastQuery;
    private final List<Entry> filtered = new ArrayList<>();
    private float scrollTarget;
    private int hoveredIndex = -1;
    private boolean headerHovered, searchHovered, listHovered;
    private SettingWidget editor;
    private Entry editorEntry;

    public ListPickerWidget(Component setting, Source source) {
        super(setting);
        this.source = source;
        search.maxLength = 32;
    }

    private float bodyHeight() {
        float h = 2f + SEARCH_H + 1.5f + VISIBLE * ITEM_H + 2f;
        if (editor != null) h += editor.height();
        return h;
    }

    @Override
    public float height() { return Theme.SETTING_H + openA.value() * bodyHeight(); }

    private void refilter() {
        String q = search.text.toLowerCase(Locale.ROOT).trim();
        filtered.clear();
        List<Entry> sel = new ArrayList<>(), rest = new ArrayList<>();
        for (Entry e : source.entries()) {
            if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q) && !e.id().contains(q)) continue;
            (source.selected(e.key()) ? sel : rest).add(e);
        }
        filtered.addAll(sel);
        filtered.addAll(rest);
        lastQuery = search.text;
    }

    @Override
    protected void draw(Ui ui) {
        openA.update(ui.dt, Theme.animSpeed());
        float h = Theme.SETTING_H;
        background(ui, height());
        headerHovered = ui.hovered(x, y, w, h);
        ui.text(source.label(), x + Theme.PAD, y, h, Theme.FONT_SMALL,
                ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, headerHovered ? 1f : 0f));
        float right = x + w - Theme.PAD;
        ui.chevron(right - 1.3f, y + h * 0.5f, 1.3f, openA.value(), Theme.TEXT_DIM);
        int n = source.selectedCount();
        ui.textRight(n == 0 ? "None" : n + " selected", right - 4f, y, h, Theme.FONT_SMALL, n == 0 ? Theme.TEXT_DIM : Theme.accent());

        float e = openA.value();
        hoveredIndex = -1;
        searchHovered = listHovered = false;
        if (e < 0.001f) return;
        if (lastQuery == null || !lastQuery.equals(search.text)) {
            refilter();
            scrollTarget = 0;
        }
        ui.pushClip(x, y + h, w, bodyHeight() * e);
        float left = x + Theme.PAD, iw = w - 2 * Theme.PAD;
        float sy = y + h + 2f;
        searchHovered = ui.hovered(left, sy, iw, SEARCH_H);
        search.draw(ui, left, sy, iw, SEARCH_H, 6f, "Search…", false);

        float ly = sy + SEARCH_H + 1.5f, lh = VISIBLE * ITEM_H;
        listHovered = ui.hovered(left, ly, iw, lh);
        float maxScroll = Math.max(0f, (filtered.size() - VISIBLE) * ITEM_H);
        scrollTarget = Math.min(Math.max(scrollTarget, 0f), maxScroll);
        scrollA.setTarget(scrollTarget);
        scrollA.update(ui.dt, Theme.animSpeed());
        ui.round(left, ly, iw, lh, 1.5f, 0xFF0F0F12);
        ui.pushClip(left, ly, iw, lh);
        int first = (int) (scrollA.value() / ITEM_H);
        for (int i = first; i < Math.min(filtered.size(), first + VISIBLE + 1); i++) {
            Entry en = filtered.get(i);
            float iy = ly + i * ITEM_H - scrollA.value();
            boolean hov = ui.hovered(left, iy, iw, ITEM_H);
            if (hov) hoveredIndex = i;
            boolean sel = source.selected(en.key());
            if (hov) ui.rect(left, iy, iw, ITEM_H, 0x14FFFFFF);
            if (sel) ui.rect(left, iy + 1.5f, 1f, ITEM_H - 3f, Theme.accent());
            float tx = left + 2.5f;
            int dot = sel ? source.dot(en.key()) : 0;
            if (dot != 0) {
                ui.round(tx, iy + ITEM_H * 0.5f - 1.25f, 2.5f, 2.5f, 1.25f, dot | 0xFF000000);
                tx += 4f;
            }
            ui.text(ui.ellipsize(en.name(), left + iw - tx - 2f, 6f), tx, iy, ITEM_H, 6f,
                    sel ? Theme.accent() : hov ? Theme.TEXT : Theme.TEXT_DIM);
        }
        if (filtered.isEmpty()) ui.textCentered("No matches", left + iw * 0.5f, ly, ITEM_H * 2, 6f, Theme.TEXT_DIM);
        ui.popClip();
        if (maxScroll > 0) {
            float barH = Math.max(4f, lh * VISIBLE / (float) filtered.size());
            float barY = ly + (lh - barH) * (scrollA.value() / maxScroll);
            ui.round(left + iw - 1.2f, barY, 0.8f, barH, 0.4f, Theme.accent(0.6f));
        }
        if (editor != null) editor.render(ui, x, ly + lh + 2f, w);
        ui.popClip();
    }

    @Override
    public boolean mouseClicked(Ui ui, int button, int mods) {
        if (headerHovered) {
            open = !open;
            openA.setTarget(open ? 1f : 0f);
            if (!open) search.end();
            else lastQuery = null; // re-sort (selected first) every time it opens
            return true;
        }
        if (!open) return false;
        if (searchHovered && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (!search.editing) search.begin(search.text);
            return true;
        }
        search.end();
        if (hoveredIndex >= 0 && hoveredIndex < filtered.size()) {
            Entry en = filtered.get(hoveredIndex);
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                source.toggle(en.key());
                if (editorEntry != null && editorEntry.key().equals(en.key()) && !source.selected(en.key())) closeEditor();
                // keep the list stable while clicking; re-sort on the next search change / reopen
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && source.selected(en.key())) {
                if (editorEntry != null && editorEntry.key().equals(en.key())) closeEditor();
                else {
                    editor = source.editor(en);
                    editorEntry = editor == null ? null : en;
                }
            }
            return true;
        }
        if (editor != null && editor.isHovered()) return editor.mouseClicked(ui, button, mods) || true;
        return true;
    }

    private void closeEditor() {
        editor = null;
        editorEntry = null;
    }

    @Override
    public void mouseReleased(Ui ui, int button) {
        if (editor != null) editor.mouseReleased(ui, button);
    }

    @Override
    public boolean mouseScrolled(Ui ui, double amount) {
        if (!open || !listHovered) return false;
        scrollTarget -= (float) amount * ITEM_H * 2f;
        return true;
    }

    @Override
    public boolean wantsKeyboard() { return search.editing || (editor != null && editor.wantsKeyboard()); }

    @Override
    public void blur() {
        search.end();
        if (editor != null) editor.blur();
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (editor != null && editor.wantsKeyboard()) return editor.keyPressed(key, mods);
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            search.end();
            return true;
        }
        search.keyPressed(key, mods);
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) {
        if (editor != null && editor.wantsKeyboard()) return editor.charTyped(codepoint);
        return search.charTyped(codepoint);
    }
}
