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
import org.lwjgl.glfw.GLFW;

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
 * dort für Details). Diese Klasse liefert dem Layer nur die Positionen/Stacks über {@link #pendingIcons}.
 */
public class HudOverlay extends Module {

    public static HudOverlay instance;

    // ── Style ────────────────────────────────────────────────────────────────
    public final Slider scale = new Slider("Text Scale", 0.5, 1.5, 1.0, 0.05, null)
            .withDescription("Größe von Text und Icons");
    public final ToggleButton background = new ToggleButton("Background", true, null)
            .withDescription("Zeichnet eine Panel-Box hinter jeder Zeilen-Gruppe");
    public final ToggleButton icons = new ToggleButton("Icons", true, null)
            .withDescription("Zeigt vor jeder Zeile ein kleines Icon-Badge");
    public final ToggleButton editLayout = new ToggleButton("Edit Layout", false,
            val -> { if (val) openEditor(); else closeEditor(); })
            .withDescription("Öffnet einen Screen, in dem du jede HUD-Box einzeln mit der Maus verschieben kannst (Esc zum Beenden)");

    // ── Which rows ───────────────────────────────────────────────────────────
    public final ToggleButton showCoords = new ToggleButton("Coordinates", true, null)
            .withDescription("X / Y / Z");
    public final ToggleButton showFacing = new ToggleButton("Pitch/Yaw", true, null)
            .withDescription("Pitch, Yaw und die grobe Blickrichtung (N/NO/O/...)");
    public final ToggleButton showHealth = new ToggleButton("Health", true, null)
            .withDescription("Aktuelle / maximale Leben, Farbe läuft von Rot nach Grün");
    public final ToggleButton showArmor = new ToggleButton("Armor", true, null)
            .withDescription("Haltbarkeit jedes getragenen Rüstungsteils");
    public final ToggleButton showSpeed = new ToggleButton("Speed", false, null)
            .withDescription("Horizontale Bewegungsgeschwindigkeit in Blöcken/Sekunde");
    public final ToggleButton showFps = new ToggleButton("FPS", false, null)
            .withDescription("Aktuelle Framerate");
    public final ToggleButton showPing = new ToggleButton("Ping", false, null)
            .withDescription("Ping zum Server in ms");
    public final ToggleButton showBiome = new ToggleButton("Biome", false, null)
            .withDescription("Aktuelles Biom");

    // eigene Pipeline, unabhängig von ClickGui - zeichnet jeden Frame, egal ob die ClickGUI offen ist
    private static final UiRenderer renderer = new UiRenderer();
    private static final Ui ui = new Ui(renderer);

    private static final float ROW_H = 11f, ICON = 8f, PAD = 4f, GAP = 3f, WIDGET_GAP = 4f;
    private static final float CAPTION_H = 9f, ANIM_SPEED = 0.16f;
    private static final String[] WIDGET_IDS = {"coords", "facing", "health", "armor", "speed", "fps", "ping", "biome"};
    private static final String[] WIDGET_TITLES =
            {"Coordinates", "Facing", "Health", "Armor", "Speed", "FPS", "Ping", "Biome"};

    private final Map<String, Widget> widgets = new LinkedHashMap<>();
    private boolean editMode;
    private int buttonsDown;
    private boolean pendingClick;

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
        settings.add(showBiome);

        HudOverlayItemLayer.register();
    }

    /** Custom (dragged) widget positions for saving; a box the user never moved keeps flowing automatically. */
    public Map<String, ConfigCodec.WidgetPos> widgetPositions() {
        Map<String, ConfigCodec.WidgetPos> out = new LinkedHashMap<>();
        widgets.forEach((id, w) -> { if (w.customPos) out.put(id, new ConfigCodec.WidgetPos(w.x, w.y)); });
        return out;
    }

    /** Restores previously saved widget positions (applied lazily as each box is first drawn). */
    public void applyWidgetPositions(Map<String, ConfigCodec.WidgetPos> saved) {
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
        for (Widget w : widgets.values()) w.dragging = false;
        editLayout.enabled = false;
        if (Minecraft.getInstance().gui.screen() instanceof HudOverlayScreen) Minecraft.getInstance().gui.setScreen(null);
    }

    /** Called by {@link HudOverlayScreen#removed()} if the screen gets closed some other way than Esc. */
    void screenClosed() {
        editMode = false;
        editLayout.enabled = false;
        for (Widget w : widgets.values()) w.dragging = false;
    }

    void mouseClicked(int button) {
        buttonsDown |= 1 << button;
        if (button == 0) pendingClick = true;
    }

    void mouseReleased(int button) {
        buttonsDown &= ~(1 << button);
        for (Widget w : widgets.values()) w.dragging = false;
    }

    void keyPressed(int key, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) closeEditor();
    }

    // ── HUD draw - einmal pro Frame, siehe GameRendererUiMixin ─────────────────

    public static void draw() {
        Minecraft mc = Minecraft.getInstance();
        if (instance == null || mc.player == null) return;

        boolean editing = instance.editMode && mc.gui.screen() instanceof HudOverlayScreen;
        if (!editing && (!instance.enabled || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen()
                || ClickGui.INSTANCE.isOpen() || mc.gui.screen() != null)) {
            pendingIcons.clear();
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

        instance.drawWidgets(mc, editing);

        if (editing) {
            String hint = "Drag boxes to move them  ·  Esc to finish editing";
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
    }

    /**
     * Echte Item-Icons laufen NICHT mehr über diese Pipeline (die kennt nur SDF/Text), sondern über den
     * separaten {@link HudOverlayItemLayer}, der via {@code HudElementRegistry} vor {@code GuiRenderer#render()}
     * läuft und deshalb noch einen echten {@code GuiGraphicsExtractor} bekommt. Wir sammeln hier nur, WAS
     * gezeichnet werden soll (in framebuffer-Pixeln, unser Koordinatensystem) - der Layer liest das eine
     * Zeile weiter unten aus und rechnet selbst auf GUI-skalierte Pixel um. Ein Frame Verzögerung zwischen
     * Badge-Position (hier) und echtem Icon (im nächsten Extract-Pass) ist bei normaler Framerate unsichtbar.
     */
    static final List<PendingIcon> pendingIcons = new ArrayList<>();

    record PendingIcon(ItemStack stack, float fbX, float fbY, float fbSize) {}

    private void drawWidgets(Minecraft mc, boolean editing) {
        float textSize = Theme.FONT;
        boolean showIcons = icons.enabled;
        float flowY = 8f;
        float glow = Theme.glow();
        pendingIcons.clear();

        for (int idx = 0; idx < WIDGET_IDS.length; idx++) {
            String id = WIDGET_IDS[idx];
            String title = WIDGET_TITLES[idx];
            List<Row> rows = rowsFor(id, mc);
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

            Widget wRef = w;
            wRef.w = boxW;
            wRef.h = boxH;
            if (!wRef.customPos) { wRef.x = 8f; wRef.y = flowY; }
            flowY += boxH + WIDGET_GAP;

            if (editing && wRef.dragging && (buttonsDown & 1) != 0) {
                wRef.x = ui.mouseX - wRef.grabDX;
                wRef.y = ui.mouseY - wRef.grabDY;
                wRef.customPos = true;
            }

            boolean hovered = editing && ui.hovered(wRef.x, wRef.y, wRef.w, wRef.h);
            if (editing && pendingClick && hovered && noWidgetDragging()) {
                wRef.dragging = true;
                wRef.grabDX = ui.mouseX - wRef.x;
                wRef.grabDY = ui.mouseY - wRef.y;
            }

            float oldAlpha = ui.alpha;
            ui.alpha = oldAlpha * wRef.appear;

            boolean drawBg = background.enabled || editing;
            if (drawBg) {
                // subtle accent glow behind the box, same recipe as ClickGui panels, so the overlay
                // reads as part of the same UI language instead of a bare list of floating text
                if (glow > 0f) ui.glow(wRef.x, wRef.y, wRef.w, wRef.h, Theme.radius(), 6f, Theme.accent(0.16f * glow));
                int fill = background.enabled ? Theme.PANEL_BG : ColorUtil.alpha(0xFF000000, 0.28f);
                ui.round(wRef.x, wRef.y, wRef.w, wRef.h, Theme.radius(), fill);
            }
            if (editing) {
                ui.outline(wRef.x, wRef.y, wRef.w, wRef.h, Theme.radius(), 1f, Theme.accent(hovered ? 0.9f : 0.45f));
            } else if (background.enabled && Theme.outline()) {
                ui.outline(wRef.x, wRef.y, wRef.w, wRef.h, Theme.radius(), 1f, Theme.OUTLINE);
            }
            if (drawBg) {
                // left accent bar, mirrors the same "this is one group" cue used by the module-settings
                // block and the teammate/module HUD lists elsewhere in the theme
                ui.round(wRef.x, wRef.y + 1.5f, 1.3f, wRef.h - 3f, 0.65f, Theme.accent(0.6f));
            }

            float captionX = wRef.x + PAD + (showIcons ? ICON + GAP : 0f);
            ui.text(title.toUpperCase(java.util.Locale.ROOT), captionX, wRef.y + PAD - 1f, CAPTION_H, Theme.FONT_SMALL,
                    ColorUtil.alpha(Theme.TEXT_DIM, 0.9f));

            float rowY = wRef.y + PAD + CAPTION_H;
            for (Row r : drawRows) {
                float rowX = wRef.x + PAD;
                if (showIcons) {
                    float iconY = rowY + (ROW_H - ICON) * 0.5f;
                    boolean realItem = r.item() != null && !r.item().isEmpty();
                    drawBadge(rowX, iconY, r.badgeColor(), r.icon(), !realItem);
                    if (realItem) {
                        pendingIcons.add(new PendingIcon(r.item(), (rowX + 0.5f) * ui.scale,
                                (iconY + 0.5f) * ui.scale, (ICON - 1f) * ui.scale));
                    }
                    rowX += ICON + GAP;
                }
                ui.text(r.text(), rowX, rowY, ROW_H, textSize, Theme.TEXT);
                if (r.ratio() >= 0f) {
                    float barW = wRef.x + wRef.w - PAD - rowX;
                    float barY = rowY + ROW_H - 2.6f;
                    float ratio = Math.max(0f, Math.min(1f, r.ratio()));
                    ui.round(rowX, barY, barW, 1.4f, 0.7f, Theme.TRACK);
                    if (ratio > 0.01f) ui.round(rowX, barY, barW * ratio, 1.4f, 0.7f, r.color());
                }
                rowY += ROW_H;
            }

            ui.alpha = oldAlpha;
        }
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

    /** Kleines abgerundetes Icon-Badge; bei einem echten Item (siehe {@link PendingIcon}) nur Rahmen +
     *  Hintergrund als Fassung ums Icon, ohne Monogramm-Buchstaben (sonst läge Text auf dem Icon). */
    private static void drawBadge(float x, float y, int color, String label, boolean drawLabel) {
        float r = ICON * 0.28f;
        ui.round(x, y, ICON, ICON, r, ColorUtil.alpha(color, 0.20f));
        ui.outline(x, y, ICON, ICON, r, 1f, ColorUtil.alpha(color, 0.7f));
        if (drawLabel) ui.textCentered(label, x + ICON * 0.5f, y - 0.3f, ICON, ICON * 0.58f, color);
    }

    // ── Per-widget drag state ───────────────────────────────────────────────

    private static final class Widget {
        float x, y;         // top-left, GUI units
        float w, h;          // last computed size (hit test + edit outline)
        boolean customPos;    // true once the user dragged it at least once
        boolean dragging;
        float grabDX, grabDY; // mouse offset from x/y at drag start
        float appear;         // 0..1 fade, so toggling a row on/off doesn't pop the box in/out instantly
        List<Row> lastRows;   // kept around while fading out (rows() is empty by then)
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
            // Die folgenden drei nutzen APIs, die je nach Mapping/MC-Version anders heißen können
            // (fps-Zähler, PlayerInfo/Ping, Biome-Registry-Key) - Methodennamen ggf. anpassen.
            case "fps" -> {
                if (showFps.enabled) rows.add(new Row(mc.getFps() + " fps", Theme.TEXT, "FP"));
            }
            case "ping" -> {
                if (showPing.enabled && mc.getConnection() != null) {
                    var info = mc.getConnection().getPlayerInfo(p.getUUID());
                    int ping = info != null ? info.getLatency() : -1;
                    if (ping >= 0) rows.add(new Row(ping + " ms", Theme.TEXT, "PN"));
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