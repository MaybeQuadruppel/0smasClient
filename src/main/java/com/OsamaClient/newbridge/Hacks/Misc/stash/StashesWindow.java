package com.OsamaClient.newbridge.Hacks.Misc.stash;

import com.OsamaClient.newbridge.Hacks.Misc.Waypoints;
import com.OsamaClient.newbridge.Hacks.Movement.GoTo;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.window.UiWindow;
import com.OsamaClient.newbridge.UI.gui.window.WindowManager;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/** Liste aller gefundenen Stashes mit Wert, Inhalt, Entfernung; Waypoint / Go / Löschen. */
public final class StashesWindow extends UiWindow {

    private final ScrollList list = new ScrollList();
    private boolean byDistance;
    private StashStore.Stash confirm;
    private float confirmTime;
    private String status = "";
    private float statusTime;

    @Override protected float prefWidth() { return 340f; }
    @Override protected float prefHeight() { return 210f; }
    @Override protected String title() { return "Stashes"; }

    @Override
    protected void drawContent(float x, float y, float w, float h) {
        StashLogger m = StashLogger.INSTANCE;
        if (m == null) return;
        Minecraft mc = Minecraft.getInstance();
        statusTime -= ui.dt;
        confirmTime -= ui.dt;
        if (confirmTime <= 0) confirm = null;
        String key = m.key(mc);

        if (button(byDistance ? "Sort: distance" : "Sort: value", x, y, 70f, 11f, true, byDistance)) byDistance = !byDistance;
        if (button(m.enabled ? "Logging: on" : "Logging: off", x + 74f, y, 62f, 11f, true, m.enabled)) m.toggle();
        boolean clearing = confirm != null && confirm.dimension == null;
        if (button(clearing ? "Sure?" : "Clear all", x + w - 50f, y, 50f, 11f, !m.store.list(key).isEmpty(), clearing)) {
            if (clearing) { m.store.clear(key); confirm = null; status("Cleared"); }
            else { confirm = new StashStore.Stash(); confirmTime = 2.5f; }
        }

        List<StashStore.Stash> stashes = m.store.sorted(key);
        if (byDistance && mc.player != null) {
            stashes.sort((a, b) -> Double.compare(mc.player.distanceToSqr(a.x, a.y, a.z), mc.player.distanceToSqr(b.x, b.y, b.z)));
        }
        float ly = y + 15f, lh = h - 15f - 10f, itemH = 15f;
        ui.round(x, ly, w, lh, 1.5f, 0x40000000);
        float cy = list.begin(x, ly, w, lh, stashes.size() * itemH + 2f) + 1f;
        if (stashes.isEmpty()) {
            ui.textCentered(m.enabled ? "Nothing found yet - just fly/walk around" : "Logging is off",
                    x + w / 2f, ly, 20f, Theme.FONT_SMALL, Theme.TEXT_DIM);
        }
        String dimNow = Waypoints.dim(mc);
        for (StashStore.Stash s : stashes) {
            if (cy + itemH >= ly && cy <= ly + lh) drawRow(m, s, x + 1f, cy, w - 4f, itemH - 1f, key, dimNow);
            cy += itemH;
        }
        list.end();

        String foot = statusTime > 0 ? status : stashes.size() + " stash(es)  -  shulker boxes count x3";
        ui.text(foot, x, y + h - 9f, 9f, Theme.FONT_SMALL * 0.9f, statusTime > 0 ? Theme.accent() : Theme.TEXT_DIM);
    }

    private void drawRow(StashLogger m, StashStore.Stash s, float x, float y, float w, float h, String key, String dimNow) {
        Minecraft mc = Minecraft.getInstance();
        boolean hover = ui.hovered(x, y, w, h);
        ui.round(x, y, w, h, 1.5f, hover ? Theme.ROW_HOVER : Theme.ROW);
        int col = s.weight() >= 40 ? 0xFFFF4D4D : s.weight() >= 20 ? 0xFFFF9F43 : 0xFFFFD43B;
        ui.round(x + 3f, y + 3f, 22f, h - 6f, 1.5f, col);
        ui.textCentered(String.valueOf(s.weight()), x + 14f, y, h, Theme.FONT_SMALL, 0xFF111111);
        float small = Theme.FONT_SMALL * 0.9f;
        float textW = w - 30f - 3 * 26f;
        ui.text(ui.ellipsize(s.x + " " + s.y + " " + s.z, 70f, Theme.FONT_SMALL), x + 29f, y, h, Theme.FONT_SMALL, Theme.TEXT);
        String dist = "";
        if (mc.player != null && dimNow.equals(s.dimension)) {
            double d = Math.sqrt(mc.player.distanceToSqr(s.x, s.y, s.z));
            dist = d >= 1000 ? String.format(Locale.ROOT, "%.1fkm", d / 1000) : String.format(Locale.ROOT, "%.0fm", d);
        }
        ui.text(ui.ellipsize(s.summary(), textW - 110f, small), x + 101f, y, h, small, Theme.TEXT_DIM);
        if (!dist.isEmpty()) ui.textRight(dist, x + 29f + textW, y, h, small, Theme.TEXT_DIM);
        if (hover) tooltip(s.summary() + "  -  first seen " + age(s.firstSeen));

        float bw = 24f, by = y + 2f, bh = h - 4f, bx = x + w - 3 * (bw + 2f);
        if (button("WP", bx, by, bw, bh, Waypoints.INSTANCE != null, false)) {
            m.addWaypoint(mc, s);
            status("Waypoint added");
        }
        if (button("Go", bx + bw + 2f, by, bw, bh, mc.player != null && dimNow.equals(s.dimension), false)) {
            if (ModuleManager.getModuleByName("GoTo") instanceof GoTo goTo) {
                goTo.setTarget(s.x, s.y, s.z);
                WindowManager.INSTANCE.close();
            }
        }
        boolean c = confirm == s;
        if (button(c ? "Sure?" : "Del", bx + 2 * (bw + 2f), by, bw, bh, true, c)) {
            if (c || shiftClick()) { m.store.remove(key, s); confirm = null; }
            else { confirm = s; confirmTime = 2.5f; }
        }
    }

    private void status(String s) { status = s; statusTime = 3f; }

    private static String age(long t) {
        long s = (System.currentTimeMillis() - t) / 1000;
        if (s < 60) return "just now";
        if (s < 3600) return s / 60 + " min ago";
        if (s < 86400) return s / 3600 + " h ago";
        return s / 86400 + " d ago";
    }
}
