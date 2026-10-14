package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.setting.TextField;
import com.OsamaClient.newbridge.UI.gui.window.UiWindow;
import com.OsamaClient.newbridge.UI.gui.window.WindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Waypoint-Manager (Xaero-artig): alle Waypoints des Servers als Liste mit Icon, Koordinaten,
 * Dimension und Entfernung; rechts ein Editor (Name, Koordinaten, Farbe, sichtbar).
 * Öffnen: ".wp", ClickGUI-Dock oder Waypoints-Modul -> "Open Manager".
 */
public final class WaypointsWindow extends UiWindow {

    private final ScrollList list = new ScrollList();
    private final TextField search = new TextField();
    private boolean onlyThisDim;

    // Editor
    private WaypointStore.Waypoint editing;      // null + editorOpen = neuer Waypoint
    private boolean editorOpen;
    private final TextField fName = new TextField();
    private final TextField fX = numberField(), fY = numberField(), fZ = numberField();
    private int fColor;
    private boolean fVisible = true;
    private String fDim;

    private WaypointStore.Waypoint confirmDelete;
    private float confirmTime;
    private String status = "";
    private float statusTime;

    private static TextField numberField() {
        TextField t = new TextField();
        t.maxLength = 9;
        t.filter = cp -> (cp >= '0' && cp <= '9') || cp == '-' || cp == '~';
        return t;
    }

    public WaypointsWindow() {
        search.maxLength = 32;
        fName.maxLength = WaypointStore.MAX_NAME;
    }

    @Override protected float prefWidth() { return 380f; }
    @Override protected float prefHeight() { return 240f; }
    @Override protected String title() { return "Waypoints"; }

    private static Waypoints module() { return Waypoints.INSTANCE; }

    private static String key() { return module().serverKey(Minecraft.getInstance()); }

    // ------------------------------------------------------------------ actions (auch für Tests)

    public void startNew() {
        Minecraft mc = Minecraft.getInstance();
        editing = null;
        editorOpen = true;
        BlockPos p = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        set(fName, module().store.uniqueName(key(), "Waypoint"));
        set(fX, "" + p.getX());
        set(fY, "" + p.getY());
        set(fZ, "" + p.getZ());
        fColor = module().newColor();
        fVisible = true;
        fDim = Waypoints.dim(mc);
    }

    public void edit(WaypointStore.Waypoint w) {
        editing = w;
        editorOpen = true;
        set(fName, w.name);
        set(fX, "" + w.x);
        set(fY, "" + w.y);
        set(fZ, "" + w.z);
        fColor = w.color;
        fVisible = !w.hidden;
        fDim = w.dimension;
    }

    /** Übernimmt den Editor-Inhalt. @return Fehlermeldung oder null */
    public String saveEditor() {
        Minecraft mc = Minecraft.getInstance();
        BlockPos p = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        Integer x = parse(fX.text, p.getX()), y = parse(fY.text, p.getY()), z = parse(fZ.text, p.getZ());
        if (x == null || y == null || z == null) return "Invalid coordinates";
        String name = WaypointStore.cleanName(fName.text);
        if (name == null) return "Name is empty";
        WaypointStore store = module().store;
        if (editing == null) {
            if (store.find(key(), name) != null) return "Name already used";
            WaypointStore.Waypoint w = new WaypointStore.Waypoint(name, x, y, z, fDim, fColor, false);
            w.hidden = !fVisible;
            store.put(key(), w);
            editing = w;
        } else {
            if (!name.equals(editing.name) && !store.rename(key(), editing, name)) return "Name already used";
            editing.x = x;
            editing.y = y;
            editing.z = z;
            editing.color = fColor;
            editing.hidden = !fVisible;
            store.save();
        }
        return null;
    }

