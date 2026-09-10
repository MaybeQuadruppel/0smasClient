package com.OsamaClient.newbridge.UI;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Globale, persistierbare Einstellungen für die ClickGUI.
 *
 * Die Werte werden von UISettingsModule verändert und von Config
 * gespeichert/geladen.
 */
public final class UISettings {

    private UISettings() {}

    // ========================================================================
    // SCALE
    // ========================================================================

    /**
     * Gesamtskalierung der GUI.
     *
     * 0.70 = sehr kompakt
     * 0.90 = kompakt
     * 1.00 = Standard
     * 1.30 = groß
     */
    public static float scale = 0.90f;

    public static final float SCALE_MIN = 0.70f;
    public static final float SCALE_MAX = 1.30f;

    public static int scaled(int value) {
        return Math.max(
                1,
                Math.round(value * scale)
        );
    }

    public static void setScale(float value) {
        scale = Math.max(
                SCALE_MIN,
                Math.min(
                        SCALE_MAX,
                        value
                )
        );

        /*
         * Font bleibt proportional zur GUI.
         */
        setFontScale(scale);
    }


    // ========================================================================
    // FONT SCALE
    // ========================================================================

    public static float fontScale = 1.0f;

    public static final float FONT_SCALE_MIN = 0.60f;
    public static final float FONT_SCALE_MAX = 1.20f;

    public static void setFontScale(float value) {
        fontScale = Math.max(
                FONT_SCALE_MIN,
                Math.min(
                        FONT_SCALE_MAX,
                        value
                )
        );
    }

    public static void drawText(
            GuiGraphicsExtractor g,
            Font font,
            String str,
            int x,
            int y,
            int color,
            boolean shadow
    ) {

        if (fontScale == 1.0f) {

            g.text(
                    font,
                    str,
                    x,
                    y,
                    color,
                    shadow
            );

            return;
        }

        g.pose().pushMatrix();

        g.pose().translate(
                (float) x,
                (float) y
        );

        g.pose().scale(
                fontScale,
                fontScale
        );

        g.text(
                font,
                str,
                0,
                0,
                color,
                shadow
        );

        g.pose().popMatrix();
    }

    public static void drawText(
            GuiGraphicsExtractor g,
            Font font,
            String str,
            int x,
            int y,
            int color
    ) {

        if (fontScale == 1.0f) {

            g.text(
                    font,
                    str,
                    x,
                    y,
                    color
            );

            return;
        }

        g.pose().pushMatrix();

        g.pose().translate(
                (float) x,
                (float) y
        );

        g.pose().scale(
                fontScale,
                fontScale
        );

        g.text(
                font,
                str,
                0,
                0,
                color
        );

        g.pose().popMatrix();
    }

    public static int textWidth(
            Font font,
            String str
    ) {

        return Math.round(
                font.width(str) * fontScale
        );
    }


    // ========================================================================
    // NEW GUI LAYOUT
    // ========================================================================

    /**
     * Breite der linken Sidebar.
     *
     * 0.70 = sehr schmal
     * 1.00 = Standard
     * 1.30 = breit
     */
    public static float sidebarWidthScale = 1.0f;

    public static final float SIDEBAR_WIDTH_MIN = 0.75f;
    public static final float SIDEBAR_WIDTH_MAX = 1.30f;

    public static void setSidebarWidthScale(
            float value
    ) {

        sidebarWidthScale = Math.max(
                SIDEBAR_WIDTH_MIN,
                Math.min(
                        SIDEBAR_WIDTH_MAX,
                        value
                )
        );
    }

    public static int sidebarWidth() {

        return scaled(
                Math.round(
                        108 * sidebarWidthScale
                )
        );
    }


    /**
     * Breite der Modul-Karten.
     *
     * 0.75 = schmalere Karten
     * 1.00 = Standard
     * 1.25 = breitere Karten
     *
     * Die GUI bleibt dabei immer responsive.
     */
    public static float columnWidthScale = 0.90f;

    public static final float COLUMN_WIDTH_MIN = 0.65f;
    public static final float COLUMN_WIDTH_MAX = 1.25f;

    public static void setCustomColumnWidth(
            float value
    ) {

        columnWidthScale = Math.max(
                COLUMN_WIDTH_MIN,
                Math.min(
                        COLUMN_WIDTH_MAX,
                        value
                )
        );
    }


    /**
     * Abstand zwischen Modul-Karten.
     */
    public static float moduleGapScale = 1.0f;

    public static final float MODULE_GAP_MIN = 0.50f;
    public static final float MODULE_GAP_MAX = 1.75f;

    public static void setModuleGapScale(
            float value
    ) {

        moduleGapScale = Math.max(
                MODULE_GAP_MIN,
                Math.min(
                        MODULE_GAP_MAX,
                        value
                )
        );
    }

    public static int moduleGap() {

        return Math.max(
                1,
                Math.round(
                        scaled(4)
                                * moduleGapScale
                )
        );
    }


    // ========================================================================
    // COMPACT MODE
    // ========================================================================

    public static boolean compactMode = false;

    public static void applyCompactPreset() {

        compactMode = true;

        /*
         * Nicht über setScale(), weil der Benutzer seinen
         * eigentlichen Scale-Wert behalten können soll.
         */
        setFontScale(
                Math.min(
                        0.85f,
                        scale
                )
        );

        moduleGapScale = 0.75f;
    }

    public static void applyComfortablePreset() {

        compactMode = false;

        setFontScale(
                Math.max(
                        1.0f,
                        Math.min(
                                1.0f,
                                scale
                        )
                )
        );

        moduleGapScale = 1.0f;
    }

    public static void applyLargePreset() {

        compactMode = false;

        setFontScale(
                Math.min(
                        1.10f,
                        FONT_SCALE_MAX
                )
        );

        moduleGapScale = 1.25f;
    }


    public static int panelAlpha = 0xF2;

    public static int withPanelAlpha(int colorRGB) {
        int alpha = Math.max(0, Math.min(255, panelAlpha));
        return (alpha << 24) | (colorRGB & 0x00FFFFFF);
    }

    /**
     * Mischung aus Basis-Hintergrund und Akzentfarbe für ein dynamisches Theme-Feeling.
     */
    public static int getTintedBackground(int baseBg, int accentColor, float tintFactor) {
        int r = (int) (((baseBg >> 16) & 0xFF) * (1f - tintFactor) + ((accentColor >> 16) & 0xFF) * tintFactor);
        int g = (int) (((baseBg >> 8) & 0xFF) * (1f - tintFactor) + ((accentColor >> 8) & 0xFF) * tintFactor);
        int b = (int) ((baseBg & 0xFF) * (1f - tintFactor) + (accentColor & 0xFF) * tintFactor);
        int rgb = (r << 16) | (g << 8) | b;
        return withPanelAlpha(rgb);
    }


    // ========================================================================
    // ANIMATION
    // ========================================================================

    public static boolean animationsEnabled = true;

    public static float animStep(
            float defaultStep
    ) {

        return animationsEnabled
                ? defaultStep
                : 1f;
    }


    // ========================================================================
    // CORNERS
    // ========================================================================

    public static boolean roundedCorners = true;


    // ========================================================================
    // SOUND
    // ========================================================================

    public static float soundVolume = 1.0f;

    public static boolean soundEnabled = true;

    public static void applyToSounds() {

        Sounds.masterVolume =
                Math.max(
                        0f,
                        Math.min(
                                1f,
                                soundVolume
                        )
                );

        Sounds.enabled =
                soundEnabled;
    }
}