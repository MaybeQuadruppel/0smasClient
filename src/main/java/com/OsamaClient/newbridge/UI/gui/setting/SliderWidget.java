package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Number slider: label + value, thin track with an eased fill. Drag, Shift+LMB to type, arrows = ±step. */
public class SliderWidget extends SettingWidget {

    private static final float H = 12f;

    private final Slider slider;
    private final Anim fillA, knobA = new Anim(0f, 18f);
    private boolean dragging;
    private final TextField field = new TextField();
    private float trackX, trackW;

    public SliderWidget(Slider slider) {
        super(slider);
        this.slider = slider;
        this.fillA = new Anim(fraction(), 18f);
        field.maxLength = 16;
        field.filter = cp -> (cp >= '0' && cp <= '9') || cp == '.' || cp == '-' || cp == ',';
    }

    @Override
    public float height() { return H; }

    private float fraction() {
        double range = slider.getMax() - slider.getMin();
        return range <= 0 ? 0f : (float) ((slider.getValue() - slider.getMin()) / range);
    }

    static String format(double v, double step) {
        int decimals;
        if (step <= 0) decimals = 2;
        else {
            String s = new java.math.BigDecimal(Double.toString(step)).stripTrailingZeros().toPlainString();
            int dot = s.indexOf('.');
            decimals = dot < 0 ? 0 : Math.min(4, s.length() - dot - 1);
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    @Override
    protected void draw(Ui ui) {
        background(ui, H);
        trackX = x + Theme.PAD;
        trackW = w - 2 * Theme.PAD;
        if (dragging) {
            float f = Math.min(1f, Math.max(0f, (ui.mouseX - trackX) / trackW));
            double v = slider.getMin() + f * (slider.getMax() - slider.getMin());
            if (v != slider.getValue()) slider.setValue(v);
        }
        fillA.setTarget(fraction());
        fillA.update(ui.dt, Theme.animSpeed());
        knobA.setTarget(dragging ? 1f : hovered ? 0.6f : 0f);
        knobA.update(ui.dt, Theme.animSpeed());

        float labelH = 8f;
        ui.text(ui.ellipsize(slider.getLabel(), w * 0.6f, Theme.FONT_SMALL), x + Theme.PAD, y + 0.5f, labelH,
                Theme.FONT_SMALL, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hoverA.value()));
        if (field.editing) {
            float fw = 22f;
            field.draw(ui, x + w - Theme.PAD - fw, y + 1f, fw, 7f, Theme.FONT_SMALL, "", true);
        } else {
            ui.textRight(format(slider.getValue(), slider.getStep()), x + w - Theme.PAD, y + 0.5f, labelH,
                    Theme.FONT_SMALL, Theme.TEXT);
        }

        float ty = y + H - 3f, th = 1.2f;
        float fill = trackW * fillA.value();
        ui.round(trackX, ty, trackW, th, th * 0.5f, Theme.TRACK);
        if (fill > 0.05f) {
            ui.glow(trackX, ty, fill, th, th * 0.5f, 2.5f, Theme.accent(0.35f * Theme.glow()));
            ui.gradientH(trackX, ty, fill, th, th * 0.5f, Theme.accent(0.65f), Theme.accent());
        }
        float k = 2.4f + 1.2f * knobA.value();
        ui.round(trackX + fill - k * 0.5f, ty + th * 0.5f - k * 0.5f, k, k, k * 0.5f,
                ColorUtil.lerp(0xFFD8D8E0, 0xFFFFFFFF, knobA.value()));
    }

    @Override
    public boolean mouseClicked(Ui ui, int button, int mods) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (UiInput.shift(mods)) {
            field.begin(format(slider.getValue(), slider.getStep()));
            return true;
        }
        if (field.editing) commit();
        dragging = true;
        return true;
    }

    @Override
    public void mouseReleased(Ui ui, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) dragging = false;
    }

    @Override
    public boolean wantsKeyboard() { return field.editing; }

    @Override
    public void blur() { commit(); }

    private void commit() {
        if (!field.editing) return;
        field.end();
        try {
            slider.setValue(Double.parseDouble(field.text.replace(',', '.')));
        } catch (NumberFormatException ignored) {
            // keep the old value
        }
    }

    @Override
    public boolean keyPressed(int key, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        }
        field.keyPressed(key, mods);
        return true;
    }

    @Override
    public boolean charTyped(int codepoint) { return field.charTyped(codepoint); }

    @Override
    public boolean hoverKey(int key, int mods) {
        if (key != GLFW.GLFW_KEY_LEFT && key != GLFW.GLFW_KEY_RIGHT) return false;
        double step = slider.getStep() > 0 ? slider.getStep() : (slider.getMax() - slider.getMin()) / 100.0;
        slider.setValue(slider.getValue() + (key == GLFW.GLFW_KEY_RIGHT ? step : -step));
        return true;
    }
}
