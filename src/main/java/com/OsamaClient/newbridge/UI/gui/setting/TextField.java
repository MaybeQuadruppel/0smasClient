package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntPredicate;

/** Single-line text editing state + drawing, shared by every text input in the GUI. */
public final class TextField {

    public String text = "";
    public int cursor;
    public boolean editing;
    public int maxLength = 64;
    public IntPredicate filter = cp -> cp >= 32 && cp != 127;
    private float blink;
    private final Anim focusA = new Anim(0f, 16f);

    public void begin(String initial) {
        text = initial == null ? "" : initial;
        cursor = text.length();
        editing = true;
        blink = 0;
    }

    public void end() { editing = false; }

    /** @return true if the text changed */
    public boolean charTyped(int cp) {
        if (!editing || !filter.test(cp) || text.length() >= maxLength) return false;
        String s = new String(Character.toChars(cp));
        text = text.substring(0, cursor) + s + text.substring(cursor);
        cursor += s.length();
        blink = 0;
        return true;
    }

    /**
     * Handles editing keys. Enter/Escape are not handled here (callers decide commit/cancel).
     * @return true if the text changed
     */
    public boolean keyPressed(int key, int mods) {
        if (!editing) return false;
        blink = 0;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursor == 0) return false;
                int from = UiInput.ctrl(mods) ? 0 : cursor - 1;
                text = text.substring(0, from) + text.substring(cursor);
                cursor = from;
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (cursor >= text.length()) return false;
                text = text.substring(0, cursor) + text.substring(cursor + 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> cursor = Math.max(0, cursor - 1);
            case GLFW.GLFW_KEY_RIGHT -> cursor = Math.min(text.length(), cursor + 1);
            case GLFW.GLFW_KEY_HOME -> cursor = 0;
            case GLFW.GLFW_KEY_END -> cursor = text.length();
            case GLFW.GLFW_KEY_V -> {
                if (!UiInput.ctrl(mods)) return false;
                boolean changed = false;
                for (int cp : UiInput.clipboard().codePoints().toArray()) changed |= charTyped(cp);
                return changed;
            }
            case GLFW.GLFW_KEY_A -> {
                if (!UiInput.ctrl(mods)) return false;
                text = "";
                cursor = 0;
                return true;
            }
            default -> { return false; }
        }
        return false;
    }

    /** Draws the field box with text (or placeholder) and a blinking caret while editing. */
    public void draw(Ui ui, float x, float y, float w, float h, float size, String placeholder, boolean alignRight) {
        focusA.setTarget(editing ? 1f : 0f);
        focusA.update(ui.dt, Theme.animSpeed());
        blink += ui.dt;
        float f = focusA.value();
        ui.round(x, y, w, h, 1.5f, ColorUtil.lerp(0xFF101013, 0xFF0C0C0F, f));
        ui.outline(x, y, w, h, 1.5f, 1f, ColorUtil.lerp(0x18FFFFFF, Theme.accent(0.8f), f));
        float pad = 2f;
        ui.pushClip(x + 1f, y, w - 2f, h);
        boolean empty = text.isEmpty();
        String shown = empty ? placeholder : text;
        int color = empty ? 0xFF5A5A66 : Theme.TEXT;
        float tw = ui.textWidth(shown, size);
        float caretOffset = empty ? 0 : ui.textWidth(text.substring(0, Math.min(cursor, text.length())), size);
        float tx = alignRight && !editing ? x + w - pad - tw : x + pad;
        // keep the caret visible for long text
        float overflow = tx + caretOffset - (x + w - pad);
        if (editing && overflow > 0) tx -= overflow;
        ui.text(shown, tx, y, h, size, color);
        if (editing && (blink % 1f) < 0.55f) {
            float cx = tx + caretOffset;
            ui.rect(cx, y + h * 0.2f, 1f / ui.scale * Math.max(1f, ui.scale * 0.6f), h * 0.6f, Theme.accent());
        }
        ui.popClip();
    }
}
