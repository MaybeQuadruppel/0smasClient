package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.ClickGui;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.render.font.FontAtlas;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import net.minecraft.client.Minecraft;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Animated HUD list of active modules ("ArrayList"). Läuft jetzt über dieselbe Pipeline wie die
 * ClickGUI ({@link UiRenderer} + {@link UiFont}/{@link FontAtlas}) statt über Minecrafts eigene
 * Bitmap-Schrift - dieselben knackscharfen FreeType-Glyphen, echte abgerundete Ecken statt
 * schlichter Rechtecke, und dieselben globalen Theme-Regler (Accent, Radius, Outline, "Rainbow
 * Accent" aus der ClickGUI).
 *
 * WICHTIG - Aufruf-Stelle: {@link UiRenderer#flush()} baut sich pro Aufruf eine eigene RenderPass.
 * Das mitten in Minecrafts gepuffertem HUD-Draw (der alte {@code HudElementRegistry}-Pfad mit
 * einem {@code GuiGraphicsExtractor}) zu tun, ist nicht sauber - {@link com.OsamaClient.newbridge.UI.gui.ClickGui}
 * wird deshalb laut eigenem Klassenkommentar von einem dedizierten {@code GameRendererUiMixin}
 * gerendert, unabhängig vom normalen HUD-Element-Durchlauf. {@link #draw()} hier erwartet denselben
 * Aufruf-Stil: einmal pro Frame, ohne Parameter, am besten direkt neben {@code ClickGui.INSTANCE.frame()}.
 */
public class ModuleList extends Module {

    public static ModuleList instance;

    // ── Fixed layout constants (in GUI-Units - selbes Koordinatensystem wie Theme/Ui) ─────────
    private static final float RIGHT_MARGIN = 4f;
    private static final float ACCENT_W     = 1.2f;
    /** Zusätzlicher Abstand zwischen den Zeilen (on top of der reinen Zeilenhöhe). */
    private static final float ROW_GAP      = 1.5f;
    private static final float START_Y      = 4f;
    private static final float SLIDE_RANGE  = 24f;
    private static final float ANIM_SPEED   = 0.14f;
    /** Sättigung für den Text-Rainbow - dieselbe wie {@link Theme#accent()} für die Akzent-Variante. */
    private static final float RAINBOW_SAT  = 0.85f;

    // ── Eigene Render-Pipeline, unabhängig von der ClickGUI - die Liste zeichnet jeden Frame,
    // auch während das Menü geschlossen ist, kann sich deren renderer-Instanz also nicht teilen. ──
    private static final UiRenderer renderer = new UiRenderer();
    private static final Ui ui = new Ui(renderer);

    // ── Per-entry animation state ─────────────────────────────────────────────
    // float[0] = alpha   0→1  (fade in/out)
    // float[1] = slideT  0→1  (0 = fully right/off-screen, 1 = in position)
    // float[2] = currentY     (interpolierte Render-Y, in Units)
    private static final Map<String, float[]> anim = new LinkedHashMap<>();

    // ── Settings ──────────────────────────────────────────────────────────────
    private String  sortMode     = "Width";   // Width | Alphabetical
    private boolean rainbow      = false;
    private double  rainbowSpeed = 3.0;
    private boolean letterFade   = false;
    private double  fontScale    = 1.0;
    /** Neu: Hintergrund-Box pro Zeile ein/ausschaltbar. */
    private boolean background   = true;

    private static float rainbowHue = 0f;

    // ─────────────────────────────────────────────────────────────────────────

    public ModuleList() {
        // Liegt in "Client" statt "Misc" - direkt neben dem ClickGui-Modul, wo auch dessen eigene
        // GUI-Einstellungen (Accent, Scale, Rainbow Accent, ...) liegen.
        super("ArrayList", "Animated HUD list of active modules", Category.CLIENT);
        this.enabled = true;
        instance = this;

        this.settings.add(new ModeButton("Sort",
                List.of("Width", "Alphabetical"), 0,
                val -> sortMode = val).withDescription("Determines how the active modules are sorted in the list."));
        this.settings.add(new ToggleButton("Rainbow", false,
                val -> rainbow = val).withDescription("Cycles each entry's text color through the rainbow."));
        this.settings.add(new Slider("Rainbow Speed", 0.5, 10.0, 3.0,
                val -> rainbowSpeed = val).withDescription("Controls the speed of the rainbow color transition."));
        this.settings.add(new ToggleButton("Letter Fade", false,
                val -> letterFade = val).withDescription("Adds a fading animation effect to the text."));
        this.settings.add(new Slider("Text Scale", 0.5, 1.0, 1.0,
                val -> fontScale = val).withDescription("Adjusts the text size and scale of the HUD list."));
        this.settings.add(new ToggleButton("Background", true,
                val -> background = val).withDescription("Draws a small backing box behind each entry."));
    }

    // ── HUD draw - einmal pro Frame, siehe Klassenkommentar zur Aufruf-Stelle ──────────────────

    public static void draw() {
        Minecraft mc = Minecraft.getInstance();
        if (instance == null
                || !instance.enabled
                || mc.player == null
                || mc.gui.hud.isHidden()
                || mc.getDebugOverlay().showDebugScreen()
                || ClickGui.INSTANCE.isOpen()
                || mc.gui.screen() != null) {
            // Drain all animations to 0 so they fade out cleanly on hide
            for (float[] s : anim.values()) { s[0] = 0f; s[1] = 0f; }
            return;
        }

        renderer.begin();
        ui.scale = Theme.scale() * (float) instance.fontScale;
        ui.alpha = 1f;
        ui.hoverBlocked = true; // reines HUD-Overlay, blockt/nimmt nie Maus-Hover entgegen

        // ── Rainbow hue (per-Eintrag Text-Rainbow, siehe "Rainbow"-Setting) ─────────────────────
        if (instance.rainbow) {
            rainbowHue = (rainbowHue + (float) instance.rainbowSpeed * 0.001f) % 1.0f;
        }

        // ── Build sorted active-module list ───────────────────────────────────
        List<Module> active = ModuleManager.modules.stream()
                .filter(m -> m.enabled && m != instance)
                .sorted(instance.buildComparator())
                .collect(Collectors.toList());

        Set<String> activeNames = active.stream()
                .map(m -> m.name)
                .collect(Collectors.toSet());

        // ── Ensure every active module has an anim slot ───────────────────────
        for (Module m : active) {
            anim.putIfAbsent(m.name, new float[]{0f, 0f, START_Y});
        }

        // ── Step animation toward targets ─────────────────────────────────────
        for (Map.Entry<String, float[]> e : anim.entrySet()) {
            boolean present = activeNames.contains(e.getKey());
            float[] s = e.getValue();
            s[0] = approach(s[0], present ? 1f : 0f, ANIM_SPEED);  // alpha
            s[1] = approach(s[1], present ? 1f : 0f, ANIM_SPEED);  // slide
        }

        // Remove entries that have fully faded out and are no longer active
        anim.entrySet().removeIf(e ->
                !activeNames.contains(e.getKey())
                        && e.getValue()[0] < 0.01f);

        List<String> order = new ArrayList<>();
        for (Module m : active) order.add(m.name);
        for (String name : anim.keySet())
            if (!activeNames.contains(name)) order.add(name);

        // ── Font/Layout einmal pro Frame auflösen (FontAtlas rastert exakt für diese Pixelgröße -
        // knackscharf statt hochskalierter Bitmap-Schrift). ────────────────────────────────────
        float textSize = Theme.FONT;
        FontAtlas atlas = ui.font(textSize);
        float lineH = atlas.lineHeight / ui.scale;
        float rowH = lineH + ROW_GAP;
        float rightEdge = ui.width() - RIGHT_MARGIN;
        float radius = Theme.radius();

        float targetY = START_Y;
        for (int i = 0; i < order.size(); i++) {
            String name = order.get(i);
            float[] s = anim.get(name);
            if (s == null) continue;

            if (s[2] <= 0f) s[2] = targetY;
            s[2] = approach(s[2], targetY, 0.18f);

            // Skip fully invisible
            if (s[0] < 0.01f) { targetY += rowH; continue; }

            Module mod = ModuleManager.getModuleByName(name);
            String label = (mod != null) ? mod.name : name;

            float lw = ui.textWidth(label, textSize);
            float slideOffset = (1f - s[1]) * SLIDE_RANGE;
            float textX = rightEdge - lw + slideOffset;
            float renderY = s[2];

            // Dezente Hintergrund-Box im GUI-Stil - jetzt WIRKLICH abgerundet (Theme.radius()),
            // nicht mehr nur ein Rechteck, und per "Background"-Setting komplett abschaltbar.
            if (instance.background) {
                float padX = 3f, padY = 1f;
                ui.alpha = s[0] * 0.75f;
                ui.round(textX - padX, renderY - padY, lw + padX * 2, lineH + padY * 2, radius, Theme.ROW);
                if (Theme.outline()) {
                    ui.outline(textX - padX, renderY - padY, lw + padX * 2, lineH + padY * 2, radius, 1f, Theme.OUTLINE);
                }
            }
            ui.alpha = s[0];

            // Akzent-Leiste am Bildschirmrand: liest Theme.accent(), läuft also automatisch im
            // Regenbogen mit, wenn "Rainbow Accent" in der ClickGUI aktiv ist - ohne Extra-Code hier.
            ui.rect(rightEdge + RIGHT_MARGIN - ACCENT_W, renderY - 1, ACCENT_W, lineH + 2, Theme.accent());

            if (instance.letterFade) {
                float x = textX;
                for (int c = 0; c < label.length(); c++) {
                    String ch = String.valueOf(label.charAt(c));
                    float wave = (float) (Math.sin(System.currentTimeMillis() * 0.003 - c * 0.3 - i * 0.2) * 0.5 + 0.5);
                    int color = getLetterFadeColor(wave);
                    // ui.text() gibt die Pen-x-Position nach dem Zeichen zurück (inkl. Kerning) -
                    // sauberer als die alte manuelle mc.font.width()-Summe.
                    x = ui.text(ch, x, renderY, lineH, textSize, color);
                }
            } else {
                float wave = (float) (Math.sin(System.currentTimeMillis() * 0.0018 + i * 0.45) * 0.5 + 0.5);
                int color;

                if (instance.rainbow) {
                    float h = (rainbowHue + i * 0.08f) % 1.0f;
                    float v = 0.82f + wave * 0.18f;
                    // Sättigung war vorher fest 0 (also nur Graustufen trotz "Rainbow"-Namen) -
                    // jetzt tatsächlich farbig, wie gewünscht.
                    color = ColorUtil.hsvToRgb(h, RAINBOW_SAT, v) | 0xFF000000;
                } else {
                    // Theme-Text ↔ gedimmter Text-Pulse, passend zur restlichen GUI-Palette.
                    color = ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, 0.55f + wave * 0.45f);
                }

                ui.text(label, textX, renderY, lineH, textSize, color);
            }

            targetY += rowH;
        }

        renderer.flush();
        UiFont.collect(600);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Comparator<Module> buildComparator() {
        if ("Alphabetical".equals(sortMode))
            return Comparator.comparing(m -> m.name);
        return (m1, m2) -> Float.compare(
                ui.textWidth(m2.name, Theme.FONT),
                ui.textWidth(m1.name, Theme.FONT));
    }

    private static float approach(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.min(1f, speed);
    }

    /** Spezielle Fade-Berechnung für Theme-Text -> Grau -> Schwarz. Alpha übernimmt Ui#alpha selbst. */
    private static int getLetterFadeColor(float t) {
        if (t < 0.5f) return ColorUtil.lerp(Theme.TEXT, 0xFF999999, t * 2f);
        return ColorUtil.lerp(0xFF999999, 0xFF000000, (t - 0.5f) * 2f);
    }
}