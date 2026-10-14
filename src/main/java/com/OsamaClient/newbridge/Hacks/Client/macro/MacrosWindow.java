package com.OsamaClient.newbridge.Hacks.Client.macro;

import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.setting.TextField;
import com.OsamaClient.newbridge.UI.gui.util.KeyNames;
import com.OsamaClient.newbridge.UI.gui.window.UiWindow;
import com.OsamaClient.newbridge.UI.gui.input.Keys;

import java.util.ArrayList;
import java.util.List;

/** Macro-Manager: Liste oben, Editor unten (Name, Taste, Aktionen, Verzögerung). */
public final class MacrosWindow extends UiWindow {

    private final ScrollList list = new ScrollList();
    private MacroStore.Macro selected;
    private final TextField fName = new TextField();
    private final TextField fActions = new TextField();
    private boolean binding;
    private MacroStore.Macro confirm;
    private float confirmTime;
    private String status = "";
    private float statusTime;

    public MacrosWindow() {
        fName.maxLength = 32;
        fActions.maxLength = 512;
    }

    @Override protected float prefWidth() { return 340f; }
    @Override protected float prefHeight() { return 220f; }
    @Override protected String title() { return "Macros"; }

    private static Macros m() { return Macros.INSTANCE; }

    public void select(MacroStore.Macro mac) {
        selected = mac;
        binding = false;
        set(fName, mac.name);
        set(fActions, mac.actions);
    }

    private static void set(TextField f, String v) {
        f.end();
        f.text = v == null ? "" : v;
        f.cursor = f.text.length();
    }

    /** Übernimmt Name/Aktionen in das ausgewählte Macro. */
    public void apply() {
        if (selected == null) return;
        String n = fName.text.trim();
        if (!n.isEmpty() && !n.equalsIgnoreCase(selected.name)) {
            if (m().store.find(n) == null) selected.name = n;
            else status("Name already used");
        }
        selected.actions = fActions.text;
        m().store.save();
    }

    private void status(String s) { status = s; statusTime = 3f; }

    @Override
    protected void drawContent(float x, float y, float w, float h) {
        if (m() == null) return;
        statusTime -= ui.dt;
        confirmTime -= ui.dt;
        if (confirmTime <= 0) confirm = null;

        if (button("+ New macro", x, y, 64f, 11f, true, false)) {
            select(m().store.add("Macro"));
            status("Created " + selected.name);
        }
        ui.text("Actions: text ; /command ; .client cmd ; wait 20", x + 70f, y, 11f, Theme.FONT_SMALL * 0.9f, Theme.TEXT_DIM);

        // ── Liste ──
        List<MacroStore.Macro> macros = new ArrayList<>(m().store.list());
        float ly = y + 15f, lh = selected != null ? h * 0.45f : h - 25f, itemH = 14f;
        ui.round(x, ly, w, lh, 1.5f, 0x40000000);
        float cy = list.begin(x, ly, w, lh, macros.size() * itemH + 2f) + 1f;
        if (macros.isEmpty()) ui.textCentered("No macros yet", x + w / 2f, ly, 20f, Theme.FONT_SMALL, Theme.TEXT_DIM);
        for (MacroStore.Macro mac : macros) {
            if (cy + itemH >= ly && cy <= ly + lh) drawRow(mac, x + 1f, cy, w - 4f, itemH - 1f);
            cy += itemH;
        }
        list.end();

        // ── Editor ──
        if (selected != null && m().store.list().contains(selected)) {
            float ey = ly + lh + 5f;
            label("Name", x, ey, 11f, Theme.TEXT_DIM);
            field(fName, x + 30f, ey, 100f, 11f, "name");
            String keyLabel = binding ? "Press a key..." : selected.key < 0 ? "Bind key" : "Key: " + KeyNames.name(selected.key);
            if (button(keyLabel, x + 136f, ey, 70f, 11f, true, binding)) binding = !binding;
            label("Delay", x + 212f, ey, 11f, Theme.TEXT_DIM);
            if (button("-", x + 236f, ey, 11f, 11f, selected.delay > 0, false)) { selected.delay--; m().store.save(); }
            ui.textCentered(selected.delay + "t", x + 256f, ey, 11f, Theme.FONT_SMALL, Theme.TEXT);
            if (button("+", x + 266f, ey, 11f, 11f, selected.delay < 100, false)) { selected.delay++; m().store.save(); }
            ey += 15f;
            label("Do", x, ey, 11f, Theme.TEXT_DIM);
            field(fActions, x + 30f, ey, w - 30f, 11f, "hello everyone ; wait 40 ; /home ; .wp goto Base");
            ey += 15f;
            int steps = MacroRunner.parse(fActions.text).size();
            label(steps + " step(s)", x + 30f, ey, 11f, Theme.TEXT_DIM);
            if (button("Save", x + w - 96f, ey, 46f, 12f, true, false)) { apply(); status("Saved " + selected.name); }
            if (button("Test run", x + w - 48f, ey, 48f, 12f, steps > 0, false)) {
                apply();
                m().run(selected);
                status("Running " + selected.name);
            }
        }

        String foot = statusTime > 0 ? status : macros.size() + " macro(s)  -  keys only fire while no screen is open";
        ui.text(foot, x, y + h - 9f, 9f, Theme.FONT_SMALL * 0.9f, statusTime > 0 ? Theme.accent() : Theme.TEXT_DIM);
    }

