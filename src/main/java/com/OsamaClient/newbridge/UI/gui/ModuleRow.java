package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.components.Component;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.setting.SettingWidget;
import com.OsamaClient.newbridge.UI.gui.setting.SettingWidgets;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import com.OsamaClient.newbridge.UI.gui.util.KeyNames;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** One module row: toggle/hover/expand animations, bind mode, and the inline setting widgets. */
public final class ModuleRow {

    private static final int TEXT_OFF = 0xFFB9B9C3;

    final Module module;
    final List<SettingWidget> widgets = new ArrayList<>();
    private final Anim toggleA, hoverA = new Anim(0f, 18f), expandA = new Anim(0f, 14f);
    private boolean expanded;
    boolean binding;
    private boolean hovered;
    private float time;

    ModuleRow(Module module) {
        this.module = module;
        this.toggleA = new Anim(module.enabled ? 1f : 0f, 14f);
        for (Component c : module.settings) widgets.add(SettingWidgets.create(c));
    }

    private float settingsHeight() {
        float h = 0;
        for (SettingWidget sw : widgets) h += sw.height();
        return h;
    }

    public float height() {
        float e = expandA.value();
        return Theme.ROW_H + (e > 0 ? e * settingsHeight() : 0);
    }

    public boolean isExpanded() { return expanded; }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded && !widgets.isEmpty();
        expandA.setTarget(this.expanded ? 1f : 0f);
    }

    void render(Ui ui, float x, float y, float w) {
        float speed = Theme.animSpeed();
        time += ui.dt;
        hovered = ui.hovered(x, y, w, Theme.ROW_H);
        hoverA.setTarget(hovered ? 1f : 0f);
        toggleA.setTarget(module.enabled ? 1f : 0f);
        hoverA.update(ui.dt, speed);
        toggleA.update(ui.dt, speed);
        expandA.update(ui.dt, speed);
        if (hovered) ClickGui.INSTANCE.hint(module.description);

        float t = toggleA.value(), hv = hoverA.value();
        float h = Theme.ROW_H;
        if (ui.visible(x, y, w, h)) {
            ui.rect(x, y, w, h, ColorUtil.lerp(Theme.ROW, Theme.ROW_HOVER, hv));
            if (t > 0.001f) {
                ui.rect(x, y, w, h, Theme.accent(0.20f * t));
                float bar = 1.5f * t;
                ui.glow(x, y + 1.5f, bar, h - 3f, 0.75f, 4f, Theme.accent(0.55f * t * Theme.glow()));
                ui.round(x, y + 1.5f, bar, h - 3f, 0.75f, Theme.accent());
            }
            int textColor = ColorUtil.lerp(TEXT_OFF, 0xFFFFFFFF, t);
            float textX = x + Theme.PAD + 1.5f * t + 1f;
            String right;
            int rightColor = Theme.TEXT_DIM;
            if (binding) {
                float pulse = 0.55f + 0.45f * (float) Math.sin(time * 6.0);
                right = "Press a key…";
                rightColor = ColorUtil.alpha(Theme.accent(), pulse);
            } else {
                right = KeyNames.name(module.key);
            }
            float rightEdge = x + w - Theme.PAD;
            if (!widgets.isEmpty()) {
                ui.chevron(rightEdge - 1.5f, y + h * 0.5f, 1.6f, expandA.value(),
                        ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv));
                rightEdge -= 5f;
            }
            float rightW = right.isEmpty() ? 0 : ui.textWidth(right, Theme.FONT_SMALL) + 3f;
            String name = ui.ellipsize(module.name, rightEdge - rightW - textX, Theme.FONT);
            ui.text(name, textX, y, h, Theme.FONT, textColor);
            if (!right.isEmpty()) ui.textRight(right, rightEdge, y, h, Theme.FONT_SMALL, rightColor);
        }

        float e = expandA.value();
        if (e > 0.001f) {
            float sh = settingsHeight() * e;
            ui.pushClip(x, y + h, w, sh);
            float yy = y + h;
            for (SettingWidget sw : widgets) {
                float wh = sw.height();
                if (ui.visible(x, yy, w, wh) || sw.wantsKeyboard()) sw.render(ui, x + 1f, yy, w - 1f);
                if (sw.isHovered()) ClickGui.INSTANCE.hint(sw.description());
                yy += wh;
            }
            // thin accent line on the left edge of the settings block
            ui.rect(x, y + h, 1f, sh, Theme.accent(0.35f + 0.35f * t));
            ui.popClip();
        }
    }

    boolean mouseClicked(Ui ui, int button, int mods) {
        if (hovered) {
            switch (button) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> ClickGui.INSTANCE.safeToggle(module);
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> setExpanded(!expanded);
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> ClickGui.INSTANCE.startBinding(this);
                default -> { return false; }
            }
            return true;
        }
        if (expanded && expandA.value() > 0.5f) {
            for (SettingWidget sw : widgets) {
                if (sw.isHovered() && sw.mouseClicked(ui, button, mods)) {
                    ClickGui.INSTANCE.focus(sw.wantsKeyboard() ? sw : null);
                    return true;
                }
            }
        }
        return false;
    }

    void mouseReleased(Ui ui, int button) {
        for (SettingWidget sw : widgets) sw.mouseReleased(ui, button);
    }

    boolean mouseScrolled(Ui ui, double amount) {
        if (!expanded) return false;
        for (SettingWidget sw : widgets) if (sw.isHovered() && sw.mouseScrolled(ui, amount)) return true;
        return false;
    }

    boolean hoverKey(int key, int mods) {
        if (!expanded) return false;
        for (SettingWidget sw : widgets) if (sw.isHovered() && sw.hoverKey(key, mods)) return true;
        return false;
    }
}
