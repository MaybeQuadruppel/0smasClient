package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Component;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * ARGB color: label + swatch; click expands a saturation/value square, hue bar, alpha bar, hex field and
 * Copy / Paste. Can be embedded (no header row) inside other widgets, e.g. the entity filter.
 */
public class ColorWidget extends SettingWidget {

    private static final float SV_H = 34f, BAR_W = 4f, GAP = 2f, BTN_H = 8f;
    private static final float PICKER_H = GAP + SV_H + GAP + BTN_H + GAP;
    private static final int DRAG_NONE = 0, DRAG_SV = 1, DRAG_HUE = 2, DRAG_ALPHA = 3;

    private final String label;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final boolean embedded;
    private final Anim openA = new Anim(0f, 14f), swatchA = new Anim(0f, 18f);
    private boolean open;

    private float hue, sat, val, alpha;
    private int lastColor;
    private int drag = DRAG_NONE;
    private final TextField hex = new TextField();

    // last-frame hit areas (units)
    private float svX, svY, svW, hueX, alphaX, rowY, hexX, hexW, copyX, pasteX, btnW, swX, swY;
    private boolean headerHovered;

    public ColorWidget(ColorPicker picker) {
        this(picker, picker.getLabel(), picker::getColor, picker::setColor, false);
    }

    public ColorWidget(Component owner, String label, IntSupplier getter, IntConsumer setter, boolean embedded) {
        super(owner);
        this.label = label;
        this.getter = getter;
        this.setter = setter;
        this.embedded = embedded;
        hex.maxLength = 9;
        hex.filter = cp -> cp == '#' || Character.digit(cp, 16) >= 0;
        syncFrom(getter.getAsInt());
    }

    private void syncFrom(int argb) {
        float[] hsv = ColorUtil.rgbToHsv(argb);
        // keep the hue while the color is gray/black, so dragging through gray doesn't reset it
        if (hsv[1] > 0.001f && hsv[2] > 0.001f) hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        alpha = (argb >>> 24) / 255f;
        lastColor = argb;
    }

    private void apply() {
        int argb = (Math.round(alpha * 255f) << 24) | ColorUtil.hsvToRgb(hue, sat, val);
        lastColor = argb;
        setter.accept(argb);
    }

    public boolean isOpen() { return open; }

    public void setOpen(boolean o) {
        open = o;
        openA.setTarget(o ? 1f : 0f);
        if (!o) {
            drag = DRAG_NONE;
            hex.end();
        }
    }

    @Override
    public float height() {
        return (embedded ? 0f : Theme.SETTING_H) + openA.value() * PICKER_H;
    }

    @Override
    public String description() {
        return setting != null && !embedded ? setting.getDescription() : "";
    }

    @Override
    protected void draw(Ui ui) {
        openA.update(ui.dt, Theme.animSpeed());
        int color = getter.getAsInt();
        if (color != lastColor && drag == DRAG_NONE) syncFrom(color);

        background(ui, height());
        float top = y;
        if (!embedded) {
            float h = Theme.SETTING_H;
            headerHovered = ui.hovered(x, y, w, h);
            ui.text(ui.ellipsize(label, w - 18f, Theme.FONT_SMALL), x + Theme.PAD, y, h, Theme.FONT_SMALL,
                    ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hoverA.value()));
            swX = x + w - Theme.PAD - 9f;
            swY = y + (h - 5f) * 0.5f;
            swatchA.setTarget(headerHovered || open ? 1f : 0f);
            swatchA.update(ui.dt, Theme.animSpeed());
            swatch(ui, swX, swY, 9f, 5f, color, swatchA.value());
            top = y + h;
        }

        float e = openA.value();
        if (e < 0.001f) return;
        ui.pushClip(x, top, w, PICKER_H * e);
        if (drag != DRAG_NONE) dragUpdate(ui);

        float left = x + Theme.PAD, total = w - 2 * Theme.PAD;
        svX = left;
        svY = top + GAP;
        svW = total - 2 * (BAR_W + GAP);
        hueX = svX + svW + GAP;
        alphaX = hueX + BAR_W + GAP;

        // saturation / value square
        int pure = 0xFF000000 | ColorUtil.hsvToRgb(hue, 1f, 1f);
        ui.gradientH(svX, svY, svW, SV_H, 1f, 0xFFFFFFFF, pure);
        ui.gradientV(svX, svY, svW, SV_H, 1f, 0x00000000, 0xFF000000);
        float cx = svX + sat * svW, cy = svY + (1f - val) * SV_H;
        ui.round(cx - 1.6f, cy - 1.6f, 3.2f, 3.2f, 1.6f, 0xFF000000 | ColorUtil.hsvToRgb(hue, sat, val));
        ui.outline(cx - 1.6f, cy - 1.6f, 3.2f, 3.2f, 1.6f, 1f, 0xFFFFFFFF);

        // hue bar (6 gradient segments)
        float seg = SV_H / 6f;
        for (int i = 0; i < 6; i++) {
            int c0 = 0xFF000000 | ColorUtil.hsvToRgb(i / 6f, 1f, 1f);
            int c1 = 0xFF000000 | ColorUtil.hsvToRgb((i + 1) / 6f, 1f, 1f);
            ui.gradientV(hueX, svY + i * seg, BAR_W, seg + 0.05f, 0, c0, c1);
        }
        marker(ui, hueX, svY + hue * SV_H);

