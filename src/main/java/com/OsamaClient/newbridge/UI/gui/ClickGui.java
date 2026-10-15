package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteWheel;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.config.ConfigCodec;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.anim.Progress;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import com.OsamaClient.newbridge.UI.gui.render.BlurRenderer;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.setting.SettingWidget;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import net.minecraft.client.Minecraft;
import com.OsamaClient.newbridge.UI.gui.input.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * ClickGUI root: panels, input routing, open/close animation, tooltip, help line. Drawn every frame by
 * {@code GameRendererUiMixin} with our own renderer while the (blank) {@link ClickGuiScreen} is open
 * or the close animation is still running.
 */
public final class ClickGui {

    public static final ClickGui INSTANCE = new ClickGui();
    private static final Logger LOG = LoggerFactory.getLogger("newbridge/ClickGui");

    private static final Module.Category[] ORDER = {
            Module.Category.COMBAT, Module.Category.MOVEMENT, Module.Category.VISUAL,
            Module.Category.MISC, Module.Category.DUPE, Module.Category.Donut, Module.Category.CLIENT, Module.Category.PLAYER,
            Module.Category.TROLL};
    private static final String[] TITLES = {"Combat", "Movement", "Visual", "Misc", "Dupe", "Donut", "Client", "Player", "Troll"};

    private final UiRenderer renderer = new UiRenderer();
    private final BlurRenderer blur = new BlurRenderer();
    private final Ui ui = new Ui(renderer);
    private final List<Panel> panels = new ArrayList<>();
    private final Progress openP = new Progress(0.3f);
    private final Anim tooltipA = new Anim(0f, 16f);
    private long lastNanos;
    private int failures;

    private String hint, shownHint = "";
    private float hintTime;
    private ModuleRow bindingRow;
    private SettingWidget focused;
    private final SearchBar searchBar = new SearchBar();
    private int buttonsDown;

    /** Search filter: show only enabled modules. */
    static boolean onlyEnabled;
    public static boolean onlyFavorites;
    public static boolean onlyRecent;
    /** Names of the most recently toggled modules, newest first. */
    public static final java.util.ArrayList<String> RECENT = new java.util.ArrayList<>();
    public static void noteUsed(String name) {
        RECENT.remove(name);
        RECENT.add(0, name);
        while (RECENT.size() > 8) RECENT.remove(RECENT.size() - 1);
    }

    private ClickGui() {}

    // ------------------------------------------------------------------ panels

    public List<Panel> panels() {
        if (panels.isEmpty()) buildPanels();
        return panels;
    }

    private void buildPanels() {
        for (int i = 0; i < ORDER.length; i++) {
            Panel p = new Panel(ORDER[i], TITLES[i], ModuleManager.getModulesByCategory(ORDER[i]));
            p.x = 8 + i * (Theme.panelW() + Theme.GAP);
            p.y = 8;
            applyPending(p);
            panels.add(p);
        }
        // a category without a saved position (e.g. newly added) takes the first free column instead of overlapping
        for (Panel p : panels) {
            if (pendingPanels.containsKey(p.category.name())) continue;
            float w = Theme.panelW();
            for (int slot = 0; slot < ORDER.length; slot++) {
                float sx = 8 + slot * (w + Theme.GAP);
                boolean free = true;
                for (Panel q : panels) if (q != p && Math.abs(q.x - sx) < w * 0.6f && Math.abs(q.y - 8f) < 40f) free = false;
                if (free) { p.x = sx; break; }
            }
        }
    }

    private final java.util.Map<String, ConfigCodec.PanelState> pendingPanels = new java.util.HashMap<>();

    /** Panel layout for saving (panels that were never built keep their loaded state). */
    public java.util.Map<String, ConfigCodec.PanelState> panelStates() {
        java.util.Map<String, ConfigCodec.PanelState> out = new java.util.HashMap<>(pendingPanels);
        for (Panel p : panels) out.put(p.category.name(), new ConfigCodec.PanelState(p.x, p.y, p.collapsed));
        return out;
    }

