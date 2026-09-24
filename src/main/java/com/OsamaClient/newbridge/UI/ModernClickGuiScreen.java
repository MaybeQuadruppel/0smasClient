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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Meteor-artige "Modern"-GUI: pro Kategorie ein frei verschiebbares Panel.
 *
 * - Header ziehen = Panel verschieben (Positionen werden über Config gespeichert)
 * - [-]/[+] im Header = Panel ein-/ausklappen
 * - Linksklick auf eine Modulzeile = Modul an/aus
 * - Klick auf das Dreieck rechts = Einstellungen des Moduls aufklappen
 *   (rendert die vorhandenen, neu gestylten Component-Widgets)
 *
 * Additiv: die Classic-GUI ({@link ClickGuiScreen}) bleibt unangetastet;
 * umgeschaltet wird über {@link GuiManager}.
 */
public class ModernClickGuiScreen extends Screen {

    // Basis-Layout (über UISettings.modernScaled() skaliert)
    private static final int PANEL_W  = 116;
    private static final int HEADER_H = 14;
    private static final int ROW_H    = 13;
    private static final int PAD      = 6;
    private static final int SET_GAP  = 3;
    private static final int PANEL_GAP = 8;
    private static final int TOP      = 16;
    private static final int OPEN_ANIM_MS = 160;

    /** Persistenter Panel-Zustand (Position/Collapsed) – über Sessions geteilt. */
    public static final class PanelState {
        public double x, y;
        public boolean collapsed;
        public double scroll;
        public boolean placed; // false = noch keine sinnvolle Startposition
        PanelState() { this.placed = false; }
        PanelState(double x, double y, boolean collapsed) {
            this.x = x; this.y = y; this.collapsed = collapsed; this.placed = true;
        }
    }

    private static final Map<Module.Category, PanelState> PANELS = new LinkedHashMap<>();
    private final List<Module.Category> zOrder = new ArrayList<>();
    private final Set<Module> expanded = new HashSet<>();

    private Module.Category dragCategory;
    private double dragOffX, dragOffY;
    private long openTimeMs = -1L;

    public ModernClickGuiScreen() {
        super(net.minecraft.network.chat.Component.literal(""));
    }

    private static int ms(int v) { return UISettings.modernScaled(v); }

