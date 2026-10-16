package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.components.Component;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.setting.BindWidget;
import com.OsamaClient.newbridge.UI.gui.setting.SettingWidget;
import com.OsamaClient.newbridge.UI.gui.setting.SettingWidgets;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import com.OsamaClient.newbridge.UI.gui.util.KeyNames;
import com.OsamaClient.newbridge.UI.gui.input.Keys;

import java.util.ArrayList;
import java.util.List;

/** One module row: toggle/hover/expand animations, bind mode, and the inline setting widgets. */
public final class ModuleRow {

    private static final int TEXT_OFF = 0xFFB9B9C3;

    public final Module module;
    /** Lower-case name for the search filter (computed once, not per frame). */
    final String searchName;
    /** Lower-case description, searched when the name does not match. */
    final String searchDesc;
    /** Lower-case labels of all settings, so the search also finds modules by a setting name. */
    final String searchSettings;
    /** Tags without the #, e.g. "sword undetectable". */
    final String searchTags;
    final List<SettingWidget> widgets = new ArrayList<>();
    /** Every setting widget; {@link #widgets} is the currently visible subset (Basic / Advanced). */
    private final List<SettingWidget> all = new ArrayList<>();
    private SettingWidget bindWidget;
    private int advCount;
    private boolean showAdv;
    private static final java.util.Set<String> SHOW_ADV = new java.util.HashSet<>();

    private boolean lastAll;

    /** Global "Settings View: Advanced" (ClickGui module): every setting is shown, no per-module Advanced row. */
    private static boolean allSettings() {
        var cg = ClickGuiModule.INSTANCE;
        return cg != null && "Advanced".equals(cg.settingsView.getMode());
    }

    private String lastMask = "";

    /** Which conditionally shown settings are visible right now (a change rebuilds the list). */
    private String visMask() {
        StringBuilder sb = new StringBuilder();
        for (Component c : module.settings) if (c.visibleIf != null) sb.append(c.isVisible() ? '1' : '0');
        return sb.toString();
    }

    private void rebuildWidgets() {
        lastMask = visMask();
        boolean everything = allSettings();
        lastAll = everything;
        widgets.clear();
        boolean toggleAdded = false;
        for (SettingWidget sw : all) {
            if (sw.setting() != null && !sw.setting().isVisible()) continue;
            boolean adv = sw.setting() != null && sw.setting().advanced;
            if (adv && !toggleAdded && !everything) { widgets.add(new com.OsamaClient.newbridge.UI.gui.setting.AdvancedWidget(advCount, () -> showAdv, this::toggleAdvanced)); toggleAdded = true; }
            if (adv && !showAdv && !everything) continue;
            widgets.add(sw);
        }
        if (advCount > 0 && !toggleAdded && !everything) widgets.add(new com.OsamaClient.newbridge.UI.gui.setting.AdvancedWidget(advCount, () -> showAdv, this::toggleAdvanced));
        widgets.add(bindWidget);
    }

    private void toggleAdvanced() {
        showAdv = !showAdv;
        if (showAdv) SHOW_ADV.add(module.name); else SHOW_ADV.remove(module.name);
        rebuildWidgets();
    }
    private final Anim toggleA, hoverA = new Anim(0f, 18f), expandA = new Anim(0f, 14f), flashA = new Anim(0f, 9f);
    private boolean expanded;
    boolean binding;
    private boolean hovered;
    private float time;

    /** Vape layout: taller row with an icon box, a settings summary and a switch. */
    final boolean vape;
    static final float VAPE_H = 18f;
    private String summary = "";
    private float summaryAge = 9f;
    private float lastX, lastW;

    ModuleRow(Module module) { this(module, false); }

