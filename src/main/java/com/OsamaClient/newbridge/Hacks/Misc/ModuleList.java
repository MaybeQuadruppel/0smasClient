package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.*;
import java.util.stream.Collectors;

public class ModuleList extends Module {

    public static ModuleList instance;

    // ── Fixed layout constants (not adjustable) ───────────────────────────────
    /** Abstand zum rechten Rand verkleinert (vorher 18), um näher an der Akzent-Linie zu sein. */
    private static final int  RIGHT_MARGIN = 6;
    /** Width of the vertical accent bar at the screen edge. */
    private static final int  ACCENT_W     = 2;
    /** Pixel rows between module entries. */
    private static final int  SPACING      = 0;
    /** Y of the first entry. */
    private static final int  START_Y      = 4;
    /** How many px to the right the entry starts when fully slid out. */
    private static final int  SLIDE_RANGE  = 50;
    /** Lerp speed per frame (≈0.14 → ~20 frames / ~0.33 s to reach target). */
    private static final float ANIM_SPEED  = 0.14f;

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_GREY  = 0xFF999999;
    private static final int C_BLACK = 0xFF000000;

    // ── Per-entry animation state ─────────────────────────────────────────────
    // float[0] = alpha   0→1  (fade in/out)
    // float[1] = slideT  0→1  (0 = fully right/off-screen, 1 = in position)
    // float[2] = currentY     (interpolated render Y)
    private static final Map<String, float[]> anim = new LinkedHashMap<>();

    // ── Settings ──────────────────────────────────────────────────────────────
    private String  sortMode     = "Width";   // Width | Alphabetical
    private boolean rainbow      = false;
    private double  rainbowSpeed = 3.0;

    // Neue Settings
    private boolean letterFade   = false;
    private double  fontScale    = 1.0;

    private static float rainbowHue = 0f;

    // ─────────────────────────────────────────────────────────────────────────

    public ModuleList() {
        super("ArrayList", "Animated HUD list of active modules", Category.MISC);
        this.enabled = true;
        instance = this;

        this.settings.add(new ModeButton("Sort",
                List.of("Width", "Alphabetical"), 0,
                val -> sortMode = val));
        this.settings.add(new ToggleButton("Rainbow", false,
                val -> rainbow = val));
        this.settings.add(new Slider("Rainbow Speed", 0.5, 10.0, 3.0,
                val -> rainbowSpeed = val));

        // Neue Optionen im Menü registrieren
        this.settings.add(new ToggleButton("Letter Fade", false,
                val -> letterFade = val));
        this.settings.add(new Slider("Text Scale", 0.5, 1.0, 1.0,
                val -> fontScale = val));
    }

    // ── HUD draw (called every frame from HudElementRegistry) ─────────────────

