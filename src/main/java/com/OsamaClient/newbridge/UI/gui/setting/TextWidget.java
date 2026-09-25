package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.TextBox;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

/** Text: label + inline field; click to edit, Enter / click elsewhere commits, Esc cancels. */
public class TextWidget extends SettingWidget {

    private final TextBox box;
    private final TextField field = new TextField();

    public TextWidget(TextBox box) {
        super(box);
        this.box = box;
        if (box.getMaxLength() >= 0) field.maxLength = box.getMaxLength();
        else field.maxLength = 256;
        if (box.isNumericOnly()) field.filter = cp -> (cp >= '0' && cp <= '9') || cp == '.' || cp == '-';
    }

    @Override
    protected void draw(Ui ui) {
        float h = height();
        background(ui, h);
        float labelW = Math.min(ui.textWidth(box.getLabel(), Theme.FONT_SMALL), w * 0.45f);
        ui.text(ui.ellipsize(box.getLabel(), labelW + 0.5f, Theme.FONT_SMALL), x + Theme.PAD, y, h, Theme.FONT_SMALL,
                ColorUtil.lerp(Theme.TEXT_DIM, Theme.TEXT, hoverA.value()));
        if (!field.editing) field.text = box.getText();
        float fx = x + Theme.PAD + labelW + 3f;
        field.draw(ui, fx, y + 1.25f, x + w - Theme.PAD - fx, h - 2.5f, Theme.FONT_SMALL, "—", true);
    }

    @Override
    public boolean mouseClicked(Ui ui, int button, int mods) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (!field.editing) field.begin(box.getText());
        return true;
    }

    @Override
    public boolean wantsKeyboard() { return field.editing; }

    @Override
    public void blur() { commit(); }

    private void commit() {
        if (!field.editing) return;
        field.end();
        box.setText(field.text);
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
}
