package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Fortgeschrittener Farb-Picker:
 *  - 2D-Saturation/Brightness-Feld (SV-Square)
 *  - vertikaler Hue-Regler
 *  - vertikaler Alpha-Regler (mit Schachbrett-Hintergrund)
 *  - editierbares HEX-Eingabefeld (#RRGGBB oder #AARRGGBB)
 *  - Live-RGBA-Anzeige
 *  - Presets + persistierbare Favoriten (Klick = wählen, Rechtsklick = löschen)
 *
 * Kompatibel zum alten Picker: {@link #getLabel()}, {@link #getColor()} und
 * {@link #setColor(int)} bleiben unverändert, damit Config-Serialisierung und
 * bestehende Aufrufer (Trajectories, UISettingsModule) weiter funktionieren.
 *
 * Hinweis zum Eyedropper: Ein echtes Bildschirm-Pixel-Sampling ist in der neuen
 * Render-Pipeline (26.2) nicht ohne riskantes GPU-Readback möglich und wurde
 * daher bewusst weggelassen. Die Presets/Favoriten decken den praktischen
 * "schnell eine Farbe übernehmen"-Anwendungsfall ab.
 */
public class ColorPicker extends Component {

    // ── Persistente, über alle Picker geteilte Favoriten ────────────────────
    // Bewusst statisch: ein einmal gespeicherter Lieblingston steht in jedem
    // Farb-Picker der GUI zur Verfügung. (In-Memory; ein späterer Config-Hook
    // kann getFavorites()/den Setter serialisieren.)
    private static final List<Integer> FAVORITES = new ArrayList<>();
    private static final int MAX_FAVORITES = 8;

    private static final int[] PRESETS = {
            0xFFFF5555, 0xFFFFAA00, 0xFFFFFF55, 0xFF55FF55,
            0xFF55FFFF, 0xFF5599FF, 0xFFAA55FF, 0xFFFFFFFF
    };

    // ── Basis-Layout (unskaliert; über UISettings.scaled() multipliziert) ───
    private static final int PAD      = 6;
    private static final int HEADER_H = 16;  // Label + Vorschau-Zeile
    private static final int SV_W      = 60;
    private static final int SV_H      = 42;
    private static final int BAR_W     = 8;
    private static final int BAR_GAP   = 5;
    private static final int HEX_H     = 12;
    private static final int ROW_GAP   = 5;
    private static final int SW_SIZE   = 9;  // Swatch-Kantenlänge
    private static final int SW_GAP    = 3;
    private static final int BASE_W    = 110;
    private static final int BASE_H    = HEADER_H + SV_H + ROW_GAP + HEX_H + ROW_GAP + SW_SIZE + 3; // ≈ 100

    private final String label;
    private final Consumer<Integer> onChange;

    private float hue = 0f;
    private float saturation = 1f;
    private float brightness = 1f;
    private int alpha = 255;          // 0..255
    private int color;               // volles ARGB

    private enum Drag { NONE, SV, HUE, ALPHA }
    private Drag dragging = Drag.NONE;

    private boolean hexFocused = false;
    private String hexInput = "";     // ohne '#', während der Eingabe
    private boolean collapsed = true; // eingeklappt: nur "Label + Swatch"
    private float hoverAnim = 0f;

    private int headerH() { return UISettings.scaled(12); }

    public ColorPicker(String label, int defaultColor, Consumer<Integer> onChange) {
        super(0, 0, BASE_W, BASE_H);
        this.label = label;
        this.onChange = onChange;
        syncScaledSize(BASE_W, BASE_H, 96, 90);
        applyArgb(defaultColor, false);
    }

    // ── Öffentliche API (kompatibel mit Config & Altcode) ───────────────────

    public String getLabel() { return this.label; }
    public int getColor()    { return this.color; }

    public void setColor(int newColor) {
        applyArgb(newColor, true);
    }

    public ColorPicker withDescription(String description) {
        this.description = description;
        return this;
    }

    /** Aktuelle Favoritenliste (für die Persistierung durch Config). */
    public static List<Integer> getFavorites() {
        return new ArrayList<>(FAVORITES);
    }

    /** Ersetzt die Favoritenliste beim Laden der Config (dedupliziert, gekappt). */
    public static void loadFavorites(List<Integer> colors) {
        FAVORITES.clear();
        if (colors == null) return;
        for (Integer c : colors) {
            if (c != null && !FAVORITES.contains(c) && FAVORITES.size() < MAX_FAVORITES) {
                FAVORITES.add(c);
            }
        }
    }

    // ── Farb-Zerlegung / -Zusammensetzung ───────────────────────────────────

    /** Übernimmt ein ARGB, zerlegt es in HSV + Alpha und (optional) feuert onChange. */
    private void applyArgb(int argb, boolean notify) {
        int a = (argb >>> 24) & 0xFF;
        // Wenn kein Alpha angegeben wurde (Top-Byte 0), als voll deckend behandeln –
        // sonst wäre eine als reines RGB übergebene Standardfarbe unsichtbar.
        this.alpha = (a == 0) ? 255 : a;

        float[] hsb = new float[3];
        java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, hsb);
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];

        recomposeColor(notify);
    }

    /** Baut {@link #color} aus HSV + Alpha neu und feuert optional onChange. */
    private void recomposeColor(boolean notify) {
        int rgb = java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0x00FFFFFF;
        this.color = (alpha << 24) | rgb;
        if (!hexFocused) hexInput = hexString(false);
        if (notify && onChange != null) onChange.accept(this.color);
    }

    private String hexString(boolean withHash) {
        String body = alpha == 255
                ? String.format("%06X", color & 0xFFFFFF)
                : String.format("%08X", color);
        return withHash ? "#" + body : body;
    }

    // ── Region-Geometrie ────────────────────────────────────────────────────

    private int svX()  { return x + UISettings.scaled(PAD); }
    private int svY()  { return y + UISettings.scaled(HEADER_H); }
    private int svW()  { return UISettings.scaled(SV_W); }
    private int svH()  { return UISettings.scaled(SV_H); }

    private int hueX() { return svX() + svW() + UISettings.scaled(BAR_GAP); }
    private int barW() { return UISettings.scaled(BAR_W); }
    private int alphaX() { return hueX() + barW() + UISettings.scaled(4); }

    private int hexY() { return svY() + svH() + UISettings.scaled(ROW_GAP); }
    private int hexW() { return UISettings.scaled(64); }
    private int hexH() { return UISettings.scaled(HEX_H); }

    private int swatchY() { return hexY() + hexH() + UISettings.scaled(ROW_GAP); }
    private int swSize()  { return UISettings.scaled(SW_SIZE); }
    private int swGap()   { return UISettings.scaled(SW_GAP); }

    private static boolean inRect(double mx, double my, int rx, int ry, int rw, int rh) {
        return mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh;
    }

    // ── Rendering ───────────────────────────────────────────────────────────

    @Override
    public void render(Object graphics, int mouseX, int mouseY) {
        if (!(graphics instanceof GuiGraphicsExtractor g)) return;

        Theme.Palette p = Theme.getActive().palette;
        if (this.width <= 0) this.width = UISettings.scaled(baseWidth);
        this.baseHeight = baseHeight;
        int headH = headerH();
        this.height = collapsed ? headH : Math.max(headH, UISettings.scaled(baseHeight));

        boolean headerHov = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + headH;
        hoverAnim = approach(hoverAnim, headerHov ? 1f : 0f, 0.3f);

        // Kopfzeile: "Label" links + Swatch rechts (klickbar zum Auf-/Zuklappen)
        if (hoverAnim > 0.01f) {
            g.fill(x, y, x + width, y + headH, withAlpha(p.bgHover, hoverAnim * 0.4f));
        }
        int pad = UISettings.scaled(PAD);
        UISettings.drawText(g, Minecraft.getInstance().font, label,
                x + pad, y + (headH - UISettings.scaled(7)) / 2,
                lerpColor(p.textDim, p.text, hoverAnim), false);
        int pvW = UISettings.scaled(12), pvH = Math.max(4, headH - UISettings.scaled(4));
        int pvX = x + width - pvW - pad, pvY = y + (headH - pvH) / 2;
        drawChecker(g, pvX, pvY, pvW, pvH);
        g.fill(pvX, pvY, pvX + pvW, pvY + pvH, color);
        drawRoundedOutline(g, pvX, pvY, pvW, pvH, p.border);

        if (collapsed) return;

        // Live-Drag anwenden (nur im aufgeklappten Zustand)
        switch (dragging) {
            case SV    -> applySV(mouseX, mouseY);
            case HUE   -> applyHue(mouseY);
            case ALPHA -> applyAlpha(mouseY);
            default -> {}
        }

        renderSvSquare(g, p);
        renderHueBar(g);
        renderAlphaBar(g);
        renderHexField(g, p, mouseX, mouseY);
        renderSwatches(g, p, mouseX, mouseY);
    }

    /** SV-Feld: pro Spalte ein vertikaler Verlauf vollfarbig→schwarz.
     *  Da HSB-Helligkeit die RGB-Werte linear skaliert, entspricht der lineare
     *  Verlauf topColor→schwarz exakt der Helligkeitsachse. */
    private void renderSvSquare(GuiGraphicsExtractor g, Theme.Palette p) {
        int sx = svX(), sy = svY(), sw = svW(), sh = svH();
        for (int i = 0; i < sw; i++) {
            int top = java.awt.Color.HSBtoRGB(hue, i / (float) sw, 1f) | 0xFF000000;
            g.fillGradient(sx + i, sy, sx + i + 1, sy + sh, top, 0xFF000000);
        }
        drawRoundedOutline(g, sx, sy, sw, sh, p.border);

        int tx = sx + Math.round(saturation * (sw - 1));
        int ty = sy + Math.round((1f - brightness) * (sh - 1));
        // Kleiner Ring als SV-Thumb
        drawRoundedOutline(g, tx - 2, ty - 2, 5, 5, 0xFF000000);
        drawRoundedOutline(g, tx - 1, ty - 1, 3, 3, 0xFFFFFFFF);
    }

    private void renderHueBar(GuiGraphicsExtractor g) {
        int hx = hueX(), hy = svY(), hw = barW(), hh = svH();
        for (int i = 0; i < hh; i++) {
            int col = java.awt.Color.HSBtoRGB(i / (float) hh, 1f, 1f) | 0xFF000000;
            g.fill(hx, hy + i, hx + hw, hy + i + 1, col);
        }
        int ty = hy + Math.round(hue * (hh - 1));
        g.fill(hx - 1, ty - 1, hx + hw + 1, ty + 2, 0xFFFFFFFF);
    }

    private void renderAlphaBar(GuiGraphicsExtractor g) {
        int ax = alphaX(), ay = svY(), aw = barW(), ah = svH();
        drawChecker(g, ax, ay, aw, ah);
        int rgb = color & 0x00FFFFFF;
        g.fillGradient(ax, ay, ax + aw, ay + ah, rgb | 0xFF000000, rgb /* alpha 0 */);
        int ty = ay + Math.round((1f - alpha / 255f) * (ah - 1));
        g.fill(ax - 1, ty - 1, ax + aw + 1, ty + 2, 0xFFFFFFFF);
    }

    private void renderHexField(GuiGraphicsExtractor g, Theme.Palette p, int mouseX, int mouseY) {
        int fx = svX(), fy = hexY(), fw = hexW(), fh = hexH();
        boolean hov = inRect(mouseX, mouseY, fx, fy, fw, fh);
        int bg = lerpColor(p.bg, p.bgHover, hov || hexFocused ? 1f : 0f);
        drawRoundedRect(g, fx, fy, fw, fh, UISettings.withPanelAlpha(bg));
        drawRoundedOutline(g, fx, fy, fw, fh, hexFocused ? p.accent : p.border);

        boolean caret = hexFocused && (System.currentTimeMillis() / 500) % 2 == 0;
        String shown = "#" + (hexFocused ? hexInput : hexString(false)) + (caret ? "|" : "");
        UISettings.drawText(g, Minecraft.getInstance().font, shown,
                fx + UISettings.scaled(4), fy + (fh / 2) - UISettings.scaled(4), p.text, false);

        // RGBA-Anzeige rechts neben dem Hex-Feld
        int r = (color >> 16) & 0xFF, gg = (color >> 8) & 0xFF, b = color & 0xFF;
        String rgba = r + "," + gg + "," + b + "," + alpha;
        UISettings.drawText(g, Minecraft.getInstance().font, rgba,
                fx + fw + UISettings.scaled(4), fy + (fh / 2) - UISettings.scaled(4), p.textDim, false);
    }

    private void renderSwatches(GuiGraphicsExtractor g, Theme.Palette p, int mouseX, int mouseY) {
        int rowX = svX(), rowY = swatchY(), size = swSize(), gap = swGap();
        int rightLimit = x + width - UISettings.scaled(PAD);
        g.enableScissor(x, rowY, x + width, rowY + size);

        int cx = rowX;
        for (SwatchSlot slot : swatchSlots()) {
            if (cx + size > rightLimit) break;
            boolean hov = inRect(mouseX, mouseY, cx, rowY, size, size);
            if (slot.isAdd) {
                drawRoundedRect(g, cx, rowY, size, size, UISettings.withPanelAlpha(p.bgHover));
                drawRoundedOutline(g, cx, rowY, size, size, hov ? p.accent : p.border);
                // "+"-Kreuz
                int midX = cx + size / 2, midY = rowY + size / 2, arm = Math.max(1, size / 4);
                g.fill(midX - arm, midY, midX + arm + 1, midY + 1, p.text);
                g.fill(midX, midY - arm, midX + 1, midY + arm + 1, p.text);
            } else {
                drawChecker(g, cx, rowY, size, size);
                drawRoundedRect(g, cx, rowY, size, size, slot.color);
                drawRoundedOutline(g, cx, rowY, size, size,
                        hov ? p.accent : (slot.color == this.color ? p.accent : p.border));
            }
            cx += size + gap;
        }
        g.disableScissor();
    }

    /** Schachbrett-Hintergrund, damit Transparenz sichtbar wird. */
    private void drawChecker(GuiGraphicsExtractor g, int rx, int ry, int rw, int rh) {
        int cell = Math.max(2, UISettings.scaled(3));
        for (int yy = 0; yy < rh; yy += cell) {
            for (int xx = 0; xx < rw; xx += cell) {
                boolean dark = ((xx / cell) + (yy / cell)) % 2 == 0;
                int c = dark ? 0xFF808080 : 0xFFC0C0C0;
                g.fill(rx + xx, ry + yy,
                        Math.min(rx + rw, rx + xx + cell), Math.min(ry + rh, ry + yy + cell), c);
            }
        }
    }

    // ── Swatch-Slots (Presets + Favoriten + "+") ────────────────────────────

    private record SwatchSlot(int color, boolean isAdd, boolean isFavorite) {}

    private List<SwatchSlot> swatchSlots() {
        List<SwatchSlot> slots = new ArrayList<>();
        for (int c : PRESETS) slots.add(new SwatchSlot(c | 0xFF000000, false, false));
        for (int c : FAVORITES) slots.add(new SwatchSlot(c, false, true));
        if (FAVORITES.size() < MAX_FAVORITES) slots.add(new SwatchSlot(0, true, false));
        return slots;
    }

    // ── Drag-Berechnungen ───────────────────────────────────────────────────

    private void applySV(double mouseX, double mouseY) {
        int sx = svX(), sy = svY(), sw = svW(), sh = svH();
        saturation = clamp01((mouseX - sx) / (double) sw);
        brightness = 1f - clamp01((mouseY - sy) / (double) sh);
        recomposeColor(true);
    }

    private void applyHue(double mouseY) {
        hue = clamp01((mouseY - svY()) / (double) svH());
        recomposeColor(true);
    }

    private void applyAlpha(double mouseY) {
        alpha = Math.round((1f - clamp01((mouseY - svY()) / (double) svH())) * 255f);
        recomposeColor(true);
    }

    private static float clamp01(double v) {
        return (float) Math.max(0.0, Math.min(1.0, v));
    }

    // ── Maus ────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Kopfzeile klicken = auf-/zuklappen
        if (button == 0 && inRect(mouseX, mouseY, x, y, width, headerH())) {
            collapsed = !collapsed;
            Sounds.select();
            return true;
        }
        if (collapsed) return false;

        if (button == 0) {
            if (inRect(mouseX, mouseY, svX(), svY(), svW(), svH())) {
                dragging = Drag.SV; applySV(mouseX, mouseY); Sounds.select(); return true;
            }
            if (inRect(mouseX, mouseY, hueX(), svY(), barW(), svH())) {
                dragging = Drag.HUE; applyHue(mouseY); Sounds.select(); return true;
            }
            if (inRect(mouseX, mouseY, alphaX(), svY(), barW(), svH())) {
                dragging = Drag.ALPHA; applyAlpha(mouseY); Sounds.select(); return true;
            }
            if (inRect(mouseX, mouseY, svX(), hexY(), hexW(), hexH())) {
                if (!hexFocused) { hexFocused = true; hexInput = hexString(false); Sounds.select(); }
                return true;
            }
            // Swatch-Reihe
            SwatchSlot hit = swatchAt(mouseX, mouseY);
            if (hit != null) {
                if (hit.isAdd) addCurrentToFavorites();
                else setColor(hit.color);
                Sounds.select();
                return true;
            }
            // Klick woanders im Panel -> Hex-Fokus lösen
            if (hexFocused) { hexFocused = false; commitHex(); }
            return isHovered(mouseX, mouseY);
        }

        if (button == 1) { // Rechtsklick: Favorit entfernen
            SwatchSlot hit = swatchAt(mouseX, mouseY);
            if (hit != null && hit.isFavorite) {
                FAVORITES.remove(Integer.valueOf(hit.color));
                Sounds.deselect();
                return true;
            }
        }
        return false;
    }

    private SwatchSlot swatchAt(double mouseX, double mouseY) {
        int cx = svX(), rowY = swatchY(), size = swSize(), gap = swGap();
        int rightLimit = x + width - UISettings.scaled(PAD);
        for (SwatchSlot slot : swatchSlots()) {
            if (cx + size > rightLimit) break;
            if (inRect(mouseX, mouseY, cx, rowY, size, size)) return slot;
            cx += size + gap;
        }
        return null;
    }

    private void addCurrentToFavorites() {
        Integer c = this.color;
        if (!FAVORITES.contains(c) && FAVORITES.size() < MAX_FAVORITES) {
            FAVORITES.add(c);
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = Drag.NONE;
        return false;
    }

    // ── Tastatur (Hex-Eingabe) ──────────────────────────────────────────────

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!hexFocused) return false;
        char c = (char) event.codepoint();
        boolean hexDigit = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
        if (hexDigit && hexInput.length() < 8) {
            hexInput += Character.toUpperCase(c);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!hexFocused) return false;
        int key = event.key();
        if (key == 256 || key == 257 || key == 335) { // ESC / Enter / NumPad Enter
            hexFocused = false;
            commitHex();
            Sounds.deselect();
            return true;
        }
        if (key == 259 && !hexInput.isEmpty()) { // Backspace
            hexInput = hexInput.substring(0, hexInput.length() - 1);
            return true;
        }
        return false;
    }

    /** Parst das Hex-Eingabefeld. 6 Stellen = RGB (Alpha bleibt), 8 = AARRGGBB.
     *  Ungültige Eingaben werden verworfen (Farbe bleibt unverändert). */
    private void commitHex() {
        try {
            String s = hexInput.trim();
            if (s.length() == 6) {
                int rgb = (int) Long.parseLong(s, 16);
                applyArgb((alpha << 24) | (rgb & 0xFFFFFF), true);
            } else if (s.length() == 8) {
                int argb = (int) Long.parseLong(s, 16);
                applyArgb(argb, true);
            }
        } catch (NumberFormatException ignored) {
            // ungültig -> nichts ändern
        }
        hexInput = hexString(false);
    }
}
