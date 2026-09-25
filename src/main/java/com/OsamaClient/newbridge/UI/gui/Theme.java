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

    private static ClickGuiModule m() { return ClickGuiModule.INSTANCE; }

    public static int accent() { return m() != null ? (m().accent.getColor() | 0xFF000000) : 0xFF6C8CFF; }

    /** Accent with the given alpha (0..1). */
    public static int accent(float alpha) { return ColorUtil.alpha(accent(), alpha); }

    public static float scale() { return m() != null ? (float) m().scale.getValue() : 1.5f; }
    public static float radius() { return m() != null ? (float) m().radius.getValue() : 2.5f; }
    public static float glow() { return m() != null ? (float) m().glow.getValue() : 0.6f; }
    public static float animSpeed() { return m() != null ? (float) m().animSpeed.getValue() : 1f; }
    public static String background() { return m() != null ? m().background.getMode() : "Dim"; }
    public static boolean outline() { return m() == null || m().outline.enabled; }
    public static boolean descriptions() { return m() == null || m().descriptions.enabled; }
}
