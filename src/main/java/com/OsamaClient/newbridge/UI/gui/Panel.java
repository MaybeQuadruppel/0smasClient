package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.SubCategories;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import com.OsamaClient.newbridge.UI.gui.input.Keys;

import java.util.ArrayList;
import java.util.List;

/** One category column: header (drag / collapse), scrollable list of module rows. */
public final class Panel {

    final Module.Category category;
    /** The Favorites panel: lists every favorited module (rows for all modules, filtered). */
    final boolean favorites;
    final String title;
    final List<ModuleRow> rows = new ArrayList<>();

    /** Saved position (units); {@link #dx}/{@link #dy} is where it is drawn (clamped to the screen). */
    float x, y;
    private float dx, dy;
    boolean collapsed;
    private final Anim openA = new Anim(1f, 14f);
    private final Anim scrollA = new Anim(0f, 16f);
    private final Anim headerHoverA = new Anim(0f, 18f);
    private final Anim[] rowVisible;
    private float scrollTarget;

    boolean dragging;
    private float dragDX, dragDY;

    // last frame bounds (units)
    private float lastH;
    private boolean headerHovered, bodyHovered;

    /** Sub-category group of each row (null = panel has none). */
    private String[] groupOf;
    /** Group headers drawn last frame: name, top y, height (for clicks and tests). */
    private final List<Object[]> headerRects = new ArrayList<>();
    private final java.util.Set<String> groupShown = new java.util.HashSet<>();
    private boolean grouping;
    static final float GROUP_H = 10f;

    Panel(Module.Category category, String title, List<Module> modules) { this(category, title, modules, false); }

    Panel(Module.Category category, String title, List<Module> modules, boolean favorites) {
        this.category = category;
        this.favorites = favorites;
        this.title = title;
        List<Module> ms = new ArrayList<>(modules);
        if (!favorites && SubCategories.has(category)) ms.sort(java.util.Comparator.comparingInt(SubCategories::order));
        for (Module m : ms) rows.add(new ModuleRow(m));
        if (!favorites && SubCategories.has(category)) {
            groupOf = new String[rows.size()];
            for (int i = 0; i < groupOf.length; i++) groupOf[i] = SubCategories.of(rows.get(i).module);
        }
        rowVisible = new Anim[rows.size()];
        for (int i = 0; i < rowVisible.length; i++) rowVisible[i] = new Anim(1f, 16f);
    }

    public List<ModuleRow> rows() { return rows; }

    /** Key under which the panel's layout is saved. */
    String key() { return favorites ? "FAVORITES" : category.name(); }

    private boolean matches(ModuleRow r, String search) {
        if (favorites && !r.module.favorite) return false;
        if (com.OsamaClient.newbridge.Hacks.Client.NormalMode.hides(r.module)) return false;
        var tq = com.OsamaClient.newbridge.UI.components.Tags.parse(search);
        if (!com.OsamaClient.newbridge.UI.components.Tags.matches(r.module, tq)) return false;
        String text = tq.text();
        boolean textOk = text.isEmpty() || r.searchName.contains(text)
                || (text.length() >= 3 && (r.searchDesc.contains(text) || r.searchSettings.contains(text) || r.searchTags.contains(text)));
        return textOk && (!ClickGui.onlyEnabled || r.module.enabled) && (!ClickGui.onlyFavorites || r.module.favorite) && (!ClickGui.onlyRecent || ClickGui.RECENT.contains(r.module.name));
    }

    /** Number of rows that pass the current search / "enabled only" filter. */
    public int matchCount(String search) {
        int n = 0;
        for (ModuleRow r : rows) if (matches(r, search)) n++;
        return n;
    }

    /** Height drawn last frame, in GUI units (for tests). */
    public float lastHeight() { return lastH; }
    public boolean isCollapsed() { return collapsed; }
    public boolean isFavorites() { return favorites; }

    /** Small identity colour per category, shown as the dot in front of the panel title. */
    private int dotColor() { return favorites ? 0xFFFFC94D : categoryColor(category); }

    private static int categoryColor(Module.Category c) {
        return switch (c) {
            case COMBAT -> 0xFFFF5C6C;
            case MOVEMENT -> 0xFF5CE1A0;
            case VISUAL -> 0xFFB48CFF;
            case WORLD -> 0xFF4FD6C8;
            case MISC -> 0xFFFFC857;
            case DUPE -> 0xFF7CFFB2;
            case Donut -> 0xFF58D0FF;
            case CLIENT -> 0xFF8A9BFF;
            case PLAYER -> 0xFFFF9A5C;
            case TROLL -> 0xFFFF7ACB;
        };
    }