    /** Restores saved panel positions / collapsed state (applied when the panels are built). */
    public void applyPanelStates(java.util.Map<String, ConfigCodec.PanelState> states) {
        pendingPanels.putAll(states);
        for (Panel p : panels) applyPending(p);
    }

    private void applyPending(Panel p) {
        ConfigCodec.PanelState s = pendingPanels.get(p.category.name());
        if (s == null) return;
        p.x = s.x();
        p.y = s.y();
        p.snapCollapsed(s.collapsed());
    }

    public Panel panel(Module.Category c) {
        for (Panel p : panels()) if (p.category == c) return p;
        return null;
    }

    // ------------------------------------------------------------------ open / close

    public boolean isOpen() {
        return Minecraft.getInstance().gui.screen() instanceof ClickGuiScreen && openP.forward();
    }

    public void toggleOpen() {
        if (isOpen()) close();
        else open();
    }

    public void open() {
        Minecraft mc = Minecraft.getInstance();
        panels();
        if (!(mc.gui.screen() instanceof ClickGuiScreen)) mc.gui.setScreen(new ClickGuiScreen());
        if (!openP.forward()) com.OsamaClient.newbridge.UI.gui.util.UiSounds.open();
        openP.setForward(true);
        buttonsDown = 0;
        lastNanos = 0;
    }

    /** Starts the close animation; the screen is closed when it finishes. */
    public void close() {
        altDown = false;
        ctrlDown = false;
        if (focused != null) focused.blur();
        focused = null;
        bindingRow = null;
        for (Panel p : panels) p.dragging = false;
        for (Panel p : hudPanels) p.dragging = false;
        searchBar.close();
        if (openP.forward()) com.OsamaClient.newbridge.UI.gui.util.UiSounds.close();
        openP.setForward(false);
        onClosed();
    }

    /** Hook for persistence (config save). */
    private void onClosed() {
        GuiEvents.fireClosed();
    }

    /** Frees our GPU resources (client shutdown). */
    public void shutdown() {
        renderer.close();
        blur.close();
        EmoteWheel.INSTANCE.shutdown();
        UiFont.closeAll();
    }

    public void onScreenRemoved() {
        // screen replaced by something else (or closed by us at the end of the animation)
        if (openP.forward()) {
            openP.snap(false);
            onClosed();
        }
    }

    // ------------------------------------------------------------------ frame

