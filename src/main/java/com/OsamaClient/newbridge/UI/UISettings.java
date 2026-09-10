package com.OsamaClient.newbridge.UI;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Globale, persistierbare Einstellungen für das Erscheinungsbild der ClickGUI.
 * Die Werte werden idealerweise in {@code Config} mitgespeichert/geladen
 * (siehe Hinweise unten) – hier liegen nur die live genutzten Felder + Helper.
 */
public final class UISettings {

    private UISettings() {}

    // ── Skalierung (gesamte GUI: Boxen, Hitboxen, Abstände) ─────────────────
    /** 0.75 = kompakt, 1.0 = Standard, 1.25 = groß. */
    public static float scale = 1.0f;
    public static final float SCALE_MIN = 0.7f;
    public static final float SCALE_MAX = 1.3f;

    public static int scaled(int value) {
        return Math.max(1, Math.round(value * scale));
    }

    /**
     * Setzt {@link #scale} und zieht {@link #fontScale} proportional mit,
     * statt beide unabhängig voneinander einstellbar zu lassen. So bleiben
     * Schriftgröße und Boxengröße/-breite immer im gleichen Verhältnis
     * zueinander (kein zu großer Text in zu kleinen Boxen oder umgekehrt).
     * fontScale bleibt dabei innerhalb seiner eigenen, engeren Grenzen
     * (siehe {@link #setFontScale(float)}), damit Text nie unleserlich wird.
     */
    public static void setScale(float value) {
        scale = Math.max(SCALE_MIN, Math.min(SCALE_MAX, value));
        setFontScale(scale);
    }

    // ── Font-Skalierung (nur Text, unabhängig von Boxengröße) ───────────────
    /** 1.0 = Standardgröße. Kleiner als 1.0 -> feinere, kompaktere Schrift. */
    public static float fontScale = 1.0f;
    public static final float FONT_SCALE_MIN = 0.6f;
    public static final float FONT_SCALE_MAX = 1.2f;

    public static void setFontScale(float value) {
        fontScale = Math.max(FONT_SCALE_MIN, Math.min(FONT_SCALE_MAX, value));
    }

    /**
     * Zeichnet Text unter Berücksichtigung von {@link #fontScale}, ohne dass
     * Aufrufer ihre x/y-Koordinaten manuell umrechnen müssen. Fällt bei
     * fontScale == 1.0 direkt auf den normalen Zeichenpfad zurück (kein
     * Overhead durch PoseStack-Transformationen im Normalfall).
     */
    public static void drawText(GuiGraphicsExtractor g, Font font, String str,
                                int x, int y, int color, boolean shadow) {
        if (fontScale == 1.0f) {
            g.text(font, str, x, y, color, shadow);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(fontScale, fontScale);
        g.text(font, str, 0, 0, color, shadow);
        g.pose().popMatrix();
    }

    public static void drawText(GuiGraphicsExtractor g, Font font, String str,
                                int x, int y, int color) {
        if (fontScale == 1.0f) {
            g.text(font, str, x, y, color);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate((float) x, (float) y);
        g.pose().scale(fontScale, fontScale);
        g.text(font, str, 0, 0, color);
        g.pose().popMatrix();
    }

    /**
     * Tatsächlich gerenderte Breite eines Strings inkl. aktueller
     * {@link #fontScale}. {@code Font.width(str)} allein reicht NICHT, sobald
     * fontScale != 1 ist: {@link #drawText} skaliert den Text per PoseStack,
     * wodurch die real gezeichnete Breite von der rohen Font-Breite abweicht.
     * Wer Text rechtsbündig/zentriert positioniert oder Scroll-/Clip-Grenzen
     * berechnet, muss diese Methode statt {@code font.width(...)} verwenden -
     * sonst rutscht der Text bei jeder fontScale != 1 aus der Box.
     */
    public static int textWidth(Font font, String str) {
        return Math.round(font.width(str) * fontScale);
    }

    // ── Kategorie-Spaltenbreite (Customizing der ClickGUI-Panels) ───────────
    /** 0 = automatisch (füllt den Bildschirm gleichmäßig), sonst fixe Breite. */
    public static int customColumnWidth = 0;
    public static final int COLUMN_WIDTH_MAX = 160;

    /**
     * BUGFIX: Vorher wurde jeder Wert über 0 auf mindestens COLUMN_WIDTH_MIN
     * (48) hochgezogen - ein Slider-Wert von z. B. 1 landete dadurch bei 48px
     * und wirkte "normal", obwohl klar kleiner gewählt wurde. Die Spalte darf
     * nie BREITER sein als der eingestellte Wert, deshalb nur noch nach oben
     * (COLUMN_WIDTH_MAX) begrenzen, nicht mehr nach unten hochziehen.
     */
    public static void setCustomColumnWidth(int px) {
        customColumnWidth = Math.max(0, Math.min(COLUMN_WIDTH_MAX, px));
    }

    /** Liefert entweder die manuelle Breite oder, falls 0, die automatisch berechnete. */
    public static int columnWidth(int autoWidth) {
        return customColumnWidth > 0 ? customColumnWidth : autoWidth;
    }

    // ── Compact-Mode (Preset: kleinere Zeilen, engere Abstände, kleinere Schrift) ─
    public static boolean compactMode = false;

    public static void applyCompactPreset() {
        compactMode = true;
        setFontScale(0.85f);
    }

    public static void applyComfortablePreset() {
        compactMode = false;
        setFontScale(1.0f);
    }

    /** Dritte Stufe: etwas größere, luftigere Darstellung (immer noch getestete Grenzwerte). */
    public static void applyLargePreset() {
        compactMode = false;
        setFontScale(1.1f);
    }

    // ── Sound ─────────────────────────────────────────────────────────────
    public static float soundVolume = 1.0f; // 0.0 - 1.0
    public static boolean soundEnabled = true;

    public static void applyToSounds() {
        Sounds.masterVolume = soundVolume;
        Sounds.enabled = soundEnabled;
    }

    // ── Animation ─────────────────────────────────────────────────────────
    public static boolean animationsEnabled = true;

    /** Liefert die Lerp-Schrittweite für Hover/Toggle-Animationen; bei
     *  deaktivierten Animationen springt der Wert sofort auf das Ziel. */
    public static float animStep(float defaultStep) {
        return animationsEnabled ? defaultStep : 1f;
    }

    // ── Ecken-Stil ────────────────────────────────────────────────────────
    /** true = abgerundete Ecken (Standard), false = scharfkantige Rechtecke.
     *  Wird direkt in Component.drawRoundedRect/-Outline ausgewertet, wirkt
     *  also automatisch auf ALLE Komponenten. */
    public static boolean roundedCorners = true;

    // ── Panel-Transparenz ─────────────────────────────────────────────────
    /** 0 = voll durchsichtig, 255 = voll deckend. Wirkt auf die 0xF2-Alpha-Werte. */
    public static int panelAlpha = 0xF2;

    public static int withPanelAlpha(int colorRGB) {
        return (Math.min(255, Math.max(0, panelAlpha)) << 24) | (colorRGB & 0x00FFFFFF);
    }
}