    /** Scrolls the list so that the row is at the top (also used by tests). */
    public void scrollToRow(ModuleRow row) {
        float off = 0;
        for (int i = 0; i < rows.size(); i++) {
            ModuleRow r = rows.get(i);
            if (headerAt(i)) off += GROUP_H;
            if (r == row) break;
            off += r.height() * rowVisible[i].value();
        }
        scrollTarget = off;
    }

    public void setCollapsed(boolean c) {
        collapsed = c;
        openA.setTarget(c ? 0f : 1f);
    }

    public void snapCollapsed(boolean c) {
        setCollapsed(c);
        openA.snap(c ? 0f : 1f);
    }

    /** Normal Mode with "Hide Other Modules": a category without any allowed module is not shown at all. */
    boolean hiddenByMode() {
        if (favorites) {
            for (ModuleRow r : rows) if (r.module.favorite && !com.OsamaClient.newbridge.Hacks.Client.NormalMode.hides(r.module)) return false;
            return true;
        }
        if (!com.OsamaClient.newbridge.Hacks.Client.NormalMode.active() || rows.isEmpty()) return false;
        for (ModuleRow r : rows) if (!com.OsamaClient.newbridge.Hacks.Client.NormalMode.hides(r.module)) return false;
        return true;
    }

    boolean contains(float mx, float my) {
        if (hiddenByMode()) return false;
        return mx >= dx && mx < dx + Theme.panelW() && my >= dy && my < dy + lastH;
    }

    private static final float FOOT_H = 11f;
    private boolean footerOn;
    private float footY;

    private float contentHeight() {
        float h = footerOn ? FOOT_H + 2f : 0f;
        for (int i = 0; i < rows.size(); i++) {
            h += rows.get(i).height() * rowVisible[i].value();
            if (headerAt(i)) h += GROUP_H;
        }
        return h;
    }

    /** true if row i starts a group whose header is drawn. */
    private boolean headerAt(int i) {
        if (!grouping || groupOf == null) return false;
        if (i > 0 && groupOf[i].equals(groupOf[i - 1])) return false;
        return groupShown.contains(groupOf[i]);
    }

    /** Sub-category names currently drawn as headers (tests). */
    public List<String> groupHeaders() {
        List<String> out = new ArrayList<>();
        for (Object[] h : headerRects) out.add((String) h[0]);
        return out;
    }

    /** Folds / unfolds a group (tests, clicks). */
    public void toggleGroup(String g) {
        SubCategories.setCollapsed(category, g, !SubCategories.collapsed(category, g));
        GuiEvents.fireChanged();
    }