    ModuleRow(Module module, boolean vape) {
        this.vape = vape;
        this.module = module;
        this.searchName = module.name.toLowerCase(java.util.Locale.ROOT);
        this.searchDesc = module.description == null ? "" : module.description.toLowerCase(java.util.Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (var c : module.settings) sb.append(DetailCard.label(c).toLowerCase(java.util.Locale.ROOT)).append('|');
        this.searchSettings = sb.toString();
        this.searchTags = String.join(" ", com.OsamaClient.newbridge.UI.components.Tags.of(module));
        this.toggleA = new Anim(module.enabled ? 1f : 0f, 14f);
        for (Component c : module.settings) { all.add(SettingWidgets.create(c)); if (c.advanced) advCount++; }
        bindWidget = new BindWidget(module, () -> binding, () -> ClickGui.INSTANCE.startBinding(this));
        showAdv = SHOW_ADV.contains(module.name);
        rebuildWidgets();
    }

    private float settingsHeight() {
        float h = 0;
        for (SettingWidget sw : widgets) h += sw.height();
        return h;
    }

    float rowH() { return vape ? VAPE_H : Theme.rowH(); }

    public float height() {
        float e = expandA.value();
        return rowH() + (e > 0 ? e * settingsHeight() : 0);
    }

    public boolean isExpanded() { return expanded; }

    /** The row or one of its settings is under the mouse (as of the last frame). */
    public boolean anyHover() {
        if (hovered) return true;
        for (SettingWidget sw : widgets) if (sw.isHovered()) return true;
        return false;
    }

    boolean wantsKeyboardNow() {
        for (SettingWidget sw : widgets) if (sw.wantsKeyboard()) return true;
        return false;
    }

    public Module module() { return module; }

    public List<SettingWidget> widgets() { return widgets; }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded && !widgets.isEmpty();
        expandA.setTarget(this.expanded ? 1f : 0f);
    }

