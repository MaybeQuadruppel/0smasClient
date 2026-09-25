package com.OsamaClient.newbridge.UI.gui.render;

import com.OsamaClient.newbridge.UI.gui.render.font.FontAtlas;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;

/**
 * Draw facade used by all widgets. Works in GUI units; every coordinate is multiplied by {@link #scale}
 * (physical pixels per unit) before it reaches the {@link UiRenderer}. Rect edges are snapped to whole
 * pixels so separators and outlines stay crisp. A global {@link #alpha} fades everything (open/close).
 */
public final class Ui {

    public final UiRenderer r;
    public float scale = 1.5f;
    /** Global opacity multiplier (0..1). */
    public float alpha = 1f;
    /** Mouse position in GUI units, updated every frame. */
    public float mouseX, mouseY;
    /** Frame delta in seconds (clamped). */
    public float dt;

    public Ui(UiRenderer r) {
        this.r = r;
    }

    /** Screen size in GUI units. */
    public float width() { return r.width() / scale; }
    public float height() { return r.height() / scale; }

    private int a(int argb) {
        return alpha >= 1f ? argb : ColorUtil.alpha(argb, alpha);
    }

    private float snap(float units) { return Math.round(units * scale); }

    // ------------------------------------------------------------------ shapes

    public void rect(float x, float y, float w, float h, int color) {
        round(x, y, w, h, 0, color);
    }

    public void round(float x, float y, float w, float h, float radius, int color) {
        float x0 = snap(x), y0 = snap(y), x1 = snap(x + w), y1 = snap(y + h);
        int c = a(color);
        r.shape(x0, y0, x1 - x0, y1 - y0, radius * scale, 0, 0, c, c, c, c);
    }

    /** Outline with a thickness in physical pixels (1 = one crisp pixel). */
    public void outline(float x, float y, float w, float h, float radius, float thicknessPx, int color) {
        float x0 = snap(x), y0 = snap(y), x1 = snap(x + w), y1 = snap(y + h);
        int c = a(color);
        r.shape(x0, y0, x1 - x0, y1 - y0, radius * scale, 1, thicknessPx, c, c, c, c);
    }

    /** Soft glow around the rect; {@code size} in units. */
    public void glow(float x, float y, float w, float h, float radius, float size, int color) {
        if (size <= 0) return;
        int c = a(color);
        r.shape(x * scale, y * scale, w * scale, h * scale, radius * scale, 2, size * scale, c, c, c, c);
    }

    public void gradientH(float x, float y, float w, float h, float radius, int left, int right) {
        float x0 = snap(x), y0 = snap(y), x1 = snap(x + w), y1 = snap(y + h);
        int l = a(left), rr = a(right);
        r.shape(x0, y0, x1 - x0, y1 - y0, radius * scale, 0, 0, l, rr, rr, l);
    }

    public void gradientV(float x, float y, float w, float h, float radius, int top, int bottom) {
        float x0 = snap(x), y0 = snap(y), x1 = snap(x + w), y1 = snap(y + h);
        int t = a(top), b = a(bottom);
        r.shape(x0, y0, x1 - x0, y1 - y0, radius * scale, 0, 0, t, t, b, b);
    }

    /** Anti-aliased line in units; thickness in physical pixels. */
    public void line(float ax, float ay, float bx, float by, float thicknessPx, int color) {
        r.line(ax * scale, ay * scale, bx * scale, by * scale, thicknessPx, a(color));
    }

    /**
     * Chevron centered at (cx, cy) with half width {@code size}; {@code flip} 0 = pointing down, 1 = pointing up
     * (values in between animate the flip).
     */
    public void chevron(float cx, float cy, float size, float flip, int color) {
        float dy = size * 0.5f * (1f - 2f * flip);
        float t = Math.max(1.1f, scale * 0.8f);
        line(cx - size, cy - dy, cx, cy + dy, t, color);
        line(cx, cy + dy, cx + size, cy - dy, t, color);
    }

    /** Checkmark inside the box (x, y, s, s); {@code p} 0..1 draws it progressively. */
    public void check(float x, float y, float s, float p, int color) {
        if (p <= 0.001f) return;
        float t = Math.max(1.1f, scale * 0.85f);
        float ax = x + s * 0.22f, ay = y + s * 0.52f;
        float bx = x + s * 0.42f, by = y + s * 0.72f;
        float cx = x + s * 0.78f, cy = y + s * 0.30f;
        float p1 = Math.min(1f, p * 2.5f), p2 = Math.max(0f, (p - 0.4f) / 0.6f);
        line(ax, ay, ax + (bx - ax) * p1, ay + (by - ay) * p1, t, color);
        if (p2 > 0) line(bx, by, bx + (cx - bx) * p2, by + (cy - by) * p2, t, color);
    }

    // ------------------------------------------------------------------ text

    public FontAtlas font(float sizeUnits) {
        return UiFont.atlas(Math.round(sizeUnits * scale));
    }

    public float textWidth(String s, float sizeUnits) {
        return UiFont.width(font(sizeUnits), s) / scale;
    }

    /** Text vertically centered (on cap height) in the box [y, y+h]; returns the x after the text (units). */
    public float text(String s, float x, float y, float h, float sizeUnits, int color) {
        FontAtlas f = font(sizeUnits);
        float baseline = (y + h * 0.5f) * scale + f.capHeight * 0.5f;
        return UiFont.draw(r, f, s, Math.round(x * scale), baseline, a(color)) / scale;
    }

    public void textRight(String s, float right, float y, float h, float sizeUnits, int color) {
        text(s, right - textWidth(s, sizeUnits), y, h, sizeUnits, color);
    }

    public void textCentered(String s, float cx, float y, float h, float sizeUnits, int color) {
        text(s, cx - textWidth(s, sizeUnits) * 0.5f, y, h, sizeUnits, color);
    }

    public String ellipsize(String s, float maxWidthUnits, float sizeUnits) {
        return UiFont.ellipsize(font(sizeUnits), s, maxWidthUnits * scale);
    }

    // ------------------------------------------------------------------ clipping / hit tests

    public void pushClip(float x, float y, float w, float h) {
        r.pushClip(x * scale, y * scale, w * scale, h * scale);
    }

    public void popClip() { r.popClip(); }

    public boolean visible(float x, float y, float w, float h) {
        return !r.clipped(x * scale, y * scale, w * scale, h * scale);
    }

    /** When true (another panel is on top of the mouse), nothing reports hover. */
    public boolean hoverBlocked;

    public boolean hovered(float x, float y, float w, float h) {
        if (hoverBlocked) return false;
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                && r.insideClip(mouseX * scale, mouseY * scale);
    }
}
