package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.function.Consumer;

public class TextBox extends Component {

    private static final int PAD_X = 6;

    private final String label;
    private String text;
    private final Consumer<String> onResponder;
    private boolean focused = false;

    private int viewOffset = 0;
    private int maxLength = -1;
    private boolean numericOnly = false;
    private float focusAnim = 0f;

    private static final int BASE_BOX_HEIGHT = 16;
    private static final int BASE_TOTAL_HEIGHT = 28; // Label + Input-Box

    public TextBox(String label, String defaultText, Consumer<String> onResponder) {
        super(0, 0, 92, BASE_TOTAL_HEIGHT);
        this.label = label;
        this.text = defaultText != null ? defaultText : "";
        this.onResponder = onResponder;
        syncScaledSize(92, BASE_TOTAL_HEIGHT, 50, BASE_TOTAL_HEIGHT);
    }

    public String getText() {
        return this.text;
    }

    public void setText(String text) {
        this.text = clampLength(text != null ? text : "");
        updateViewOffset();
        if (this.onResponder != null) {
            this.onResponder.accept(this.text);
        }
    }

    public TextBox withDescription(String description) {
        this.description = description;
        return this;
    }

    public TextBox withMaxLength(int maxLength) {
        this.maxLength = maxLength;
        this.text = clampLength(this.text);
        return this;
    }

    public TextBox numericOnly() {
        this.numericOnly = true;
        return this;
    }

    public void unfocus() {
        if (focused) {
            focused = false;
            Sounds.deselect();
        }
    }

    private String clampLength(String value) {
        if (maxLength >= 0 && value.length() > maxLength) {
            return value.substring(0, maxLength);
        }
        return value;
    }

    private boolean isCharAllowed(char c) {
        if (c < 32 || c == 127) return false;
        if (!numericOnly) return true;
        if (Character.isDigit(c)) return true;
        if (c == '.' && text.indexOf('.') < 0) return true;
        if (c == '-' && text.isEmpty()) return true;
        return false;
    }

    private void updateViewOffset() {
        int pad = UISettings.scaled(PAD_X);
        int innerW = Math.max(1, width - pad * 2);
        int textW = UISettings.textWidth(Minecraft.getInstance().font, text);
        int maxOffset = Math.max(0, textW - innerW);
        viewOffset = Math.min(viewOffset, maxOffset);
        viewOffset = maxOffset;
    }

    private int getBoxY() {
        return y + UISettings.scaled(12);
    }

    private int getBoxHeight() {
        return UISettings.scaled(BASE_BOX_HEIGHT);
    }

    private boolean isBoxHovered(int mouseX, int mouseY) {
        int bY = getBoxY();
        int bH = getBoxHeight();
        return mouseX >= x && mouseX <= x + width && mouseY >= bY && mouseY <= bY + bH;
    }

    @Override
    public void render(Object graphics, int mouseX, int mouseY) {
        if (!(graphics instanceof GuiGraphicsExtractor guiGraphics)) return;

        syncHeight(BASE_TOTAL_HEIGHT, UISettings.scaled(BASE_TOTAL_HEIGHT));
        updateViewOffset();

        Theme.Palette p = Theme.getActive().palette;

        // Label über der Textbox
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label + ":",
                x, y + 1, p.accent, false);

        int boxY = getBoxY();
        int boxH = getBoxHeight();
        boolean hov = isBoxHovered(mouseX, mouseY);

        focusAnim = focused
                ? Math.min(1f, focusAnim + UISettings.animStep(0.2f))
                : Math.max(0f, focusAnim - UISettings.animStep(0.2f));

        int bgCol = lerpColor(p.bg, p.bgHover, hov || focused ? 1f : 0f);
        drawRoundedRect(guiGraphics, x, boxY, width, boxH, UISettings.withPanelAlpha(bgCol));
        drawRoundedOutline(guiGraphics, x, boxY, width, boxH,
                lerpColor(p.border, p.accent, focusAnim));

        boolean showCursor = focused && ((System.currentTimeMillis() / 500) % 2 == 0);
        String cursorStr = showCursor ? "|" : "";
        String displayStr = text.isEmpty() && !focused ? "Type here..." : text + (focused ? cursorStr : "");
        int textColor = text.isEmpty() && !focused ? p.textDim : p.text;

        int pad = UISettings.scaled(PAD_X);
        guiGraphics.enableScissor(x + 1, boxY + 1, x + width - 1, boxY + boxH - 1);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, displayStr,
                x + pad - viewOffset, boxY + (boxH / 2) - UISettings.scaled(4), textColor, false);
        guiGraphics.disableScissor();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!focused) return false;
        char c = (char) event.codepoint();
        if (isCharAllowed(c) && (maxLength < 0 || text.length() < maxLength)) {
            text += c;
            updateViewOffset();
            if (onResponder != null) onResponder.accept(text);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!focused) return false;
        int key = event.key();
        if (key == 256 || key == 257 || key == 335) { // ESC / Enter / NumPad Enter
            focused = false;
            Sounds.deselect();
            return true;
        }

        if (key == 259 && !text.isEmpty()) { // Backspace
            text = text.substring(0, text.length() - 1);
            updateViewOffset();
            if (onResponder != null) onResponder.accept(text);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            boolean wasFocused = focused;
            focused = isBoxHovered((int) mouseX, (int) mouseY);

            if (focused != wasFocused) {
                if (focused) Sounds.select(); else Sounds.deselect();
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