    /** @param appear 0..1 open-animation progress of this panel */
    void render(Ui ui, float appear, String search) {
        if (hiddenByMode()) { lastH = 0; return; }
        float speed = Theme.animSpeed();
        openA.update(ui.dt, speed);
        grouping = groupOf != null && SubCategories.enabled() && (search == null || search.isBlank());
        {
            java.util.List<Module> ms = new ArrayList<>();
            for (ModuleRow r : rows) ms.add(r.module);
            footerOn = !favorites && (search == null || search.isBlank()) && com.OsamaClient.newbridge.UI.components.ModuleFilter.extra(ms) > 0;
        }
        groupShown.clear();
        if (grouping) for (int i = 0; i < rows.size(); i++) if (matches(rows.get(i), search)) groupShown.add(groupOf[i]);
        for (int i = 0; i < rows.size(); i++) {
            boolean match = matches(rows.get(i), search);
            if (grouping && SubCategories.collapsed(category, groupOf[i])) match = false;
            rowVisible[i].setTarget(match ? 1f : 0f);
            rowVisible[i].update(ui.dt, speed);
        }

        float w = Theme.panelW();
        // keep the panel reachable after a window resize, without changing its saved position
        dx = dragging ? this.x : Math.max(0f, Math.min(this.x, ui.width() - w));
        dy = dragging ? this.y : Math.max(0f, Math.min(this.y, ui.height() - Theme.HEADER_H));
        float x = dx, y = dy; // drawn position (shadows the saved one below)
        float yy = y - 6f * (1f - appear);
        float radius = Theme.radius();
        float headerH = Theme.HEADER_H;
        float bottomPad = Math.max(1.5f, radius);

        float content = contentHeight();
        float maxBody = Math.max(20f, ui.height() - yy - headerH - bottomPad - 6f);
        maxBody = Math.min(maxBody, Math.max(20f, ui.height() * Theme.maxPanelHeight() - headerH - bottomPad));
        float bodyH = Math.min(content, maxBody) * openA.value();
        float maxScroll = Math.max(0f, content - maxBody);
        scrollTarget = Math.min(Math.max(scrollTarget, 0f), maxScroll);
        scrollA.setTarget(scrollTarget);
        scrollA.update(ui.dt, speed);
        float totalH = headerH + bodyH + bottomPad * Math.min(1f, openA.value() * 4f);
        if (totalH < headerH) totalH = headerH;
        lastH = totalH;

        headerHovered = ui.hovered(x, yy, w, headerH);
        bodyHovered = ui.hovered(x, yy + headerH, w, totalH - headerH);
        headerHoverA.setTarget(headerHovered ? 1f : 0f);
        headerHoverA.update(ui.dt, speed);

        // glow + body
        float g = Theme.glow();
        ui.glow(x, yy + 2f, w, totalH, radius, 9f, 0x55000000); // soft drop shadow for depth
        if (g > 0) ui.glow(x, yy, w, totalH, radius, 7f, Theme.accent(0.16f * g));
        ui.round(x, yy, w, totalH, radius, Theme.panelBg());
        if (Theme.glass()) {
            // frosted glass: soft white sheen from the top edge plus a faint accent wash
            ui.gradientV(x, yy, w, totalH * 0.5f, radius, ColorUtil.alpha(0xFFFFFFFF, 0.13f), ColorUtil.alpha(0xFFFFFFFF, 0f));
            ui.gradientV(x, yy, w, headerH, radius, Theme.accent(0.12f + 0.06f * headerHoverA.value()), Theme.accent(0f));
            ui.gradientH(x + radius, yy + 0.5f, w - 2 * radius, 1f / ui.scale, 0, ColorUtil.alpha(0xFFFFFFFF, 0f), ColorUtil.alpha(0xFFFFFFFF, 0.8f));
        } else
        ui.gradientV(x, yy, w, headerH, radius, Theme.accent(0.14f + 0.06f * headerHoverA.value()), Theme.accent(0f));

        // header
        int dot = dotColor();
        float is = 7.5f, ix = x + Theme.PAD, iy = yy + (headerH - is) * 0.5f;
        ui.glow(ix + 1f, iy + 1f, is - 2f, is - 2f, is * 0.4f, 4f, ColorUtil.alpha(dot, 0.28f + 0.2f * headerHoverA.value()));
        com.OsamaClient.newbridge.UI.gui.render.Icons.draw(ui, (favorites ? "star" : com.OsamaClient.newbridge.UI.gui.render.Icons.category(category.name())), ix, iy, is, dot);
        ui.text(title, x + Theme.PAD + is + 3f, yy, headerH, Theme.FONT, Theme.TEXT);
        int enabled = 0, shown = 0;
        for (ModuleRow r : rows) {
            if (com.OsamaClient.newbridge.Hacks.Client.NormalMode.hides(r.module) || (favorites && !r.module.favorite)) continue;
            shown++;
            if (r.module.enabled) enabled++;
        }
        String count = enabled + "/" + shown;
        ui.chevron(x + w - Theme.PAD - 2f, yy + headerH * 0.5f, 1.75f, openA.value(),
                ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, headerHoverA.value()));
        ui.textRight(count, x + w - Theme.PAD - 8f, yy, headerH, Theme.FONT_SMALL, Theme.TEXT_DIM);
        // slim accent line under the header
        float lineW = (w - 2 * Theme.PAD) * (0.35f + 0.65f * openA.value());
        ui.gradientH(x + Theme.PAD, yy + headerH - 1f, lineW, 1f / ui.scale, 0, Theme.accent(0.9f), Theme.accent(0.05f));

        // rows (a collapsed body draws none, so drop their hover state)
        if (bodyH <= 0.01f) { for (ModuleRow r : rows) r.clearHover(); headerRects.clear(); }
        if (bodyH > 0.01f) {
            float top = yy + headerH;
            ui.pushClip(x, top, w, bodyH);
            float ry = top - scrollA.value();
            headerRects.clear();
            for (int i = 0; i < rows.size(); i++) {
                if (headerAt(i)) { drawGroupHeader(ui, groupOf[i], x, ry, w); headerRects.add(new Object[]{groupOf[i], ry, GROUP_H}); ry += GROUP_H; }
                float v = rowVisible[i].value();
                if (v < 0.001f) {
                    rows.get(i).clearHover();
                    continue;
                }
                ModuleRow row = rows.get(i);
                float rh = row.height() * v;
                if (v < 0.999f) {
                    ui.pushClip(x, ry, w, rh);
                    row.render(ui, x, ry, w);
                    ui.popClip();
                } else {
                    row.render(ui, x, ry, w);
                }
                ry += rh;
            }
            if (footerOn) {
                footY = ry + 1f;
                boolean hv = ui.hovered(x, footY, w, FOOT_H) && ui.hovered(x, top, w, bodyH);
                ui.round(x + 3f, footY, w - 6f, FOOT_H, 2f, Theme.accent(hv ? 0.28f : 0.14f));
                int fc = hv ? Theme.TEXT : Theme.TEXT_DIM;
                int extraN = 0;
                for (ModuleRow r : rows) if (com.OsamaClient.newbridge.UI.components.ModuleFilter.nonEssential(r.module)) extraN++;
                ui.textCentered(com.OsamaClient.newbridge.UI.components.ModuleFilter.footerLabel(extraN), x + w / 2f, footY, FOOT_H, Theme.FONT_SMALL, fc);
            }
            ui.popClip();
            // scrollbar
            if (maxScroll > 0.5f) {
                float barH = Math.max(8f, bodyH * bodyH / content);
                float barY = top + (bodyH - barH) * (scrollA.value() / maxScroll);
                ui.round(x + w - 1.5f, barY, 1f, barH, 0.5f, Theme.accent(0.6f));
            }
        }

