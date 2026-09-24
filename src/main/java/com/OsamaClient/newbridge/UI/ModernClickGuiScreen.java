package com.OsamaClient.newbridge.UI;

import com.OsamaClient.newbridge.Config;
import com.OsamaClient.newbridge.UI.components.Component;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabbasierte "Modern"-GUI.
 *
 * Additiv und eigenständig: nutzt {@link ModuleManager}, das {@link Theme}-
 * System, {@link UISettings} (eigener {@code modernScale}) und die vorhandenen
 * {@link Component}-Settings-Widgets. Die bestehende {@link ClickGuiScreen}
 * bleibt komplett unangetastet – umgeschaltet wird über {@link GuiManager}.
 *
 * Aufbau:
 *  - Titelzeile mit Umschalt-Button zurück zur Classic-GUI
 *  - horizontale Kategorie-Tabs
 *  - scrollbare Modulliste; ein Klick auf eine Modulzeile klappt deren
 *    Einstellungen als Akkordeon auf (rendert die vorhandenen Widgets)
 *  - der Pill-Schalter rechts toggelt das Modul
 */
public class ModernClickGuiScreen extends Screen {

    // Basis-Layout (über UISettings.modernScaled() skaliert)
    private static final int MARGIN   = 16;
    private static final int TITLE_H  = 16;
    private static final int TAB_H    = 15;
    private static final int TAB_PAD  = 8;
    private static final int TAB_GAP  = 3;
    private static final int ROW_H    = 15;
    private static final int PILL_W   = 22;
    private static final int PILL_H   = 9;
    private static final int PAD      = 8;
    private static final int INDENT   = 10;
    private static final int SET_GAP  = 3;
    private static final int SECT_GAP = 6;

    private static final int OPEN_ANIM_MS = 200;

    private Module.Category activeCategory;
    private Module expandedModule;
    private double scrollY = 0;
    private int maxScroll = 0;
    private long openTimeMs = -1L;

