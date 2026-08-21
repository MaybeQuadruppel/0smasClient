package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.function.Consumer;

public class TextBox extends Component {

    private final String label;
    private String text;
    private final Consumer<String> onResponder;
    private boolean focused = false;

    private static final int C_BG         = 0xF20A0A0A;
    private static final int C_BG_HOV     = 0xFF181818;
    private static final int C_SEPARATOR  = 0xFF2C2C2C;
    private static final int C_ACCENT     = 0xFFFFFFFF;
    private static final int C_TEXT       = 0xFFEEEEEE;
    private static final int C_TEXT_DIM   = 0xFF666666;
    private static final int C_FOCUS      = 0xFFFFFFFF;

    public TextBox(String label, String defaultText, Consumer<String> onResponder) {
        this.label = label;
        this.text = defaultText != null ? defaultText : "";
        this.onResponder = onResponder;
        this.width = 100;
        this.height = 14;
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
        if (this.onResponder != null) {
            this.onResponder.accept(this.text);
        }
    }

    public TextBox withDescription(String description) {
        this.description = description;
        return this;
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        guiGraphics.text(Minecraft.getInstance().font, label + ":", x, y - 11, C_ACCENT, false);

        boolean hov = isHovered(mouseX, mouseY);
        int bgCol = lerpColor(C_BG, C_BG_HOV, hov ? 1f : 0f);
        drawRoundedRect(guiGraphics, x, y, width, height, bgCol);
        drawRoundedOutline(guiGraphics, x, y, width, height, focused ? C_FOCUS : C_SEPARATOR);
        boolean showCursor = focused && ((System.currentTimeMillis() / 500) % 2 == 0);
        String cursorStr = showCursor ? "|" : "";
        String displayStr = text.isEmpty() && !focused ? "Type here..." : text + (focused ? cursorStr : "");
        guiGraphics.text(Minecraft.getInstance().font, displayStr, x + 4, y + (height / 2) - 4, text.isEmpty() && !focused ? C_TEXT_DIM : C_TEXT, false);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!focused) return false;
        char c = (char) event.codepoint();
        if (c >= 32 && c != 127) {
            text += c;
            if (onResponder != null) onResponder.accept(text);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!focused) return false;
        int key = event.key();
        if (key == 256 || key == 257 || key == 335) { // 256 = ESC, 257/335 = Enter (Normal / NumPad)
            focused = false;
            ClickGuiScreen.playGuiSound(0.85f, 0.2f);
            return true;
        }

        if (key == 259 && !text.isEmpty()) {
            text = text.substring(0, text.length() - 1);
            if (onResponder != null) onResponder.accept(text);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean wasFocused = focused;
            focused = isHovered(mouseX, mouseY);

            if (focused != wasFocused) {
                ClickGuiScreen.playGuiSound(focused ? 1.15f : 0.85f, 0.25f);
            }
            return focused;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }
}