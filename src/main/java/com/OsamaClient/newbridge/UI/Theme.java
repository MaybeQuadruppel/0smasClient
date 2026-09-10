package com.OsamaClient.newbridge.UI;

/**
 * Zentrales Theme-System für die ClickGUI.
 * Jede Komponente liest ihre Farben über {@code Theme.getActive().palette}
 * statt fest verdrahteter Konstanten – dadurch reicht ein Wechsel hier,
 * um die komplette GUI umzufärben.
 *
 * Zusätzlich zu den festen Presets gibt es {@link #CUSTOM}: eine editierbare
 * Palette, die zur Laufzeit (z. B. über einen Farb-Picker in den Settings)
 * verändert werden kann, ohne dass ein neues Enum-Konstrukt nötig ist.
 */
public enum Theme {

    MONOCHROME("Monochrome", "\u25A0", new Palette(
            0xF20A0A0A, 0xFF181818, 0xFF2C2C2C, 0xFF999999,
            0xFFFFFFFF, 0xFFEEEEEE, 0xFF666666, 0xFFFFFFFF, 0xFF3A3A3A, 0xFFBBBBBB)),

    OCEAN("Ocean", "\u2248", new Palette(
            0xF2081018, 0xFF102030, 0xFF1E3346, 0xFF3E7CA6,
            0xFF39C0FF, 0xFFE8F6FF, 0xFF5C7A8C, 0xFF39C0FF, 0xFF1B2A36, 0xFF7FD0FF)),

    CRIMSON("Crimson", "\u2726", new Palette(
            0xF2140808, 0xFF241010, 0xFF3A1616, 0xFFA65858,
            0xFFFF4D4D, 0xFFF8E8E8, 0xFF8C5C5C, 0xFFFF4D4D, 0xFF3A1A1A, 0xFFFF9E9E)),

    FOREST("Forest", "\u2618", new Palette(
            0xF20A140A, 0xFF122016, 0xFF1E3320, 0xFF5F9E68,
            0xFF4DFF88, 0xFFE8F8EC, 0xFF5C8C63, 0xFF4DFF88, 0xFF1A331F, 0xFF9EFFBB)),

    VIOLET("Violet", "\u2727", new Palette(
            0xF2100A18, 0xFF1A1024, 0xFF2C1E3A, 0xFF8C6FA6,
            0xFFBB7BFF, 0xFFF0E8FF, 0xFF7C6C8C, 0xFFBB7BFF, 0xFF241A33, 0xFFD3A6FF)),

    /** Frei editierbares Theme, z. B. über einen Farb-Picker in den Settings. */
    CUSTOM("Custom", "\u2699", new Palette(
            0xF20A0A0A, 0xFF181818, 0xFF2C2C2C, 0xFF999999,
            0xFFFFFFFF, 0xFFEEEEEE, 0xFF666666, 0xFFFFFFFF, 0xFF3A3A3A, 0xFFBBBBBB));

    public final String displayName;
    public final String icon;
    public final Palette palette;

    Theme(String displayName, String icon, Palette palette) {
        this.displayName = displayName;
        this.icon = icon;
        this.palette = palette;
    }

    // ── Aktives Theme (global, wird von Config gelesen/geschrieben) ─────────
    private static Theme active = MONOCHROME;

    public static Theme getActive() { return active; }

    public static void setActive(Theme theme) {
        if (theme != null) active = theme;
    }

    public static void setActive(String name) {
        for (Theme t : values()) {
            if (t.name().equalsIgnoreCase(name)) { active = t; return; }
        }
    }

    public static Theme next() {
        Theme[] vals = values();
        active = vals[(active.ordinal() + 1) % vals.length];
        return active;
    }

    public static Theme previous() {
        Theme[] vals = values();
        active = vals[(active.ordinal() - 1 + vals.length) % vals.length];
        return active;
    }

    // ── Custom-Theme-Helfer ──────────────────────────────────────────────────

    /** Kopiert die Farben eines bestehenden Themes in die CUSTOM-Palette als Startpunkt. */
    public static void seedCustomFrom(Theme base) {
        if (base != null) CUSTOM.palette.copyFrom(base.palette);
    }

    // ── Custom-Theme: aus wenigen Basisfarben ableiten ──────────────────────
    // Statt jeden einzelnen Palette-Slot über einen eigenen Farb-Picker
    // einzustellen (Accent, Border, Border-Hover, Enabled, Keybind, ...),
    // wählt man nur eine Hauptfarbe + eine Textfarbe. Alle anderen Slots
    // werden automatisch daraus abgeleitet (z. B. Border = abgedunkelte
    // Hauptfarbe, Border-Hover = aufgehellte Hauptfarbe). Das ist sowohl
    // einfacher zu bedienen als auch optisch stimmiger, weil alle
    // abgeleiteten Farben zwangsläufig zusammenpassen.

