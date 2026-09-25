package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Mode: "Label   Value ⌄"; LMB expands the option list inline (animated), RMB cycles to the next mode. */
public class ModeWidget extends SettingWidget {

    private static final float OPT_H = 8.5f;

    private final ModeButton mode;
    private final Anim openA = new Anim(0f, 14f);
    private final Anim[] optHover;
    private boolean open;
    private int hoveredOption = -1;

    public ModeWidget(ModeButton mode) {
        super(mode);
        this.mode = mode;
        optHover = new Anim[mode.getModes().size()];
        for (int i = 0; i < optHover.length; i++) optHover[i] = new Anim(0f, 18f);
    }

    @Override
    public float height() {
        return Theme.SETTING_H + openA.value() * (mode.getModes().size() * OPT_H + 1.5f);
    }

    @Override
    protected void draw(Ui ui) {
        openA.update(ui.dt, Theme.animSpeed());
        float h = Theme.SETTING_H;
        boolean headHover = ui.hovered(x, y, w, h);
        background(ui, height());
        ui.text(ui.ellipsize(mode.getLabel(), w * 0.5f, Theme.FONT_SMALL), x + Theme.PAD, y, h, Theme.FONT_SMALL,
                ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, headHover ? 1f : 0f));
        float right = x + w - Theme.PAD;
        ui.chevron(right - 1.3f, y + h * 0.5f, 1.3f, openA.value(), Theme.TEXT_DIM);
        String value = ui.ellipsize(mode.getMode(), w * 0.45f, Theme.FONT_SMALL);
        ui.textRight(value, right - 4f, y, h, Theme.FONT_SMALL, Theme.accent());

        hoveredOption = -1;
        float e = openA.value();
        if (e > 0.001f) {
            float listH = (mode.getModes().size() * OPT_H + 1.5f) * e;
            ui.pushClip(x, y + h, w, listH);
            float oy = y + h;
            List<String> modes = mode.getModes();
            for (int i = 0; i < modes.size(); i++) {
                boolean hov = ui.hovered(x, oy, w, OPT_H);
                if (hov) hoveredOption = i;
                optHover[i].setTarget(hov ? 1f : 0f);
                optHover[i].update(ui.dt, Theme.animSpeed());
                boolean sel = i == mode.getIndex();
                if (optHover[i].value() > 0.01f) ui.rect(x + 2f, oy, w - 4f, OPT_H, ColorUtil.withAlpha(0xFFFFFF, Math.round(12 * optHover[i].value())));
                if (sel) ui.round(x + 3f, oy + 2.25f, 1f, OPT_H - 4.5f, 0.5f, Theme.accent());
                ui.text(modes.get(i), x + Theme.PAD + 3f, oy, OPT_H, Theme.FONT_SMALL,
                        sel ? Theme.accent() : ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, optHover[i].value()));
                oy += OPT_H;
            }
            ui.popClip();
        }
    }

    @Override
    public boolean mouseClicked(Ui ui, int button, int mods) {
        if (hoveredOption >= 0 && open) {
            mode.setIndex(hoveredOption);
            setOpen(false);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            setOpen(!open);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            mode.setIndex((mode.getIndex() + 1) % mode.getModes().size());
            return true;
        }
        return false;
    }

    private void setOpen(boolean o) {
        open = o;
        openA.setTarget(o ? 1f : 0f);
    }
}
