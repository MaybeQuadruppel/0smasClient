package com.OsamaClient.newbridge.UI;

import com.OsamaClient.newbridge.Config;
import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class ClickGuiScreen extends Screen {

    // ── View state ────────────────────────────────────────────────────────────
    private Module selectedModule = null;

    // ── Fade-in ───────────────────────────────────────────────────────────────
    private long openTimeMs = -1;
    private static final long FADE_MS = 200L;

    // ── Hover animations & sound tracking ─────────────────────────────────────
    private final Map<String, Float> moduleHover = new HashMap<>();
    private float backBtnHover = 0f;
    private String lastHoveredModule = null; // Verhindert Sound-Spamming beim Hovern

    // ── Draggable panels ──────────────────────────────────────────────────────
    public static final Map<Module.Category, int[]> panelPos = new LinkedHashMap<>();
    private Module.Category draggingCat = null;
    private int dragOffX, dragOffY;
    private int lastMouseX, lastMouseY;

    // ── Panel scroll ──────────────────────────────────────────────────────────
    private final Map<Module.Category, Integer> panelScroll = new HashMap<>();
    private static final int PANEL_BOTTOM_PAD = 10;

    // ── Dynamic layout (Adaptive layout) ──────────────────────────────────────
    public static int dynColW = 88;
    public static int dynModH = 15;

    private static final int HDR_H   = 18;
    private static final int START_X = 10;
    private static final int START_Y = 8;

    // ── Search ──────────────────────────
    private boolean searchActive = false;
    private String  searchQuery  = "";

    public static final Map<String, Integer> keybinds = new HashMap<>();
    private String bindingModule = null;

    // ── Settings scroll ──────────────────────────────────────────────────────
    private double settingsScrollOffset = 0;
    private int settingsMaxScroll = 0;
    private static final int SETTINGS_LIST_TOP   = 38;
    private static final int SETTINGS_BOTTOM_PAD = 8;
    private static final int SCROLL_STEP         = 18;

    // ── Black & White Palette ─────────────────────────────────────────────────
    private static final int C_OVERLAY      = 0xBB000000; // semi-transparent black
    private static final int C_PANEL_BG     = 0xF20A0A0A; // near-black panel
    private static final int C_PANEL_HEADER = 0xFF181818; // dark grey header
    private static final int C_SEPARATOR    = 0xFF2C2C2C; // subtle divider
    private static final int C_ACCENT       = 0xFFFFFFFF; // white accent
    private static final int C_ACCENT_DIM   = 0xFF999999; // grey accent
    private static final int C_TEXT         = 0xFFEEEEEE; // near-white text
    private static final int C_TEXT_DIM     = 0xFF666666; // muted grey text
    private static final int C_ENABLED      = 0xFFFFFFFF; // white = on
    private static final int C_DISABLED     = 0xFF3A3A3A; // dark grey = off
    private static final int C_KEYBIND      = 0xFFBBBBBB; // light grey badge
    private static final int C_BIND_PULSE   = 0xFFFFFFFF; // white pulse

    // ─────────────────────────────────────────────────────────────────────────

    public ClickGuiScreen() {
        super(net.minecraft.network.chat.Component.literal(""));
    }

// ── Sound Utility ─────────────────────────────────────────────────────────

    public static void playGuiSound(float pitch, float volume) {
        try {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume)
            );
        } catch (Exception ignored) {}
    }

    public static void playGuiSound(float pitch) {
        playGuiSound(pitch, 0.25f);
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void init() {
        super.init();
        openTimeMs = System.currentTimeMillis();
        updateAdaptiveLayout();

        // Sound beim Öffnen der ClickGUI
        playGuiSound(1.4f, 0.35f);
    }

    /**
     * Berechnet die Breite der Kategorien dynamisch anhand der Bildschirmbreite
     * und ordnet sie so an, dass sie niemals rechts aus dem Bildschirm ragen.
     */
    private void updateAdaptiveLayout() {
        List<Module.Category> activeCats = Arrays.stream(Module.Category.values())
                .filter(cat -> !ModuleManager.getModulesByCategory(cat).isEmpty())
                .collect(Collectors.toList());

        if (activeCats.isEmpty()) return;

        int gap = 8;
        int totalAvail = this.width - (START_X * 2);

        // Dynamische Breite berechnen (Minimum 55px, Maximum 88px)
        int calculatedW = (totalAvail - (activeCats.size() - 1) * gap) / activeCats.size();
        dynColW = Math.max(55, Math.min(88, calculatedW));

        int x = START_X;
        for (Module.Category cat : activeCats) {
            if (!panelPos.containsKey(cat)) {
                panelPos.put(cat, new int[]{x, START_Y});
            }

            // Verhindern, dass Panels nach Bildschirm-Resize rechts raushängen
            int[] pos = panelPos.get(cat);
            if (pos != null) {
                pos[0] = Math.min(pos[0], Math.max(0, this.width - dynColW - 4));
            }
            x += dynColW + gap;
        }
    }

    // ── Text Utility ──────────────────────────────────────────────────────────

    private String trimToWidth(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) return text;
        String dots = "..";
        String trimmed = text;
        while (!trimmed.isEmpty() && this.font.width(trimmed + dots) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "" : trimmed + dots;
    }

    // ── Animation ─────────────────────────────────────────────────────────────

    private float rawFade() {
        if (openTimeMs < 0) return 1f;
        return Math.min(1f, (System.currentTimeMillis() - openTimeMs) / (float) FADE_MS);
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t); }

    // ── Main render ───────────────────────────────────────────────────────────

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor guiGraphics,
                                   int mouseX, int mouseY, float delta) {
        updateAdaptiveLayout(); // Sorgt jederzeit für korrekte Breiten & Positionen

        float fade = easeOut(rawFade());

        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (draggingCat != null) {
            int[] pos = panelPos.get(draggingCat);
            if (pos != null) {
                pos[0] = Math.max(0, Math.min(this.width  - dynColW - 4, mouseX - dragOffX));
                pos[1] = Math.max(0, Math.min(this.height - 40,          mouseY - dragOffY));
            }
        }

        guiGraphics.fill(0, 0, this.width, this.height,
                Component.withAlpha(C_OVERLAY, fade));

        if (selectedModule == null) {
            renderModuleList(guiGraphics, mouseX, mouseY, fade);
        } else {
            renderSettingsView(guiGraphics, mouseX, mouseY);
        }

        renderSearchBar(guiGraphics, mouseX, mouseY);
        renderKeybindPrompt(guiGraphics);

        if (fade < 1f)
            guiGraphics.fill(0, 0, this.width, this.height,
                    Component.withAlpha(0xFF000000, 1f - fade));

        super.extractRenderState(guiGraphics, mouseX, mouseY, delta);
    }

    // ── Module list ───────────────────────────────────────────────────────────

    private void renderModuleList(GuiGraphicsExtractor g,
                                  int mouseX, int mouseY, float fade) {
        Module hoveredModule = null;
        boolean anyModuleHovered = false;

        for (Module.Category cat : Module.Category.values()) {
            if (ModuleManager.getModulesByCategory(cat).isEmpty()) continue;

            List<Module> modules = getFilteredModules(cat);
            int[] pos    = panelPos.getOrDefault(cat, new int[]{START_X, START_Y});
            int   panelX = pos[0] + (int)((1f - fade) * -12);
            int   panelY = pos[1];

            int visibleRows = computeVisibleRows(panelY, modules.size());
            int frameH      = HDR_H + visibleRows * dynModH + 6;

            int maxScroll = Math.max(0, modules.size() - visibleRows);
            int scroll    = Math.max(0, Math.min(panelScroll.getOrDefault(cat, 0), maxScroll));
            panelScroll.put(cat, scroll);

            Component.drawShadow(g, panelX - 2, panelY, dynColW + 4, frameH);

            Component.drawRoundedRect(g, panelX - 2, panelY, dynColW + 4, frameH, C_PANEL_BG);
            Component.drawRoundedRect(g, panelX - 2, panelY, dynColW + 4, HDR_H, C_PANEL_HEADER);

            boolean hdrHov = mouseX >= panelX - 2 && mouseX <= panelX + dynColW + 2
                    && mouseY >= panelY      && mouseY <= panelY + HDR_H;
            int topBarColor = draggingCat == cat ? C_ACCENT
                    : hdrHov ? C_ACCENT
                    : C_ACCENT_DIM;
            g.fill(panelX, panelY, panelX + dynColW, panelY + 2, topBarColor);

            // Titel adaptiv trimmen
            String headerTitle = trimToWidth("\u2630 " + cat.name(), dynColW - 6);
            g.text(this.font, headerTitle,
                    panelX + 4, panelY + (HDR_H / 2) - 4,
                    hdrHov ? C_ACCENT : C_TEXT, false);

            g.fill(panelX, panelY + HDR_H, panelX + dynColW, panelY + HDR_H + 1, C_SEPARATOR);

            int listTop    = panelY + HDR_H + 3;
            int listBottom = listTop + visibleRows * dynModH;

            g.enableScissor(panelX - 2, listTop, panelX + dynColW + 2, listBottom);

            int rowY = listTop;

            if (modules.isEmpty()) {
                g.text(this.font, "no results",
                        panelX + 7, rowY + (dynModH / 2) - 4, C_TEXT_DIM, false);
            } else {
                for (int i = 0; i < visibleRows; i++) {
                    int idx = i + scroll;
                    if (idx >= modules.size()) break;
                    Module module = modules.get(idx);

                    boolean hov = mouseX >= panelX && mouseX <= panelX + dynColW
                            && mouseY >= rowY   && mouseY <= rowY + dynModH;

                    float hp = moduleHover.getOrDefault(module.name, 0f);
                    hp = hov ? Math.min(1f, hp + 0.14f) : Math.max(0f, hp - 0.14f);
                    moduleHover.put(module.name, hp);

                    if (hov) {
                        hoveredModule = module;
                        anyModuleHovered = true;

                        // Hover-Sound abspielen, falls neu darauf gezeigt wird
                        if (!module.name.equals(lastHoveredModule)) {
                            playGuiSound(1.8f, 0.12f);
                            lastHoveredModule = module.name;
                        }
                    }

                    if (hp > 0.01f)
                        Component.drawRoundedRect(g, panelX + 1, rowY, dynColW - 2, dynModH - 1,
                                Component.withAlpha(0xFFFFFFFF, hp * 0.07f));

                    int barColor = module.enabled
                            ? Component.lerpColor(C_DISABLED, C_ENABLED, 0.8f + hp * 0.2f)
                            : Component.lerpColor(C_DISABLED, C_ACCENT_DIM, hp * 0.6f);
                    g.fill(panelX + 1, rowY + 2, panelX + 3, rowY + dynModH - 2, barColor);

                    boolean isBound   = keybinds.containsKey(module.name);
                    boolean isBinding = module.name.equals(bindingModule);

                    int keybindWidth = 0;
                    String kStr = "";
                    if (isBound || isBinding) {
                        kStr = isBinding
                                ? ((System.currentTimeMillis() / 400) % 2 == 0 ? "[?]" : "[ ]")
                                : "[" + keyName(keybinds.get(module.name)) + "]";
                        keybindWidth = this.font.width(kStr) + 2;
                    }

                    // Modulname adaptiv trimmen
                    int maxNameWidth = dynColW - 10 - keybindWidth;
                    String displayName = trimToWidth(module.name, maxNameWidth);

                    int nameColor = module.enabled
                            ? Component.lerpColor(C_ACCENT_DIM, C_ENABLED, 0.7f + hp * 0.3f)
                            : Component.lerpColor(C_TEXT_DIM, C_TEXT, hp);
                    g.text(this.font, displayName,
                            panelX + 7, rowY + (dynModH / 2) - 4, nameColor, false);

                    if (isBound || isBinding) {
                        int kColor = isBinding ? C_BIND_PULSE : C_KEYBIND;
                        int kw = this.font.width(kStr);
                        g.text(this.font, kStr,
                                panelX + dynColW - kw - 3, rowY + (dynModH / 2) - 4, kColor, false);
                    }

                    rowY += dynModH;
                }
            }

            g.disableScissor();

            if (modules.size() > visibleRows) {
                int sbX = panelX + dynColW - 1;
                int listH = listBottom - listTop;
                int thumbH = Math.max(10, (int) (listH * (visibleRows / (float) modules.size())));
                int thumbY = listTop + (int) ((listH - thumbH) * (scroll / (float) maxScroll));
                g.fill(sbX, listTop, sbX + 2, listBottom, C_SEPARATOR);
                g.fill(sbX, thumbY, sbX + 2, thumbY + thumbH, C_ACCENT_DIM);
            }

            Component.drawRoundedOutline(g, panelX - 2, panelY, dynColW + 4, frameH, C_SEPARATOR);
        }

        // Reset hover state, wenn über keinem Modul gehovert wird
        if (!anyModuleHovered) {
            lastHoveredModule = null;
        }

        if (hoveredModule != null
                && hoveredModule.description != null
                && !hoveredModule.description.isEmpty())
            renderTooltip(g, hoveredModule.description, mouseX, mouseY);
    }

    private int computeVisibleRows(int panelY, int moduleCount) {
        int availH  = this.height - panelY - HDR_H - 6 - PANEL_BOTTOM_PAD;
        int maxRows = Math.max(1, availH / dynModH);
        int display = Math.max(1, moduleCount);
        return Math.min(display, maxRows);
    }

    private List<Module> getFilteredModules(Module.Category cat) {
        List<Module> all = ModuleManager.getModulesByCategory(cat);
        if (!searchActive || searchQuery.isEmpty()) return all;
        String q = searchQuery.toLowerCase();
        return all.stream()
                .filter(m -> m.name.toLowerCase().contains(q))
                .collect(Collectors.toList());
    }

    // ── Settings view ─────────────────────────────────────────────────────────

    private void renderSettingsView(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean backHov = mouseX >= 8 && mouseX <= 94 && mouseY >= 8 && mouseY <= 28;
        backBtnHover = backHov
                ? Math.min(1f, backBtnHover + 0.15f)
                : Math.max(0f, backBtnHover - 0.15f);

        int backBg  = Component.lerpColor(0xFF0A0A0A, C_PANEL_HEADER, backBtnHover);
        int backFg  = Component.lerpColor(C_TEXT_DIM,  C_ACCENT,      backBtnHover);
        int backBdr = Component.lerpColor(C_SEPARATOR,  C_ACCENT,     backBtnHover);

        Component.drawRoundedRect(   g, 8, 8, 86, 20, backBg);
        Component.drawRoundedOutline(g, 8, 8, 86, 20, backBdr);
        g.text(this.font, "< Back", 15, 14, backFg, false);
        g.text(this.font, "Settings  \u2014  " + selectedModule.name, 102, 14, C_TEXT, false);
        g.fill(8, 32, this.width - 8, 33, C_SEPARATOR);

        int rightX = 140;
        Component hoveredComponent = null;

        int leftHeight = 0;
        int rightHeight = 0;
        for (Component component : selectedModule.settings) {
            if (component instanceof BlockPicker || component instanceof ItemPicker || component instanceof EntityFilterPicker) {
                rightHeight += component.height + 5;
            } else {
                leftHeight += component.height + 5;
            }
        }
        int contentHeight = Math.max(leftHeight, rightHeight);

        int viewBottom = this.height - SETTINGS_BOTTOM_PAD;
        int viewHeight = Math.max(0, viewBottom - SETTINGS_LIST_TOP);
        settingsMaxScroll = Math.max(0, contentHeight - viewHeight);
        settingsScrollOffset = Math.max(0, Math.min(settingsScrollOffset, settingsMaxScroll));

        g.enableScissor(0, SETTINGS_LIST_TOP, this.width, viewBottom);

        int leftY = SETTINGS_LIST_TOP - (int) settingsScrollOffset;
        int rightY = SETTINGS_LIST_TOP - (int) settingsScrollOffset;

        for (Component component : selectedModule.settings) {
            if (component instanceof BlockPicker || component instanceof ItemPicker || component instanceof EntityFilterPicker) {
                component.x = rightX;
                component.y = rightY;
                if (component.y + component.height >= SETTINGS_LIST_TOP && component.y <= viewBottom) {
                    component.render(g, mouseX, mouseY);
                    if (component.getDescription() != null && !component.getDescription().isEmpty()
                            && mouseX >= component.x && mouseX <= component.x + component.width
                            && mouseY >= Math.max(component.y, SETTINGS_LIST_TOP)
                            && mouseY <= Math.min(component.y + component.height, viewBottom)) {
                        hoveredComponent = component;
                    }
                }
                rightY += component.height + 5;
            } else {
                component.x = 15;
                component.y = leftY;
                if (component.y + component.height >= SETTINGS_LIST_TOP && component.y <= viewBottom) {
                    component.render(g, mouseX, mouseY);
                    if (component.getDescription() != null && !component.getDescription().isEmpty()
                            && mouseX >= component.x && mouseX <= component.x + component.width
                            && mouseY >= Math.max(component.y, SETTINGS_LIST_TOP)
                            && mouseY <= Math.min(component.y + component.height, viewBottom)) {
                        hoveredComponent = component;
                    }
                }
                leftY += component.height + 5;
            }
        }

        g.disableScissor();

        if (settingsMaxScroll > 0) {
            int trackX = this.width - 6;
            Component.drawRoundedRect(g, trackX, SETTINGS_LIST_TOP, 3, viewHeight, 0xFF1A1A1A);
            int thumbH = Math.max(20, (int) (viewHeight * (viewHeight / (float) contentHeight)));
            int thumbY = SETTINGS_LIST_TOP
                    + (int) ((viewHeight - thumbH) * (settingsScrollOffset / (float) settingsMaxScroll));
            Component.drawRoundedRect(g, trackX, thumbY, 3, thumbH, C_ACCENT_DIM);
        }

        if (hoveredComponent != null)
            renderTooltip(g, hoveredComponent.getDescription(), mouseX, mouseY);
    }

    private void renderSearchBar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!searchActive) {
            String hint = "CTRL+F  |  MMB = bind key";
            g.text(this.font, hint,
                    this.width / 2 - this.font.width(hint) / 2,
                    this.height - 11, C_TEXT_DIM, false);
            return;
        }

        int barW = 200, barH = 18;
        int barX = this.width / 2 - barW / 2;
        int barY = this.height - 30;

        Component.drawRoundedRect(   g, barX, barY, barW, barH, 0xF00A0A0A);
        Component.drawRoundedOutline(g, barX, barY, barW, barH, C_ACCENT);

        boolean showCursor = searchActive && ((System.currentTimeMillis() / 500) % 2 == 0);
        String cursor  = showCursor ? "|" : "";

        String display = "\u26b2  " + searchQuery + cursor;
        g.text(this.font, display, barX + 6, barY + 5, C_TEXT, false);

        String esc = "ESC";
        g.text(this.font, esc, barX + barW - this.font.width(esc) - 5, barY + 5, C_TEXT_DIM, false);
    }

    private void renderKeybindPrompt(GuiGraphicsExtractor g) {
        if (bindingModule == null) return;
        String msg = "  Press a key to bind: " + bindingModule + "  (ESC = clear)  ";
        int w  = this.font.width(msg) + 8;
        int bx = this.width / 2 - w / 2, by = 4;
        Component.drawRoundedRect(   g, bx, by, w, 15, 0xFF0A0A0A);
        Component.drawRoundedOutline(g, bx, by, w, 15, C_ACCENT);
        g.text(this.font, msg, bx + 4, by + 4, C_BIND_PULSE, false);
    }

    private void renderTooltip(GuiGraphicsExtractor g, String desc, int mx, int my) {
        List<net.minecraft.network.chat.FormattedText> lines =
                this.font.getSplitter().splitLines(
                        net.minecraft.network.chat.Component.literal(desc), 200, Style.EMPTY);

        int tw = 0;
        for (var l : lines) tw = Math.max(tw, this.font.width(l));

        int lh = this.font.lineHeight, pad = 5;
        int totalH = lines.size() * lh + pad * 2;
        int tx = mx + 14, ty = my - 14;
        if (tx + tw + pad * 2 > this.width)  tx = mx - tw - 18;
        if (ty + totalH > this.height)       ty = my - totalH;

        Component.drawShadow(        g, tx - pad, ty - pad, tw + pad * 2, totalH);
        Component.drawRoundedRect(   g, tx - pad, ty - pad, tw + pad * 2, totalH, 0xF00A0A0A);
        Component.drawRoundedOutline(g, tx - pad, ty - pad, tw + pad * 2, totalH, C_ACCENT);

        int cy = ty;
        for (var l : lines) { g.text(this.font, l.getString(), tx, cy, C_TEXT); cy += lh; }
    }

    private static final Map<Integer, String> KEY_NAMES = new HashMap<>();
    static {
        for (int k = GLFW.GLFW_KEY_A; k <= GLFW.GLFW_KEY_Z; k++)
            KEY_NAMES.put(k, String.valueOf((char) k));
        for (int k = GLFW.GLFW_KEY_0; k <= GLFW.GLFW_KEY_9; k++)
            KEY_NAMES.put(k, String.valueOf((char) k));
        for (int f = 0; f < 12; f++)
            KEY_NAMES.put(GLFW.GLFW_KEY_F1 + f, "F" + (f + 1));
        KEY_NAMES.put(GLFW.GLFW_KEY_TAB,           "TAB");
        KEY_NAMES.put(GLFW.GLFW_KEY_LEFT_SHIFT,    "LSHIFT");
        KEY_NAMES.put(GLFW.GLFW_KEY_RIGHT_SHIFT,   "RSHIFT");
        KEY_NAMES.put(GLFW.GLFW_KEY_LEFT_CONTROL,  "LCTRL");
        KEY_NAMES.put(GLFW.GLFW_KEY_RIGHT_CONTROL, "RCTRL");
        KEY_NAMES.put(GLFW.GLFW_KEY_LEFT_ALT,      "ALT");
        KEY_NAMES.put(GLFW.GLFW_KEY_SPACE,         "SPC");
        KEY_NAMES.put(GLFW.GLFW_KEY_INSERT,        "INS");
        KEY_NAMES.put(GLFW.GLFW_KEY_DELETE,        "DEL");
        KEY_NAMES.put(GLFW.GLFW_KEY_HOME,          "HOME");
        KEY_NAMES.put(GLFW.GLFW_KEY_END,           "END");
        KEY_NAMES.put(GLFW.GLFW_KEY_PAGE_UP,       "PGUP");
        KEY_NAMES.put(GLFW.GLFW_KEY_PAGE_DOWN,     "PGDN");
        KEY_NAMES.put(GLFW.GLFW_KEY_CAPS_LOCK,     "CAPS");
    }
    private static String keyName(int key) {
        return KEY_NAMES.getOrDefault(key, "K" + key);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        int mx = (int) event.x(), my = (int) event.y(), btn = event.button();

        if (selectedModule == null) {
            if (btn == 0) {
                for (Module.Category cat : Module.Category.values()) {
                    int[] pos = panelPos.getOrDefault(cat, new int[]{START_X, START_Y});
                    if (mx >= pos[0] - 2 && mx <= pos[0] + dynColW + 2
                            && my >= pos[1]   && my <= pos[1] + HDR_H) {
                        draggingCat = cat;
                        dragOffX = mx - pos[0];
                        dragOffY = my - pos[1];
                        playGuiSound(1.1f, 0.2f);
                        return true;
                    }
                }
            }

            for (Module.Category cat : Module.Category.values()) {
                int[] pos = panelPos.getOrDefault(cat, new int[]{START_X, START_Y});
                List<Module> modules = getFilteredModules(cat);
                int visibleRows = computeVisibleRows(pos[1], modules.size());
                int maxScroll   = Math.max(0, modules.size() - visibleRows);
                int scroll      = Math.max(0, Math.min(panelScroll.getOrDefault(cat, 0), maxScroll));

                int rowY = pos[1] + HDR_H + 3;
                for (int i = 0; i < visibleRows; i++) {
                    int idx = i + scroll;
                    if (idx >= modules.size()) break;
                    Module module = modules.get(idx);
                    if (mx >= pos[0] && mx <= pos[0] + dynColW
                            && my >= rowY && my <= rowY + dynModH) {
                        if (btn == 1) {
                            selectedModule = module;
                            bindingModule = null;
                            settingsScrollOffset = 0;
                            playGuiSound(1.2f, 0.3f);
                        }
                        else if (btn == 0) {
                            module.toggle();
                            playGuiSound(module.enabled ? 1.0f : 0.8f, 0.3f);
                        }
                        else if (btn == 2) {
                            bindingModule = module.name;
                            playGuiSound(0.9f, 0.3f);
                        }
                        return true;
                    }
                    rowY += dynModH;
                }
            }
        } else {
            if (mx >= 8 && mx <= 94 && my >= 8 && my <= 28) {
                selectedModule = null;
                settingsScrollOffset = 0;
                playGuiSound(0.85f, 0.3f);
                return true;
            }

            int viewBottom = this.height - SETTINGS_BOTTOM_PAD;

            for (Component c : selectedModule.settings) {
                if (c instanceof BlockPicker || c instanceof ItemPicker || c instanceof EntityFilterPicker) {
                    if (c.y + c.height >= SETTINGS_LIST_TOP && c.y <= viewBottom) {
                        if (c.mouseClicked(mx, my, btn)) return true;
                    }
                }
            }

            for (Component c : selectedModule.settings) {
                if (!(c instanceof BlockPicker || c instanceof ItemPicker || c instanceof EntityFilterPicker)) {
                    if (c.y + c.height >= SETTINGS_LIST_TOP && c.y <= viewBottom) {
                        if (c.mouseClicked(mx, my, btn)) return true;
                    }
                }
            }
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingCat = null;
        if (selectedModule != null)
            for (Component c : selectedModule.settings)
                c.mouseReleased(event.x(), event.y(), event.button());
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        if (selectedModule != null) {
            for (Component c : selectedModule.settings) {
                if (c instanceof BlockPicker bp && bp.mouseScrolled(mx, my, vAmt)) return true;
                if (c instanceof ItemPicker ip && ip.mouseScrolled(mx, my, vAmt)) return true;
                if (c instanceof EntityFilterPicker ep && ep.mouseScrolled(mx, my, vAmt)) return true;
            }
            if (settingsMaxScroll > 0) {
                settingsScrollOffset -= vAmt * SCROLL_STEP;
                settingsScrollOffset = Math.max(0, Math.min(settingsScrollOffset, settingsMaxScroll));
                return true;
            }
        } else {
            for (Module.Category cat : Module.Category.values()) {
                int[] pos = panelPos.getOrDefault(cat, new int[]{START_X, START_Y});
                List<Module> modules = getFilteredModules(cat);
                int visibleRows = computeVisibleRows(pos[1], modules.size());
                int frameH      = HDR_H + visibleRows * dynModH + 6;

                if (mx >= pos[0] - 2 && mx <= pos[0] + dynColW + 2
                        && my >= pos[1] && my <= pos[1] + frameH) {
                    int maxScroll = Math.max(0, modules.size() - visibleRows);
                    if (maxScroll <= 0) return true;
                    int scroll = panelScroll.getOrDefault(cat, 0);
                    if      (vAmt > 0 && scroll > 0)         scroll--;
                    else if (vAmt < 0 && scroll < maxScroll) scroll++;
                    panelScroll.put(cat, scroll);
                    return true;
                }
            }
        }
        return super.mouseScrolled(mx, my, hAmt, vAmt);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int key  = event.key();
        int mods = event.modifiers();

        if (bindingModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                keybinds.remove(bindingModule);
                playGuiSound(0.7f, 0.3f);
            } else {
                keybinds.put(bindingModule, key);
                playGuiSound(1.3f, 0.3f);
            }
            bindingModule = null;
            return true;
        }

        if (key == GLFW.GLFW_KEY_F && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            searchActive = !searchActive;
            searchQuery  = "";
            playGuiSound(searchActive ? 1.5f : 0.9f, 0.25f);
            return true;
        }

        if (searchActive && key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            return true;
        }

        if (selectedModule != null) {
            for (Component c : selectedModule.settings) {
                if (c.keyPressed(event)) return true;
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (searchActive) {
                searchActive = false;
                searchQuery = "";
                playGuiSound(0.8f, 0.25f);
                return true;
            }
            if (selectedModule != null) {
                selectedModule = null;
                playGuiSound(0.85f, 0.3f);
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (searchActive && bindingModule == null) {
            char ch = (char) event.codepoint();
            if (ch >= 32) { searchQuery += ch; return true; }
        }
        if (selectedModule != null)
            for (Component c : selectedModule.settings)
                if (c.charTyped(event)) return true;
        return super.charTyped(event);
    }

    @Override public void onClose()          { Config.save(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }
}