    /** Setzt die komplette CUSTOM-Palette anhand von nur zwei Basisfarben
     *  (Hauptfarbe + Textfarbe) und aktiviert anschließend das Custom-Theme. */
    public static void applyCustomBase(int mainColor, int textColor) {
        CUSTOM.set(ColorSlot.ACCENT, mainColor);
        CUSTOM.set(ColorSlot.ENABLED, mainColor);
        CUSTOM.set(ColorSlot.KEYBIND, mainColor);
        CUSTOM.set(ColorSlot.BORDER, darken(mainColor, 0.55f));
        CUSTOM.set(ColorSlot.BORDER_HOVER, lighten(mainColor, 0.25f));

        CUSTOM.set(ColorSlot.TEXT, textColor);
        CUSTOM.set(ColorSlot.TEXT_DIM, darken(textColor, 0.4f));
        CUSTOM.set(ColorSlot.DISABLED, darken(textColor, 0.55f));

        setActive(CUSTOM);
    }

    /** Mischt eine Farbe Richtung Schwarz. amount 0 = unverändert, 1 = schwarz. Alpha bleibt erhalten. */
    public static int darken(int argb, float amount) {
        return mix(argb, 0x000000, amount);
    }

    /** Mischt eine Farbe Richtung Weiß. amount 0 = unverändert, 1 = weiß. Alpha bleibt erhalten. */
    public static int lighten(int argb, float amount) {
        return mix(argb, 0xFFFFFF, amount);
    }

    private static int mix(int argb, int targetRGB, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a  = (argb >>> 24) & 0xFF;
        int r  = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int tr = (targetRGB >> 16) & 0xFF, tg = (targetRGB >> 8) & 0xFF, tb = targetRGB & 0xFF;
        int nr = Math.round(r + (tr - r) * t);
        int ng = Math.round(g + (tg - g) * t);
        int nb = Math.round(b + (tb - b) * t);
        return (a << 24) | (nr << 16) | (ng << 8) | nb;
    }

    /** Einzelnen Farbkanal des aktuell aktiven Themes lesen (für einen Farb-Picker). */
    public int get(ColorSlot slot) { return slot.get(palette); }

    /** Einzelnen Farbkanal setzen – funktioniert für jedes Theme, sinnvoll v. a. für CUSTOM. */
    public void set(ColorSlot slot, int color) { slot.set(palette, color); }

    /** Adressierbare Farbkanäle einer Palette – Basis für einen generischen Farb-Picker. */
    public enum ColorSlot {
        BACKGROUND, BACKGROUND_HOVER, BORDER, BORDER_HOVER, ACCENT,
        TEXT, TEXT_DIM, ENABLED, DISABLED, KEYBIND;

        int get(Palette p) {
            return switch (this) {
                case BACKGROUND -> p.bg;
                case BACKGROUND_HOVER -> p.bgHover;
                case BORDER -> p.border;
                case BORDER_HOVER -> p.borderHover;
                case ACCENT -> p.accent;
                case TEXT -> p.text;
                case TEXT_DIM -> p.textDim;
                case ENABLED -> p.enabled;
                case DISABLED -> p.disabled;
                case KEYBIND -> p.keybind;
            };
        }

        void set(Palette p, int color) {
            switch (this) {
                case BACKGROUND -> p.bg = color;
                case BACKGROUND_HOVER -> p.bgHover = color;
                case BORDER -> p.border = color;
                case BORDER_HOVER -> p.borderHover = color;
                case ACCENT -> p.accent = color;
                case TEXT -> p.text = color;
                case TEXT_DIM -> p.textDim = color;
                case ENABLED -> p.enabled = color;
                case DISABLED -> p.disabled = color;
                case KEYBIND -> p.keybind = color;
            }
        }
    }

    /**
     * Ein vollständiger Farbsatz für eine GUI-Komponente.
     * Bewusst nicht {@code final}: {@link Theme#CUSTOM} muss zur Laufzeit
     * (z. B. per Farb-Picker) veränderbar sein.
     */
    public static final class Palette {
        public int bg;          // Panel-/Komponentenhintergrund
        public int bgHover;     // Hintergrund bei Hover
        public int border;      // Standard-Rahmen
        public int borderHover; // Rahmen bei Hover/Fokus
        public int accent;      // Haupt-Akzentfarbe (Slider-Fill, aktiver Zustand, ...)
        public int text;        // Haupttext
        public int textDim;     // Gedimmter Text
        public int enabled;     // "An"-Zustand (Toggle, Modul aktiv)
        public int disabled;    // "Aus"-Zustand
        public int keybind;     // Keybind-Badge

        public Palette(int bg, int bgHover, int border, int borderHover, int accent,
                       int text, int textDim, int enabled, int disabled, int keybind) {
            this.bg = bg;
            this.bgHover = bgHover;
            this.border = border;
            this.borderHover = borderHover;
            this.accent = accent;
            this.text = text;
            this.textDim = textDim;
            this.enabled = enabled;
            this.disabled = disabled;
            this.keybind = keybind;
        }

        public void copyFrom(Palette other) {
            this.bg = other.bg;
            this.bgHover = other.bgHover;
            this.border = other.border;
            this.borderHover = other.borderHover;
            this.accent = other.accent;
            this.text = other.text;
            this.textDim = other.textDim;
            this.enabled = other.enabled;
            this.disabled = other.disabled;
            this.keybind = other.keybind;
        }
    }
}