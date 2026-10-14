package com.OsamaClient.newbridge.Hacks.Client;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.setting.TextField;
import com.OsamaClient.newbridge.UI.gui.window.UiWindow;
import com.OsamaClient.newbridge.config.Config;
import net.minecraft.client.Minecraft;
import com.OsamaClient.newbridge.UI.gui.input.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Profil-Manager: alle Profile als Liste (aktiv / Server-Profil markiert, Alter, Anzahl Module),
 * rechts Vorschau mit Unterschied zum aktuellen Setup (+ wird eingeschaltet, - wird ausgeschaltet).
 */
public final class ProfilesWindow extends UiWindow {

    private final ScrollList list = new ScrollList();
    private final ScrollList detailList = new ScrollList();
    private final TextField newName = new TextField();
    private final TextField renameField = new TextField();
    private String selected;
    private List<String> selectedModules = List.of();
    private String confirmDelete;
    private float confirmTime;
    private String status = "";
    private float statusTime;
    private List<String> cache;
    private float cacheAge = 99f;

    public ProfilesWindow() {
        newName.maxLength = 48;
        renameField.maxLength = 48;
        newName.filter = cp -> Character.isLetterOrDigit(cp) || cp == '-' || cp == '_' || cp == '.';
        renameField.filter = newName.filter;
    }

    @Override protected float prefWidth() { return 340f; }
    @Override protected float prefHeight() { return 230f; }
    @Override protected String title() { return "Profiles"; }

    private static Profiles p() { return Profiles.INSTANCE; }

    private List<String> profiles() {
        if (cache == null || cacheAge > 1f) {
            cache = Config.listProfiles();
            cacheAge = 0;
        }
        return cache;
    }

    private void refresh() {
        cache = null;
        if (selected != null) select(selected);
    }

    public void select(String name) {
        selected = name;
        selectedModules = Config.profileEnabledModules(name);
        renameField.end();
        renameField.text = name;
        renameField.cursor = name.length();
    }

    private void status(String s) {
        status = s;
        statusTime = 3f;
    }

    @Override
    protected void drawContent(float x, float y, float w, float h) {
        if (p() == null) return;
        cacheAge += ui.dt;
        statusTime -= ui.dt;
        confirmTime -= ui.dt;
        if (confirmTime <= 0) confirmDelete = null;

        float leftW = w * 0.56f;
        float rightX = x + leftW + 6f, rightW = w - leftW - 6f;
        float footH = 30f;

        // ── Liste ──
        List<String> names = profiles();
        float itemH = 15f;
        float lh = h - footH;
        ui.round(x, y, leftW, lh, 1.5f, 0x40000000);
        float cy = list.begin(x, y, leftW, lh, names.size() * itemH + 2f) + 1f;
        if (names.isEmpty()) {
            ui.textCentered("No profiles yet - type a name below and click Save", x + leftW / 2f, y, 20f, Theme.FONT_SMALL, Theme.TEXT_DIM);
        }
        for (String n : new ArrayList<>(names)) {
            if (cy + itemH >= y && cy <= y + lh) drawRow(n, x + 1f, cy, leftW - 4f, itemH - 1f);
            cy += itemH;
        }
        list.end();

        // ── Details ──
        ui.round(rightX, y, rightW, lh, 1.5f, 0x40000000);
        if (selected != null && names.contains(selected)) drawDetails(rightX + 4f, y + 3f, rightW - 8f, lh - 6f);
        else ui.textCentered("Select a profile", rightX + rightW / 2f, y, 20f, Theme.FONT_SMALL, Theme.TEXT_DIM);

        // ── Fuß: neues Profil + Server ──
        float fy = y + lh + 4f;
        field(newName, x, fy, 110f, 11f, "new profile name");
        if (button("Save current", x + 114f, fy, 60f, 11f, !newName.text.isBlank(), false)) {
            p().save(newName.text);
            select(Config.sanitizeProfileName(newName.text));
            status("Saved " + newName.text);
            newName.text = "";
            refresh();
        }
        String key = Profiles.serverKey(Minecraft.getInstance());
        if (button("Bind to this server", x + 178f, fy, 80f, 11f, key != null, false)) {
            p().bindToServer();
            status("Saved as server_" + key);
            refresh();
        }
        if (key != null) tooltipIfHovered(x + 178f, fy, 80f, 11f, "Saves the current setup as server_" + key + " (auto-loaded on join)");
        boolean auto = checkbox("Auto-load per server", x + 262f, fy + 1.5f, p().perServer());
        if (auto != p().perServer()) p().setPerServer(auto);

        String foot = statusTime > 0 ? status
                : "Active: " + (p().activeProfile() == null ? "-" : p().activeProfile())
                + "   -   " + names.size() + " profile(s)   -   + = gets enabled, - = gets disabled when loading";
        ui.text(foot, x, fy + 15f, 9f, Theme.FONT_SMALL * 0.9f, statusTime > 0 ? Theme.accent() : Theme.TEXT_DIM);
    }

    private void tooltipIfHovered(float x, float y, float w, float h, String text) {
        if (ui.hovered(x, y, w, h)) tooltip(text);
    }

