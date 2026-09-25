package com.OsamaClient.newbridge.UI.gui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorUtilTest {

    @Test
    void hsvToRgbPrimaries() {
        assertEquals(0xFF0000, ColorUtil.hsvToRgb(0f, 1f, 1f));
        assertEquals(0x00FF00, ColorUtil.hsvToRgb(1f / 3f, 1f, 1f));
        assertEquals(0x0000FF, ColorUtil.hsvToRgb(2f / 3f, 1f, 1f));
        assertEquals(0xFFFFFF, ColorUtil.hsvToRgb(0.5f, 0f, 1f));
        assertEquals(0x000000, ColorUtil.hsvToRgb(0.5f, 1f, 0f));
        assertEquals(0xFF0000, ColorUtil.hsvToRgb(1f, 1f, 1f)); // hue wraps
    }

    @Test
    void rgbToHsvRoundTrips() {
        int[] colors = {0xFF0000, 0x6C8CFF, 0x123456, 0x808080, 0xFFFFFF, 0x000000, 0x00FFAA};
        for (int c : colors) {
            float[] hsv = ColorUtil.rgbToHsv(c);
            assertEquals(c, ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]), "round trip of " + Integer.toHexString(c));
        }
    }

    @Test
    void rgbToHsvIgnoresAlphaAndReturnsZeroHueForGray() {
        float[] hsv = ColorUtil.rgbToHsv(0x80808080);
        assertEquals(0f, hsv[0]);
        assertEquals(0f, hsv[1]);
        assertEquals(128 / 255f, hsv[2], 1e-6);
    }

    @Test
    void formatsHexWithAlpha() {
        assertEquals("#FF6C8CFF", ColorUtil.toHex(0xFF6C8CFF));
        assertEquals("#00000000", ColorUtil.toHex(0));
    }

    @Test
    void parsesHexInSeveralForms() {
        assertEquals(0xFF6C8CFF, ColorUtil.parseHex("#FF6C8CFF"));
        assertEquals(0xFF6C8CFF, ColorUtil.parseHex("6c8cff"));
        assertEquals(0xFF6C8CFF, ColorUtil.parseHex("#6C8CFF"));
        assertEquals(0x806C8CFF, ColorUtil.parseHex(" 806C8CFF "));
        assertEquals(0xFFFF0000, ColorUtil.parseHex("0xFFFF0000"));
    }

    @Test
    void rejectsInvalidHex() {
        assertNull(ColorUtil.parseHex(null));
        assertNull(ColorUtil.parseHex(""));
        assertNull(ColorUtil.parseHex("#12345"));
        assertNull(ColorUtil.parseHex("#GGGGGG"));
    }

    @Test
    void lerpsEveryChannel() {
        assertEquals(0xFF000000, ColorUtil.lerp(0xFF000000, 0xFFFFFFFF, 0f));
        assertEquals(0xFFFFFFFF, ColorUtil.lerp(0xFF000000, 0xFFFFFFFF, 1f));
        assertEquals(0x80808080, ColorUtil.lerp(0x00000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0xFFFFFFFF, ColorUtil.lerp(0xFF000000, 0xFFFFFFFF, 3f)); // clamped
    }

    @Test
    void multipliesAndReplacesAlpha() {
        assertEquals(0x80FFFFFF, ColorUtil.alpha(0xFFFFFFFF, 128 / 255f));
        assertEquals(0x00123456, ColorUtil.alpha(0xFF123456, 0f));
        assertEquals(0x40123456, ColorUtil.alpha(0x80123456, 0.5f));
        assertEquals(0x7F123456, ColorUtil.withAlpha(0xFF123456, 0x7F));
        assertEquals(0xFF123456, ColorUtil.withAlpha(0x00123456, 300)); // clamped
    }
}
