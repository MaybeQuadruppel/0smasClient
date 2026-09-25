package com.OsamaClient.newbridge.UI.gui.util;

/** ARGB color helpers (pure Java). */
public final class ColorUtil {

    private ColorUtil() {}

    /** HSV (each 0..1, hue wraps) to 0xRRGGBB. */
    public static int hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float c = v * s;
        float hp = h * 6f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float r, g, b;
        switch ((int) hp) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        float m = v - c;
        return (Math.round((r + m) * 255f) << 16) | (Math.round((g + m) * 255f) << 8) | Math.round((b + m) * 255f);
    }

    /** 0xRRGGBB (alpha ignored) to {h, s, v}, each 0..1. */
    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0f;
        if (d > 0f) {
            if (max == r) h = ((g - b) / d) % 6f;
            else if (max == g) h = (b - r) / d + 2f;
            else h = (r - g) / d + 4f;
            h /= 6f;
            if (h < 0f) h += 1f;
        }
        float s = max == 0f ? 0f : d / max;
        return new float[]{h, s, max};
    }

    public static String toHex(int argb) {
        return String.format("#%08X", argb);
    }

    /** Parses {@code #AARRGGBB}, {@code #RRGGBB} (opaque), with or without {@code #}/{@code 0x}; null if invalid. */
    public static Integer parseHex(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        else if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.length() != 6 && s.length() != 8) return null;
        try {
            long v = Long.parseLong(s, 16);
            return s.length() == 6 ? (int) (0xFF000000L | v) : (int) v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static int lerp(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    /** Multiplies the alpha channel by {@code mul} (0..1). */
    public static int alpha(int argb, float mul) {
        int a = Math.round((argb >>> 24) * Math.min(Math.max(mul, 0f), 1f));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int withAlpha(int argb, int a) {
        a = Math.min(Math.max(a, 0), 255);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