    private void drawRow(String n, float x, float y, float w, float h) {
        boolean sel = n.equals(selected);
        boolean active = n.equals(p().activeProfile());
        boolean hover = ui.hovered(x, y, w, h);
        ui.round(x, y, w, h, 1.5f, sel ? Theme.accent(0.22f) : hover ? Theme.ROW_HOVER : Theme.ROW);
        if (sel) ui.outline(x, y, w, h, 1.5f, 1f, Theme.accent(0.8f));
        if (active) ui.round(x + 2f, y + 3f, 2f, h - 6f, 1f, Theme.accent());
        float tx = x + 7f;
        tx = ui.text(ui.ellipsize(n, 70f, Theme.FONT_SMALL), tx, y, h, Theme.FONT_SMALL, Theme.TEXT) + 4f;
        if (n.startsWith("server_")) {
            float bw = ui.textWidth("server", Theme.FONT_SMALL * 0.8f) + 4f;
            ui.round(tx, y + 3.5f, bw, h - 7f, 1.5f, 0x3038BDF8);
            ui.textCentered("server", tx + bw / 2f, y, h, Theme.FONT_SMALL * 0.8f, 0xFF7DD3FC);
        }
        ui.text(age(Config.profileModified(n)), x + w - 90f, y, h, Theme.FONT_SMALL * 0.85f, Theme.TEXT_DIM);

        float bw = 24f, by = y + 2f, bh = h - 4f;
        float bx = x + w - (bw + 2f) * 2f;
        if (button("Load", bx, by, bw, bh, true, active)) {
            p().load(n);
            select(n);
            status("Loaded " + n);
        }
        boolean confirming = n.equals(confirmDelete);
        if (button(confirming ? "Sure?" : "Del", bx + bw + 2f, by, bw, bh, true, confirming)) {
            if (confirming || shiftClick()) {
                p().delete(n);
                if (n.equals(selected)) selected = null;
                confirmDelete = null;
                status("Deleted " + n);
                refresh();
            } else {
                confirmDelete = n;
                confirmTime = 2.5f;
            }
        }
        if (clicked(x, y, w, h, Keys.MOUSE_BUTTON_LEFT)) {
            consumeClick();
            select(n);
        }
    }

    private void drawDetails(float x, float y, float w, float h) {
        label("Name", x, y, 9f, Theme.TEXT_DIM);
        field(renameField, x + 24f, y, w - 58f, 10f, selected);
        if (button("Rename", x + w - 32f, y, 32f, 10f, !renameField.text.isBlank() && !renameField.text.equals(selected), false)) {
            String to = renameField.text;
            if (p().rename(selected, to)) {
                select(Config.sanitizeProfileName(to));
                refresh();
            } else status("Name already used");
        }
        float cy = y + 14f;
        if (button("Overwrite with current setup", x, cy, w, 11f, true, false)) {
            p().save(selected);
            select(selected);
            status("Overwrote " + selected);
        }
        cy += 15f;

        // Unterschied zum aktuellen Zustand
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Module m : ModuleManager.modules) {
            if (m instanceof Profiles || m instanceof com.OsamaClient.newbridge.UI.gui.ClickGuiModule) continue;
            boolean inProfile = containsIgnoreCase(selectedModules, m.name);
            if (inProfile && !m.enabled) { lines.add("+ " + m.name); colors.add(0xFF4ADE80); }
            else if (!inProfile && m.enabled) { lines.add("- " + m.name); colors.add(0xFFFF6B6B); }
        }
        int diffCount = lines.size();
        for (String n : selectedModules) {
            Module m = ModuleManager.getModuleByName(n);
            if (m != null && m.enabled) { lines.add("  " + n); colors.add(Theme.TEXT_DIM); }
        }
        label(selectedModules.size() + " module(s) on  -  " + (diffCount == 0 ? "matches current setup" : diffCount + " change(s)"),
                x, cy, 9f, Theme.TEXT);
        cy += 11f;
        float lh = y + h - cy;
        float ly = detailList.begin(x, cy, w, lh, lines.size() * 9f + 2f);
        for (int i = 0; i < lines.size(); i++) {
            ui.text(lines.get(i), x + 2f, ly + i * 9f, 9f, Theme.FONT_SMALL * 0.9f, colors.get(i));
        }
        detailList.end();
    }

    private static boolean containsIgnoreCase(List<String> list, String s) {
        for (String x : list) if (x.equalsIgnoreCase(s)) return true;
        return false;
    }

    private static String age(long millis) {
        if (millis <= 0) return "";
        long s = (System.currentTimeMillis() - millis) / 1000;
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " d ago";
    }

    @Override
    protected void onFieldCommitted(TextField field, int key) {
        if (key != Keys.KEY_ENTER && key != Keys.KEY_KP_ENTER) return;
        if (field == newName && !newName.text.isBlank()) {
            p().save(newName.text);
            select(Config.sanitizeProfileName(newName.text));
            newName.text = "";
            refresh();
        } else if (field == renameField && selected != null && !renameField.text.equals(selected)) {
            String to = renameField.text;
            if (p().rename(selected, to)) {
                select(Config.sanitizeProfileName(to));
                refresh();
            }
        }
    }

    @SuppressWarnings("unused")
    private static String lower(String s) { return s.toLowerCase(Locale.ROOT); }
}