        // alpha bar over a checkerboard
        checker(ui, alphaX, svY, BAR_W, SV_H);
        int rgb = ColorUtil.hsvToRgb(hue, sat, val);
        ui.gradientV(alphaX, svY, BAR_W, SV_H, 0, 0xFF000000 | rgb, rgb);
        marker(ui, alphaX, svY + (1f - alpha) * SV_H);

        // hex field + copy / paste
        rowY = svY + SV_H + GAP;
        btnW = 15f;
        hexX = left;
        hexW = total - 2 * (btnW + 1.5f);
        copyX = hexX + hexW + 1.5f;
        pasteX = copyX + btnW + 1.5f;
        if (!hex.editing) hex.text = ColorUtil.toHex(color);
        hex.draw(ui, hexX, rowY, hexW, BTN_H, 6f, "#AARRGGBB", false);
        button(ui, "Copy", copyX, rowY, btnW, BTN_H);
        button(ui, "Paste", pasteX, rowY, btnW, BTN_H);
        ui.popClip();
    }

    static void swatch(Ui ui, float sx, float sy, float sw, float sh, int color, float hover) {
        if (hover > 0.01f) ui.glow(sx, sy, sw, sh, 1.5f, 3f, ColorUtil.alpha(color | 0xFF000000, 0.5f * hover * Theme.glow()));
        checker(ui, sx, sy, sw, sh);
        ui.round(sx, sy, sw, sh, 1.5f, color);
        ui.outline(sx, sy, sw, sh, 1.5f, 1f, 0x30FFFFFF);
    }

    private static void checker(Ui ui, float cx, float cy, float cw, float ch) {
        ui.rect(cx, cy, cw, ch, 0xFF3A3A44);
        float s = 2f;
        ui.pushClip(cx, cy, cw, ch);
        for (float yy = 0; yy < ch; yy += s) {
            for (float xx = ((int) (yy / s) % 2) * s; xx < cw; xx += 2 * s) ui.rect(cx + xx, cy + yy, s, s, 0xFF6A6A74);
        }
        ui.popClip();
    }

    private static void marker(Ui ui, float bx, float my) {
        ui.rect(bx - 0.5f, my - 0.75f, BAR_W + 1f, 1.5f, 0xFF000000);
        ui.rect(bx - 0.5f, my - 0.25f, BAR_W + 1f, 0.5f, 0xFFFFFFFF);
    }

    private static void button(Ui ui, String text, float bx, float by, float bw, float bh) {
        boolean hov = ui.hovered(bx, by, bw, bh);
        ui.round(bx, by, bw, bh, 1.5f, hov ? 0xFF2C2C36 : 0xFF202028);
        if (hov) ui.outline(bx, by, bw, bh, 1.5f, 1f, Theme.accent(0.6f));
        ui.textCentered(text, bx + bw * 0.5f, by, bh, 5.5f, hov ? Theme.TEXT : Theme.TEXT_DIM);
    }

    private void dragUpdate(Ui ui) {
        float fy = Math.min(1f, Math.max(0f, (ui.mouseY - svY) / SV_H));
        switch (drag) {
            case DRAG_SV -> {
                sat = Math.min(1f, Math.max(0f, (ui.mouseX - svX) / svW));
                val = 1f - fy;
            }
            case DRAG_HUE -> hue = Math.min(0.9999f, fy);
            case DRAG_ALPHA -> alpha = 1f - fy;
            default -> { return; }
        }
        apply();
    }

    private static boolean in(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(Ui ui, int button, int mods) {
        float mx = ui.mouseX, my = ui.mouseY;
        if (!embedded && headerHovered) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                setOpen(!open);
                return true;
            }
            return false;
        }
        if (!open || openA.value() < 0.5f || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return open;
        if (hex.editing && !in(mx, my, hexX, rowY, hexW, BTN_H)) commitHex();
        if (in(mx, my, svX, svY, svW, SV_H)) drag = DRAG_SV;
        else if (in(mx, my, hueX - 1f, svY, BAR_W + 2f, SV_H)) drag = DRAG_HUE;
        else if (in(mx, my, alphaX - 1f, svY, BAR_W + 2f, SV_H)) drag = DRAG_ALPHA;
        else if (in(mx, my, hexX, rowY, hexW, BTN_H)) hex.begin(ColorUtil.toHex(getter.getAsInt()));
        else if (in(mx, my, copyX, rowY, btnW, BTN_H)) UiInput.setClipboard(ColorUtil.toHex(getter.getAsInt()));
        else if (in(mx, my, pasteX, rowY, btnW, BTN_H)) {
            Integer c = ColorUtil.parseHex(UiInput.clipboard());
            if (c != null) {
                syncFrom(c);
                apply();
            }
        }
        if (drag != DRAG_NONE) dragUpdate(ui);
        return true;
    }

    @Override
    public void mouseReleased(Ui ui, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) drag = DRAG_NONE;
    }

    private void commitHex() {
        hex.end();
        Integer c = ColorUtil.parseHex(hex.text);
        if (c != null) {
            syncFrom(c);
            apply();
        }
    }

    @Override
    public boolean wantsKeyboard() { return hex.editing; }

    @Override
    public void blur() {
        if (hex.editing) commitHex();
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commitHex();
            return true;
        }
        hex.keyPressed(key, mods);
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) { return hex.charTyped(codepoint); }
}
