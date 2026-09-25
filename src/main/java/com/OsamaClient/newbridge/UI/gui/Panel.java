package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One category column: header (drag / collapse), scrollable list of module rows. */
public final class Panel {

    final Module.Category category;
    final String title;
    final List<ModuleRow> rows = new ArrayList<>();

    float x, y;
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

    Panel(Module.Category category, String title, List<Module> modules) {
        this.category = category;
        this.title = title;
        for (Module m : modules) rows.add(new ModuleRow(m));
        rowVisible = new Anim[rows.size()];
        for (int i = 0; i < rowVisible.length; i++) rowVisible[i] = new Anim(1f, 16f);
    }

    public void setCollapsed(boolean c) {
        collapsed = c;
        openA.setTarget(c ? 0f : 1f);
    }

    public void snapCollapsed(boolean c) {
        setCollapsed(c);
        openA.snap(c ? 0f : 1f);
    }

    boolean contains(float mx, float my) {
        return mx >= x && mx < x + Theme.PANEL_W && my >= y && my < y + lastH;
    }

    private float contentHeight() {
        float h = 0;
        for (int i = 0; i < rows.size(); i++) h += rows.get(i).height() * rowVisible[i].value();
        return h;
    }

    /** @param appear 0..1 open-animation progress of this panel */
    void render(Ui ui, float appear, String search) {
        float speed = Theme.animSpeed();
        openA.update(ui.dt, speed);
        for (int i = 0; i < rows.size(); i++) {
            boolean match = search.isEmpty() || rows.get(i).module.name.toLowerCase(Locale.ROOT).contains(search);
            rowVisible[i].setTarget(match ? 1f : 0f);
            rowVisible[i].update(ui.dt, speed);
        }

        float w = Theme.PANEL_W;
        float yy = y - 6f * (1f - appear);
        float radius = Theme.radius();
        float headerH = Theme.HEADER_H;
        float bottomPad = Math.max(1.5f, radius);

        float content = contentHeight();
        float maxBody = Math.max(20f, ui.height() - yy - headerH - bottomPad - 6f);
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
        if (g > 0) ui.glow(x, yy, w, totalH, radius, 7f, Theme.accent(0.16f * g));
        ui.round(x, yy, w, totalH, radius, Theme.PANEL_BG);

        // header
        ui.text(title, x + Theme.PAD + 2f, yy, headerH, Theme.FONT, Theme.TEXT);
        int enabled = 0;
        for (ModuleRow r : rows) if (r.module.enabled) enabled++;
        String count = enabled + "/" + rows.size();
        ui.chevron(x + w - Theme.PAD - 2f, yy + headerH * 0.5f, 1.75f, openA.value(),
                ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, headerHoverA.value()));
        ui.textRight(count, x + w - Theme.PAD - 8f, yy, headerH, Theme.FONT_SMALL, Theme.TEXT_DIM);
        // slim accent line under the header
        float lineW = (w - 2 * Theme.PAD) * (0.35f + 0.65f * openA.value());
        ui.gradientH(x + Theme.PAD, yy + headerH - 1f, lineW, 1f / ui.scale, 0, Theme.accent(0.9f), Theme.accent(0.05f));

        // rows
        if (bodyH > 0.01f) {
            float top = yy + headerH;
            ui.pushClip(x, top, w, bodyH);
            float ry = top - scrollA.value();
            for (int i = 0; i < rows.size(); i++) {
                float v = rowVisible[i].value();
                if (v < 0.001f) continue;
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
            ui.popClip();
            // scrollbar
            if (maxScroll > 0.5f) {
                float barH = Math.max(8f, bodyH * bodyH / content);
                float barY = top + (bodyH - barH) * (scrollA.value() / maxScroll);
                ui.round(x + w - 1.5f, barY, 1f, barH, 0.5f, Theme.accent(0.6f));
            }
        }

        if (Theme.outline()) ui.outline(x, yy, w, totalH, radius, 1f, Theme.OUTLINE);
    }

    /** @return true if consumed */
    boolean mouseClicked(Ui ui, int button, int mods) {
        if (headerHovered) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                dragging = true;
                dragDX = ui.mouseX - x;
                dragDY = ui.mouseY - y;
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                setCollapsed(!collapsed);
            }
            return true;
        }
        if (bodyHovered && !collapsed) {
            for (ModuleRow r : rows) if (r.mouseClicked(ui, button, mods)) return true;
            return true; // clicks on the panel never fall through to panels below
        }
        return false;
    }

    void drag(Ui ui) {
        if (!dragging) return;
        x = Math.round(Math.min(Math.max(ui.mouseX - dragDX, -Theme.PANEL_W + 20f), ui.width() - 20f));
        y = Math.round(Math.min(Math.max(ui.mouseY - dragDY, 0f), ui.height() - Theme.HEADER_H));
    }

    void mouseReleased(Ui ui, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragging = false;
        for (ModuleRow r : rows) r.mouseReleased(ui, button);
    }

    boolean mouseScrolled(Ui ui, double amount) {
        if (!bodyHovered) return false;
        for (ModuleRow r : rows) if (r.mouseScrolled(ui, amount)) return true;
        scrollTarget -= (float) amount * Theme.ROW_H * 2f;
        return true;
    }

    boolean hoverKey(int key, int mods) {
        for (ModuleRow r : rows) if (r.hoverKey(key, mods)) return true;
        return false;
    }
}
