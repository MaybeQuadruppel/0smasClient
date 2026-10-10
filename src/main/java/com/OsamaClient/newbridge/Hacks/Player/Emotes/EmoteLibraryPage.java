package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteEditor.EmoteEditor;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Emote library page, drawn by {@link EmoteWheel} (same screen, so no extra render hook is needed).
 * Lists every emote: toggle whether it sits on the wheel, reorder the wheel, play, edit and delete custom ones.
 */
final class EmoteLibraryPage {

    private static final float ROW_H = 26f;

    private final EmoteWheel owner;
    private float scroll, targetScroll, maxScroll;
    private Runnable hot;
    private String confirmDelete, pendingConfirm;
    private String status = "";
    private float statusTime;

    EmoteLibraryPage(EmoteWheel owner) {
        this.owner = owner;
    }

    void reset() {
        scroll = targetScroll = 0f;
        confirmDelete = pendingConfirm = null;
        hot = null;
        status = "";
        statusTime = 0f;
    }

    private void flash(String text) {
        status = text;
        statusTime = 3.5f;
    }

    void draw(Ui ui, float dt) {
        hot = null;
        statusTime = Math.max(0f, statusTime - dt);
        scroll += (targetScroll - scroll) * Math.min(1f, dt * 18f);

        float w = ui.width(), h = ui.height();
        float pw = Math.min(560f, w - 20f), ph = Math.min(h - 40f, 480f);
        float px = (w - pw) / 2f, py = (h - ph) / 2f;

        ui.rect(0, 0, w, h, 0x88000000);
        ui.round(px, py, pw, ph, 6f, Theme.PANEL_BG);
        ui.outline(px, py, pw, ph, 6f, 1f, Theme.SETTING_BLOCK_BORDER);
        ui.text("Emote Library", px + 14f, py + 8f, 16f, 11f, Theme.TEXT);

        int size = EmoteLibrary.wheelSize();
        boolean full = size >= EmoteLibrary.WHEEL_MAX;
        ui.textRight("Wheel " + size + "/" + EmoteLibrary.WHEEL_MAX, px + pw - 14f, py + 8f, 16f,
                Theme.FONT_SMALL, full ? Theme.accent() : Theme.TEXT_DIM);

        float lx = px + 10f, ly = py + 32f, lw = pw - 20f, lh = ph - 32f - 48f;
        List<String> names = EmoteLibrary.orderedNames();
        maxScroll = Math.max(0f, names.size() * ROW_H - lh);
        targetScroll = Math.max(0f, Math.min(targetScroll, maxScroll));

        ui.pushClip(lx, ly, lw, lh);
        for (int i = 0; i < names.size(); i++) {
            float y = ly + i * ROW_H - scroll;
            if (y + ROW_H < ly || y > ly + lh) continue;
            drawRow(ui, names.get(i), lx, y, lw);
        }
        ui.popClip();

        if (maxScroll > 0.5f) {
            float barH = Math.max(14f, lh * lh / (lh + maxScroll));
            float barY = ly + (lh - barH) * (scroll / maxScroll);
            ui.round(lx + lw - 2f, barY, 2f, barH, 1f, Theme.accent(0.6f));
        }

        // bottom bar
        float by = py + ph - 36f;
        btn(ui, px + 14f, by, 110f, 24f, "New Emote", false, true, EmoteEditor.INSTANCE::openNew);
        btn(ui, px + pw - 14f - 80f, by, 80f, 24f, "Back", false, true, owner::showWheel);
        String msg = statusTime > 0f ? status
                : "Emotes on the wheel come first. The rest stays in the library.";
        float msgX = px + 14f + 110f + 10f, msgW = pw - 28f - 110f - 80f - 20f;
        if (msgW > 40f) {
            ui.text(ui.ellipsize(msg, msgW, Theme.FONT_SMALL), msgX, by, 24f, Theme.FONT_SMALL,
                    statusTime > 0f ? Theme.accent() : Theme.TEXT_DIM);
        }
    }

