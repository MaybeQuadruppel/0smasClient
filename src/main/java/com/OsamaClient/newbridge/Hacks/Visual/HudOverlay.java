package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.ClickGui;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import com.OsamaClient.newbridge.config.ConfigCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import com.OsamaClient.newbridge.UI.gui.input.Keys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Info-Overlay ("HUD"): Coordinates, Pitch/Yaw/Facing, Health, Armor-Haltbarkeit und ein paar optionale
 * Zeilen (Speed, FPS, Ping, Biome). Jede Kategorie ist eine eigene, frei draggable Box - "Edit Layout"
 * (Setting weiter unten) öffnet dafür einen blanken {@link HudOverlayScreen}, exakt nach demselben
 * Muster wie ClickGui/ClickGuiScreen: der Screen fängt nur Input ab, gezeichnet wird weiterhin
 * ausschließlich hier über die eigene {@link UiRenderer}/{@link Ui}-Pipeline, jeden Frame vom
 * {@code GameRendererUiMixin} aus - unabhängig davon, ob gerade editiert wird oder nicht.
 *
 * Icons: unsere eigene Pipeline kennt nur SDF-Shapes und FreeType-Text, keine Item-Texturen - die farbigen
 * Badges hier sind deshalb grundsätzlich Monogramm-Buchstaben. Für Armor sitzt zusätzlich ein echtes
 * Item-Icon im Badge-Rahmen: das läuft NICHT über diese Pipeline, sondern über {@link HudOverlayItemLayer}
 * (Fabric {@code HudElementRegistry}, läuft vor {@code GuiRenderer#render()} und bekommt dadurch noch einen
 * echten {@code GuiGraphicsExtractor} - der einzige Weg in 26.2, ein Item tatsächlich zu zeichnen, siehe
 * dort für Details). Diese Klasse liefert dem Layer nur die Positionen/Stacks über {@link HudIcons}.
 */
public class HudOverlay extends Module {

    public static HudOverlay instance;

    // ── Style ────────────────────────────────────────────────────────────────
    public final Slider scale = new Slider("Text Scale", 0.5, 1.5, 1.0, 0.05, null)
            .withDescription("Size of text and icons");
    public final ToggleButton background = new ToggleButton("Background", true, null)
            .withDescription("Draws a panel box behind every group of lines");
    public final ToggleButton icons = new ToggleButton("Icons", true, null)
            .withDescription("Shows a small icon badge in front of every line");
    public final ToggleButton editLayout = new ToggleButton("Edit Layout", false,
            val -> { if (val) openEditor(); else closeEditor(); })
            .withDescription("Opens a screen where you can drag every HUD box with the mouse (Esc to finish)");

    // ── Which rows ───────────────────────────────────────────────────────────
    public final ToggleButton showCoords = new ToggleButton("Coordinates", true, null)
            .withDescription("X / Y / Z");
    public final ToggleButton showFacing = new ToggleButton("Pitch/Yaw", true, null)
            .withDescription("Pitch, yaw and the rough facing direction (N/NE/E/...)");
    public final ToggleButton showHealth = new ToggleButton("Health", true, null)
            .withDescription("Current / max health, colour goes from red to green");
    public final ToggleButton showArmor = new ToggleButton("Armor", true, null)
            .withDescription("Durability of every armour piece you wear");
    public final ToggleButton showSpeed = new ToggleButton("Speed", false, null)
            .withDescription("Horizontal movement speed in blocks/second");
    public final ToggleButton showFps = new ToggleButton("FPS", false, null)
            .withDescription("Current frame rate");
    public final ToggleButton showPing = new ToggleButton("Ping", false, null)
            .withDescription("Ping to the server in ms");
    public final ToggleButton showTps = new ToggleButton("TPS", false, null)
            .withDescription("Estimated server TPS (from the time packets), turns red when the server lags");
    public final ToggleButton showBiome = new ToggleButton("Biome", false, null)
            .withDescription("The biome you are standing in");

    public final ToggleButton showHeld = new ToggleButton("Held Item", true, null)
            .withDescription("The item in your main hand with its durability");
    public final ToggleButton showItems = new ToggleButton("Item Counts", true, null)
            .withDescription("Totems, pearls, golden apples, crystals, XP bottles, fireworks and arrows in your inventory (with item icons)");
    public final ToggleButton showEffects = new ToggleButton("Effects", true, null)
            .withDescription("Active potion effects with level and remaining time");
    public final ToggleButton showTime = new ToggleButton("Clock", true, null)
            .withDescription("Real time of day");
    public final ToggleButton showServer = new ToggleButton("Server", true, null)
            .withDescription("Address of the server you are on");
    public final ToggleButton showPlayers = new ToggleButton("Players", true, null)
            .withDescription("Number of players around you");
    public final ToggleButton showKeys = new ToggleButton("Keystrokes", true, null)
            .withDescription("WASD, Space and mouse buttons as little keys that light up while pressed");
    public final ToggleButton showCps = new ToggleButton("Keystroke CPS", true, null)
            .withDescription("Shows clicks per second on the mouse keys");

    // eigene Pipeline, unabhängig von ClickGui - zeichnet jeden Frame, egal ob die ClickGUI offen ist
    private static final UiRenderer renderer = new UiRenderer();
    private static final Ui ui = new Ui(renderer);

    private static final float ROW_H = 11f, ICON = 8f, PAD = 4f, GAP = 3f, WIDGET_GAP = 4f;
    private static final float CAPTION_H = 9f, ANIM_SPEED = 0.16f;
    private static final String[] WIDGET_IDS = {"coords", "facing", "health", "armor", "speed", "fps", "ping", "biome", "held", "items", "effects", "info", "keys"};
    private static final String[] WIDGET_TITLES =
            {"Coordinates", "Facing", "Health", "Armor", "Speed", "FPS", "Ping", "Biome", "Held Item", "Items", "Effects", "Info", "Keys"};

    private final Map<String, Widget> widgets = new LinkedHashMap<>();
    private boolean editMode;
    private int buttonsDown;
    private boolean pendingClick, pendingRight, widgetGrabbed;
    private double pendingScroll;
    private String scaleMsg = "";
    private long scaleMsgUntil;

    public HudOverlay() {
        super("HudOverlay", "Draggable info panel: coordinates, pitch/yaw, health, armor and more", Category.VISUAL);
        instance = this;
        settings.add(scale);
        settings.add(background);
        settings.add(icons);
        settings.add(editLayout);
        settings.add(showCoords);
        settings.add(showFacing);
        settings.add(showHealth);
        settings.add(showArmor);
        settings.add(showSpeed);
        settings.add(showFps);
        settings.add(showPing);
        settings.add(showTps);
        settings.add(showBiome);
        settings.add(showHeld);
        settings.add(showItems);
        settings.add(showEffects);
        settings.add(showTime);
        settings.add(showServer);
        settings.add(showPlayers);
        settings.add(showKeys);
        settings.add(showCps);

        HudOverlayItemLayer.register();
    }

    /** Custom (dragged) widget positions for saving; a box the user never moved keeps flowing automatically. */
    public Map<String, ConfigCodec.WidgetPos> widgetPositions() {
        Map<String, ConfigCodec.WidgetPos> out = new LinkedHashMap<>(HudLayout.positions());
        widgets.forEach((id, w) -> { if (w.customPos) out.put(id, new ConfigCodec.WidgetPos(w.x, w.y)); });
        return out;
    }

    /** Restores previously saved widget positions (applied lazily as each box is first drawn). */
    public void applyWidgetPositions(Map<String, ConfigCodec.WidgetPos> savedAll) {
        Map<String, ConfigCodec.WidgetPos> saved = HudLayout.apply(savedAll);
        saved.forEach((id, p) -> {
            Widget w = widgets.computeIfAbsent(id, k -> new Widget());
            w.x = p.x();
            w.y = p.y();
            w.customPos = true;
        });
    }

    // ── Editor (drag mode) ──────────────────────────────────────────────────

    public void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { editLayout.enabled = false; return; }
        if (!(mc.gui.screen() instanceof HudOverlayScreen)) mc.gui.setScreen(new HudOverlayScreen());
        editMode = true;
    }

    public void closeEditor() {
        editMode = false;
        buttonsDown = 0;
        pendingClick = false;
        pendingRight = false;
        HudLayout.stopDrag();
        for (Widget w : widgets.values()) w.dragging = false;
        editLayout.enabled = false;
        if (Minecraft.getInstance().gui.screen() instanceof HudOverlayScreen) Minecraft.getInstance().gui.setScreen(null);
    }

    /** Called by {@link HudOverlayScreen#removed()} if the screen gets closed some other way than Esc. */
    void screenClosed() {
        editMode = false;
        editLayout.enabled = false;
        HudLayout.stopDrag();
        for (Widget w : widgets.values()) w.dragging = false;
    }

    /** true while the layout editor is open (other HUD elements draw a sample and report their rectangle). */
    public static boolean editing() {
        return instance != null && instance.editMode && Minecraft.getInstance().gui.screen() instanceof HudOverlayScreen;
    }

    private void layoutEditor() {
        Minecraft mc = Minecraft.getInstance();
        float mx = UiInput.mouseFbX(), my = UiInput.mouseFbY();
        int fbW = mc.getWindow().getWidth(), fbH = mc.getWindow().getHeight();
        if (HudLayout.dragging()) {
            if ((buttonsDown & 1) != 0) HudLayout.dragTo(mx, my, fbW, fbH);
            else HudLayout.stopDrag();
        } else if (pendingClick && !widgetGrabbed && noWidgetDragging()) {
            HudLayout.beginDrag(mx, my);
        }
        if (pendingRight && !HudLayout.resetAt(mx, my)) {
            String id = widgetAt(mx, my);
            if (id != null) {
                Widget w = widgets.get(id);
                w.customPos = false;
                HudLayout.resetScale(id);
            }
        }
        if (pendingScroll != 0) {
            HudLayout.Elem e = HudLayout.elementAt(mx, my);
            String id = e != null ? e.id : widgetAt(mx, my);
            String label = e != null ? e.label : id == null ? null : WIDGET_TITLES[java.util.Arrays.asList(WIDGET_IDS).indexOf(id)];
            if (e != null && !HudLayout.has(e.id)) {
                // pin the element where it is, otherwise its default spot (computed with its own scale) moves away
                HudLayout.set(e.id, (e.anchorRight ? e.x + e.w : e.x) / fbW, e.y / fbH);
            }
            if (id != null) {
                float sc2 = HudLayout.addScale(id, pendingScroll);
                scaleMsg = label + "  " + Math.round(sc2 * 100) + "%";
                scaleMsgUntil = System.currentTimeMillis() + 1200;
            }
            pendingScroll = 0;
        }
        float sc = ui.scale;
        if (System.currentTimeMillis() < scaleMsgUntil && !scaleMsg.isEmpty()) {
            float tw = ui.textWidth(scaleMsg, 6.5f) + 8f;
            float bx = mx / sc + 10f, by = my / sc - 12f;
            ui.round(bx, by, tw, 10f, 2.5f, ColorUtil.alpha(0xFF101014, 0.95f));
            ui.outline(bx, by, tw, 10f, 2.5f, 1f, Theme.accent(0.8f));
            ui.text(scaleMsg, bx + 4f, by, 10f, 6.5f, Theme.TEXT);
        }
        for (HudLayout.Elem e : HudLayout.visible()) {
            float x = e.x / sc, y = e.y / sc, w = e.w / sc, h = e.h / sc;
            boolean hov = mx >= e.x && mx <= e.x + e.w && my >= e.y && my <= e.y + e.h;
            boolean drag = e.id.equals(HudLayout.draggingId());
            ui.round(x, y, w, h, Theme.radius(), Theme.accent(drag ? 0.14f : hov ? 0.08f : 0.03f));
            ui.outline(x, y, w, h, Theme.radius(), 1f, Theme.accent(drag || hov ? 0.95f : 0.5f));
            float cw = ui.textWidth(e.label, 5.6f) + 5f;
            float cy = y - 8f < 0 ? y + h + 1f : y - 8f;
            ui.round(x, cy, cw, 7f, 2f, Theme.accent(0.85f));
            ui.text(e.label, x + 2.5f, cy, 7f, 5.6f, 0xFF0B0B10);
        }
    }

    /** Topmost HudOverlay box under the point (fb px), or null. */
    private String widgetAt(float fx, float fy) {
        String hit = null;
        for (int i = 0; i < WIDGET_IDS.length; i++) {
            Widget w = widgets.get(WIDGET_IDS[i]);
            if (w == null || w.fb == null || !w.visibleFrame || w.appear < 0.5f) continue;
            float[] r = w.fb;
            if (fx >= r[0] && fx <= r[0] + r[2] && fy >= r[1] && fy <= r[1] + r[3]) hit = WIDGET_IDS[i];
        }
        return hit;
    }

    /** Last drawn rect of a HudOverlay box in fb px (tests), or null. */
    public float[] widgetRect(String id) {
        Widget w = widgets.get(id);
        return w == null || w.fb == null ? null : w.fb.clone();
    }

    void mouseScrolled(double amount) {
        if (amount != 0) pendingScroll += amount;
    }

    /** Button bit: 1 = left, 2 = right, 4 = middle. 26.x numbers mouse buttons from 1 (SDL), so 1 << button is wrong. */
    private static int bit(int button) {
        if (button == com.OsamaClient.newbridge.UI.gui.input.Keys.MOUSE_BUTTON_LEFT) return 1;
        if (button == com.OsamaClient.newbridge.UI.gui.input.Keys.MOUSE_BUTTON_RIGHT) return 2;
        if (button == com.OsamaClient.newbridge.UI.gui.input.Keys.MOUSE_BUTTON_MIDDLE) return 4;
        return 0;
    }

    void mouseClicked(int button) {
        buttonsDown |= bit(button);
        if (button == com.OsamaClient.newbridge.UI.gui.input.Keys.MOUSE_BUTTON_LEFT) pendingClick = true;
        if (button == com.OsamaClient.newbridge.UI.gui.input.Keys.MOUSE_BUTTON_RIGHT) pendingRight = true;
    }

    void mouseReleased(int button) {
        buttonsDown &= ~bit(button);
        for (Widget w : widgets.values()) w.dragging = false;
        HudLayout.stopDrag();
    }

    void keyPressed(int key, int mods) {
        if (key == Keys.KEY_ESCAPE) closeEditor();
    }

    // ── HUD draw - einmal pro Frame, siehe GameRendererUiMixin ─────────────────

    public static void draw() {
        Minecraft mc = Minecraft.getInstance();
        if (instance == null || mc.player == null) return;

        boolean editing = instance.editMode && mc.gui.screen() instanceof HudOverlayScreen;
        if (!editing && (!instance.enabled || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()
                || ClickGui.INSTANCE.isOpen() || mc.gui.screen() != null)) {
            return;
        }

        renderer.begin();
        ui.scale = Theme.scale() * (float) instance.scale.getValue();
        ui.alpha = 1f;
        ui.hoverBlocked = !editing;
        if (editing) {
            ui.mouseX = UiInput.mouseFbX() / ui.scale;
            ui.mouseY = UiInput.mouseFbY() / ui.scale;
        }

        instance.widgetGrabbed = false;
        instance.drawWidgets(mc, editing);
        if (editing) instance.layoutEditor();

        if (editing) {
            String hint = "Drag to move  ·  Mouse wheel to resize  ·  Right-click to reset  ·  Esc to finish";
            float hs = 6.5f, hh = 9f;
            float hw = ui.textWidth(hint, hs) + Theme.PAD * 2;
            float hx = 6f, hy = ui.height() - hh - 4f;
            ui.round(hx, hy, hw, hh, 2f, ColorUtil.alpha(0xFF101014, 0.92f));
            ui.outline(hx, hy, hw, hh, 2f, 1f, Theme.accent(0.35f));
            ui.text(hint, hx + Theme.PAD, hy, hh, hs, Theme.TEXT);
        }

        renderer.flush();
        UiFont.collect(600);
        instance.pendingClick = false;
        instance.pendingRight = false;
    }

    /**
     * Echte Item-Icons laufen NICHT mehr über diese Pipeline (die kennt nur SDF/Text), sondern über den
     * separaten {@link HudOverlayItemLayer}, der via {@code HudElementRegistry} vor {@code GuiRenderer#render()}
     * läuft und deshalb noch einen echten {@code GuiGraphicsExtractor} bekommt. Wir sammeln hier nur, WAS
     * gezeichnet werden soll (in framebuffer-Pixeln, unser Koordinatensystem) - der Layer liest das eine
     * Zeile weiter unten aus und rechnet selbst auf GUI-skalierte Pixel um. Ein Frame Verzögerung zwischen
     * Badge-Position (hier) und echtem Icon (im nächsten Extract-Pass) ist bei normaler Framerate unsichtbar.
     */

    private void drawWidgets(Minecraft mc, boolean editing) {
        float textSize = Theme.FONT;
        boolean showIcons = icons.enabled;
        float flowY = 8f;
        float glow = Theme.glow();

        float base = ui.scale;
        float fbMx = UiInput.mouseFbX(), fbMy = UiInput.mouseFbY();
        for (int idx = 0; idx < WIDGET_IDS.length; idx++) {
            String id = WIDGET_IDS[idx];
            String title = WIDGET_TITLES[idx];
            // per-box size (mouse wheel in the layout editor); x/y stay in base units, drawing happens in local units
            float ws = HudLayout.scale(id);
            ui.scale = base * ws;
            if (editing) { ui.mouseX = fbMx / ui.scale; ui.mouseY = fbMy / ui.scale; }
            boolean special = id.equals("keys");
            List<Row> rows = special ? (showKeys.enabled ? KEYS_MARKER : List.of()) : rowsFor(id, mc);
            boolean active = !rows.isEmpty();

            Widget w = widgets.computeIfAbsent(id, k -> new Widget());
            if (active) w.lastRows = rows;
            w.appear = approach(w.appear, active ? 1f : 0f, ANIM_SPEED);
            if (!active && w.appear < 0.01f) continue; // fully faded and gone: don't reserve flow space either

            List<Row> drawRows = active ? rows : w.lastRows;
            if (drawRows == null || drawRows.isEmpty()) continue;

            float maxTextW = 0f;
            for (Row r : drawRows) maxTextW = Math.max(maxTextW, ui.textWidth(r.text(), textSize));
            float captionW = ui.textWidth(title, Theme.FONT_SMALL);
            float contentW = (showIcons ? ICON + GAP : 0f) + maxTextW;
            float boxW = Math.max(contentW, captionW) + PAD * 2;
            float boxH = CAPTION_H + drawRows.size() * ROW_H + PAD * 2;
            if (special) {
                boxW = Math.max(KEY_AREA_W, captionW) + PAD * 2;
                boxH = CAPTION_H + KEY_AREA_H + PAD * 2;
            }

            Widget wRef = w;
            wRef.w = boxW;
            wRef.h = boxH;
            if (!wRef.customPos) { wRef.x = 8f; wRef.y = flowY; }
            flowY += boxH * ws + WIDGET_GAP;

            if (editing && wRef.dragging && (buttonsDown & 1) != 0) {
                wRef.x = (ui.mouseX - wRef.grabDX) * ws;
                wRef.y = (ui.mouseY - wRef.grabDY) * ws;
                wRef.customPos = true;
            }
            final float X = wRef.x / ws, Y = wRef.y / ws;
            wRef.fb = new float[]{X * ui.scale, Y * ui.scale, boxW * ui.scale, boxH * ui.scale};
            wRef.visibleFrame = active;

            boolean hovered = editing && ui.hovered(X, Y, wRef.w, wRef.h);
            // elements drawn on top (Split Chat, Check Meter, ...) win over an overlapping overlay widget
            if (editing && pendingClick && hovered && noWidgetDragging() && !HudLayout.hitsElement(UiInput.mouseFbX(), UiInput.mouseFbY())) {
                wRef.dragging = true;
                widgetGrabbed = true;
                wRef.grabDX = ui.mouseX - X;
                wRef.grabDY = ui.mouseY - Y;
            }

            float oldAlpha = ui.alpha;
            ui.alpha = oldAlpha * wRef.appear;

            boolean drawBg = background.enabled || editing;
            if (drawBg) {
                // subtle accent glow behind the box, same recipe as ClickGui panels, so the overlay
                // reads as part of the same UI language instead of a bare list of floating text
                if (glow > 0f) ui.glow(X, Y, wRef.w, wRef.h, Theme.radius(), 6f, Theme.accent(0.16f * glow));
                int fill = background.enabled ? Theme.PANEL_BG : ColorUtil.alpha(0xFF000000, 0.28f);
                ui.round(X, Y, wRef.w, wRef.h, Theme.radius(), fill);
            }
            if (editing) {
                ui.outline(X, Y, wRef.w, wRef.h, Theme.radius(), 1f, Theme.accent(hovered ? 0.9f : 0.45f));
            } else if (background.enabled && Theme.outline()) {
                ui.outline(X, Y, wRef.w, wRef.h, Theme.radius(), 1f, Theme.OUTLINE);
            }
            if (drawBg) {
                // left accent bar, mirrors the same "this is one group" cue used by the module-settings
                // block and the teammate/module HUD lists elsewhere in the theme
                ui.round(X, Y + 1.5f, 1.3f, wRef.h - 3f, 0.65f, Theme.accent(0.6f));
            }

            float captionX = X + PAD + (showIcons ? ICON + GAP : 0f);
            if (showIcons) com.OsamaClient.newbridge.UI.gui.render.Icons.draw(ui, WIDGET_ICONS.get(id), X + PAD + 1f, Y + PAD + (CAPTION_H - 6f) * 0.5f - 1f, 6f,
                    ColorUtil.alpha(Theme.accent(), 0.85f));
            ui.text(title.toUpperCase(java.util.Locale.ROOT), captionX, Y + PAD - 1f, CAPTION_H, Theme.FONT_SMALL,
                    ColorUtil.alpha(Theme.TEXT_DIM, 0.9f));

            float rowY = Y + PAD + CAPTION_H;
            if (special) drawKeystrokes(mc, X + PAD, rowY);
            else for (Row r : drawRows) {
                float rowX = X + PAD;
                if (showIcons) {
                    float iconY = rowY + (ROW_H - ICON) * 0.5f;
                    boolean realItem = r.item() != null && !r.item().isEmpty();
                    drawBadge(rowX, iconY, r.badgeColor(), r.icon(), !realItem);
                    if (realItem) {
                        HudIcons.item(r.item(), (rowX + 0.5f) * ui.scale,
                                (iconY + 0.5f) * ui.scale, (ICON - 1f) * ui.scale);
                    }
                    rowX += ICON + GAP;
                }
                ui.text(r.text(), rowX, rowY, ROW_H, textSize, Theme.TEXT);
                if (r.ratio() >= 0f) {
                    float barW = X + wRef.w - PAD - rowX;
                    float barY = rowY + ROW_H - 2.6f;
                    float ratio = Math.max(0f, Math.min(1f, r.ratio()));
                    ui.round(rowX, barY, barW, 1.4f, 0.7f, Theme.TRACK);
                    if (ratio > 0.01f) ui.round(rowX, barY, barW * ratio, 1.4f, 0.7f, r.color());
                }
                rowY += ROW_H;
            }

            ui.alpha = oldAlpha;
        }
        ui.scale = base;
        if (editing) { ui.mouseX = fbMx / base; ui.mouseY = fbMy / base; }
    }

    // ── Keystrokes ───────────────────────────────────────────────────────────

    private static final List<Row> KEYS_MARKER = List.of(new Row("", 0, ""));
    private static final float KEY = 10f, KEY_GAP = 1.5f;
    private static final float KEY_AREA_W = KEY * 3 + KEY_GAP * 2, KEY_AREA_H = KEY * 4 + KEY_GAP * 3;
    private final float[] keyA = new float[7];
    private boolean prevLeft, prevRight;
    private final java.util.ArrayDeque<Long> leftClicks = new java.util.ArrayDeque<>(), rightClicks = new java.util.ArrayDeque<>();

    private static int cps(java.util.ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.pollFirst();
        return q.size();
    }

    private void drawKeystrokes(Minecraft mc, float x, float y) {
        var o = mc.options;
        boolean[] down = {o.keyUp.isDown(), o.keyLeft.isDown(), o.keyDown.isDown(), o.keyRight.isDown(),
                o.keyAttack.isDown(), o.keyUse.isDown(), o.keyJump.isDown()};
        long now = System.currentTimeMillis();
        if (down[4] && !prevLeft) leftClicks.addLast(now);
        if (down[5] && !prevRight) rightClicks.addLast(now);
        prevLeft = down[4];
        prevRight = down[5];
        for (int i = 0; i < keyA.length; i++) keyA[i] = approach(keyA[i], down[i] ? 1f : 0f, 0.35f);
        float step = KEY + KEY_GAP, half = (KEY_AREA_W - KEY_GAP) / 2f;
        keyCell(x + step, y, KEY, "W", keyA[0]);
        keyCell(x, y + step, KEY, "A", keyA[1]);
        keyCell(x + step, y + step, KEY, "S", keyA[2]);
        keyCell(x + step * 2, y + step, KEY, "D", keyA[3]);
        String l = showCps.enabled ? "L " + cps(leftClicks, now) : "LMB";
        String r = showCps.enabled ? "R " + cps(rightClicks, now) : "RMB";
        keyCellWide(x, y + step * 2, half, l, keyA[4]);
        keyCellWide(x + half + KEY_GAP, y + step * 2, half, r, keyA[5]);
        keyCellWide(x, y + step * 3, KEY_AREA_W, "", keyA[6]);
        float a = keyA[6];
        ui.round(x + KEY_AREA_W * 0.3f, y + step * 3 + KEY * 0.5f - 0.5f, KEY_AREA_W * 0.4f, 1f, 0.5f,
                ColorUtil.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, a));
    }

    private void keyCell(float x, float y, float size, String label, float a) { keyCellWide(x, y, size, label, a); }

    private void keyCellWide(float x, float y, float w, String label, float a) {
        float r = Math.max(1.2f, Theme.radius() * 0.8f);
        int accent = Theme.accent();
        ui.round(x, y, w, KEY, r, ColorUtil.lerp(0xFF1B1B21, ColorUtil.alpha(accent, 0.9f), a * 0.85f));
        ui.outline(x, y, w, KEY, r, 1f, ColorUtil.lerp(Theme.OUTLINE, accent, a));
        if (!label.isEmpty())
            ui.textCentered(label, x + w * 0.5f, y, KEY, Theme.FONT_SMALL, ColorUtil.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, a));
    }

    private boolean noWidgetDragging() {
        for (Widget w : widgets.values()) if (w.dragging) return false;
        return true;
    }

    private static float approach(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.min(1f, speed);
    }

    /** Kleines abgerundetes Icon-Badge; bei einem echten Item (siehe {@link HudIcons}) nur Rahmen +
     *  Hintergrund als Fassung ums Icon, ohne Monogramm-Buchstaben (sonst läge Text auf dem Icon). */
    private static void drawBadge(float x, float y, int color, String label, boolean drawLabel) {
        float r = ICON * 0.28f;
        ui.round(x, y, ICON, ICON, r, ColorUtil.alpha(color, 0.20f));
        ui.outline(x, y, ICON, ICON, r, 1f, ColorUtil.alpha(color, 0.7f));
        if (!drawLabel) return;
        String icon = rowIcon(label);
        if (icon == null || !com.OsamaClient.newbridge.UI.gui.render.Icons.draw(ui, icon, x + 1.2f, y + 1.2f, ICON - 2.4f, color))
            ui.textCentered(label, x + ICON * 0.5f, y - 0.3f, ICON, ICON * 0.58f, color);
    }

    /** Vector icon for the short row tags ("HP", "PN", ...); null keeps the letter. */
    private static String rowIcon(String tag) {
        return switch (tag) {
            case "P" -> "pin";
            case "PI" -> "pitch";
            case "YA" -> "compass";
            case "HP" -> "heart";
            case "SP" -> "speed";
            case "FP" -> "bolt";
            case "PN" -> "signal";
            case "TP" -> "hourglass";
            case "BI" -> "tree";
            case "T" -> "clock";
            case "S" -> "server";
            case "PL" -> "players";
            case "FX" -> "potion";
            default -> null;
        };
    }

    private static final java.util.Map<String, String> WIDGET_ICONS = java.util.Map.ofEntries(
            java.util.Map.entry("coords", "pin"), java.util.Map.entry("facing", "compass"), java.util.Map.entry("health", "heart"),
            java.util.Map.entry("armor", "shield"), java.util.Map.entry("speed", "speed"), java.util.Map.entry("fps", "bolt"),
            java.util.Map.entry("ping", "signal"), java.util.Map.entry("biome", "tree"), java.util.Map.entry("held", "hand"),
            java.util.Map.entry("items", "box"), java.util.Map.entry("effects", "potion"), java.util.Map.entry("info", "clock"),
            java.util.Map.entry("keys", "keyboard"));

    // ── Per-widget drag state ───────────────────────────────────────────────

    private static final class Widget {
        float x, y;         // top-left, GUI units
        float w, h;          // last computed size (hit test + edit outline)
        boolean customPos;    // true once the user dragged it at least once
        boolean dragging;
        float grabDX, grabDY; // mouse offset from x/y at drag start
        float appear;         // 0..1 fade, so toggling a row on/off doesn't pop the box in/out instantly
        List<Row> lastRows;   // kept around while fading out (rows() is empty by then)
        float[] fb;           // last drawn rect in framebuffer px (wheel scaling / right-click reset)
        boolean visibleFrame;
    }

    // ── Rows ─────────────────────────────────────────────────────────────────

    /** ratio: 0..1 draws a thin colored bar under the text (e.g. health/durability); negative = no bar.
     *  badgeColor: color of the icon badge; defaults to {@code color} when not given separately (e.g. so
     *  an armor piece's badge can show its material tier while the bar still shows its condition).
     *  item: null for the usual monogram badge (coords/health/fps/...); set only for armor rows, where the
     *  {@link HudOverlayItemLayer} renders the real ItemStack icon on top of the badge frame. */
    private record Row(String text, int color, String icon, float ratio, int badgeColor, ItemStack item) {
        Row(String text, int color, String icon) { this(text, color, icon, -1f, color, null); }
        Row(String text, int color, String icon, float ratio) { this(text, color, icon, ratio, color, null); }
        Row(String text, int color, String icon, float ratio, int badgeColor) { this(text, color, icon, ratio, badgeColor, null); }
    }

    private List<Row> rowsFor(String id, Minecraft mc) {
        LocalPlayer p = mc.player;
        int accent = Theme.accent();
        List<Row> rows = new ArrayList<>();

        switch (id) {
            case "coords" -> {
                if (showCoords.enabled) {
                    rows.add(new Row(String.format("%.1f, %.1f, %.1f", p.getX(), p.getY(), p.getZ()), accent, "P"));
                }
            }
            case "facing" -> {
                if (showFacing.enabled) {
                    float pitch = p.getXRot(), yaw = p.getYRot();
                    rows.add(new Row(String.format("Pitch %.1f\u00b0", pitch), accent, "PI"));
                    rows.add(new Row(String.format("Yaw %.1f\u00b0 (%s)", yaw, facing(yaw)), accent, "YA"));
                }
            }
            case "health" -> {
                if (showHealth.enabled) {
                    float ratio = p.getMaxHealth() <= 0 ? 0f : Math.max(0f, Math.min(1f, p.getHealth() / p.getMaxHealth()));
                    rows.add(new Row(String.format("%.1f / %.1f", p.getHealth(), p.getMaxHealth()), healthColor(p), "HP", ratio));
                }
            }
            case "armor" -> {
                if (showArmor.enabled) {
                    EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
                    String[] tags = {"H", "C", "L", "B"};
                    for (int i = 0; i < slots.length; i++) {
                        ItemStack st = p.getItemBySlot(slots[i]);
                        if (st.isEmpty()) continue;
                        boolean damageable = st.isDamageableItem();
                        String text = damageable
                                ? (st.getMaxDamage() - st.getDamageValue()) + " / " + st.getMaxDamage()
                                : "-";
                        float ratio = damageable ? 1f - (float) st.getDamageValue() / st.getMaxDamage() : -1f;
                        rows.add(new Row(text, durabilityColor(st), tags[i], ratio, materialTierColor(st), st));
                    }
                }
            }
            case "speed" -> {
                if (showSpeed.enabled) {
                    var v = p.getDeltaMovement();
                    double blocksPerSec = Math.sqrt(v.x * v.x + v.z * v.z) * 20.0;
                    rows.add(new Row(String.format("%.2f b/s", blocksPerSec), accent, "SP"));
                }
            }
            case "held" -> {
                if (showHeld.enabled) {
                    ItemStack st = p.getMainHandItem();
                    if (!st.isEmpty()) {
                        boolean damageable = st.isDamageableItem();
                        String name = st.getHoverName().getString();
                        if (st.getCount() > 1) name += " x" + st.getCount();
                        if (damageable) name += "  " + (st.getMaxDamage() - st.getDamageValue());
                        float ratio = damageable ? 1f - (float) st.getDamageValue() / st.getMaxDamage() : -1f;
                        rows.add(new Row(name, damageable ? durabilityColor(st) : Theme.TEXT, "I", ratio, materialTierColor(st), st));
                    }
                }
            }
            case "items" -> {
                if (showItems.enabled) {
                    for (var tracked : TRACKED) {
                        int n = countItems(p, tracked);
                        if (n > 0) rows.add(new Row("x" + n, n <= tracked.low() ? 0xFFE0525A : Theme.TEXT, "I", -1f, accent, new ItemStack(tracked.item())));
                    }
                }
            }
            case "effects" -> {
                if (showEffects.enabled) {
                    for (var fx : p.getActiveEffects()) {
                        var eff = fx.getEffect().value();
                        String lvl = fx.getAmplifier() > 0 ? " " + (fx.getAmplifier() + 1) : "";
                        String time = fx.isInfiniteDuration() ? "inf" : String.format("%d:%02d", fx.getDuration() / 1200, (fx.getDuration() / 20) % 60);
                        int col = eff.isBeneficial() ? 0xFF52E084 : 0xFFE0525A;
                        String nm = eff.getDisplayName().getString();
                        rows.add(new Row(nm + lvl + "  " + time, col, "FX"));
                    }
                }
            }
            case "info" -> {
                if (showTime.enabled) rows.add(new Row(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")), Theme.TEXT, "T"));
                if (showServer.enabled) {
                    var sd = mc.getCurrentServer();
                    rows.add(new Row(sd != null ? sd.ip : "Singleplayer", Theme.TEXT, "S"));
                }
                if (showPlayers.enabled && mc.level != null) rows.add(new Row(mc.level.players().size() + " players", Theme.TEXT, "PL"));
            }
            // Die folgenden drei nutzen APIs, die je nach Mapping/MC-Version anders heißen können
            // (fps-Zähler, PlayerInfo/Ping, Biome-Registry-Key) - Methodennamen ggf. anpassen.
            case "fps" -> {
                if (showFps.enabled) rows.add(new Row(mc.getFps() + " fps", Theme.TEXT, "FP"));
            }
            case "ping" -> {
                if (showPing.enabled && mc.getConnection() != null) {
                    var info = mc.getConnection().getPlayerInfo(p.getUUID());
                    int ping = info != null ? info.getLatency() : -1;
                    int pc = ping < 80 ? 0xFF52E084 : ping < 160 ? 0xFFF0C040 : 0xFFE0525A;
                    if (ping >= 0) rows.add(new Row(ping + " ms", pc, "PN"));
                }
                if (showTps.enabled) {
                    double tps = com.OsamaClient.newbridge.Utils.TpsTracker.tps();
                    boolean stalled = com.OsamaClient.newbridge.Utils.TpsTracker.secondsSinceUpdate() > 4.0;
                    String txt = tps < 0 ? "-- TPS" : String.format(java.util.Locale.ROOT, "%.1f TPS", tps);
                    if (stalled && tps >= 0) txt = "no data";
                    int tc = tps < 0 ? Theme.TEXT_DIM : stalled ? 0xFFE0525A : tps >= 18 ? 0xFF52E084 : tps >= 14 ? 0xFFF0C040 : 0xFFE0525A;
                    rows.add(new Row(txt, tc, "TP"));
                }
            }
            case "biome" -> {
                if (showBiome.enabled && p.level() != null) {
                    // .location() heißt in eurem Mapping .identifier() (liefert Identifier statt ResourceLocation)
                    String name = p.level().getBiome(p.blockPosition())
                            .unwrapKey()
                            .map(k -> k.identifier().getPath())
                            .orElse("unknown");
                    rows.add(new Row(name, Theme.TEXT, "BI"));
                }
            }
        }
        return rows;
    }

    private record Tracked(net.minecraft.world.item.Item item, int low, net.minecraft.world.item.Item alt) {}

    private static final List<Tracked> TRACKED = List.of(
            new Tracked(net.minecraft.world.item.Items.TOTEM_OF_UNDYING, 1, null),
            new Tracked(net.minecraft.world.item.Items.ENDER_PEARL, 0, null),
            new Tracked(net.minecraft.world.item.Items.GOLDEN_APPLE, 0, net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE),
            new Tracked(net.minecraft.world.item.Items.END_CRYSTAL, 0, null),
            new Tracked(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE, 0, null),
            new Tracked(net.minecraft.world.item.Items.FIREWORK_ROCKET, 0, null),
            new Tracked(net.minecraft.world.item.Items.ARROW, 0, null));

    private static int countItems(LocalPlayer p, Tracked t) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(t.item()) || (t.alt() != null && st.is(t.alt()))) n += st.getCount();
        }
        return n;
    }

    /** Grobe 8-Wege-Himmelsrichtung aus dem Yaw (0 = Süden in Minecraft). */
    private static String facing(float yaw) {
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        float norm = ((yaw % 360f) + 360f) % 360f;
        int idx = Math.round(norm / 45f) % 8;
        return dirs[idx];
    }

    private static int healthColor(LocalPlayer p) {
        float max = p.getMaxHealth();
        float ratio = max <= 0 ? 0f : Math.max(0f, Math.min(1f, p.getHealth() / max));
        return ColorUtil.lerp(0xFFE0525A, 0xFF52E084, ratio);
    }

    private static int durabilityColor(ItemStack st) {
        if (!st.isDamageableItem()) return Theme.TEXT;
        float ratio = 1f - (float) st.getDamageValue() / st.getMaxDamage();
        return ColorUtil.lerp(0xFFE0525A, 0xFF52E084, Math.max(0f, Math.min(1f, ratio)));
    }

    /**
     * Grobe Materialfarbe fürs Badge, aus dem Item-Registry-Namen geraten (kein echtes Material-Enum
     * nötig - funktioniert für Vanilla-Rüstung und die meisten modded Sets, die sich an die
     * "material_slot"-Namenskonvention halten). Fällt auf {@link Theme#accent()} zurück, wenn nichts passt.
     */
    private static int materialTierColor(ItemStack st) {
        String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        if (path.contains("netherite")) return 0xFF4A4A4E;
        if (path.contains("diamond")) return 0xFF62E4E0;
        if (path.contains("gold")) return 0xFFF5D76E;
        if (path.contains("iron")) return 0xFFD9D9DE;
        if (path.contains("chainmail")) return 0xFFB8B8BE;
        if (path.contains("leather")) return 0xFFB07A4C;
        if (path.contains("turtle")) return 0xFF3FA560;
        return Theme.accent();
    }
}
