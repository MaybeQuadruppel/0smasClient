package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Entity filter: expands to one switch per entity group, each with a color swatch and an inline color picker. */
public class EntityFilterWidget extends SettingWidget {

    private final EntityFilterPicker picker;
    private final Anim openA = new Anim(0f, 14f);
    private boolean open;
    private final List<Entry> entries = new ArrayList<>();
    private boolean headerHovered;

    private final class Entry {
        final String key;
        final Anim onA, hoverA = new Anim(0f, 18f), swatchA = new Anim(0f, 18f);
        final ColorWidget color;
        float ey, swX;
        boolean hovered, swatchHovered;

        Entry(String key) {
            this.key = key;
            this.onA = new Anim(picker.isFilterEnabled(key) ? 1f : 0f, 16f);
            this.color = new ColorWidget(picker, key, () -> picker.getColor(key), c -> picker.colors.put(key, c), true);
        }

        float height() { return Theme.SETTING_H + color.height(); }
    }

    public EntityFilterWidget(EntityFilterPicker picker) {
        super(picker);
        this.picker = picker;
        for (String k : picker.filters.keySet()) entries.add(new Entry(k));
    }

    private float bodyHeight() {
        float h = 0;
        for (Entry e : entries) h += e.height();
        return h;
    }

    @Override
    public float height() { return Theme.SETTING_H + openA.value() * bodyHeight(); }

    @Override
    protected void draw(Ui ui) {
        openA.update(ui.dt, Theme.animSpeed());
        float h = Theme.SETTING_H;
        background(ui, height());
        headerHovered = ui.hovered(x, y, w, h);
        ui.text(picker.getLabel(), x + Theme.PAD, y, h, Theme.FONT_SMALL,
                ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, headerHovered ? 1f : 0f));
        int on = 0;
        for (String k : picker.filters.keySet()) if (picker.isFilterEnabled(k)) on++;
        float right = x + w - Theme.PAD;
        ui.chevron(right - 1.3f, y + h * 0.5f, 1.3f, openA.value(), Theme.TEXT_DIM);
        ui.textRight(on + "/" + picker.filters.size(), right - 4f, y, h, Theme.FONT_SMALL, Theme.accent());

        float e = openA.value();
        if (e < 0.001f) {
            for (Entry en : entries) en.hovered = en.swatchHovered = false;
            return;
        }
        ui.pushClip(x, y + h, w, bodyHeight() * e);
        float yy = y + h;
        for (Entry en : entries) {
            en.ey = yy;
            en.hovered = ui.hovered(x, yy, w, h);
            en.hoverA.setTarget(en.hovered ? 1f : 0f);
            en.hoverA.update(ui.dt, Theme.animSpeed());
            en.onA.setTarget(picker.isFilterEnabled(en.key) ? 1f : 0f);
            en.onA.update(ui.dt, Theme.animSpeed());
            if (en.hoverA.value() > 0.01f) ui.rect(x, yy, w, h, ColorUtil.withAlpha(0xFFFFFF, Math.round(10 * en.hoverA.value())));
            ui.text(en.key, x + Theme.PAD + 3f, yy, h, Theme.FONT_SMALL,
                    ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, Math.max(en.onA.value(), en.hoverA.value() * 0.5f)));
            float swx = x + w - Theme.PAD - 9f - 3f - 7f;
            en.swX = swx;
            en.swatchHovered = ui.hovered(swx, yy + 2f, 7f, h - 4f);
            en.swatchA.setTarget(en.swatchHovered || en.color.isOpen() ? 1f : 0f);
            en.swatchA.update(ui.dt, Theme.animSpeed());
            ColorWidget.swatch(ui, swx, yy + 2.5f, 7f, h - 5f, picker.getColor(en.key), en.swatchA.value());
            BoolWidget.drawSwitch(ui, x + w - Theme.PAD - 9f, yy + (h - 5f) * 0.5f, en.onA.value(), en.hoverA.value());
            yy += h;
            // always render: the embedded picker advances its own open animation in draw()
            en.color.render(ui, x + 2f, yy, w - 2f);
            float ch = en.color.height();
            yy += ch;
        }
        ui.popClip();
    }

    @Override
    public boolean mouseClicked(Ui ui, int button, int mods) {
        if (headerHovered) {
            open = !open;
            openA.setTarget(open ? 1f : 0f);
            return true;
        }
        if (!open) return false;
        for (Entry en : entries) {
            if (en.swatchHovered) {
                en.color.setOpen(!en.color.isOpen());
                return true;
            }
            if (en.hovered && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                picker.filters.put(en.key, !picker.isFilterEnabled(en.key));
                return true;
            }
            if (en.color.isOpen() && en.color.isHovered()) return en.color.mouseClicked(ui, button, mods) || true;
        }
        return true;
    }

    @Override
    public void mouseReleased(Ui ui, int button) {
        for (Entry en : entries) en.color.mouseReleased(ui, button);
    }

    // hex editing of an embedded picker
    private ColorWidget editing() {
        for (Entry en : entries) if (en.color.wantsKeyboard()) return en.color;
        return null;
    }

    @Override
    public boolean wantsKeyboard() { return editing() != null; }

    @Override
    public void blur() {
        ColorWidget c = editing();
        if (c != null) c.blur();
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        ColorWidget c = editing();
        return c != null && c.keyPressed(key, mods);
    }

    @Override
    public boolean charTyped(int codepoint) {
        ColorWidget c = editing();
        return c != null && c.charTyped(codepoint);
    }
}
