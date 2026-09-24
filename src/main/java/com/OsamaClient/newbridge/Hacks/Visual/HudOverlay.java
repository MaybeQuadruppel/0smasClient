package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Frei platzierbares HUD-Overlay (FPS, Ping, Koordinaten, Rotation, Rüstung,
 * Keystrokes, CPS, Uhr, Blickrichtung, Speed, Potion-Effekte, ...), ähnlich
 * dem HUD-Editor von Lunar/Badlion.
 *
 * Positionierung passiert nicht hier, sondern in {@link HudLayoutScreen} -
 * dieses Modul liefert nur die Inhalte/das Rendering, hält die
 * Widget-Definitionen (Position, Farbe, Skalierung, an/aus) und deren
 * Persistenz.
 *
 * NEU:
 *  - Jedes Widget hat jetzt eine eigene Farbe, wählbar über einen kleinen
 *    Hue-Slider über dem Widget ({@link #hitTestColorBar}, {@link #setColorFromBarX}),
 *    der erst per "C"-Taste eingeblendet wird,
 *    und eine eigene Skalierung zusätzlich zur globalen Skalierung
 *    ({@link #bumpElementScale}), steuerbar direkt im {@link HudLayoutScreen}.
 *  - "Armor Durability" wird nicht mehr als Textzeilen, sondern als
 *    Item-Icon + verbleibende Haltbarkeit gerendert (siehe renderArmorWidget).
 *  - {@link #updateDragPosition} kann jetzt Sperrzonen (z.B. den "Done"-Button)
 *    respektieren, damit ein Widget beim Draggen nicht mehr dahinter landen
 *    und dadurch unerreichbar werden kann.
 */

public class HudOverlay extends Module {

    public static HudOverlay instance;

    public static final class HudElementData {
        public final String id;
        public final String label;
        public boolean enabled;
        /** Normalisierte Position (0..1 relativ zur Bildschirmgröße), damit
         *  ein Layout auch nach Auflösungswechsel sinnvoll bleibt. */
        public float x, y;
        /** Eigene Textfarbe (RGB, ohne Alpha) - Standard: Weiß. */
        public int color = 0xFFFFFF;
        /** Individueller Skalierungsfaktor zusätzlich zur globalen "Scale"
         *  Einstellung, damit sich einzelne Widgets kleiner/größer als der
         *  Rest ziehen lassen (0.5x - 2.0x). */
        public float scaleMul = 1.0f;
        public final Function<Minecraft, List<String>> content;
        /** Zuletzt gerenderte Pixel-Bounding-Box - für Hit-Testing im Editor. */
        public transient int lastPxX, lastPxY, lastPxW, lastPxH;
        /** Zuletzt gerenderte Bounding-Box des kleinen Hue-Sliders neben dem
         *  Widget im Editor (erscheint erst per "C") - für Hit-Testing/Dragging der Farbe. lastBarW <= 0
         *  bedeutet "wurde diesen Frame noch nicht gezeichnet". */
        public transient int lastBarX, lastBarY, lastBarW, lastBarH;

        HudElementData(String id, String label, float defX, float defY, boolean defEnabled,
                       Function<Minecraft, List<String>> content) {
            this.id = id;
            this.label = label;
            this.x = defX;
            this.y = defY;
            this.enabled = defEnabled;
            this.content = content;
        }
    }

    private final List<HudElementData> elements = new ArrayList<>();
    private final Map<String, HudElementData> byId = new LinkedHashMap<>();

    // ========================================================================
    // SETTINGS
    // ========================================================================

    private boolean background = true;
    private boolean textShadow = true;
    private double scale = 1.0;

    // ========================================================================
    // CPS-TRACKING (nur linke Maustaste, "Angriff" - klassische CPS-Anzeige)
    // ========================================================================

    private boolean prevAttackDown = false;
    private final List<Long> recentClicks = new ArrayList<>();

    // ========================================================================
    // SPEED-TRACKING
    // ========================================================================

    private Vec3 lastPos = null;
    private double lastSpeedBps = 0.0;

    public HudOverlay() {
        super("HUD Overlay", "Draggable overlay: FPS, ping, coords, keystrokes and more.", Category.VISUAL);
        this.enabled = true;
        instance = this;

        register("fps", "FPS", 0.01f, 0.01f, true, HudOverlay::fpsLines);
        register("ping", "Ping", 0.01f, 0.05f, true, HudOverlay::pingLines);
        register("coords", "Coordinates", 0.01f, 0.09f, true, HudOverlay::coordLines);
        register("rotation", "Yaw / Pitch", 0.01f, 0.13f, false, HudOverlay::rotationLines);
        register("direction", "Direction", 0.01f, 0.17f, false, HudOverlay::directionLines);
        register("speed", "Speed", 0.01f, 0.21f, false, this::speedLines);
        // "armor" wird als Icon-Widget gerendert (siehe renderArmorWidget), der
        // Content-Supplier bleibt als Text-Fallback bestehen, falls das
        // Icon-Rendering aus irgendeinem Grund fehlschlägt.
        register("armor", "Armor Durability", 0.01f, 0.25f, true, HudOverlay::armorLines);
        register("potions", "Potion Effects", 0.01f, 0.40f, false, HudOverlay::potionLines);
        register("clock", "Clock", 0.90f, 0.01f, true, HudOverlay::clockLines);
        register("cps", "CPS", 0.90f, 0.05f, false, this::cpsLines);
        // "keystrokes" wird speziell gerendert (Tasten-Grid statt Textzeilen),
        // braucht daher keinen Content-Supplier - siehe renderKeystrokes().
        register("keystrokes", "Keystrokes", 0.85f, 0.80f, false, mc -> List.of());

        loadLayout();

        this.settings.add(new ToggleButton("Background", background, v -> background = v)
                .withDescription("Draws a translucent backing box behind each widget."));
        this.settings.add(new ToggleButton("Text Shadow", textShadow, v -> textShadow = v)
                .withDescription("Toggles the drop shadow on overlay text."));
        this.settings.add(new Slider("Scale", 0.6, 1.5, scale, 0.05, v -> scale = v)
                .withDescription("Scales all overlay widgets (multiplies with each widget's own scale)."));

        for (HudElementData el : elements) {
            this.settings.add(new ToggleButton(el.label, el.enabled, v -> {
                el.enabled = v;
                saveLayout();
            }).withDescription("Shows/hides the \"" + el.label + "\" widget. Position, color and size are set via the HUD layout editor (drag / scroll / \"C\")."));
        }
    }

    private void register(String id, String label, float defX, float defY, boolean defEnabled,
                          Function<Minecraft, List<String>> content) {
        HudElementData el = new HudElementData(id, label, defX, defY, defEnabled, content);
        elements.add(el);
        byId.put(id, el);
    }

    // ========================================================================
    // TICK (CPS + Speed brauchen einen festen 20/s-Takt)
    // ========================================================================

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;

        boolean attackDown = mc.options.keyAttack.isDown();
        if (attackDown && !prevAttackDown) {
            recentClicks.add(System.currentTimeMillis());
        }
        prevAttackDown = attackDown;
        long now = System.currentTimeMillis();
        recentClicks.removeIf(t -> now - t > 1000L);

        Vec3 pos = mc.player.position();
        if (lastPos != null) {
            double dx = pos.x - lastPos.x;
            double dz = pos.z - lastPos.z;
            lastSpeedBps = Math.sqrt(dx * dx + dz * dz) * 20.0; // Blöcke/Tick -> Blöcke/s
        }
        lastPos = pos;
    }

    // ========================================================================
    // DRAW (normales HUD)
    // ========================================================================

    public static void draw(GuiGraphicsExtractor g) {
        drawInternal(g, false, -1, -1);
    }

    /** Wird vom HudLayoutScreen aufgerufen: zeigt ALLE Widgets (auch
     *  deaktivierte, gedimmt) mit Auswahlrahmen zum Editieren. */
    public static void drawForEditor(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        drawInternal(g, true, mouseX, mouseY);
    }

    private static void drawInternal(GuiGraphicsExtractor g, boolean editMode, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (instance == null || mc.player == null) return;
        if (!editMode && (!instance.enabled || mc.gui.hud.isHidden() || mc.getDebugOverlay().showDebugScreen())) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        for (HudElementData el : instance.elements) {
            if (!editMode && !el.enabled) continue;

            int px = Math.round(el.x * screenW);
            int py = Math.round(el.y * screenH);

            boolean hovered = editMode && mouseX >= el.lastPxX && mouseX <= el.lastPxX + el.lastPxW
                    && mouseY >= el.lastPxY && mouseY <= el.lastPxY + el.lastPxH;

            if (el.id.equals("keystrokes")) {
                renderKeystrokes(g, mc, el, px, py, editMode, hovered);
            } else if (el.id.equals("armor")) {
                renderArmorWidget(g, mc, el, px, py, editMode, hovered);
            } else {
                renderTextWidget(g, mc, el, px, py, editMode, hovered);
            }
        }
    }

    // ========================================================================
    // TEXT-WIDGET RENDERING
    // ========================================================================

    private static void renderTextWidget(GuiGraphicsExtractor g, Minecraft mc, HudElementData el,
                                         int px, int py, boolean editMode, boolean hovered) {
        List<String> lines;
        try {
            lines = el.content.apply(mc);
        } catch (Throwable t) {
            lines = List.of(el.label + ": N/A");
        }
        if (lines == null || lines.isEmpty()) lines = List.of(el.label);

        float scale = (float) instance.scale * el.scaleMul;
        int lineH = mc.font.lineHeight + 1;
        int maxW = 0;
        for (String line : lines) maxW = Math.max(maxW, mc.font.width(line));

        int boxW = Math.round((maxW + 6) * scale);
        int boxH = Math.round((lines.size() * lineH + 4) * scale);
        el.lastPxX = px - 2;
        el.lastPxY = py - 2;
        el.lastPxW = boxW;
        el.lastPxH = boxH;

        boolean dimmed = editMode && !el.enabled;
        float alphaMul = dimmed ? 0.45f : 1.0f;

        if (instance.background) {
            int bg = (int) (0x90 * alphaMul) << 24;
            g.fill(px - 2, py - 2, px - 2 + boxW, py - 2 + boxH, bg);
        }

        g.pose().pushMatrix();
        g.pose().translate((float) px, (float) py);
        g.pose().scale(scale, scale);

        int textAlpha = (int) (0xFF * alphaMul);
        int textColor = (textAlpha << 24) | (el.color & 0xFFFFFF);
        int y = 0;
        for (String line : lines) {
            g.text(mc.font, line, 0, y, textColor, instance.textShadow && !dimmed);
            y += lineH;
        }
        g.pose().popMatrix();

        if (editMode) {
            drawEditorFrame(g, mc, el, hovered);
        }
    }

    // ========================================================================
    // ARMOR-WIDGET (Icons statt Text, siehe Referenzbild: Helm/Brust/Hose/
    // Schuhe-Icon + verbleibende Haltbarkeit rechts daneben)
    // ========================================================================

    private static void renderArmorWidget(GuiGraphicsExtractor g, Minecraft mc, HudElementData el,
                                          int px, int py, boolean editMode, boolean hovered) {
        if (mc.player == null) return;

        EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        };

        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot slot : slots) stacks.add(mc.player.getItemBySlot(slot));

        float scale = (float) instance.scale * el.scaleMul;
        int iconSize = Math.round(16 * scale);
        int rowGap   = Math.max(1, Math.round(2 * scale));
        int rowH     = iconSize + rowGap;

        int maxTextW = 0;
        for (ItemStack stack : stacks) maxTextW = Math.max(maxTextW, mc.font.width(durabilityText(stack)));
        int textGap = Math.round(4 * scale);

        int boxW = iconSize + textGap + Math.round(maxTextW * scale) + 4;
        int boxH = rowH * slots.length - rowGap + 4;

        el.lastPxX = px - 2;
        el.lastPxY = py - 2;
        el.lastPxW = boxW;
        el.lastPxH = boxH;

        boolean dimmed = editMode && !el.enabled;
        float alphaMul = dimmed ? 0.45f : 1.0f;

        if (instance.background) {
            int bg = (int) (0x90 * alphaMul) << 24;
            g.fill(el.lastPxX, el.lastPxY, el.lastPxX + boxW, el.lastPxY + boxH, bg);
        }

        int rowY = py;
        int textAlpha = (int) (0xFF * alphaMul);
        int textColor = (textAlpha << 24) | (el.color & 0xFFFFFF);
        int scaledLineH = Math.max(1, Math.round(mc.font.lineHeight * scale));

        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {

                g.pose().pushMatrix();
                g.pose().translate((float) px, (float) rowY);
                g.pose().scale(scale, scale);
                g.item(stack, 0, 0);
                g.pose().popMatrix();
            }

            // Die Zahl wurde vorher IMMER in nativer Fontgröße gezeichnet,
            // während Icon/Position schon skaliert waren - dadurch wirkte
            // die Haltbarkeitszahl bei kleiner Widget-Skalierung riesig
            // neben einem winzigen Icon. Jetzt läuft der Text durch dieselbe
            // Skalierungsmatrix wie das Icon, bleibt also proportional.
            String txt = durabilityText(stack);
            int textX = px + iconSize + textGap;
            int textY = rowY + (iconSize - scaledLineH) / 2;
            g.pose().pushMatrix();
            g.pose().translate((float) textX, (float) textY);
            g.pose().scale(scale, scale);
            g.text(mc.font, txt, 0, 0, textColor, instance.textShadow && !dimmed);
            g.pose().popMatrix();

            rowY += rowH;
        }

        if (editMode) {
            drawEditorFrame(g, mc, el, hovered);
        }
    }

    /** Verbleibende Haltbarkeit als reine Zahl (wie im Referenzbild), "-" für
     *  leere Slots oder nicht-beschädigbare Items (z.B. Elytra ohne Schaden = trotzdem zählbar). */
    private static String durabilityText(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "-";
        if (stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            int cur = max - stack.getDamageValue();
            return String.valueOf(cur);
        }
        return "-";
    }

    // ========================================================================
    // KEYSTROKES (Sonderfall: kein Text, sondern ein Tasten-Grid)
    // ========================================================================

    private static void renderKeystrokes(GuiGraphicsExtractor g, Minecraft mc, HudElementData el,
                                         int px, int py, boolean editMode, boolean hovered) {
        float scale = (float) instance.scale * el.scaleMul;
        int cell = Math.round(16 * scale);
        int gap = Math.max(1, Math.round(2 * scale));

        boolean w = mc.options.keyUp.isDown();
        boolean a = mc.options.keyLeft.isDown();
        boolean s = mc.options.keyDown.isDown();
        boolean d = mc.options.keyRight.isDown();
        boolean lmb = mc.options.keyAttack.isDown();
        boolean rmb = mc.options.keyUse.isDown();

        int gridW = cell * 3 + gap * 2;
        int rowH = cell + gap;
        int gridH = rowH * 2 + cell; // WASD-Reihen + Mausleiste

        el.lastPxX = px;
        el.lastPxY = py;
        el.lastPxW = gridW;
        el.lastPxH = gridH;

        boolean dimmed = editMode && !el.enabled;
        float alphaMul = dimmed ? 0.45f : 1.0f;
        int activeColor = el.color & 0xFFFFFF;

        // Reihe 1: [ ][W][ ]
        drawKeyBox(g, mc, px + cell + gap, py, cell, "W", w, alphaMul, activeColor, scale);
        // Reihe 2: [A][S][D]
        int row2Y = py + rowH;
        drawKeyBox(g, mc, px, row2Y, cell, "A", a, alphaMul, activeColor, scale);
        drawKeyBox(g, mc, px + cell + gap, row2Y, cell, "S", s, alphaMul, activeColor, scale);
        drawKeyBox(g, mc, px + (cell + gap) * 2, row2Y, cell, "D", d, alphaMul, activeColor, scale);
        // Reihe 3: [LMB][RMB] (breiter, über die volle Grid-Breite verteilt)
        int row3Y = row2Y + rowH;
        int mouseBtnW = (gridW - gap) / 2;
        drawKeyBoxWide(g, mc, px, row3Y, mouseBtnW, cell, "LMB", lmb, alphaMul, activeColor, scale);
        drawKeyBoxWide(g, mc, px + mouseBtnW + gap, row3Y, gridW - mouseBtnW - gap, cell, "RMB", rmb, alphaMul, activeColor, scale);

        if (editMode) {
            drawEditorFrame(g, mc, el, hovered);
        }
    }

    private static void drawKeyBox(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int size,
                                   String label, boolean down, float alphaMul, int activeColor, float scale) {
        drawKeyBoxWide(g, mc, x, y, size, size, label, down, alphaMul, activeColor, scale);
    }

    private static void drawKeyBoxWide(GuiGraphicsExtractor g, Minecraft mc, int x, int y, int w, int h,
                                       String label, boolean down, float alphaMul, int activeColor, float scale) {
        int bgA = (int) ((down ? 0xD0 : 0x70) * alphaMul) << 24;
        int bg = bgA | (down ? activeColor : 0x202020);
        g.fill(x, y, x + w, y + h, bg);

        int textAlpha = (int) (0xFF * alphaMul);
        int color = (Math.min(255, textAlpha) << 24) | (down ? 0x000000 : 0xFFFFFF);

        // Text wird mit derselben Skalierung wie die Box gezeichnet (vorher:
        // immer native Fontgröße, wodurch "LMB"/"RMB" bei kleiner Widget-
        // Skalierung breiter als ihre Box wurden und ineinander clippten).
        // Reicht der Platz selbst bei "scale" nicht (sehr schmale/kleine
        // Boxen), wird zusätzlich so weit heruntergerechnet, bis der Text passt.
        int lw = mc.font.width(label);
        float textScale = scale;
        float availableW = (w - 2) / Math.max(0.001f, textScale);
        if (lw > availableW && lw > 0) {
            textScale *= availableW / lw;
        }

        g.pose().pushMatrix();
        g.pose().translate(x + w / 2f, y + h / 2f);
        g.pose().scale(textScale, textScale);
        g.text(mc.font, label, -lw / 2, -mc.font.lineHeight / 2, color, false);
        g.pose().popMatrix();
    }

    private static void drawOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Gemeinsamer Editor-Rahmen (Auswahlbox + optionaler Hue-Slider)
     *  für alle Widget-Typen - vermeidet Codeverdopplung zwischen Text-,
     *  Armor- und Keystrokes-Widgets. */
    private static final int COLOR_BAR_W = 28;
    private static final int COLOR_BAR_H = 6;

    private static void drawEditorFrame(GuiGraphicsExtractor g, Minecraft mc, HudElementData el, boolean hovered) {
        int outline = !el.enabled ? 0xFF888888 : hovered ? 0xFFFFFF55 : 0xFF55FF55;
        drawOutline(g, el.lastPxX - 1, el.lastPxY - 1, el.lastPxW + 2, el.lastPxH + 2, outline);

        // Keine Namens-/Prozent-Labels mehr über den Widgets: die Module
        // sind an ihrem Inhalt (FPS-Zahl, Uhr, Armor-Icons, Keystrokes-Grid,
        // ...) selbst erkennbar. Übrig bleibt nur der Auswahlrahmen oben und
        // - falls gerade per "C" geöffnet - der Hue-Slider direkt über dem
        // Widget.
        if (!isColorPickerOpen(el.id)) {
            el.lastBarW = 0;
            return;
        }

        int barX = el.lastPxX;
        int barY = el.lastPxY - COLOR_BAR_H - 6;
        el.lastBarX = barX;
        el.lastBarY = barY;
        el.lastBarW = COLOR_BAR_W;
        el.lastBarH = COLOR_BAR_H;

        // Weicher Schatten/Rahmen für etwas Tiefe, statt eines nackten
        // Farbstreifens.
        g.fill(barX - 2, barY - 2, barX + COLOR_BAR_W + 2, barY + COLOR_BAR_H + 2, 0x90000000);
        g.fill(barX - 1, barY - 1, barX + COLOR_BAR_W + 1, barY + COLOR_BAR_H + 1, 0xFF1A1A1A);

        for (int i = 0; i < COLOR_BAR_W; i++) {
            float hue = i / (float) (COLOR_BAR_W - 1);
            int col = java.awt.Color.HSBtoRGB(hue, 1f, 1f) | 0xFF000000;
            g.fill(barX + i, barY, barX + i + 1, barY + COLOR_BAR_H, col);
        }
        // Dezenter Glanz-Streifen oben für einen "glasigen" Look.
        g.fill(barX, barY, barX + COLOR_BAR_W, barY + 1, 0x50FFFFFF);

        // Thumb: kleiner, über den Slider hinausragender Griff mit
        // schwarzem Rand + weißem Kern statt eines simplen 2px-Balkens -
        // deutlich besser greifbar und sichtbar.
        int thumbX = barX + Math.round(rgbToHue(el.color) * (COLOR_BAR_W - 1));
        int thumbTop = barY - 2;
        int thumbBottom = barY + COLOR_BAR_H + 2;
        g.fill(thumbX - 2, thumbTop, thumbX + 3, thumbBottom, 0xFF000000);
        g.fill(thumbX - 1, thumbTop + 1, thumbX + 2, thumbBottom - 1, 0xFFFFFFFF);
    }

    private static float rgbToHue(int rgb) {
        float[] hsb = new float[3];
        java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, hsb);
        return hsb[0];
    }

    // ========================================================================
    // WIDGET-INHALTE
    // ========================================================================

    private static List<String> fpsLines(Minecraft mc) {
        return List.of("FPS: " + mc.getFps());
    }

    private static List<String> pingLines(Minecraft mc) {
        int ping = -1;
        try {
            if (mc.getConnection() != null && mc.player != null) {
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if (info != null) ping = info.getLatency();
            }
        } catch (Throwable ignored) {}
        return List.of("Ping: " + (ping >= 0 ? ping + "ms" : "N/A"));
    }

    private static List<String> coordLines(Minecraft mc) {
        Vec3 p = mc.player.position();
        return List.of(String.format("XYZ: %.1f / %.1f / %.1f", p.x, p.y, p.z));
    }

    private static List<String> rotationLines(Minecraft mc) {
        return List.of(String.format("Yaw: %.1f  Pitch: %.1f",
                normalizeYaw(mc.player.getYRot()), mc.player.getXRot()));
    }

    private static List<String> directionLines(Minecraft mc) {
        return List.of("Facing: " + compassDirection(mc.player.getYRot()));
    }

    private List<String> speedLines(Minecraft mc) {
        return List.of(String.format("Speed: %.2f b/s", lastSpeedBps));
    }

    /** Text-Fallback, falls das Icon-Rendering (renderArmorWidget) nicht
     *  greifen sollte - wird aktuell nicht mehr für das normale Rendering
     *  benutzt, bleibt aber als content-Supplier registriert. */
    private static List<String> armorLines(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        try {
            if (mc.player != null) {
                EquipmentSlot[] armorSlots = new EquipmentSlot[] {
                        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
                };

                for (EquipmentSlot slot : armorSlots) {
                    ItemStack stack = mc.player.getItemBySlot(slot);
                    if (stack == null || stack.isEmpty()) continue;

                    String name = stack.getHoverName().getString();
                    if (stack.isDamageableItem()) {
                        int max = stack.getMaxDamage();
                        int cur = max - stack.getDamageValue();
                        lines.add(name + ": " + cur);
                    } else {
                        lines.add(name);
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (lines.isEmpty()) lines.add("Armor: none");
        return lines;
    }

    private static List<String> potionLines(Minecraft mc) {
        List<String> lines = new ArrayList<>();
        try {
            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                String name = effect.getEffect().value().getDisplayName().getString();
                int seconds = effect.getDuration() / 20;
                lines.add(name + " " + (effect.getAmplifier() + 1) + " (" + seconds + "s)");
            }
        } catch (Throwable ignored) {}
        if (lines.isEmpty()) lines.add("No effects");
        return lines;
    }

    private static final SimpleDateFormat CLOCK_FORMAT = new SimpleDateFormat("HH:mm:ss");

    private static List<String> clockLines(Minecraft mc) {
        return List.of(CLOCK_FORMAT.format(new Date()));
    }

    private List<String> cpsLines(Minecraft mc) {
        long now = System.currentTimeMillis();
        recentClicks.removeIf(t -> now - t > 1000L);
        return List.of("CPS: " + recentClicks.size());
    }

    private static float normalizeYaw(float yaw) {
        float y = yaw % 360f;
        if (y < 0) y += 360f;
        return y;
    }

    private static String compassDirection(float yaw) {
        float y = normalizeYaw(yaw);
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE", "S"};
        int index = Math.round(y / 45f);
        return dirs[index];
    }

    // ========================================================================
    // EDITOR-HILFSMETHODEN (von HudLayoutScreen benutzt)
    // ========================================================================

    /** Liefert die id des Widgets, dessen zuletzt gerenderte Box (mx,my) enthält, oder null. */
    public static String hitTest(int mx, int my) {
        if (instance == null) return null;
        for (int i = instance.elements.size() - 1; i >= 0; i--) {
            HudElementData el = instance.elements.get(i);
            if (mx >= el.lastPxX && mx <= el.lastPxX + el.lastPxW
                    && my >= el.lastPxY && my <= el.lastPxY + el.lastPxH) {
                return el.id;
            }
        }
        return null;
    }

    public static int[] pixelPosition(String id, int screenW, int screenH) {
        HudElementData el = instance.byId.get(id);
        if (el == null) return new int[]{0, 0};
        return new int[]{Math.round(el.x * screenW), Math.round(el.y * screenH)};
    }

    /** Setzt die Position eines Widgets anhand einer neuen Pixel-Position
     *  (linke obere Ecke), geklemmt auf den sichtbaren Bereich. Abwärtskompatible
     *  Variante ohne Sperrzonen. */
    public static void updateDragPosition(String id, int pxX, int pxY, int screenW, int screenH) {
        updateDragPosition(id, pxX, pxY, screenW, screenH, null);
    }

    /**
     * Wie oben, respektiert zusätzlich eine Liste von Sperrzonen
     * ({@code {x, y, w, h}}, z.B. der "Done"-Button oder die Hinweis-Leiste im
     * HudLayoutScreen): überlappt die geklemmte Zielposition eine Sperrzone,
     * wird das Widget in die Richtung mit der geringsten nötigen Verschiebung
     * herausgeschoben, statt dahinter (und damit unklickbar) zu landen.
     */
    public static void updateDragPosition(String id, int pxX, int pxY, int screenW, int screenH,
                                          List<int[]> avoidRects) {
        HudElementData el = instance.byId.get(id);
        if (el == null) return;

        int w = Math.max(el.lastPxW, 4);
        int h = Math.max(el.lastPxH, 4);
        int clampedX = Math.max(0, Math.min(screenW - w, pxX));
        int clampedY = Math.max(0, Math.min(screenH - h, pxY));

        if (avoidRects != null && !avoidRects.isEmpty()) {
            // Mehrere Durchläufe, falls das Herausschieben aus einer Zone
            // zufällig in eine andere Sperrzone führt (bei den hier genutzten
            // Zonen praktisch nie relevant, kostet aber nichts).
            for (int pass = 0; pass < 3; pass++) {
                boolean movedAny = false;
                for (int[] r : avoidRects) {
                    if (r == null || r.length < 4) continue;
                    if (!rectsOverlap(clampedX, clampedY, w, h, r[0], r[1], r[2], r[3])) continue;

                    int pushDown  = r[1] + r[3] - clampedY;
                    int pushUp    = clampedY + h - r[1];
                    int pushRight = r[0] + r[2] - clampedX;
                    int pushLeft  = clampedX + w - r[0];
                    int best = Math.min(Math.min(pushDown, pushUp), Math.min(pushRight, pushLeft));

                    if (best == pushDown)       clampedY = Math.min(screenH - h, r[1] + r[3]);
                    else if (best == pushUp)    clampedY = Math.max(0, r[1] - h);
                    else if (best == pushRight) clampedX = Math.min(screenW - w, r[0] + r[2]);
                    else                        clampedX = Math.max(0, r[0] - w);
                    movedAny = true;
                }
                if (!movedAny) break;
            }
        }

        el.x = screenW > 0 ? clampedX / (float) screenW : 0f;
        el.y = screenH > 0 ? clampedY / (float) screenH : 0f;
    }

    private static boolean rectsOverlap(int x1, int y1, int w1, int h1, int x2, int y2, int w2, int h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    /**
     * Rückt bereits platzierte Widgets aus den angegebenen Sperrzonen heraus,
     * falls sie zufällig dort liegen (z.B. "Clock"/"CPS" nahe 0.90 landen bei
     * manchen Auflösungen direkt unter dem "Done"-Button). Nutzt die zuletzt
     * gerenderte Box (lastPxX/Y/W/H), muss also NACH einem drawForEditor-Aufruf
     * laufen. Widgets, die der Aufrufer per {@code skipIds} ausschließt (z.B.
     * das aktuell aktiv gezogene), werden nicht angefasst. Speichert nur, wenn
     * sich wirklich etwas geändert hat.
     */
    public static void resolveOverlaps(int screenW, int screenH, List<int[]> avoidRects, java.util.Set<String> skipIds) {
        if (instance == null || avoidRects == null || avoidRects.isEmpty()) return;
        boolean anyChanged = false;

        for (HudElementData el : instance.elements) {
            if (skipIds != null && skipIds.contains(el.id)) continue;
            if (el.lastPxW <= 0 || el.lastPxH <= 0) continue;

            int newX = el.lastPxX, newY = el.lastPxY;
            int w = el.lastPxW, h = el.lastPxH;
            boolean moved = false;

            for (int[] r : avoidRects) {
                if (r == null || r.length < 4) continue;
                if (!rectsOverlap(newX, newY, w, h, r[0], r[1], r[2], r[3])) continue;

                int pushDown  = r[1] + r[3] - newY;
                int pushUp    = newY + h - r[1];
                int pushRight = r[0] + r[2] - newX;
                int pushLeft  = newX + w - r[0];
                int best = Math.min(Math.min(pushDown, pushUp), Math.min(pushRight, pushLeft));

                if (best == pushDown)       newY = Math.min(screenH - h, r[1] + r[3]);
                else if (best == pushUp)    newY = Math.max(0, r[1] - h);
                else if (best == pushRight) newX = Math.min(screenW - w, r[0] + r[2]);
                else                        newX = Math.max(0, r[0] - w);
                moved = true;
            }

            if (moved) {
                el.x = screenW > 0 ? newX / (float) screenW : 0f;
                el.y = screenH > 0 ? newY / (float) screenH : 0f;
                anyChanged = true;
            }
        }

        if (anyChanged) saveLayout();
    }

    public static void toggleElement(String id) {
        HudElementData el = instance.byId.get(id);
        if (el == null) return;
        el.enabled = !el.enabled;
        saveLayout();
    }

    // ========================================================================
    // FARB-PICKER SICHTBARKEIT (Hue-Slider erscheint erst per "C")
    // ========================================================================

    /** id des Widgets, dessen Hue-Slider gerade eingeblendet ist - null,
     *  wenn keiner offen ist. Es kann immer nur einer gleichzeitig offen
     *  sein, analog zu einem Dropdown/Popup. */
    private static String colorPickerId = null;

    /** Wird von {@link HudLayoutScreen} aufgerufen, wenn "C" gedrückt wird,
     *  während die Maus über einem Widget steht: öffnet den Hue-Slider für
     *  dieses Widget, oder schließt ihn wieder, falls er für dasselbe
     *  Widget schon offen war. */
    public static void toggleColorPicker(String id) {
        colorPickerId = (colorPickerId != null && colorPickerId.equals(id)) ? null : id;
    }

    public static void hideColorPicker() {
        colorPickerId = null;
    }

    public static boolean isColorPickerOpen(String id) {
        return id != null && id.equals(colorPickerId);
    }

    /** Liefert die id des Widgets, dessen zuletzt gezeichneter Hue-Slider
     *  (mx,my) enthält (inkl. kleinem Toleranzrand), oder null. Wird beim
     *  Klick geprüft, bevor der normale Drag-Hit-Test läuft. */
    public static String hitTestColorBar(int mx, int my) {
        if (instance == null) return null;
        for (int i = instance.elements.size() - 1; i >= 0; i--) {
            HudElementData el = instance.elements.get(i);
            if (el.lastBarW <= 0) continue;
            if (mx >= el.lastBarX - 2 && mx <= el.lastBarX + el.lastBarW + 2
                    && my >= el.lastBarY - 3 && my <= el.lastBarY + el.lastBarH + 3) {
                return el.id;
            }
        }
        return null;
    }

    /** Setzt die Farbe des Widgets anhand einer Maus-X-Position relativ zum
     *  zuletzt gezeichneten Hue-Slider (volle Sättigung/Helligkeit, nur der
     *  Farbton wird gewählt - reicht für gut unterscheidbare HUD-Farben). */
    public static void setColorFromBarX(String id, int mouseX) {
        HudElementData el = instance.byId.get(id);
        if (el == null || el.lastBarW <= 0) return;
        float hue = Math.max(0f, Math.min(1f, (mouseX - el.lastBarX) / (float) el.lastBarW));
        el.color = java.awt.Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF;
        saveLayout();
    }

    /** Ändert die individuelle Skalierung eines Widgets (Mausrad im Editor),
     *  geklemmt auf 0.5x - 2.0x. */
    public static void bumpElementScale(String id, float delta) {
        HudElementData el = instance.byId.get(id);
        if (el == null) return;
        el.scaleMul = Math.max(0.5f, Math.min(2.0f, el.scaleMul + delta));
        saveLayout();
    }

    // ========================================================================
    // PERSISTENZ (eigene Datei, unabhängig von Config/Profiles)
    // ========================================================================

    private static final Path LAYOUT_FILE =
            FabricLoader.getInstance().getConfigDir().resolve("newbridge_hud_overlay.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void saveLayout() {
        if (instance == null) return;
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (HudElementData el : instance.elements) {
            JsonObject o = new JsonObject();
            o.addProperty("id", el.id);
            o.addProperty("x", el.x);
            o.addProperty("y", el.y);
            o.addProperty("enabled", el.enabled);
            o.addProperty("color", el.color);
            o.addProperty("scale", el.scaleMul);
            arr.add(o);
        }
        root.add("elements", arr);

        try (FileWriter writer = new FileWriter(LAYOUT_FILE.toFile())) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadLayout() {
        try {
            if (!Files.exists(LAYOUT_FILE)) return;
            try (FileReader reader = new FileReader(LAYOUT_FILE.toFile())) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                if (root == null || !root.has("elements")) return;

                for (var entry : root.getAsJsonArray("elements")) {
                    JsonObject o = entry.getAsJsonObject();
                    if (!o.has("id")) continue;
                    HudElementData el = byId.get(o.get("id").getAsString());
                    if (el == null) continue; // Widget existiert nicht (mehr) - ignorieren

                    if (o.has("x")) el.x = o.get("x").getAsFloat();
                    if (o.has("y")) el.y = o.get("y").getAsFloat();
                    if (o.has("enabled")) el.enabled = o.get("enabled").getAsBoolean();
                    if (o.has("color")) el.color = o.get("color").getAsInt();
                    if (o.has("scale")) el.scaleMul = o.get("scale").getAsFloat();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}