    public static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (instance == null
                || !instance.enabled
                || mc.player == null
                || mc.options.hideGui
                || mc.getDebugOverlay().showDebugScreen()) {
            // Drain all animations to 0 so they fade out cleanly on hide
            for (float[] s : anim.values()) { s[0] = 0f; s[1] = 0f; }
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();

        // Font Skalierung anwenden
        float scale = (float) instance.fontScale;
        int scaledLh = (int) (mc.font.lineHeight * scale);

        // ── Rainbow hue ───────────────────────────────────────────────────────
        if (instance.rainbow) {
            rainbowHue = (rainbowHue + (float) instance.rainbowSpeed * 0.001f) % 1.0f;
        }

        // ── Build sorted active-module list ───────────────────────────────────
        List<Module> active = ModuleManager.modules.stream()
                .filter(m -> m.enabled && m != instance)
                .sorted(instance.buildComparator(mc))
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

        int rightEdge = screenW - RIGHT_MARGIN;

        int targetY = START_Y;
        for (int i = 0; i < order.size(); i++) {
            String name  = order.get(i);
            float[] s    = anim.get(name);
            if (s == null) continue;


            if (s[2] <= 0f) s[2] = targetY;


            s[2] = approach(s[2], targetY, 0.18f);

            // Skip fully invisible
            if (s[0] < 0.01f) { targetY += scaledLh + SPACING + 1; continue; }

            Module mod   = ModuleManager.getModuleByName(name);
            String label = (mod != null) ? mod.name : name;

            // Breite mit Skalierung anpassen
            int lw = (int) (mc.font.width(label) * scale);


            float slideOffset = (1f - s[1]) * SLIDE_RANGE;

            int textX  = (int)(rightEdge - lw + slideOffset);
            int renderY = (int) s[2];

            int accentColor = argbWithAlpha(C_WHITE, s[0]);
            g.fill(screenW - ACCENT_W, renderY - 1,
                    screenW,           renderY + scaledLh + 1,
                    accentColor);

            boolean doScale = scale != 1.0f;
            if (doScale) {
                g.pose().pushMatrix();
                g.pose().scale(scale, scale);
            }

            float invScale = doScale ? (1f / scale) : 1f;
            float scaledTextX = textX * invScale;
            float scaledRenderY = renderY * invScale;

            if (instance.letterFade) {
                float xOffset = 0;
                for (int charIdx = 0; charIdx < label.length(); charIdx++) {
                    String charStr = String.valueOf(label.charAt(charIdx));

                    float wave = (float)(Math.sin(System.currentTimeMillis() * 0.003 - charIdx * 0.3 - i * 0.2) * 0.5 + 0.5);
                    int charColor = getLetterFadeColor(wave, s[0]);

                    g.text(mc.font, charStr, (int)(scaledTextX + xOffset), (int)scaledRenderY, charColor, false);
                    xOffset += mc.font.width(charStr);
                }
            } else {
                // Modus: Normal / Rainbow
                float wave = (float)(Math.sin(System.currentTimeMillis() * 0.0018 + i * 0.45) * 0.5 + 0.5);
                int textColor;

                if (instance.rainbow) {
                    float h = (rainbowHue + i * 0.08f) % 1.0f;
                    float v = 0.82f + wave * 0.18f;
                    textColor = argbWithAlpha(hsvToRgb(h, 0f, v), s[0]);
                } else {
                    // White ↔ light-grey pulse
                    int blended = lerpRgb(C_GREY, C_WHITE, 0.55f + wave * 0.45f);
                    textColor   = argbWithAlpha(blended, s[0]);
                }

                g.text(mc.font, label, (int)scaledTextX, (int)scaledRenderY, textColor, false);
            }

            // Skalierung wieder zurücksetzen
            if (doScale) {
                g.pose().popMatrix();
            }

            targetY += scaledLh + SPACING + 1;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Comparator<Module> buildComparator(Minecraft mc) {
        if ("Alphabetical".equals(sortMode))
            return Comparator.comparing(m -> m.name);
        // Width: widest name first (classic client style)
        return (m1, m2) -> mc.font.width(m2.name) - mc.font.width(m1.name);
    }

    private static float approach(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.min(1f, speed);
    }

    /** Blend two opaque ARGB colours linearly. */
    private static int lerpRgb(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int fR = (from >> 16) & 0xFF, fG = (from >> 8) & 0xFF, fB = from & 0xFF;
        int tR = (to   >> 16) & 0xFF, tG = (to   >> 8) & 0xFF, tB = to   & 0xFF;
        return 0xFF000000
                | ((int)(fR + (tR - fR) * t) << 16)
                | ((int)(fG + (tG - fG) * t) << 8)
                |  (int)(fB + (tB - fB) * t);
    }

    /** Spezielle Fade-Berechnung für Weiß -> Grau -> Schwarz */
    private static int getLetterFadeColor(float t, float alpha) {
        int blended;
        if (t < 0.5f) {
            // Erste Hälfte der Welle: Weiß zu Grau
            blended = lerpRgb(C_WHITE, C_GREY, t * 2f);
        } else {
            // Zweite Hälfte: Grau zu Schwarz
            blended = lerpRgb(C_GREY, C_BLACK, (t - 0.5f) * 2f);
        }
        return argbWithAlpha(blended, alpha);
    }

    /** Apply alpha multiplier (0–1) to an ARGB colour. */
    private static int argbWithAlpha(int rgb, float alpha) {
        int a = (int)(((rgb >> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (rgb & 0x00FFFFFF) | (a << 24);
    }

    /** HSV → ARGB (full alpha). */
    private static int hsvToRgb(float h, float s, float v) {
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(h, s, v) & 0x00FFFFFF);
    }
}