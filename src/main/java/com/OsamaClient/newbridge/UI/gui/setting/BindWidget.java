package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import com.OsamaClient.newbridge.UI.gui.util.KeyNames;
import org.lwjgl.glfw.GLFW;

import java.util.function.BooleanSupplier;

/** "Bind  RShift" row at the end of every module's settings; click to rebind (same as MMB on the row). */
public class BindWidget extends SettingWidget {

    private final Module module;
    private final BooleanSupplier binding;
    private final Runnable startBinding;
    private float time;

    public BindWidget(Module module, BooleanSupplier binding, Runnable startBinding) {
        super(null);
        this.module = module;
        this.binding = binding;
        this.startBinding = startBinding;
    }

    @Override
    public String description() { return "Click, then press a key (Esc / Delete = unbind)"; }

    @Override
    protected void draw(Ui ui) {
        float h = height();
        time += ui.dt;
        background(ui, h);
        ui.text("Bind", x + Theme.PAD, y, h, Theme.FONT_SMALL, ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hoverA.value()));
        String value;
        int color;
        if (binding.getAsBoolean()) {
            value = "Press a key…";
            color = ColorUtil.alpha(Theme.accent(), 0.55f + 0.45f * (float) Math.sin(time * 6.0));
        } else {
            value = module.key < 0 ? "None" : KeyNames.name(module.key);
            color = module.key < 0 ? Theme.TEXT_DIM : Theme.TEXT;
        }
        ui.textRight(value, x + w - Theme.PAD, y, h, Theme.FONT_SMALL, color);
    }

    @Override
    public boolean mouseClicked(Ui ui, int button, int mods) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        startBinding.run();
        return true;
    }
}
