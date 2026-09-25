package com.OsamaClient.newbridge.UI.gui.render.font;

import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Text measuring and drawing in physical pixels on top of {@link FontAtlas}. One atlas per pixel size,
 * created on first use; idle ones are freed by {@link #collect(int)}, all of them by {@link #closeAll()}.
 */
public final class UiFont {

    private static final Map<Integer, FontAtlas> ATLASES = new HashMap<>();
    private static long frame;

    private UiFont() {}

    public static FontAtlas atlas(int pixelSize) {
        FontAtlas a = ATLASES.computeIfAbsent(Math.max(4, pixelSize), FontAtlas::new);
        a.lastUsed = frame;
        return a;
    }

    /** Frees every atlas (e.g. on shutdown). */
    public static void closeAll() {
        for (FontAtlas a : ATLASES.values()) a.close();
        ATLASES.clear();
    }

    /**
     * Call once per frame after drawing: frees atlases not used for {@code maxIdleFrames} frames
     * (sizes left behind by a scale change), so their GPU textures do not pile up.
     */
    public static void collect(int maxIdleFrames) {
        frame++;
        ATLASES.entrySet().removeIf(e -> {
            if (frame - e.getValue().lastUsed <= maxIdleFrames) return false;
            e.getValue().close();
            return true;
        });
    }


    public static float width(FontAtlas a, String s) {
        float w = 0;
        FontAtlas.Glyph prev = null;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            FontAtlas.Glyph g = a.glyph(cp);
            if (g == null) continue;
            w += a.kerning(prev, g) + g.advance;
            prev = g;
        }
        return w;
    }

    /** Draws {@code s} with its baseline at {@code baseline}; returns the pen x after the text. */
    public static float draw(UiRenderer r, FontAtlas a, String s, float x, float baseline, int argb) {
        float pen = x;
        float by = Math.round(baseline);
        FontAtlas.Glyph prev = null;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            FontAtlas.Glyph g = a.glyph(cp);
            if (g == null) continue;
            pen += a.kerning(prev, g);
            if (g.w > 0) {
                float gx = Math.round(pen) + g.left;
                float gy = by - g.top;
                r.glyph(a, gx, gy, gx + g.w, gy + g.h, g.u0, g.v0, g.u1, g.v1, argb);
            }
            pen += g.advance;
            prev = g;
        }
        return pen;
    }

    /** Cuts {@code s} with a trailing ellipsis so it fits into {@code maxWidth} pixels. */
    public static String ellipsize(FontAtlas a, String s, float maxWidth) {
        if (width(a, s) <= maxWidth) return s;
        String dots = "…";
        float dw = width(a, dots);
        int end = s.length();
        while (end > 0 && width(a, s.substring(0, end)) + dw > maxWidth) end--;
        return s.substring(0, end) + dots;
    }
}
