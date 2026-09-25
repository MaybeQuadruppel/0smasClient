package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

/** Boolean: label + animated pill switch. */
public class BoolWidget extends SettingWidget {

    private final ToggleButton toggle;
    private final Anim onA;

    public BoolWidget(ToggleButton toggle) {
        super(toggle);
        this.toggle = toggle;
        this.onA = new Anim(toggle.enabled ? 1f : 0f, 16f);
    }

    @Override
    protected void draw(Ui ui) {
        float h = height();
        background(ui, h);
        onA.setTarget(toggle.enabled ? 1f : 0f);
        onA.update(ui.dt, Theme.animSpeed());
        ui.text(ui.ellipsize(toggle.getLabel(), w - 18f, Theme.FONT_SMALL), x + Theme.PAD, y, h, Theme.FONT_SMALL,
                ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, Math.max(onA.value(), hoverA.value() * 0.5f)));
        drawSwitch(ui, x + w - Theme.PAD - 9f, y + (h - 5f) * 0.5f, onA.value(), hoverA.value());
    }

    /** Pill switch, 9x5 units; shared with other widgets. */
    static void drawSwitch(Ui ui, float sx, float sy, float on, float hover) {
        float sw = 9f, sh = 5f;
        if (on > 0.01f) ui.glow(sx, sy, sw, sh, sh * 0.5f, 3f, Theme.accent(0.45f * on * Theme.glow()));
        int track = ColorUtil.lerp(ColorUtil.lerp(Theme.TRACK, 0xFF34343F, hover), Theme.accent(), on);
        ui.round(sx, sy, sw, sh, sh * 0.5f, track);
        float k = sh - 1.6f;
        float kx = sx + 0.8f + (sw - k - 1.6f) * on;
        ui.round(kx, sy + 0.8f, k, k, k * 0.5f, ColorUtil.lerp(0xFFB0B0BA, 0xFFFFFFFF, on));
    }

    @Override
    public boolean mouseClicked(Ui ui, int button, int mods) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        toggle.setValue(!toggle.enabled);
        return true;
    }
}
