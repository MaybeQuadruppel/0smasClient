package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.Component;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;

/**
 * Base of every inline setting editor. Layout is immediate: the owner calls {@link #render} with the bounds
 * each frame; the widget remembers them (and whether it was hovered) for the input handlers, which fire
 * between frames and therefore act on what the user saw.
 */
public abstract class SettingWidget {

    protected final Component setting;
    protected float x, y, w;
    protected boolean hovered;
    protected final Anim hoverA = new Anim(0f, 18f);

    protected SettingWidget(Component setting) {
        this.setting = setting;
    }

    public Component setting() { return setting; }

    /** Current (possibly animating) height in GUI units. */
    public float height() { return Theme.SETTING_H; }

    public final void render(Ui ui, float x, float y, float w) {
        this.x = x;
        this.y = y;
        this.w = w;
        hovered = ui.hovered(x, y, w, height());
        hoverA.setTarget(hovered ? 1f : 0f);
        hoverA.update(ui.dt, Theme.animSpeed());
        draw(ui);
    }

    protected abstract void draw(Ui ui);

    /** Row background shared by all settings (brightens on hover). */
    protected void background(Ui ui, float h) {
        ui.rect(x, y, w, h, Theme.SETTING_BG);
        if (hoverA.value() > 0.001f) ui.rect(x, y, w, h, ColorUtil.withAlpha(0xFFFFFF, Math.round(10 * hoverA.value())));
    }

    public boolean isHovered() { return hovered; }

    public String description() { return setting.getDescription(); }

    /** @return true if the click was consumed. */
    public boolean mouseClicked(Ui ui, int button, int mods) { return false; }

    public void mouseReleased(Ui ui, int button) {}

    public boolean mouseScrolled(Ui ui, double amount) { return false; }

    /** Only called while this widget has keyboard focus (see {@link #wantsKeyboard()}). */
    public boolean keyPressed(int key, int mods) { return false; }

    public boolean charTyped(int codepoint) { return false; }

    /** True while the widget wants all key presses (text editing, value typing). */
    public boolean wantsKeyboard() { return false; }

    /** Drops keyboard focus (e.g. when something else is clicked). */
    public void blur() {}

    /** Hovered widget gets key presses too (e.g. arrow keys on sliders) even without focus. */
    public boolean hoverKey(int key, int mods) { return false; }

    protected static String label(Component c) {
        try {
            return (String) c.getClass().getMethod("getLabel").invoke(c);
        } catch (ReflectiveOperationException e) {
            return c.getClass().getSimpleName();
        }
    }
}