    public void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.overlay() != null) return; // resources (our shaders) may not be loaded yet
        boolean screenOpen = mc.gui.screen() instanceof ClickGuiScreen;
        if (!screenOpen && openP.raw() <= 0f) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;

        openP.update(dt, Theme.animSpeed());
        if (!openP.forward() && openP.finished()) {
            if (screenOpen) mc.gui.setScreen(null);
            lastNanos = 0;
            return;
        }

        try {
            renderer.begin();
            // never change the scale while a button is held (dragging the Scale slider would feed back)
            if (buttonsDown == 0) ui.scale = effectiveScale();
            ui.dt = dt;
            ui.mouseX = UiInput.mouseFbX() / ui.scale;
            ui.mouseY = UiInput.mouseFbY() / ui.scale;
            ui.alpha = 1f;
            ui.hoverBlocked = false;
            draw(dt);
            renderer.flush();
            UiFont.collect(600);
        } catch (RuntimeException e) {
            if (failures++ % 600 == 0) LOG.error("ClickGui frame failed ({}x)", failures, e); // no log flood
            renderer.begin(); // drop the half-built frame
        }
    }

    private void draw(float dt) {
        float open = openP.eased();
        String bg = Theme.background();
        if ("Blur".equals(bg)) blur.render(open, 1.5f); // executes before the UI pass (flushed later)
        float dim = "Blur".equals(bg) ? 0.2f : "Dim".equals(bg) ? 0.35f : 0f;
        if (dim > 0) ui.rect(0, 0, ui.width(), ui.height(), ColorUtil.alpha(0xFF000000, dim * open));
        ui.alpha = open;
        drawHelp(); // under the panels

        List<Panel> ps = active();
        for (Panel p : ps) p.drag(ui);
        Panel top = topPanelAtMouse();

        hint = null;
        detailModule = null;
        detailSetting = null;
        String query = searchBar.query();
        for (int i = 0; i < ps.size(); i++) {
            Panel p = ps.get(i);
            float appear = openP.forward() ? Anim.easeOutCubic(openP.delayed(i * 0.03f)) : open;
            ui.alpha = appear;
            ui.hoverBlocked = p != top || !openP.forward();
            p.render(ui, appear, query);
        }
        ui.hoverBlocked = false;
        ui.alpha = open;

        searchBar.draw(ui, matchCount());
        drawTabs();
        drawToast(dt);
        drawTooltip(dt);
    }

    // ------------------------------------------------------------------ tabs (Modules, HUD, windows ...)

    private int tab; // 0 = Modules, 1 = HUD
    private final List<Panel> hudPanels = new ArrayList<>();
    private final Anim tabA = new Anim(0f, 16f);
    private final Anim pillX = new Anim(0f, 20f), pillW = new Anim(0f, 20f);

    private static final String[] HUD_MAIN = {"HudOverlay"};
    private static final String[] HUD_MORE = {"Minimap", "ArrayList", "TeammateList", "HealthIndicator", "Nametags", "Breadcrumbs"};

    public int tab() { return tab; }

    public void setTab(int t) {
        if (t == tab) return;
        tab = t;
        for (Panel p : panels) p.dragging = false;
        for (Panel p : hudPanels()) p.dragging = false;
        com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick();
    }

    private List<Panel> hudPanels() {
        if (hudPanels.isEmpty()) {
            addHudPanel("HUD", HUD_MAIN, 8f);
            addHudPanel("Overlays", HUD_MORE, 8f + Theme.panelW() + Theme.GAP);
        }
        return hudPanels;
    }

    private void addHudPanel(String title, String[] names, float x) {
        List<Module> mods = new ArrayList<>();
        for (String n : names) {
            Module m = ModuleManager.getModuleByName(n);
            if (m != null) mods.add(m);
        }
        if (mods.isEmpty()) return;
        Panel p = new Panel(Module.Category.VISUAL, title, mods);
        p.x = x;
        p.y = 8f;
        hudPanels.add(p);
    }

    /** Panels of the current tab. */
    private List<Panel> active() {
        panels();
        return tab == 1 ? hudPanels() : panels;
    }

    private record Tab(String label, String description, boolean selected, Runnable action) {}

    private final java.util.List<float[]> tabRects = new java.util.ArrayList<>();
    private final java.util.List<Tab> tabList = new java.util.ArrayList<>();

    private void buildTabs() {
        tabList.clear();
        tabList.add(new Tab("Modules", "All modules by category", tab == 0, () -> setTab(0)));
        tabList.add(new Tab("HUD", "HUD overlay, keystrokes, minimap, module list and other on-screen elements", tab == 1, () -> setTab(1)));
        for (var e : com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.dock()) {
            tabList.add(new Tab(e.label(), e.description(), false,
                    () -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(e.factory().get())));
        }
    }

    /** Tab bar at the bottom center: view tabs (Modules / HUD) and buttons for the own windows. */
    private void drawTabs() {
        buildTabs();
        tabRects.clear();
        float h = 12f, gap = 2f, pad = 6f;
        float total = -gap;
        for (Tab t : tabList) total += ui.textWidth(t.label(), Theme.FONT_SMALL) + 2 * pad + gap;
        float x = (ui.width() - total) * 0.5f, y = ui.height() - h - 4f;
        float barPad = 2.5f;
        ui.glow(x - barPad, y - barPad, total + 2 * barPad, h + 2 * barPad, Theme.radius() + 1f, 6f, Theme.accent(0.1f * Theme.glow()));
        ui.round(x - barPad, y - barPad, total + 2 * barPad, h + 2 * barPad, Theme.radius() + 1f, Theme.panelBg());
        ui.outline(x - barPad, y - barPad, total + 2 * barPad, h + 2 * barPad, Theme.radius() + 1f, 1f, Theme.OUTLINE);
        // the selection pill glides between the view tabs instead of jumping
        float px = x;
        for (Tab t : tabList) {
            float tw = ui.textWidth(t.label(), Theme.FONT_SMALL) + 2 * pad;
            if (t.selected()) {
                pillX.setTarget(px);
                pillW.setTarget(tw);
                if (pillW.value() == 0f) { pillX.snap(px); pillW.snap(tw); }
            }
            px += tw + gap;
        }
        pillX.update(ui.dt, Theme.animSpeed());
        pillW.update(ui.dt, Theme.animSpeed());
        if (pillW.value() > 0.5f) {
            ui.round(pillX.value(), y, pillW.value(), h, Theme.radius(), Theme.accent(0.28f));
            ui.round(pillX.value() + 3f, y + h - 1.6f, pillW.value() - 6f, 1.1f, 0.55f, Theme.accent());
        }
        for (int i = 0; i < tabList.size(); i++) {
            Tab t = tabList.get(i);
            float w = ui.textWidth(t.label(), Theme.FONT_SMALL) + 2 * pad;
            boolean hover = ui.hovered(x, y, w, h);
            if (!t.selected() && hover) {
                ui.round(x, y, w, h, Theme.radius(), Theme.ROW_HOVER);
            }
            ui.textCentered(t.label(), x + w / 2f, y, h, Theme.FONT_SMALL,
                    t.selected() ? Theme.TEXT : hover ? Theme.TEXT : Theme.TEXT_DIM);
            if (hover) hint(t.description());
            tabRects.add(new float[]{x, y, w, h});
            x += w + gap;
        }
    }

    private boolean tabClicked() {
        for (int i = 0; i < tabRects.size() && i < tabList.size(); i++) {
            float[] r = tabRects.get(i);
            if (ui.mouseX >= r[0] && ui.mouseX < r[0] + r[2] && ui.mouseY >= r[1] && ui.mouseY < r[1] + r[3]) {
                com.OsamaClient.newbridge.UI.gui.util.UiSounds.click();
                tabList.get(i).action().run();
                return true;
            }
        }
        return false;
    }

    private Panel topPanelAtMouse() {
        List<Panel> ps = active();
        for (int i = ps.size() - 1; i >= 0; i--) if (ps.get(i).contains(ui.mouseX, ui.mouseY)) return ps.get(i);
        return null;
    }

    /** What the Alt detail card describes (set by the hovered row / setting every frame). */
    private com.OsamaClient.newbridge.UI.components.Module detailModule;
    private com.OsamaClient.newbridge.UI.components.Component detailSetting;
    /** Last detail card drawn (tests). */
    public static String lastDetailTitle = "";
    public static java.util.List<String> lastDetailLines = java.util.List.of();

    void detail(com.OsamaClient.newbridge.UI.components.Module m, com.OsamaClient.newbridge.UI.components.Component setting) {
        detailModule = m;
        detailSetting = setting;
    }

    /** true while Alt is held (shows the long description card instead of the short tooltip). */
    public static boolean altHeld() {
        return altDown || UiInput.keyDown(Keys.KEY_LEFT_ALT) || UiInput.keyDown(Keys.KEY_RIGHT_ALT) || forceDetail;
    }

    /** Tests: show the detail card without a real Alt key. */
    public static boolean forceDetail;
    private static boolean altDown, ctrlDown;

    public void keyReleased(int key) {
        if (key == Keys.KEY_LEFT_ALT || key == Keys.KEY_RIGHT_ALT) altDown = false;
        if (key == Keys.KEY_LEFT_CONTROL || key == Keys.KEY_RIGHT_CONTROL) ctrlDown = false;
    }

    /** Called by rows/widgets while they are hovered. */
    void hint(String text) {
        if (text != null && !text.isEmpty()) hint = text;
    }

    private void drawTooltip(float dt) {
        if (detailModule != null && altHeld() && bindingRow == null) {
            drawDetail();
            return;
        }
        boolean show = hint != null && Theme.descriptions() && bindingRow == null;
        if (show && !hint.equals(shownHint)) {
            shownHint = hint;
            hintTime = 0;
        }
        hintTime += dt;
        tooltipA.setTarget(show && hintTime > 0.25f ? 1f : 0f);
        tooltipA.update(dt, Theme.animSpeed());
        float a = tooltipA.value();
        if (a < 0.01f || shownHint.isEmpty()) return;
        float size = Theme.FONT_SMALL;
        float tw = ui.textWidth(shownHint, size);
        float w = tw + 2 * Theme.PAD, h = 9f;
        float x = ui.mouseX + 7f, y = ui.mouseY + 7f;
        if (x + w > ui.width() - 2) x = ui.mouseX - w - 3f;
        if (y + h > ui.height() - 2) y = ui.mouseY - h - 3f;
        float old = ui.alpha;
        ui.alpha = old * a;
        ui.glow(x, y, w, h, 2f, 5f, 0x66000000);
        ui.round(x, y, w, h, 2f, 0xF2101014);
        ui.outline(x, y, w, h, 2f, 1f, Theme.accent(0.35f));
        ui.text(shownHint, x + Theme.PAD, y, h, size, Theme.TEXT);
        ui.alpha = old;
    }

    private void drawDetail() {
        DetailCard.Card card = DetailCard.build(detailModule, detailSetting);
        float maxW = 190f, pad = 4f, titleSize = Theme.FONT, size = Theme.FONT_SMALL, lh = size * 1.45f;
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        for (DetailCard.Line l : card.lines()) {
            if (l.text().isEmpty()) { lines.add(""); colors.add(0); continue; }
            for (String w : wrap(l.text(), maxW - 2 * pad, size)) { lines.add(w); colors.add(l.color()); }
        }
        lastDetailTitle = card.title();
        lastDetailLines = lines;
        float th = titleSize * 1.5f;
        float h = pad + th + lines.size() * lh + pad;
        float w = maxW;
        float x = ui.mouseX + 9f, y = ui.mouseY + 6f;
        if (x + w > ui.width() - 2) x = ui.mouseX - w - 5f;
        if (x < 2) x = 2;
        if (y + h > ui.height() - 2) y = Math.max(2, ui.height() - h - 2);
        ui.glow(x, y, w, h, 3f, 7f, 0x88000000);
        ui.round(x, y, w, h, 3f, 0xF60E0E12);
        ui.outline(x, y, w, h, 3f, 1f, Theme.accent(0.6f));
        ui.rect(x + 2f, y + pad, 1.5f, th - 1f, Theme.accent());
        ui.text(card.title(), x + pad + 3f, y + pad, th, titleSize, Theme.TEXT);
        float yy = y + pad + th;
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).isEmpty()) ui.text(lines.get(i), x + pad, yy, lh, size, colors.get(i));
            yy += lh;
        }
    }

    /** Word wrap for the detail card. */
    private java.util.List<String> wrap(String text, float maxW, float size) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String probe = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && ui.textWidth(probe, size) > maxW) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private void drawHelp() {
        String[] lines = {
                "LMB toggle  ·  RMB settings  ·  MMB bind  ·  Ctrl+F search",
                "Drag header to move  ·  RMB header to collapse  ·  Shift+LMB slider to type  ·  MMB setting = reset  ·  Ctrl+Z undo  ·  Hold Alt for details"};
        float lh = 7.5f;
        float y = ui.height() - lines.length * lh - 3f;
        for (String l : lines) {
            ui.text(l, 4f, y, lh, 6f, 0x668A8A96);
            y += lh;
        }
    }

    // ------------------------------------------------------------------ input (from ClickGuiScreen)

    public static String dbgClick = "", dbgRows = "";

    public void mouseClicked(int button, int mods) {
        dbgRows = "";
        dbgClick = "btn=" + button + " mouse=" + ui.mouseX + "," + ui.mouseY + " scale=" + ui.scale + " fb=" + UiInput.mouseFbX() + "," + UiInput.mouseFbY();
        buttonsDown |= 1 << Math.max(0, Math.min(button, 30));
        if (!openP.forward()) return;
        if (bindingRow != null) {
            bindingRow.binding = false;
            bindingRow = null;
        }
        if (button == Keys.MOUSE_BUTTON_LEFT && tabClicked()) return;
        if (searchBar.mouseClicked(button)) {
            if (focused != null) focused.blur();
            focused = null;
            return;
        }
        searchBar.unfocus();
        SettingWidget wasFocused = focused;
        focused = null;
        Panel top = topPanelAtMouse();
        boolean consumed = false;
        if (top != null) {
            consumed = top.mouseClicked(ui, button, mods);
            // bring to front
            List<Panel> act = active();
            act.remove(top);
            act.add(top);
        }
        if (wasFocused != null && wasFocused != focused) wasFocused.blur();
        if (!consumed) focused = null;
        dbgClick += " | rows=" + dbgRows + " | top=" + (top != null ? top.getClass().getSimpleName() : "null") + " consumed=" + consumed + " focused=" + (focused == null ? "null" : focused.getClass().getSimpleName());
    }

    public void mouseReleased(int button) {
        buttonsDown &= ~(1 << Math.max(0, Math.min(button, 30)));
        for (Panel p : panels) p.mouseReleased(ui, button);
        for (Panel p : hudPanels) p.mouseReleased(ui, button);
    }

    public void mouseScrolled(double amount) {
        if (!openP.forward()) return;
        Panel top = topPanelAtMouse();
        if (top != null) top.mouseScrolled(ui, amount);
    }

    /** Short message shown at the bottom of the GUI for a moment (reset, undo). */
    private String toastText;
    private float toastTime;
    public static String lastToast = "";

    public void toast(String text) {
        toastText = text;
        toastTime = 2.5f;
        lastToast = text;
    }

    private void drawToast(float dt) {
        if (toastText == null || toastTime <= 0) return;
        toastTime -= dt;
        float a = Math.min(1f, toastTime * 3f);
        float size = Theme.FONT_SMALL, h = 10f, w = ui.textWidth(toastText, size) + 2 * Theme.PAD;
        float x = (ui.width() - w) / 2f, y = ui.height() - 34f;
        float old = ui.alpha;
        ui.alpha = old * a;
        ui.round(x, y, w, h, 3f, 0xF2101014);
        ui.outline(x, y, w, h, 3f, 1f, Theme.accent(0.6f));
        ui.text(toastText, x + Theme.PAD, y, h, size, Theme.TEXT);
        ui.alpha = old;
    }

    public void keyPressed(int key, int mods) {
        if (key == Keys.KEY_LEFT_ALT || key == Keys.KEY_RIGHT_ALT) { altDown = true; return; }
        if (key == Keys.KEY_LEFT_CONTROL || key == Keys.KEY_RIGHT_CONTROL) { ctrlDown = true; return; }
        // Ctrl+Z (Y too: QWERTZ keyboards) undoes the last setting change, unless a text field is being edited
        if ((key == Keys.KEY_Z || key == Keys.KEY_Y) && (UiInput.ctrl(mods) || ctrlDown) && (focused == null || !focused.wantsKeyboard())) {
            String what = com.OsamaClient.newbridge.UI.components.SettingHistory.undo();
            toast(what == null ? "Nothing to undo" : "Undone: " + what);
            if (what != null) GuiEvents.fireChanged();
            return;
        }
        if (!openP.forward()) return;
        if (focused == null) focused = editingWidget();
        if (bindingRow != null) {
            boolean unbind = key == Keys.KEY_ESCAPE || key == Keys.KEY_DELETE || key == Keys.KEY_BACKSPACE;
            bindingRow.module.key = unbind ? -1 : key;
            if (!unbind) com.OsamaClient.newbridge.UI.gui.window.KeyboardWindow.warnIfConflict(bindingRow.module);
            bindingRow.binding = false;
            bindingRow = null;
            GuiEvents.fireChanged();
            return;
        }
        if (searchBar.keyPressed(key, mods)) return;
        if (focused == null && key == Keys.KEY_F && UiInput.ctrl(mods)) {
            searchBar.open();
            return;
        }
        if (focused != null) {
            if (key == Keys.KEY_ESCAPE) {
                focused.blur();
                focused = null;
                return;
            }
            if (focused.keyPressed(key, mods)) {
                if (!focused.wantsKeyboard()) focused = null;
                return;
            }
        }
        if (key == Keys.KEY_ESCAPE || (ClickGuiModule.INSTANCE != null && key == ClickGuiModule.INSTANCE.key)) {
            close();
            return;
        }
        List<Panel> act = active();
        for (int i = act.size() - 1; i >= 0; i--) if (act.get(i).hoverKey(key, mods)) return;
    }

    /** Number of chars received (tests). */
    public static int dbgChars;

    public void charTyped(int codepoint) {
        if (focused == null) focused = editingWidget();
        dbgChars++;
        // the search only takes text after it was clicked (or opened with Ctrl+F) - no type-to-search,
        // otherwise the character of a key you just bound would start a search
        if (searchBar.charTyped(codepoint)) return;
        if (focused != null) focused.charTyped(codepoint);
    }

    // ------------------------------------------------------------------ scale / filters

    /** GUI scale actually used: the configured one, reduced so that all category columns of the tab fit the window. */
    public float effectiveScale() {
        float base = Theme.scale();
        if (!Theme.autoScale()) return base;
        int n = active().size();
        float need = 8f + n * (Theme.panelW() + Theme.GAP);
        float fit = renderer.width() / need;
        return Math.min(base, Math.max(0.6f, fit));
    }

    public float currentScale() { return ui.scale; }

    float mouseUnitsX() { return ui.mouseX; }
    float mouseUnitsY() { return ui.mouseY; }

    public String searchQuery() { return searchBar.query(); }

    /** Rows passing the search / enabled-only filter across the panels of the current tab. */
    int matchCount() {
        String q = searchBar.query();
        int n = 0;
        for (Panel p : active()) n += p.matchCount(q);
        return n;
    }

    public void toggleOnlyRecent() { onlyRecent = !onlyRecent; com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick(); }
    public void toggleOnlyFavorites() { onlyFavorites = !onlyFavorites; com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick(); }
    public void toggleOnlyEnabled() { onlyEnabled = !onlyEnabled; com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick(); }

    public boolean allCollapsed() {
        for (Panel p : active()) if (!p.collapsed) return false;
        return true;
    }

    /** Collapses every panel of the tab, or expands them all again if they are all collapsed already. */
    public void toggleCollapseAll() {
        boolean collapse = !allCollapsed();
        for (Panel p : active()) p.setCollapsed(collapse);
        com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick();
    }

    /** For tests: sets the search text as if typed. */
    public void setSearch(String text) {
        searchBar.setText(text);
    }

    public int visibleMatches() { return matchCount(); }

    // ------------------------------------------------------------------ helpers for rows / widgets

    void startBinding(ModuleRow row) {
        if (bindingRow != null) bindingRow.binding = false;
        bindingRow = row;
        row.binding = true;
    }

    /** A setting widget that is currently being edited (text field), found even if the click did not register it as focused. */
    private SettingWidget editingWidget() {
        for (Panel p : panels) for (ModuleRow r : p.rows()) for (SettingWidget sw : r.widgets()) if (sw.wantsKeyboard()) return sw;
        for (Panel p : hudPanels) for (ModuleRow r : p.rows()) for (SettingWidget sw : r.widgets()) if (sw.wantsKeyboard()) return sw;
        return null;
    }

    public void focus(SettingWidget w) {
        if (focused != null && focused != w) focused.blur();
        focused = w;
    }

    /** Toggles a module; a module that throws (e.g. no world yet) never breaks the GUI. */
    public void safeToggle(Module m) {
        noteUsed(m.name);
        try {
            m.toggle();
        } catch (RuntimeException e) {
            LOG.warn("Toggling {} failed", m.name, e);
        }
        GuiEvents.fireChanged();
    }
}