    void render(Ui ui, float x, float y, float w) {
        if (allSettings() != lastAll || !visMask().equals(lastMask)) rebuildWidgets();
        float speed = Theme.animSpeed();
        time += ui.dt;
        lastX = x; lastW = w;
        hovered = ui.hovered(x, y, w, rowH());
        hoverA.setTarget(hovered ? 1f : 0f);
        toggleA.setTarget(module.enabled ? 1f : 0f);
        hoverA.update(ui.dt, speed);
        toggleA.update(ui.dt, speed);
        expandA.update(ui.dt, speed);
        flashA.update(ui.dt, speed);
        if (hovered) {
            ClickGui.INSTANCE.hint(module.description);
            ClickGui.INSTANCE.detail(module, null);
        }

        float t = toggleA.value(), hv = hoverA.value();
        float h = rowH();
        boolean glass = Theme.glass();
        if (vape) {
            if (ui.visible(x, y, w, h)) drawVape(ui, x, y, w, h, t, hv);
        } else if (ui.visible(x, y, w, h)) {
            if (glass) {
                // pill-shaped rows: invisible at rest, frosted when hovered, tinted + glossy when enabled
                float px = x + 2f, py = y + 0.75f, pw = w - 4f, ph = h - 1.5f, pr = Math.min(ph / 2f, 4.5f);
                if (hv > 0.001f) ui.round(px, py, pw, ph, pr, ColorUtil.alpha(0xFFFFFFFF, 0.10f * hv));
                if (t > 0.001f) {
                    ui.glow(px, py, pw, ph, pr, 5f, Theme.accent(0.22f * t * Theme.glow()));
                    ui.round(px, py, pw, ph, pr, Theme.accent(0.34f * t));
                    ui.gradientV(px, py, pw, ph * 0.55f, pr, ColorUtil.alpha(0xFFFFFFFF, 0.24f * t), ColorUtil.alpha(0xFFFFFFFF, 0f));
                    ui.outline(px, py, pw, ph, pr, 1f, ColorUtil.alpha(0xFFFFFFFF, 0.28f * t));
                }
                if (flashA.value() > 0.002f) ui.round(px, py, pw, ph, pr, ColorUtil.alpha(0xFFFFFFFF, 0.2f * flashA.value()));
            } else
            ui.rect(x, y, w, h, ColorUtil.lerp(Theme.ROW, Theme.ROW_HOVER, hv));
            if (t > 0.001f && !glass) {
                // accent sweeps in from the left when the module turns on (and retracts when it turns off)
                ui.rect(x, y, w, h, Theme.accent(0.06f * t));
                ui.gradientH(x, y, w * Anim.easeOutCubic(t), h, 0, Theme.accent(0.30f * Math.min(1f, t * 1.5f)), Theme.accent(0.07f));
                float bar = 1.5f * t;
                ui.glow(x, y + 1.5f, bar, h - 3f, 0.75f, 4f, Theme.accent(0.55f * t * Theme.glow()));
                ui.round(x, y + 1.5f, bar, h - 3f, 0.75f, Theme.accent());
            }
            if (!glass) {
                if (flashA.value() > 0.002f) ui.rect(x, y, w, h, ColorUtil.alpha(0xFFFFFFFF, 0.14f * flashA.value()));
                ui.rect(x, y + h - 1f / ui.scale, w, 1f / ui.scale, 0x0DFFFFFF); // hairline between rows
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
            if (module.favorite) {
                com.OsamaClient.newbridge.UI.gui.render.Icons.draw(ui, "star", rightEdge - 5.5f, y + (h - 5.5f) * 0.5f, 5.5f, 0xFFFFC94D);
                rightEdge -= 7.5f;
            }
            var legit = com.OsamaClient.newbridge.Hacks.Client.LegitMode.INSTANCE;
            if (legit != null && legit.enabled && com.OsamaClient.newbridge.Hacks.Client.LegitMode.BLOCKED.contains(module.name)
                    || com.OsamaClient.newbridge.Hacks.Client.NormalMode.blocks(module)) {
                com.OsamaClient.newbridge.UI.gui.render.Icons.draw(ui, "lock", rightEdge - 5f, y + (h - 5f) * 0.5f, 5f, 0xFFFFB454);
                rightEdge -= 7f;
            }
            float rightW = right.isEmpty() ? 0 : ui.textWidth(right, Theme.FONT_SMALL) + 3f;
            String name = ui.ellipsize(module.name, rightEdge - rightW - textX, Theme.FONT);
            ui.text(name, textX, y, h, Theme.FONT, textColor);
            if (!right.isEmpty()) ui.textRight(right, rightEdge, y, h, Theme.FONT_SMALL, rightColor);
        }

        float e = expandA.value();
        if (e > 0.001f) {
            float sh = settingsHeight() * e;
            boolean highlight = Theme.settingHighlight();
            ui.pushClip(x, y + h, w, sh);
            if (highlight) {
                // distinct background block (lighter/bluer than a row) so the settings clearly read as
                // "belonging" to this module rather than blending into the row list below it
                ui.rect(x, y + h, w, sh, Theme.settingBlockBg());
                ui.rect(x, y + h, w, Math.max(1f, 1f / ui.scale), Theme.SETTING_BLOCK_BORDER);
            }
            float indent = highlight ? 3f : 1f;
            float yy = y + h;
            for (SettingWidget sw : widgets) {
                float wh = sw.height();
                if (ui.visible(x, yy, w, wh) || sw.wantsKeyboard()) {
                    sw.render(ui, x + indent, yy, w - indent);
                    var st = sw.setting();
                    // small accent dot: this setting is not at its default value (MMB resets it)
                    if (st != null && com.OsamaClient.newbridge.UI.components.SettingHistory.changed(st) && com.OsamaClient.newbridge.UI.components.SettingHistory.supported(st))
                        ui.round(x + indent + 0.6f, yy + wh * 0.5f - 0.8f, 1.6f, 1.6f, 0.8f, Theme.accent(0.9f));
                }
                else sw.clearHover();
                if (sw.isHovered()) {
                    ClickGui.INSTANCE.hint(sw.description());
                    ClickGui.INSTANCE.detail(module, sw.setting());
                }
                yy += wh;
            }
            // accent bar on the left edge of the settings block; brighter/thicker when highlighted so it
            // stays readable as a single continuous group even while individual rows animate in/out
            float barW = highlight ? 2f : 1f;
            ui.rect(x, y + h, barW, sh, Theme.accent((highlight ? 0.7f : 0.35f) + 0.3f * t));
            ui.popClip();
        }
    }

    private static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** Short summary of the module's settings: enabled toggles by name, sliders as "value label", modes by name. */
    private String summary(float dt) {
        summaryAge += dt;
        if (summaryAge < 0.4f) return summary;
        summaryAge = 0f;
        java.util.List<String> parts = new ArrayList<>();
        for (Component c : module.settings) {
            if (c instanceof com.OsamaClient.newbridge.UI.components.ToggleButton t) { if (t.enabled) parts.add(t.getLabel()); }
            else if (c instanceof com.OsamaClient.newbridge.UI.components.Slider sl) parts.add(num(sl.getValue()) + " " + sl.getLabel());
            else if (c instanceof com.OsamaClient.newbridge.UI.components.ModeButton mb) parts.add(mb.getMode());
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size() && i < 3; i++) sb.append(i > 0 ? ", " : "").append(parts.get(i));
        if (parts.size() > 3) sb.append(" +").append(parts.size() - 3).append(" more");
        summary = sb.toString();
        return summary;
    }

    private void drawVape(Ui ui, float x, float y, float w, float h, float t, float hv) {
        boolean glass = Theme.glass();
        float rr = glass ? 5f : 2f;
        float px = x + 1f, py = y + 1f, pw = w - 2f, ph = h - 2f;
        int base = glass ? ColorUtil.alpha(0xFFFFFFFF, 0.06f + 0.06f * hv) : ColorUtil.lerp(0xFF22232B, 0xFF2B2C36, hv);
        ui.round(px, py, pw, ph, rr, base);
        if (flashA.value() > 0.002f) ui.round(px, py, pw, ph, rr, ColorUtil.alpha(0xFFFFFFFF, 0.16f * flashA.value()));
        // icon box: filled with the accent while the module is on
        float ib = ph;
        int boxOff = glass ? ColorUtil.alpha(0xFFFFFFFF, 0.08f) : 0xFF33343F;
        ui.round(px, py, ib, ph, rr, ColorUtil.lerp(boxOff, Theme.accent(), t));
        int ic = ColorUtil.lerp(0xFF9A9AA8, 0xFFFFFFFF, t);
        if (!com.OsamaClient.newbridge.UI.gui.render.Icons.draw(ui, com.OsamaClient.newbridge.UI.gui.render.Icons.category(module.category.name()), px + ib * 0.22f, py + ib * 0.22f, ib * 0.56f, ic))
            ui.textCentered(module.name.substring(0, 1), px + ib / 2f, py, ph, Theme.FONT, ic);
        // switch on the right, then the dots that open the settings
        float rx = px + pw - 3f;
        if (!widgets.isEmpty()) {
            for (int i = 0; i < 3; i++) ui.round(rx - 1.2f, py + ph / 2f - 3.4f + i * 3.1f, 1.2f, 1.2f, 0.6f, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hv));
            rx -= 5f;
        }
        float sw = 13f, sh = 6.5f, sy = py + (ph - sh) / 2f;
        ui.round(rx - sw, sy, sw, sh, sh / 2f, ColorUtil.lerp(0xFF3A3B46, Theme.accent(), t));
        ui.round(rx - sw + 0.9f + (sw - sh) * t, sy + 0.9f, sh - 1.8f, sh - 1.8f, (sh - 1.8f) / 2f, 0xFFFFFFFF);
        rx -= sw + 4f;
        if (module.favorite) {
            com.OsamaClient.newbridge.UI.gui.render.Icons.draw(ui, "star", rx - 5.5f, py + (ph - 5.5f) * 0.5f, 5.5f, 0xFFFFC94D);
            rx -= 7.5f;
        }
        var legit = com.OsamaClient.newbridge.Hacks.Client.LegitMode.INSTANCE;
        if (legit != null && legit.enabled && com.OsamaClient.newbridge.Hacks.Client.LegitMode.BLOCKED.contains(module.name)
                || com.OsamaClient.newbridge.Hacks.Client.NormalMode.blocks(module)) {
            com.OsamaClient.newbridge.UI.gui.render.Icons.draw(ui, "lock", rx - 5f, py + (ph - 5f) * 0.5f, 5f, 0xFFFFB454);
            rx -= 7f;
        }
        float tx = px + ib + 5f;
        String right;
        if (binding) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(time * 6.0);
            ui.textRight("Press a key…", rx, py, ph, Theme.FONT_SMALL, ColorUtil.alpha(Theme.accent(), pulse));
            rx -= ui.textWidth("Press a key…", Theme.FONT_SMALL) + 4f;
        } else if (module.key >= 0) {
            right = KeyNames.name(module.key);
            ui.textRight(right, rx, py, ph, Theme.FONT_SMALL, Theme.TEXT_DIM);
            rx -= ui.textWidth(right, Theme.FONT_SMALL) + 4f;
        }
        String name = ui.ellipsize(module.name, Math.max(10f, rx - tx), Theme.FONT);
        ui.text(name, tx, py, ph, Theme.FONT, ColorUtil.lerp(0xFFC9C9D3, 0xFFFFFFFF, t));
        float sx = tx + ui.textWidth(name, Theme.FONT) + 5f;
        String sm = summary(ui.dt);
        if (!sm.isEmpty() && rx - sx > 14f) ui.text(ui.ellipsize(sm, rx - sx, Theme.FONT_SMALL), sx, py, ph, Theme.FONT_SMALL, 0xFF70717E);
    }

    public void toggleFavorite() {
        module.favorite = !module.favorite;
        com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick();
        GuiEvents.fireChanged();
    }

    /** Not drawn this frame (hidden by search, collapsed panel): forget hover state. */
    void clearHover() {
        hovered = false;
        for (SettingWidget sw : widgets) sw.clearHover();
    }

    boolean mouseClicked(Ui ui, int button, int mods) {
        if (hovered) {
            switch (button) {
                case Keys.MOUSE_BUTTON_LEFT -> {
                    if (vape && !widgets.isEmpty() && ui.mouseX >= lastX + lastW - 12f) {
                        setExpanded(!expanded); // the dots at the right edge open the settings
                        com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick();
                        return true;
                    }
                    if ((mods & Keys.MOD_CONTROL) != 0) {
                        toggleFavorite();
                        return true;
                    }
                    flashA.snap(1f);
                    flashA.setTarget(0f);
                    ClickGui.INSTANCE.safeToggle(module);
                    com.OsamaClient.newbridge.UI.gui.util.UiSounds.toggle(module.enabled);
                }
                case Keys.MOUSE_BUTTON_RIGHT -> {
                    if ((mods & Keys.MOD_SHIFT) != 0) {
                        ClickGui.INSTANCE.openMenu(this); // Shift + right click: quick menu
                        com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick();
                    } else {
                        setExpanded(!expanded);
                        com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick();
                    }
                }
                case Keys.MOUSE_BUTTON_MIDDLE -> {
                    if ((mods & Keys.MOD_SHIFT) != 0) {
                        int n = com.OsamaClient.newbridge.UI.components.SettingHistory.resetAll(module);
                        ClickGui.INSTANCE.toast(n == 0 ? module.name + ": already default" : module.name + ": " + n + " setting(s) reset (Ctrl+Z to undo)");
                        if (n > 0) GuiEvents.fireChanged();
                        return true;
                    }
                    ClickGui.INSTANCE.startBinding(this);
                    com.OsamaClient.newbridge.UI.gui.util.UiSounds.click();
                }
                default -> { return false; }
            }
            return true;
        }
        if (expanded && expandA.value() > 0.5f) {
            for (SettingWidget sw : widgets) {
                if (!sw.isHovered()) continue;
                var st = sw.setting();
                boolean undoable = st != null && com.OsamaClient.newbridge.UI.components.SettingHistory.supported(st);
                if (button == Keys.MOUSE_BUTTON_MIDDLE && undoable) {
                    boolean ok = com.OsamaClient.newbridge.UI.components.SettingHistory.reset(st);
                    ClickGui.INSTANCE.toast(ok ? DetailCard.label(st) + " reset to default (Ctrl+Z to undo)" : DetailCard.label(st) + " is already default");
                    if (ok) GuiEvents.fireChanged();
                    return true;
                }
                if (undoable) com.OsamaClient.newbridge.UI.components.SettingHistory.snapshot(st);
                if (sw.mouseClicked(ui, button, mods)) {
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
        for (SettingWidget sw : widgets) {
            if (!sw.isHovered()) continue;
            var st = sw.setting();
            if (st != null && com.OsamaClient.newbridge.UI.components.SettingHistory.supported(st)) com.OsamaClient.newbridge.UI.components.SettingHistory.snapshot(st);
            if (sw.mouseScrolled(ui, amount)) return true;
        }
        return false;
    }

    boolean hoverKey(int key, int mods) {
        if (!expanded) return false;
        for (SettingWidget sw : widgets) if (sw.isHovered() && sw.hoverKey(key, mods)) return true;
        return false;
    }
}