    private void drawRow(MacroStore.Macro mac, float x, float y, float w, float h) {
        boolean sel = mac == selected;
        boolean hover = ui.hovered(x, y, w, h);
        ui.round(x, y, w, h, 1.5f, sel ? Theme.accent(0.22f) : hover ? Theme.ROW_HOVER : Theme.ROW);
        if (sel) ui.outline(x, y, w, h, 1.5f, 1f, Theme.accent(0.8f));
        boolean en = checkbox("", x + 3f, y + (h - 8f) / 2f, mac.enabled);
        if (en != mac.enabled) { mac.enabled = en; m().store.save(); }
        ui.text(ui.ellipsize(mac.name, 70f, Theme.FONT_SMALL), x + 16f, y, h, Theme.FONT_SMALL, mac.enabled ? Theme.TEXT : Theme.TEXT_DIM);
        String key = mac.key < 0 ? "-" : KeyNames.name(mac.key);
        float kw = ui.textWidth(key, Theme.FONT_SMALL * 0.85f) + 6f;
        ui.round(x + 90f, y + 2.5f, kw, h - 5f, 1.5f, 0x30FFFFFF);
        ui.textCentered(key, x + 90f + kw / 2f, y, h, Theme.FONT_SMALL * 0.85f, Theme.TEXT);
        ui.text(ui.ellipsize(mac.actions, w - 90f - kw - 70f, Theme.FONT_SMALL * 0.85f), x + 96f + kw, y, h, Theme.FONT_SMALL * 0.85f, Theme.TEXT_DIM);
        float bw = 30f, by = y + 2f, bh = h - 4f;
        if (button("Run", x + w - 2 * (bw + 2f), by, bw, bh, !mac.actions.isBlank(), false)) {
            m().run(mac);
            status("Running " + mac.name);
        }
        boolean c = confirm == mac;
        if (button(c ? "Sure?" : "Del", x + w - bw - 2f, by, bw, bh, true, c)) {
            if (c || shiftClick()) {
                m().store.remove(mac);
                if (selected == mac) selected = null;
                confirm = null;
            } else { confirm = mac; confirmTime = 2.5f; }
        }
        if (clicked(x, y, w, h, Keys.MOUSE_BUTTON_LEFT)) {
            consumeClick();
            if (selected != null && selected != mac) apply();
            select(mac);
        }
    }

    @Override
    protected boolean onKey(int key, int mods) {
        if (binding && selected != null) {
            selected.key = (key == Keys.KEY_ESCAPE || key == Keys.KEY_DELETE || key == Keys.KEY_BACKSPACE) ? -1 : key;
            binding = false;
            m().store.save();
            status(selected.key < 0 ? "Key removed" : "Bound to " + KeyNames.name(selected.key));
            return true;
        }
        return false;
    }

    @Override
    protected void onFieldCommitted(TextField field, int key) {
        apply();
    }

    @Override
    public void onClose() {
        apply();
        super.onClose();
    }
}