    private static PanelState state(Module.Category c) {
        return PANELS.computeIfAbsent(c, k -> new PanelState());
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public void init() {
        super.init();
        openTimeMs = System.currentTimeMillis();
        zOrder.clear();
        int defX = ms(TOP);
        for (Module.Category c : Module.Category.values()) {
            if (ModuleManager.getModulesByCategory(c).isEmpty()) continue;
            zOrder.add(c);
            PanelState st = state(c);
            if (!st.placed) {                 // Erststart: in einer Reihe anordnen
                st.x = defX;
                st.y = ms(TOP);
                st.placed = true;
            }
            defX += ms(PANEL_W) + ms(PANEL_GAP);
            clampOnScreen(st);
        }
        ClickGuiScreen.playGuiSound(1.35f, 0.28f);
    }

    @Override
    public void onClose() {
        Config.save();
        ProfileManager.syncActiveProfile();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void clampOnScreen(PanelState st) {
        int w = ms(PANEL_W);
        st.x = Math.max(0, Math.min(this.width - w, st.x));
        st.y = Math.max(0, Math.min(this.height - ms(HEADER_H), st.y));
    }

    // ── Geometrie ───────────────────────────────────────────────────────────

    private List<Module> modulesOf(Module.Category c) {
        return ModuleManager.getModulesByCategory(c);
    }

    private String prettyCategory(Module.Category c) {
        String n = c.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    /** Gesamthöhe des Panel-Körpers (ohne Header) bei aktueller Aufklapp-Lage. */
    private int bodyHeight(Module.Category c, int contentW) {
        int h = ms(4);
        for (Module m : modulesOf(c)) {
            h += ms(ROW_H);
            if (expanded.contains(m)) {
                for (Component comp : m.settings) h += comp.height + ms(SET_GAP);
                h += ms(SET_GAP);
            }
        }
        return h;
    }

    private int maxBodyHeight(PanelState st) {
        return this.height - (int) st.y - ms(HEADER_H) - ms(TOP);
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        float fade = openFade();
        g.fill(0, 0, this.width, this.height, Component.withAlpha(0x000000, 0.45f * fade));

        // Drag anwenden
        if (dragCategory != null) {
            PanelState st = state(dragCategory);
            st.x = mouseX - dragOffX;
            st.y = mouseY - dragOffY;
            clampOnScreen(st);
        }

        // Panels in z-Reihenfolge zeichnen (vorderstes zuletzt)
        for (Module.Category c : zOrder) {
            renderPanel(g, c, mouseX, mouseY);
        }

        // dezenter Hinweis
        Theme.Palette p = Theme.getActive().palette;
        UISettings.drawText(g, this.font, "» Classic (Right Ctrl)",
                ms(TOP), this.height - ms(10), p.textDim, false);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void renderPanel(GuiGraphicsExtractor g, Module.Category c, int mouseX, int mouseY) {
        Theme.Palette p = Theme.getActive().palette;
        PanelState st = state(c);
        int px = (int) st.x, py = (int) st.y;
        int pw = ms(PANEL_W);
        int contentW = pw - ms(PAD) * 2;
        int headerH = ms(HEADER_H);

        int bodyH = st.collapsed ? 0 : Math.min(bodyHeight(c, contentW), maxBodyHeight(st));
        int totalH = headerH + bodyH;

        // Panel-Körper
        Component.roundRect(g, px, py, pw, totalH, ms(3), UISettings.withPanelAlpha(p.bg));

        // Header
        boolean headerHov = inRect(mouseX, mouseY, px, py, pw, headerH);
        Component.roundRect(g, px, py, pw, headerH, ms(3),
                UISettings.withPanelAlpha(headerHov ? p.bgHover : Theme.darken(p.bg, 0.1f)));
        g.fill(px, py + headerH - Math.max(1, ms(1)), px + pw, py + headerH, p.accent);
        UISettings.drawText(g, this.font, prettyCategory(c),
                px + ms(PAD), py + (headerH - ms(8)) / 2, p.text, false);
        // Collapse-Symbol rechts
        String sym = st.collapsed ? "+" : "–";
        int symW = UISettings.textWidth(this.font, sym);
        UISettings.drawText(g, this.font, sym,
                px + pw - ms(PAD) - symW, py + (headerH - ms(8)) / 2, p.textDim, false);

        if (st.collapsed) return;

        // Körper (geclippt + scrollbar)
        int bodyX = px, bodyY = py + headerH, bodyW = pw;
        g.enableScissor(bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
        int contentX = px + ms(PAD);
        int y = bodyY + ms(2) - (int) st.scroll;

        for (Module m : modulesOf(c)) {
            int rowH = ms(ROW_H);
            boolean rowHov = inRect(mouseX, mouseY, px, y, pw, rowH) && mouseY >= bodyY && mouseY <= bodyY + bodyH;
            boolean exp = expanded.contains(m);

            if (m.enabled) {
                g.fill(px, y, px + ms(2), y + rowH, p.accent); // Akzent-Balken links
                Component.roundRect(g, contentX - ms(2), y, contentW + ms(4), rowH, ms(2),
                        UISettings.withPanelAlpha(Theme.lighten(p.bg, 0.06f)));
            } else if (rowHov) {
                Component.roundRect(g, contentX - ms(2), y, contentW + ms(4), rowH, ms(2),
                        UISettings.withPanelAlpha(p.bgHover));
            }

            UISettings.drawText(g, this.font, m.name,
                    contentX, y + (rowH - ms(8)) / 2,
                    m.enabled ? p.text : (rowHov ? p.text : p.textDim), false);
            // Aufklapp-Dreieck rechts
            String tri = exp ? "▾" : "▸";
            int triW = UISettings.textWidth(this.font, tri);
            UISettings.drawText(g, this.font, tri,
                    px + pw - ms(PAD) - triW, y + (rowH - ms(8)) / 2,
                    exp ? p.accent : p.textDim, false);
            y += rowH;

            if (exp) {
                for (Component comp : m.settings) {
                    comp.width = contentW;
                    comp.x = contentX;
                    comp.y = y;
                    if (comp.y + comp.height >= bodyY && comp.y <= bodyY + bodyH) {
                        comp.render(g, mouseX, mouseY);
                    }
                    y += comp.height + ms(SET_GAP);
                }
                y += ms(SET_GAP);
            }
        }
        g.disableScissor();

        // Scrollbar
        int full = bodyHeight(c, contentW);
        if (full > bodyH) {
            int maxScroll = full - bodyH;
            st.scroll = Math.max(0, Math.min(st.scroll, maxScroll));
            int thumbH = Math.max(ms(10), (int) ((long) bodyH * bodyH / full));
            int thumbY = bodyY + (int) ((bodyH - thumbH) * (st.scroll / maxScroll));
            g.fill(px + pw - ms(2), thumbY, px + pw, thumbY + thumbH, p.accent);
        } else {
            st.scroll = 0;
        }
    }

    private float openFade() {
        if (!UISettings.animationsEnabled || openTimeMs < 0) return 1f;
        float t = (System.currentTimeMillis() - openTimeMs) / (float) OPEN_ANIM_MS;
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t);
    }

    // ── Input ───────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        int mx = (int) event.x(), my = (int) event.y(), button = event.button();

        // "» Classic" unten links
        int hintW = UISettings.textWidth(this.font, "» Classic (Right Ctrl)");
        if (button == 0 && inRect(mx, my, ms(TOP), this.height - ms(11), hintW, ms(10))) {
            switchToClassic();
            return true;
        }

        // Panels von vorn (Ende der zOrder) nach hinten testen
        for (int i = zOrder.size() - 1; i >= 0; i--) {
            Module.Category c = zOrder.get(i);
            if (handlePanelClick(c, mx, my, button)) {
                bringToFront(c);
                return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    private boolean handlePanelClick(Module.Category c, int mx, int my, int button) {
        PanelState st = state(c);
        int px = (int) st.x, py = (int) st.y;
        int pw = ms(PANEL_W), headerH = ms(HEADER_H);

        // Header?
        if (inRect(mx, my, px, py, pw, headerH)) {
            int symW = UISettings.textWidth(this.font, "–");
            boolean onCollapse = mx >= px + pw - ms(PAD) - symW - ms(3);
            if (button == 0 && onCollapse) {
                st.collapsed = !st.collapsed;
                ClickGuiScreen.playGuiSound(st.collapsed ? 0.8f : 1.1f, 0.2f);
            } else if (button == 0) {
                dragCategory = c;
                dragOffX = mx - st.x;
                dragOffY = my - st.y;
            }
            return true;
        }

        if (st.collapsed) return false;

        int bodyH = Math.min(bodyHeight(c, pw - ms(PAD) * 2), maxBodyHeight(st));
        int bodyY = py + headerH;
        if (!inRect(mx, my, px, bodyY, pw, bodyH)) return false;

        int contentX = px + ms(PAD), contentW = pw - ms(PAD) * 2;
        int y = bodyY + ms(2) - (int) st.scroll;

        for (Module m : modulesOf(c)) {
            int rowH = ms(ROW_H);
            boolean inRow = my >= y && my < y + rowH && my >= bodyY && my <= bodyY + bodyH;

            // Zuerst Klicks an aufgeklappte Widgets weiterreichen
            if (expanded.contains(m)) {
                // Widget-Positionen wurden im letzten render() gesetzt
                for (Component comp : m.settings) {
                    if (my >= bodyY && my <= bodyY + bodyH && comp.mouseClicked(mx, my, button)) {
                        return true;
                    }
                }
            }

            if (inRow) {
                String tri = "▸";
                int triW = UISettings.textWidth(this.font, tri);
                boolean onTri = mx >= px + pw - ms(PAD) - triW - ms(3);
                if (button == 0 && onTri) {
                    if (!expanded.add(m)) expanded.remove(m);
                    ClickGuiScreen.playGuiSound(1.1f, 0.2f);
                    return true;
                }
                if (button == 0) {
                    m.toggle();
                    ClickGuiScreen.playGuiSound(m.enabled ? 1.05f : 0.8f, 0.25f);
                    Config.save();
                    return true;
                }
                if (button == 1) { // Rechtsklick = Settings aufklappen
                    if (!expanded.add(m)) expanded.remove(m);
                    return true;
                }
            }
            y += rowH;
            if (expanded.contains(m)) {
                for (Component comp : m.settings) y += comp.height + ms(SET_GAP);
                y += ms(SET_GAP);
            }
        }
        return true; // Klick im Body abgefangen
    }

    private void bringToFront(Module.Category c) {
        zOrder.remove(c);
        zOrder.add(c);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragCategory = null;
        for (Module m : expanded) {
            for (Component comp : m.settings) {
                comp.mouseReleased(event.x(), event.y(), event.button());
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmount, double vAmount) {
        for (int i = zOrder.size() - 1; i >= 0; i--) {
            Module.Category c = zOrder.get(i);
            PanelState st = state(c);
            int px = (int) st.x, py = (int) st.y, pw = ms(PANEL_W), headerH = ms(HEADER_H);
            if (st.collapsed) continue;
            int bodyH = Math.min(bodyHeight(c, pw - ms(PAD) * 2), maxBodyHeight(st));
            if (inRect((int) mouseX, (int) mouseY, px, py + headerH, pw, bodyH)) {
                st.scroll -= vAmount * ms(16);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, hAmount, vAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        for (Module m : expanded) {
            for (Component comp : m.settings) {
                if (comp.keyPressed(event)) return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (Module m : expanded) {
            for (Component comp : m.settings) {
                if (comp.charTyped(event)) return true;
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

    private static boolean inRect(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    // ── Persistenz (von Config genutzt) ─────────────────────────────────────

    public static Map<Module.Category, PanelState> getPanelStates() {
        return PANELS;
    }

    public static void loadPanel(String categoryName, double x, double y, boolean collapsed) {
        try {
            Module.Category c = Module.Category.valueOf(categoryName);
            PANELS.put(c, new PanelState(x, y, collapsed));
        } catch (IllegalArgumentException ignored) {
            // unbekannte Kategorie überspringen
        }
    }
}
