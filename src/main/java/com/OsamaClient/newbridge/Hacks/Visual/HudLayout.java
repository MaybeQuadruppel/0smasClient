package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.config.ConfigCodec;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draggable positions of HUD elements that live outside {@link HudOverlay} (TeammateList, ArrayList, Minimap ...).
 * Positions are stored as fractions of the screen so they survive resolution changes. Each element reports the
 * rectangle it drew last frame (framebuffer pixels); the layout editor in {@link HudOverlay} draws the outlines
 * and moves the elements.
 */
public final class HudLayout {

    public static final String PREFIX = "layout.";

    public static final class Elem {
        public String id, label;
        public float x, y, w, h;      // last drawn rect, framebuffer px
        public boolean anchorRight;   // stored x = right edge instead of left edge
        long frame;
    }

    private static final Map<String, ConfigCodec.WidgetPos> custom = new HashMap<>();
    private static final Map<String, Elem> elems = new LinkedHashMap<>();
    private static long frame;
    private static String dragging;
    private static float grabX, grabY;

    private HudLayout() {}

    public static void nextFrame() { frame++; }

    public static boolean has(String id) { return custom.containsKey(id); }

    public static ConfigCodec.WidgetPos get(String id) { return custom.get(id); }

    public static void set(String id, float fx, float fy) { custom.put(id, new ConfigCodec.WidgetPos(fx, fy)); }

    public static void reset(String id) { custom.remove(id); }

    /** Position for config saving (keys prefixed so they don't clash with HudOverlay boxes). */
    public static Map<String, ConfigCodec.WidgetPos> positions() {
        Map<String, ConfigCodec.WidgetPos> out = new LinkedHashMap<>();
        custom.forEach((id, p) -> out.put(PREFIX + id, p));
        return out;
    }

    /** Takes the entries with the layout prefix out of {@code saved}; returns the rest. */
    public static Map<String, ConfigCodec.WidgetPos> apply(Map<String, ConfigCodec.WidgetPos> saved) {
        Map<String, ConfigCodec.WidgetPos> rest = new HashMap<>();
        saved.forEach((id, p) -> {
            if (id.startsWith(PREFIX)) custom.put(id.substring(PREFIX.length()), p);
            else rest.put(id, p);
        });
        return rest;
    }

    /** Anchor of an element in framebuffer px: the dragged position or the given default. */
    public static float[] origin(String id, float defX, float defY, int fbW, int fbH) {
        var p = custom.get(id);
        if (p == null) return new float[]{defX, defY};
        return new float[]{p.x() * fbW, p.y() * fbH};
    }

    /** An element tells where it was drawn this frame (framebuffer px). */
    public static void report(String id, String label, boolean anchorRight, float x, float y, float w, float h) {
        Elem e = elems.computeIfAbsent(id, k -> new Elem());
        e.id = id;
        e.label = label;
        e.anchorRight = anchorRight;
        e.x = x;
        e.y = y;
        e.w = w;
        e.h = h;
        e.frame = frame;
    }

    /** Elements drawn in the current or previous frame. */
    public static Iterable<Elem> visible() {
        java.util.List<Elem> out = new java.util.ArrayList<>();
        for (Elem e : elems.values()) if (frame - e.frame <= 1) out.add(e);
        return out;
    }

    public static boolean dragging() { return dragging != null; }

    public static String draggingId() { return dragging; }

    public static void stopDrag() { dragging = null; }

    /** Starts dragging the topmost element under the mouse (fb px). */
    public static boolean beginDrag(float mx, float my) {
        Elem hit = null;
        for (Elem e : visible()) if (mx >= e.x && mx <= e.x + e.w && my >= e.y && my <= e.y + e.h) hit = e;
        if (hit == null) return false;
        dragging = hit.id;
        grabX = mx - hit.x;
        grabY = my - hit.y;
        return true;
    }

    /** Moves the dragged element to the mouse position. */
    public static void dragTo(float mx, float my, int fbW, int fbH) {
        if (dragging == null) return;
        Elem e = elems.get(dragging);
        if (e == null) return;
        float x = Math.max(0, Math.min(fbW - e.w, mx - grabX));
        float y = Math.max(0, Math.min(fbH - e.h, my - grabY));
        float anchorX = e.anchorRight ? x + e.w : x;
        set(dragging, anchorX / fbW, y / fbH);
    }

    /** Right click: puts the element under the mouse back to its default place. */
    public static boolean resetAt(float mx, float my) {
        for (Elem e : visible()) {
            if (mx >= e.x && mx <= e.x + e.w && my >= e.y && my <= e.y + e.h) {
                custom.remove(e.id);
                return true;
            }
        }
        return false;
    }
}