    public ModernClickGuiScreen() {
        super(net.minecraft.network.chat.Component.literal(""));
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public void init() {
        super.init();
        openTimeMs = System.currentTimeMillis();
        activeCategory = firstVisibleCategory();
        expandedModule = null;
        scrollY = 0;
        ClickGuiScreen.playGuiSound(1.35f, 0.28f);
    }

    @Override
    public void onClose() {
        Config.save();
        ProfileManager.syncActiveProfile();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private static int ms(int v) { return UISettings.modernScaled(v); }

    private List<Module.Category> visibleCategories() {
        List<Module.Category> out = new ArrayList<>();
        for (Module.Category c : Module.Category.values()) {
            if (!ModuleManager.getModulesByCategory(c).isEmpty()) out.add(c);
        }
        return out;
    }

    private Module.Category firstVisibleCategory() {
        List<Module.Category> vis = visibleCategories();
        return vis.isEmpty() ? null : vis.get(0);
    }

    private int[] windowRect() {
        int wx = ms(MARGIN), wy = ms(MARGIN);
        return new int[]{wx, wy, this.width - wx * 2, this.height - wy * 2};
    }

    private int[] contentRect() {
        int[] w = windowRect();
        int top = w[1] + ms(TITLE_H) + ms(TAB_H) + ms(4);
        return new int[]{w[0] + ms(PAD), top, w[2] - ms(PAD) * 2, w[1] + w[3] - top - ms(PAD)};
    }

    private String prettyCategory(Module.Category c) {
        String n = c.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    // ── Tabs ────────────────────────────────────────────────────────────────

    private record TabRect(Module.Category category, int x, int y, int w, int h) {}

    private List<TabRect> tabRects() {
        List<TabRect> out = new ArrayList<>();
        int[] win = windowRect();
        int x = win[0] + ms(PAD);
        int y = win[1] + ms(TITLE_H);
        int h = ms(TAB_H) - ms(2);
        for (Module.Category c : visibleCategories()) {
            int tw = UISettings.textWidth(this.font, prettyCategory(c)) + ms(TAB_PAD) * 2;
            out.add(new TabRect(c, x, y, tw, h));
            x += tw + ms(TAB_GAP);
        }
        return out;
    }

    // ── Rows (module list + expanded settings) ──────────────────────────────

    private record Row(Module module, int rowY, int rowH,
                       int pillX, int pillY, int pillW, int pillH) {}

    /** Positioniert Zeilen und – für das aufgeklappte Modul – die Setting-
     *  Widgets. Setzt {@code component.x/y}. Aktualisiert {@link #maxScroll}. */
    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        int[] cr = contentRect();
        int contentX = cr[0], contentY = cr[1], contentW = cr[2];
        int y = contentY - (int) scrollY;
        int rowH = ms(ROW_H);

        if (activeCategory != null) {
            for (Module module : ModuleManager.getModulesByCategory(activeCategory)) {
                int pillW = ms(PILL_W), pillH = ms(PILL_H);
                int pillX = contentX + contentW - pillW - ms(4);
                int pillY = y + (rowH - pillH) / 2;
                rows.add(new Row(module, y, rowH, pillX, pillY, pillW, pillH));
                y += rowH;

                if (module == expandedModule) {
                    for (Component c : module.settings) {
                        c.x = contentX + ms(INDENT);
                        c.y = y;
                        y += c.height + ms(SET_GAP);
                    }
                    y += ms(SECT_GAP);
                }
            }
        }

        int totalH = (y + (int) scrollY) - contentY;
        maxScroll = Math.max(0, totalH - cr[3]);
        return rows;
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme.Palette p = Theme.getActive().palette;
        float fade = openFade();

        // Abgedunkelter Hintergrund
        g.fill(0, 0, this.width, this.height, Component.withAlpha(0x000000, 0.55f * fade));

        int[] win = windowRect();
        drawPanel(g, win[0], win[1], win[2], win[3], p);

        // Titelzeile
        UISettings.drawText(g, this.font, "0samaClient — Modern",
                win[0] + ms(PAD), win[1] + ms(5), p.accent, false);
        drawModeButton(g, win, p, mouseX, mouseY);

        // Tabs
        for (TabRect t : tabRects()) {
            boolean active = t.category == activeCategory;
            boolean hov = inRect(mouseX, mouseY, t.x, t.y, t.w, t.h);
            int bg = active ? UISettings.withPanelAlpha(p.bgHover)
                    : (hov ? UISettings.withPanelAlpha(p.bg) : 0);
            if (bg != 0) g.fill(t.x, t.y, t.x + t.w, t.y + t.h, bg);
            if (active) g.fill(t.x, t.y + t.h - ms(2), t.x + t.w, t.y + t.h, p.accent);
            UISettings.drawText(g, this.font, prettyCategory(t.category),
                    t.x + ms(TAB_PAD), t.y + (t.h - ms(8)) / 2,
                    active ? p.text : (hov ? p.text : p.textDim), false);
        }

        // Inhalt (scrollbar + geclippt)
        int[] cr = contentRect();
        List<Row> rows = buildRows();
        clampScroll();

        g.enableScissor(cr[0], cr[1], cr[0] + cr[2], cr[1] + cr[3]);
        for (Row row : rows) {
            // Nur sichtbare Zeilen zeichnen
            if (row.rowY + row.rowH < cr[1] || row.rowY > cr[1] + cr[3]) {
                // Zeile außerhalb – Settings evtl. trotzdem im Sichtbereich:
                if (row.module != expandedModule) continue;
            }
            renderRow(g, p, row, mouseX, mouseY);
            if (row.module == expandedModule) {
                for (Component c : row.module.settings) {
                    if (c.y + c.height >= cr[1] && c.y <= cr[1] + cr[3]) {
                        c.render(g, mouseX, mouseY);
                    }
                }
            }
        }
        g.disableScissor();

        // Scrollbar-Indikator
        if (maxScroll > 0) {
            int trackH = cr[3];
            int thumbH = Math.max(ms(12), (int) ((long) trackH * trackH / (trackH + maxScroll)));
            int thumbY = cr[1] + (int) ((trackH - thumbH) * (scrollY / maxScroll));
            int barX = cr[0] + cr[2] - ms(2);
            g.fill(barX, thumbY, barX + ms(2), thumbY + thumbH, p.accent);
        }

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void renderRow(GuiGraphicsExtractor g, Theme.Palette p, Row row, int mouseX, int mouseY) {
        Module m = row.module;
        boolean rowHov = inRect(mouseX, mouseY, contentRect()[0], row.rowY, contentRect()[2], row.rowH)
                && !inRect(mouseX, mouseY, row.pillX, row.pillY, row.pillW, row.pillH);
        boolean expanded = m == expandedModule;

        if (rowHov || expanded) {
            g.fill(contentRect()[0], row.rowY, contentRect()[0] + contentRect()[2], row.rowY + row.rowH,
                    UISettings.withPanelAlpha(p.bgHover));
        }
        UISettings.drawText(g, this.font, (expanded ? "▾ " : "▸ ") + m.name,
                contentRect()[0] + ms(6), row.rowY + (row.rowH - ms(8)) / 2,
                m.enabled ? p.text : p.textDim, false);

        // Toggle-Pill
        int pillCol = m.enabled ? p.enabled : p.disabled;
        g.fill(row.pillX, row.pillY, row.pillX + row.pillW, row.pillY + row.pillH,
                UISettings.withPanelAlpha(pillCol));
        Component.drawRoundedOutline(g, row.pillX, row.pillY, row.pillW, row.pillH, p.border);
        int knob = row.pillH - ms(2);
        int knobX = m.enabled ? row.pillX + row.pillW - knob - ms(1) : row.pillX + ms(1);
        g.fill(knobX, row.pillY + ms(1), knobX + knob, row.pillY + ms(1) + knob, p.text);
    }

    private void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, Theme.Palette p) {
        g.fill(x, y, x + w, y + h, UISettings.withPanelAlpha(p.bg));
        Component.drawRoundedOutline(g, x, y, w, h, p.border);
        // Titel-Trennlinie
        g.fill(x, y + ms(TITLE_H) - ms(1), x + w, y + ms(TITLE_H), p.border);
    }

    private int[] modeButtonRect() {
        int[] win = windowRect();
        int bw = UISettings.textWidth(this.font, "Classic »") + ms(10);
        int bh = ms(TITLE_H) - ms(6);
        return new int[]{win[0] + win[2] - bw - ms(PAD), win[1] + ms(3), bw, bh};
    }

    private void drawModeButton(GuiGraphicsExtractor g, int[] win, Theme.Palette p, int mouseX, int mouseY) {
        int[] r = modeButtonRect();
        boolean hov = inRect(mouseX, mouseY, r[0], r[1], r[2], r[3]);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3],
                UISettings.withPanelAlpha(hov ? p.bgHover : p.bg));
        Component.drawRoundedOutline(g, r[0], r[1], r[2], r[3], hov ? p.accent : p.border);
        UISettings.drawText(g, this.font, "Classic »",
                r[0] + ms(5), r[1] + (r[3] - ms(8)) / 2, hov ? p.text : p.textDim, false);
    }