    private void drawRow(Ui ui, String name, float x, float y, float w) {
        boolean builtin = EmoteLibrary.isBuiltin(name);
        boolean inW = EmoteLibrary.inWheel(name);
        float rh = ROW_H - 3f;
        boolean rowHover = ui.hovered(x, y, w, rh);

        ui.round(x, y, w, rh, 4f, rowHover ? Theme.ROW_HOVER : Theme.ROW);
        if (inW) ui.round(x, y + 3f, 2f, rh - 6f, 1f, Theme.accent());

        float rx = x + w - 6f;
        float by = y + 3f, bh = rh - 6f;

        if (!builtin) {
            boolean sure = name.equals(confirmDelete);
            rx -= 44f;
            btn(ui, rx, by, 44f, bh, sure ? "Sure?" : "Delete", sure, true, () -> {
                if (name.equals(pendingConfirm)) {
                    EmoteLibrary.deleteCustom(name);
                    confirmDelete = null;
                    flash("Deleted '" + name + "'");
                } else {
                    confirmDelete = name;
                }
            });
            rx -= 4f + 36f;
            btn(ui, rx, by, 36f, bh, "Edit", false, true, () -> {
                CustomEmote c = EmoteLibrary.customByName(name);
                if (c != null) EmoteEditor.INSTANCE.openEdit(c);
            });
            rx -= 4f;
        }

        if (inW) {
            int idx = EmoteLibrary.wheelIndex(name), n = EmoteLibrary.wheelSize();
            rx -= 22f;
            btn(ui, rx, by, 22f, bh, "Dn", false, idx < n - 1, () -> EmoteLibrary.moveInWheel(name, 1));
            rx -= 24f;
            btn(ui, rx, by, 22f, bh, "Up", false, idx > 0, () -> EmoteLibrary.moveInWheel(name, -1));
            rx -= 4f;
        }

        rx -= 60f;
        btn(ui, rx, by, 60f, bh, inW ? "On Wheel" : "Add", inW, true, () -> {
            if (!EmoteLibrary.toggleWheel(name)) {
                flash("Wheel is full (" + EmoteLibrary.WHEEL_MAX + ") - remove an emote first");
            }
        });
        rx -= 4f + 36f;
        btn(ui, rx, by, 36f, bh, "Play", false, true, () -> {
            EmoteAnimation a = EmoteLibrary.find(name);
            if (a != null) {
                EmotePlayer.play(a);
                owner.close();
            }
        });

        String shown = ui.ellipsize(name, Math.max(20f, rx - (x + 12f) - 50f), Theme.FONT);
        ui.text(shown, x + 10f, y, rh, Theme.FONT, inW ? Theme.TEXT : Theme.TEXT_DIM);
        float tagX = x + 10f + ui.textWidth(shown, Theme.FONT) + 6f;
        if (tagX + 34f < rx) ui.text(builtin ? "built-in" : "custom", tagX, y, rh, 5.5f, Theme.TEXT_DIM);
    }

    private void btn(Ui ui, float x, float y, float w, float h, String label, boolean on, boolean enabled, Runnable action) {
        boolean hv = enabled && ui.hovered(x, y, w, h);
        int bg = on ? Theme.accent(0.30f) : hv ? Theme.ROW_HOVER : 0x22FFFFFF;
        ui.round(x, y, w, h, 3f, bg);
        ui.outline(x, y, w, h, 3f, 1f, on || hv ? Theme.accent(hv ? 1f : 0.7f) : Theme.SETTING_BLOCK_BORDER);
        ui.textCentered(label, x + w / 2f, y, h, Theme.FONT_SMALL, enabled ? Theme.TEXT : Theme.TEXT_DIM);
        if (hv) hot = action;
    }

    void mouseClicked() {
        pendingConfirm = confirmDelete;
        confirmDelete = null;
        if (hot != null) hot.run();
    }

    void mouseScrolled(double amount) {
        targetScroll = Math.max(0f, Math.min(maxScroll, targetScroll - (float) amount * ROW_H * 2f));
    }

    boolean keyPressed(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            owner.close();
            return true;
        }
        return false;
    }
}