    /** "~" = eigene Koordinate, "~5" = relativ. */
    public static Integer parse(String s, int self) {
        s = s.trim();
        try {
            if (s.startsWith("~")) return self + (s.length() > 1 ? Integer.parseInt(s.substring(1)) : 0);
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void set(TextField f, String v) {
        f.end();
        f.text = v;
        f.cursor = v.length();
    }

    private void status(String s) {
        status = s;
        statusTime = 3f;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void drawContent(float x, float y, float w, float h) {
        Minecraft mc = Minecraft.getInstance();
        if (module() == null) return;
        statusTime -= ui.dt;
        confirmTime -= ui.dt;
        if (confirmTime <= 0) confirmDelete = null;

        String dimNow = Waypoints.dim(mc);
        float editorW = editorOpen ? 128f : 0f;
        float listW = w - editorW - (editorOpen ? 6f : 0f);

        // ── Kopfzeile (passt sich der Breite an) ──
        float rowH = 11f;
        float addW = 40f, newW = 40f, filterW = 54f;
        float searchW = Math.max(40f, Math.min(90f, listW - addW - newW - filterW - 12f));
        field(search, x, y, searchW, rowH, "Search...");
        if (button(onlyThisDim ? "This dim" : "All dims", x + searchW + 4f, y, filterW, rowH, true, onlyThisDim)) {
            onlyThisDim = !onlyThisDim;
        }
        if (ui.hovered(x + searchW + 4f, y, filterW, rowH)) tooltip("Show only waypoints of the current dimension");
        if (button("+ Here", x + listW - addW - newW - 2f, y, addW, rowH, mc.player != null, false)) {
            startNew();
            String err = saveEditor();
            status(err == null ? "Added " + editing.name : err);
        }
        if (button("+ New", x + listW - newW, y, newW, rowH, true, editorOpen && editing == null)) startNew();

        // ── Liste ──
        float ly = y + rowH + 4f;
        float lh = h - rowH - 4f - 10f;
        List<WaypointStore.Waypoint> all = module().store.sorted(key());
        List<WaypointStore.Waypoint> shown = new ArrayList<>();
        String q = search.text.trim().toLowerCase(Locale.ROOT);
        for (WaypointStore.Waypoint wp : all) {
            if (onlyThisDim && !wp.dimension.equals(dimNow)) continue;
            if (!q.isEmpty() && !wp.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            shown.add(wp);
        }

        float itemH = 15f;
        ui.round(x, ly, listW, lh, 1.5f, 0x40000000);
        float cy = list.begin(x, ly, listW, lh, shown.size() * itemH + 2f) + 1f;
        if (shown.isEmpty()) {
            ui.textCentered(all.isEmpty() ? "No waypoints yet  -  \"+ Here\" adds your position" : "Nothing matches",
                    x + listW / 2f, ly + list.offset, 20f, Theme.FONT_SMALL, Theme.TEXT_DIM);
        }
        for (WaypointStore.Waypoint wp : shown) {
            if (cy + itemH >= ly && cy <= ly + lh) drawRow(wp, x + 1f, cy, listW - 4f, itemH - 1f, dimNow);
            cy += itemH;
        }
        list.end();

        // ── Editor ──
        if (editorOpen) drawEditor(x + listW + 6f, y, editorW, h - 10f);

        // ── Fußzeile ──
        String foot = statusTime > 0 ? status
                : all.size() + " waypoint(s)  -  click a row to edit  -  Shift+click Delete skips confirm";
        ui.text(foot, x, y + h - 9f, 9f, Theme.FONT_SMALL * 0.9f, statusTime > 0 ? Theme.accent() : Theme.TEXT_DIM);
    }

    private void drawRow(WaypointStore.Waypoint wp, float x, float y, float w, float h, String dimNow) {
        Minecraft mc = Minecraft.getInstance();
        boolean selected = editorOpen && editing == wp;
        boolean hover = ui.hovered(x, y, w, h);
        ui.round(x, y, w, h, 1.5f, selected ? Theme.accent(0.22f) : hover ? Theme.ROW_HOVER : Theme.ROW);
        if (selected) ui.outline(x, y, w, h, 1.5f, 1f, Theme.accent(0.8f));

        float s = 9f;
        icon(x + 3f, y + (h - s) / 2f, s, wp.hidden ? 0xFF555555 : wp.color, wp.initial());
        int textCol = wp.hidden ? Theme.TEXT_DIM : Theme.TEXT;

        // Spalten an die verfügbare Breite anpassen (Buttons rechts brauchen 4 * 26)
        float textW = w - 15f - 4f * 26f - 4f;
        float nameW = Math.max(36f, Math.min(80f, textW * 0.42f));
        ui.text(ui.ellipsize(wp.name, nameW, Theme.FONT_SMALL), x + 15f, y, h, Theme.FONT_SMALL, textCol);
        float small = Theme.FONT_SMALL * 0.9f;
        String dist = "";
        if (mc.player != null && wp.dimension.equals(dimNow)) {
            double d = Math.sqrt(mc.player.distanceToSqr(wp.x + 0.5, wp.y + 0.5, wp.z + 0.5));
            dist = d >= 1000 ? String.format(Locale.ROOT, "%.1fkm", d / 1000) : String.format(Locale.ROOT, "%.0fm", d);
        }
        String info = WaypointStore.dimShort(wp.dimension) + (dist.isEmpty() ? "" : " " + dist);
        float infoW = ui.textWidth(info, small);
        float coordsX = x + 15f + nameW + 4f;
        float coordsW = textW - nameW - 4f - infoW - 4f;
        if (coordsW < 30f) {          // zu schmal: Dimension/Entfernung weglassen
            coordsW = textW - nameW - 4f;
            info = "";
        }
        ui.text(ui.ellipsize(wp.x + " " + wp.y + " " + wp.z, coordsW, small), coordsX, y, h, small, Theme.TEXT_DIM);
        if (!info.isEmpty()) ui.textRight(info, x + 15f + textW, y, h, small, Theme.TEXT_DIM);

        // Buttons rechts
        float bw = 24f, bh = h - 4f, by = y + 2f;
        float bx = x + w - (bw + 2f) * 4f;
        if (button(wp.hidden ? "Show" : "Hide", bx, by, bw, bh, true, !wp.hidden)) {
            wp.hidden = !wp.hidden;
            module().store.save();
        }
        if (button("Go", bx + bw + 2f, by, bw, bh, mc.player != null && wp.dimension.equals(dimNow), false)) {
            if (module().goTo(wp)) WindowManager.INSTANCE.close();
        }
        if (button("Edit", bx + 2 * (bw + 2f), by, bw, bh, true, selected)) edit(wp);
        boolean confirming = confirmDelete == wp;
        if (button(confirming ? "Sure?" : "Del", bx + 3 * (bw + 2f), by, bw, bh, true, confirming)) {
            if (confirming || shiftClick()) {
                module().store.remove(key(), wp);
                if (editing == wp) editorOpen = false;
                status("Deleted " + wp.name);
                confirmDelete = null;
            } else {
                confirmDelete = wp;
                confirmTime = 2.5f;
            }
        }
        // Klick auf die Zeile selbst = bearbeiten
        if (clicked(x, y, w, h, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            consumeClick();
            edit(wp);
        }
        if (hover && wp.death) tooltip("Created automatically when you died");
    }

    private void drawEditor(float x, float y, float w, float h) {
        ui.round(x, y, w, h, 1.5f, 0x40000000);
        float px = x + 4f, pw = w - 8f;
        float cy = y + 3f;
        label(editing == null ? "New waypoint" : "Edit waypoint", px, cy, 9f, Theme.TEXT);
        cy += 11f;
        field(fName, px, cy, pw, 11f, "Name");
        cy += 15f;

        label("X", px, cy, 11f, Theme.TEXT_DIM);
        float fw = (pw - 24f) / 3f;
        field(fX, px + 6f, cy, fw, 11f, "x");
        label("Y", px + 8f + fw, cy, 11f, Theme.TEXT_DIM);
        field(fY, px + 14f + fw, cy, fw, 11f, "y");
        label("Z", px + 16f + 2 * fw, cy, 11f, Theme.TEXT_DIM);
        field(fZ, px + 22f + 2 * fw, cy, fw, 11f, "z");
        cy += 14f;
        if (button("Use my position", px, cy, pw, 10f, Minecraft.getInstance().player != null, false)) {
            BlockPos p = Minecraft.getInstance().player.blockPosition();
            set(fX, "" + p.getX());
            set(fY, "" + p.getY());
            set(fZ, "" + p.getZ());
            fDim = Waypoints.dim(Minecraft.getInstance());
        }
        cy += 14f;

        label("Color", px, cy, 9f, Theme.TEXT_DIM);
        cy += 10f;
        float sw = (pw - 5 * 2f) / 6f;
        // zwei Reihen à 6 Farben
        int c1 = paletteRow(px, cy, sw, 0);
        int c2 = paletteRow(px, cy + sw + 2f, sw, 6);
        if (c1 != -1) fColor = c1;
        if (c2 != -1) fColor = c2;
        cy += 2 * sw + 6f;

        // Vorschau
        icon(px, cy, 11f, fVisible ? fColor : 0xFF555555, fName.text.isEmpty() ? "?" : fName.text.substring(0, 1).toUpperCase(Locale.ROOT));
        label(WaypointStore.dimShort(fDim), px + 15f, cy, 11f, Theme.TEXT_DIM);
        fVisible = checkbox("Visible", px + 40f, cy + 1.5f, fVisible);
        cy += 16f;

        float bw = (pw - 2f) / 2f;
        if (button("Save", px, cy, bw, 12f, true, false)) {
            String err = saveEditor();
            status(err == null ? "Saved " + editing.name : err);
        }
        if (button("Cancel", px + bw + 2f, cy, bw, 12f, true, false)) editorOpen = false;
        cy += 15f;
        if (editing != null && button("Go there", px, cy, pw, 12f, editing.dimension.equals(Waypoints.dim(Minecraft.getInstance())), false)) {
            if (module().goTo(editing)) WindowManager.INSTANCE.close();
        }
    }

    /** Eine Palettenreihe (6 Farben ab {@code from}); gibt die geklickte Farbe oder -1 zurück. */
    private int paletteRow(float x, float y, float size, int from) {
        int picked = -1;
        for (int i = 0; i < 6; i++) {
            int c = PALETTE[from + i];
            float px = x + i * (size + 2f);
            boolean sel = (c & 0xFFFFFF) == (fColor & 0xFFFFFF);
            ui.rect(px - 0.6f, y - 0.6f, size + 1.2f, size + 1.2f, sel ? 0xFFFFFFFF : 0x80000000);
            ui.rect(px, y, size, size, c);
            if (clicked(px, y, size, size, GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                consumeClick();
                picked = c;
            }
        }
        return picked;
    }

    @Override
    protected boolean onKey(int key, int mods) {
        if (key == GLFW.GLFW_KEY_N && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            startNew();
            return true;
        }
        return false;
    }

    @Override
    protected void onFieldCommitted(TextField field, int key) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (field == fName || field == fX || field == fY || field == fZ) {
                String err = saveEditor();
                status(err == null ? "Saved " + editing.name : err);
            }
        }
    }

    // für Tests
    public TextField nameField() { return fName; }
    public void setColor(int c) { fColor = c; }
    public void setVisible(boolean v) { fVisible = v; }
    public void setCoords(String x, String y, String z) { set(fX, x); set(fY, y); set(fZ, z); }
}