    // ── Animation ───────────────────────────────────────────────────────────

    private float openFade() {
        if (!UISettings.animationsEnabled || openTimeMs < 0) return 1f;
        float t = (System.currentTimeMillis() - openTimeMs) / (float) OPEN_ANIM_MS;
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t); // easeOut
    }

    // ── Input ───────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        int mx = (int) event.x(), my = (int) event.y(), button = event.button();

        // Mode-Button
        int[] mb = modeButtonRect();
        if (button == 0 && inRect(mx, my, mb[0], mb[1], mb[2], mb[3])) {
            switchToClassic();
            return true;
        }

        // Tabs
        for (TabRect t : tabRects()) {
            if (button == 0 && inRect(mx, my, t.x, t.y, t.w, t.h)) {
                if (activeCategory != t.category) {
                    activeCategory = t.category;
                    expandedModule = null;
                    scrollY = 0;
                    ClickGuiScreen.playGuiSound(1.20f, 0.22f);
                }
                return true;
            }
        }

        int[] cr = contentRect();
        boolean inContent = inRect(mx, my, cr[0], cr[1], cr[2], cr[3]);

        List<Row> rows = buildRows();

        // Zuerst: Klicks an aufgeklappte Setting-Widgets weiterreichen
        if (expandedModule != null) {
            for (Component c : expandedModule.settings) {
                if (inContent && c.mouseClicked(mx, my, button)) return true;
            }
        }

        if (inContent) {
            for (Row row : rows) {
                if (my < row.rowY || my > row.rowY + row.rowH) continue;
                if (button == 0 && inRect(mx, my, row.pillX, row.pillY, row.pillW, row.pillH)) {
                    row.module.toggle();
                    ClickGuiScreen.playGuiSound(row.module.enabled ? 1.05f : 0.80f, 0.25f);
                    Config.save();
                    return true;
                }
                if (button == 0) { // Zeile aufklappen/zuklappen
                    expandedModule = (expandedModule == row.module) ? null : row.module;
                    ClickGuiScreen.playGuiSound(1.10f, 0.20f);
                    return true;
                }
            }
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (expandedModule != null) {
            for (Component c : expandedModule.settings) {
                c.mouseReleased(event.x(), event.y(), event.button());
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int[] cr = contentRect();
        if (inRect((int) mouseX, (int) mouseY, cr[0], cr[1], cr[2], cr[3]) && maxScroll > 0) {
            scrollY -= verticalAmount * ms(18);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (expandedModule != null) {
            for (Component c : expandedModule.settings) {
                if (c.keyPressed(event)) return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (expandedModule != null) {
            for (Component c : expandedModule.settings) {
                if (c.charTyped(event)) return true;
            }
        }
        return super.charTyped(event);
    }

    private void switchToClassic() {
        GuiManager.setMode(GuiMode.CLASSIC);
        Config.save();
        ClickGuiScreen.playGuiSound(0.85f, 0.25f);
        Minecraft.getInstance().gui.setScreen(new ClickGuiScreen());
    }

    private void clampScroll() {
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;
    }

    private static boolean inRect(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }
}
