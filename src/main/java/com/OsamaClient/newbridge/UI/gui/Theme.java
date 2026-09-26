package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;

/** Sizes, palette and user-customizable values (read from {@link ClickGuiModule}). */
public final class Theme {

    private Theme() {}

    // sizes in GUI units
    public static final float PANEL_W = 92, HEADER_H = 14, ROW_H = 11, SETTING_H = 10, PAD = 3, GAP = 6;
    public static final float FONT = 7, FONT_SMALL = 6.5f;

    // fixed base palette
    public static final int PANEL_BG = 0xF0121216;
    public static final int ROW = 0xFF18181D;
    public static final int ROW_HOVER = 0xFF1F1F26;
    public static final int TEXT = 0xFFE6E6EB;
    public static final int TEXT_DIM = 0xFF8A8A96;
    public static final int SETTING_BG = 0xFF141418;
    public static final int OUTLINE = 0x22FFFFFF;
    public static final int TRACK = 0xFF2A2A33;
    /** Background of an expanded module's settings block; a touch lighter/bluer than a row so it visibly stands out. */
    public static final int SETTING_BLOCK_BG = 0xFF20202B;
    /** Thin divider drawn at the top of a highlighted settings block. */
    public static final int SETTING_BLOCK_BORDER = 0x40FFFFFF;

    private static ClickGuiModule m() { return ClickGuiModule.INSTANCE; }

    /**
     * Aktuelle Akzentfarbe. Läuft "Rainbow Accent" ({@link ClickGuiModule#rainbowAccent}), wird die vom
     * Nutzer gewählte Farbe ignoriert und stattdessen ein zeitbasierter Hue-Wert genommen - dadurch
     * schiften automatisch ALLE Stellen, die {@link #accent()} lesen (ClickGUI-Glow/Outline/Tooltip
     * genauso wie z.B. die ArrayList), gemeinsam durch den Regenbogen, ohne dass jede Stelle das selbst
     * nachbauen müsste.
     */
    public static int accent() {
        ClickGuiModule mod = m();
        if (mod != null && mod.rainbowAccent.enabled) {
            float speed = (float) mod.rainbowSpeed.getValue();
            float hue = (float) ((System.currentTimeMillis() % 100_000L) * 0.0001 * speed);
            return ColorUtil.hsvToRgb(hue, 0.85f, 1f) | 0xFF000000;
        }
        return mod != null ? (mod.accent.getColor() | 0xFF000000) : 0xFF6C8CFF;
    }

    /** Accent with the given alpha (0..1). */
    public static int accent(float alpha) { return ColorUtil.alpha(accent(), alpha); }

    public static float scale() { return m() != null ? (float) m().scale.getValue() : 1.5f; }
    public static float radius() { return m() != null ? (float) m().radius.getValue() : 2.5f; }
    public static float glow() { return m() != null ? (float) m().glow.getValue() : 0.6f; }
    public static float animSpeed() { return m() != null ? (float) m().animSpeed.getValue() : 1f; }
    public static String background() { return m() != null ? m().background.getMode() : "Dim"; }
    public static boolean outline() { return m() == null || m().outline.enabled; }
    public static boolean descriptions() { return m() == null || m().descriptions.enabled; }

    /** Panel width in units; user-adjustable so panels can be made roomier. */
    public static float panelW() { return m() != null ? (float) m().panelWidth.getValue() : PANEL_W; }
    /** Module row height in units; taller rows give more breathing room at high scale. */
    public static float rowH() { return m() != null ? (float) m().rowHeight.getValue() : ROW_H; }
    /** Panel background opacity (0..1). */
    public static float panelOpacity() { return m() != null ? (float) m().panelOpacity.getValue() : 0.94f; }
    /** Whether an expanded module's settings get a visually distinct background/border. */
    public static boolean settingHighlight() { return m() == null || m().settingHighlight.enabled; }

    /** Panel background color with the current opacity applied on top of the fixed base tint. */
    public static int panelBg() {
        int a = Math.round(panelOpacity() * 255f) << 24;
        return a | (PANEL_BG & 0x00FFFFFF);
    }
}