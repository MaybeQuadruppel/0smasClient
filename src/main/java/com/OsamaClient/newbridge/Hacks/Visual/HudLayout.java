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
    /** Config keys for per-element scale (x = scale). Used for HudOverlay boxes and layout elements alike. */
    public static final String SCALE_PREFIX = "scale.";
    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f;

    public static final class Elem {
        public String id, label;
        public float x, y, w, h;      // last drawn rect, framebuffer px
        public boolean anchorRight;   // stored x = right edge instead of left edge
        long frame;
    }

    private static final Map<String, ConfigCodec.WidgetPos> custom = new HashMap<>();
    private static final Map<String, Elem> elems = new LinkedHashMap<>();
    private static final Map<String, Float> scales = new HashMap<>();
    private static long frame;
    private static String dragging;
    private static float grabX, grabY;

    private HudLayout() {}

    public static void nextFrame() { frame++; HudIcons.clear(); }

    public static boolean has(String id) { return custom.containsKey(id); }

    public static ConfigCodec.WidgetPos get(String id) { return custom.get(id); }

    public static void set(String id, float fx, float fy) { custom.put(id, new ConfigCodec.WidgetPos(fx, fy)); }

    public static void reset(String id) { custom.remove(id); }

    /** Extra size of an element (1 = normal), changed with the mouse wheel in the layout editor. */
    public static float scale(String id) {
        Float f = scales.get(id);
        return f == null ? 1f : f;
    }

    public static void setScale(String id, float s) {
        s = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.round(s * 20f) / 20f));
        if (Math.abs(s - 1f) < 0.001f) scales.remove(id); else scales.put(id, s);
    }

    /** Wheel: 5 % per notch. Returns the new scale. */
    public static float addScale(String id, double wheel) {
        // several notches can arrive before the next frame: apply each of them
        double steps = Math.abs(wheel) < 1 ? Math.signum(wheel) : Math.rint(wheel);
        setScale(id, scale(id) + (float) steps * 0.05f);
        return scale(id);
    }

    public static void resetScale(String id) { scales.remove(id); }

    /** Topmost reported element under the point (fb px), or null. */
    public static Elem elementAt(float mx, float my) {
        Elem hit = null;
        for (Elem e : visible()) if (mx >= e.x && mx <= e.x + e.w && my >= e.y && my <= e.y + e.h) hit = e;
        return hit;
    }

    /** Position for config saving (keys prefixed so they don't clash with HudOverlay boxes). */
    public static Map<String, ConfigCodec.WidgetPos> positions() {
        Map<String, ConfigCodec.WidgetPos> out = new LinkedHashMap<>();
        custom.forEach((id, p) -> out.put(PREFIX + id, p));
        scales.forEach((id, f) -> out.put(SCALE_PREFIX + id, new ConfigCodec.WidgetPos(f, 0f)));
        return out;
    }

    /** Takes the entries with the layout prefix out of {@code saved}; returns the rest. */
    public static Map<String, ConfigCodec.WidgetPos> apply(Map<String, ConfigCodec.WidgetPos> saved) {
        Map<String, ConfigCodec.WidgetPos> rest = new HashMap<>();
        saved.forEach((id, p) -> {
            if (id.startsWith(PREFIX)) custom.put(id.substring(PREFIX.length()), p);
            else if (id.startsWith(SCALE_PREFIX)) setScale(id.substring(SCALE_PREFIX.length()), p.x());
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
        keepOnScreen(e);
    }

    /** A moved / resized element that sticks out of the window gets pushed back in (takes effect next frame). */
    private static void keepOnScreen(Elem e) {
        var p = custom.get(e.id);
        if (p == null || e.id.equals(dragging)) return;
        var win = net.minecraft.client.Minecraft.getInstance().getWindow();
        int fbW = win.getWidth(), fbH = win.getHeight();
        if (fbW <= 0 || fbH <= 0 || e.w >= fbW || e.h >= fbH) return;
        float dx = e.x < 0 ? -e.x : e.x + e.w > fbW ? fbW - (e.x + e.w) : 0f;
        float dy = e.y < 0 ? -e.y : e.y + e.h > fbH ? fbH - (e.y + e.h) : 0f;
        if (Math.abs(dx) < 0.5f && Math.abs(dy) < 0.5f) return;
        custom.put(e.id, new ConfigCodec.WidgetPos(p.x() + dx / fbW, p.y() + dy / fbH));
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

    /** true if a reported element (Split Chat, Check Meter, minimap, ...) is under the point (fb px). */
    public static boolean hitsElement(float mx, float my) {
        for (Elem e : visible()) if (mx >= e.x && mx <= e.x + e.w && my >= e.y && my <= e.y + e.h) return true;
        return false;
    }

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
                scales.remove(e.id);
                return true;
            }
        }
        return false;
    }
}