        if (Theme.glass()) ui.outline(x, yy, w, totalH, radius, 1f, 0x55FFFFFF);
        else if (Theme.outline()) ui.outline(x, yy, w, totalH, radius, 1f, Theme.OUTLINE);
    }

    private void drawGroupHeader(Ui ui, String g, float x, float y, float w) {
        boolean fold = SubCategories.collapsed(category, g);
        boolean hov = ui.hovered(x, y, w, GROUP_H) && ui.hovered(x, dy + Theme.HEADER_H, w, Math.max(0f, lastH - Theme.HEADER_H));
        int col = dotColor();
        float by = y + 1.5f, bh = GROUP_H - 1.5f;
        // tinted band in the category colour with a solid bar on the left, so groups stand out from the module rows
        ui.round(x + 1.5f, by, w - 3f, bh, 1.5f, ColorUtil.alpha(col, hov ? 0.26f : 0.16f));
        ui.round(x + 1.5f, by + 1f, 1.4f, bh - 2f, 0.7f, col);
        int txt = ColorUtil.lerp(0xFFD8D8E2, 0xFFFFFFFF, hov ? 1f : 0f);
        ui.chevron(x + Theme.PAD + 3.5f, by + bh * 0.5f, 1.4f, fold ? 0f : 1f, col);
        ui.text(g.toUpperCase(java.util.Locale.ROOT), x + Theme.PAD + 8f, by, bh, 6f, txt);
        int on = 0, all = 0;
        for (int i = 0; i < rows.size(); i++) {
            if (!g.equals(groupOf[i]) || com.OsamaClient.newbridge.Hacks.Client.NormalMode.hides(rows.get(i).module)) continue;
            all++;
            if (rows.get(i).module.enabled) on++;
        }
        ui.textRight(on + "/" + all, x + w - Theme.PAD - 1f, by, bh, 5.6f, on > 0 ? col : Theme.TEXT_DIM);
    }

    /** @return true if consumed */
    boolean mouseClicked(Ui ui, int button, int mods) {
        if (headerHovered) {
            if (button == Keys.MOUSE_BUTTON_LEFT) {
                dragging = true;
                x = dx;
                y = dy;
                dragDX = ui.mouseX - x;
                dragDY = ui.mouseY - y;
            } else if (button == Keys.MOUSE_BUTTON_RIGHT) {
                setCollapsed(!collapsed);
                com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick();
            }
            return true;
        }
        if (bodyHovered && !collapsed) {
            if (button == Keys.MOUSE_BUTTON_LEFT) for (Object[] h : headerRects) {
                float hy = (Float) h[1];
                if (ui.hovered(dx, hy, Theme.panelW(), GROUP_H)) {
                    toggleGroup((String) h[0]);
                    com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick();
                    return true;
                }
            }
            if (footerOn && button == Keys.MOUSE_BUTTON_LEFT && footY >= dy + Theme.HEADER_H && ui.hovered(dx, footY, Theme.panelW(), FOOT_H)) {
                com.OsamaClient.newbridge.UI.components.ModuleFilter.toggleView();
                com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick();
                return true;
            }
            for (ModuleRow r : rows) if (r.mouseClicked(ui, button, mods)) return true;
            return true; // clicks on the panel never fall through to panels below
        }
        return false;
    }

    void drag(Ui ui) {
        if (!dragging) return;
        x = Math.round(Math.min(Math.max(ui.mouseX - dragDX, -Theme.panelW() + 20f), ui.width() - 20f));
        y = Math.round(Math.min(Math.max(ui.mouseY - dragDY, 0f), ui.height() - Theme.HEADER_H));
    }

    void mouseReleased(Ui ui, int button) {
        if (button == Keys.MOUSE_BUTTON_LEFT) dragging = false;
        for (ModuleRow r : rows) r.mouseReleased(ui, button);
    }

    boolean mouseScrolled(Ui ui, double amount) {
        if (!bodyHovered) return false;
        for (ModuleRow r : rows) if (r.mouseScrolled(ui, amount)) return true;
        scrollTarget -= (float) amount * Theme.rowH() * 2f;
        return true;
    }

    boolean hoverKey(int key, int mods) {
        for (ModuleRow r : rows) if (r.hoverKey(key, mods)) return true;
        return false;
    }
}
