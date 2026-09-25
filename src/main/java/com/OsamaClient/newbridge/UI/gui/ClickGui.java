package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.anim.Progress;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.setting.SettingWidget;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
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
            Module.Category.MISC, Module.Category.Donut, Module.Category.CLIENT};
    private static final String[] TITLES = {"Combat", "Movement", "Visual", "Misc", "Donut", "Client"};

    private final UiRenderer renderer = new UiRenderer();
    private final Ui ui = new Ui(renderer);
    private final List<Panel> panels = new ArrayList<>();
    private final Progress openP = new Progress(0.3f);
    private final Anim tooltipA = new Anim(0f, 16f);
    private long lastNanos;

    private String hint, shownHint = "";
    private float hintTime;
    private ModuleRow bindingRow;
    private SettingWidget focused;
    private final SearchBar searchBar = new SearchBar();
    private int buttonsDown;

    private ClickGui() {}

    // ------------------------------------------------------------------ panels

    public List<Panel> panels() {
        if (panels.isEmpty()) buildPanels();
        return panels;
    }

    private void buildPanels() {
        for (int i = 0; i < ORDER.length; i++) {
            Panel p = new Panel(ORDER[i], TITLES[i], ModuleManager.getModulesByCategory(ORDER[i]));
            p.x = 8 + i * (Theme.PANEL_W + Theme.GAP);
            p.y = 8;
            panels.add(p);
        }
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
        openP.setForward(true);
        buttonsDown = 0;
        lastNanos = 0;
    }

    /** Starts the close animation; the screen is closed when it finishes. */
    public void close() {
        if (focused != null) focused.blur();
        focused = null;
        bindingRow = null;
        for (Panel p : panels) p.dragging = false;
        searchBar.close();
        openP.setForward(false);
        onClosed();
    }

    /** Hook for persistence (config save). */
    private void onClosed() {
        GuiEvents.fireClosed();
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
            if (buttonsDown == 0) ui.scale = Theme.scale();
            ui.dt = dt;
            ui.mouseX = UiInput.mouseFbX() / ui.scale;
            ui.mouseY = UiInput.mouseFbY() / ui.scale;
            ui.alpha = 1f;
            ui.hoverBlocked = false;
            draw(dt);
            renderer.flush();
        } catch (RuntimeException e) {
            LOG.error("ClickGui frame failed", e);
            renderer.begin(); // drop the half-built frame
        }
    }

    private void draw(float dt) {
        float open = openP.eased();
        String bg = Theme.background();
        if (!"None".equals(bg)) ui.rect(0, 0, ui.width(), ui.height(), ColorUtil.alpha(0xFF000000, 0.35f * open));

        List<Panel> ps = panels();
        for (Panel p : ps) p.drag(ui);
        Panel top = topPanelAtMouse();

        hint = null;
        for (int i = 0; i < ps.size(); i++) {
            Panel p = ps.get(i);
            float appear = openP.forward() ? Anim.easeOutCubic(openP.delayed(i * 0.03f)) : open;
            ui.alpha = appear;
            ui.hoverBlocked = p != top || !openP.forward();
            p.render(ui, appear, searchBar.query());
        }
        ui.hoverBlocked = false;
        ui.alpha = open;

        searchBar.draw(ui);
        drawTooltip(dt);
        drawHelp();
    }

    private Panel topPanelAtMouse() {
        List<Panel> ps = panels();
        for (int i = ps.size() - 1; i >= 0; i--) if (ps.get(i).contains(ui.mouseX, ui.mouseY)) return ps.get(i);
        return null;
    }

    /** Called by rows/widgets while they are hovered. */
    void hint(String text) {
        if (text != null && !text.isEmpty()) hint = text;
    }

    private void drawTooltip(float dt) {
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

    private void drawHelp() {
        String[] lines = {
                "LMB toggle  ·  RMB settings  ·  MMB bind  ·  Ctrl+F search",
                "Drag header to move  ·  RMB header to collapse  ·  Shift+LMB slider to type"};
        float lh = 7.5f;
        float y = ui.height() - lines.length * lh - 3f;
        for (String l : lines) {
            ui.text(l, 4f, y, lh, 6f, 0x668A8A96);
            y += lh;
        }
    }

    // ------------------------------------------------------------------ input (from ClickGuiScreen)

    public void mouseClicked(int button, int mods) {
        buttonsDown |= 1 << button;
        if (!openP.forward()) return;
        if (bindingRow != null) {
            bindingRow.binding = false;
            bindingRow = null;
        }
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
            panels.remove(top);
            panels.add(top);
        }
        if (wasFocused != null && wasFocused != focused) wasFocused.blur();
        if (!consumed) focused = null;
    }

    public void mouseReleased(int button) {
        buttonsDown &= ~(1 << button);
        for (Panel p : panels) p.mouseReleased(ui, button);
    }

    public void mouseScrolled(double amount) {
        if (!openP.forward()) return;
        Panel top = topPanelAtMouse();
        if (top != null) top.mouseScrolled(ui, amount);
    }

    public void keyPressed(int key, int mods) {
        if (!openP.forward()) return;
        if (bindingRow != null) {
            boolean unbind = key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE;
            bindingRow.module.key = unbind ? -1 : key;
            bindingRow.binding = false;
            bindingRow = null;
            GuiEvents.fireChanged();
            return;
        }
        if (searchBar.keyPressed(key, mods)) return;
        if (focused == null && key == GLFW.GLFW_KEY_F && UiInput.ctrl(mods)) {
            searchBar.open();
            return;
        }
        if (focused != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                focused.blur();
                focused = null;
                return;
            }
            if (focused.keyPressed(key, mods)) {
                if (!focused.wantsKeyboard()) focused = null;
                return;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || (ClickGuiModule.INSTANCE != null && key == ClickGuiModule.INSTANCE.key)) {
            close();
            return;
        }
        for (int i = panels.size() - 1; i >= 0; i--) if (panels.get(i).hoverKey(key, mods)) return;
    }

    public void charTyped(int codepoint) {
        if (searchBar.charTyped(codepoint)) return;
        if (focused != null) focused.charTyped(codepoint);
    }

    // ------------------------------------------------------------------ helpers for rows / widgets

    void startBinding(ModuleRow row) {
        if (bindingRow != null) bindingRow.binding = false;
        bindingRow = row;
        row.binding = true;
    }

    public void focus(SettingWidget w) {
        if (focused != null && focused != w) focused.blur();
        focused = w;
    }

    /** Toggles a module; a module that throws (e.g. no world yet) never breaks the GUI. */
    public void safeToggle(Module m) {
        try {
            m.toggle();
        } catch (RuntimeException e) {
            LOG.warn("Toggling {} failed", m.name, e);
        }
        GuiEvents.fireChanged();
    }
}
