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

    private static final int PAD_X = 4;

    private final String label;
    private String text;
    private final Consumer<String> onResponder;
    private boolean focused = false;

    /** Horizontaler Scroll-Offset in Pixeln, damit der Cursor bei langem Text sichtbar bleibt. */
    private int viewOffset = 0;

    /** -1 = unbegrenzt. Verhindert, dass beliebig langer Text getippt werden kann. */
    private int maxLength = -1;

    /** Erlaubt nur Ziffern (+ optional ein '.' und ein führendes '-'). Für Zahlen-Felder. */
    private boolean numericOnly = false;

    private float focusAnim = 0f;

    public TextBox(String label, String defaultText, Consumer<String> onResponder) {
        this.label = label;
        this.text = defaultText != null ? defaultText : "";
        this.onResponder = onResponder;
        // Mindestgröße: sonst wird das Eingabefeld bei kleiner Scale schmaler
        // als der sichtbare Cursor/Rand-Puffer und der Text wirkt clipped.
        syncScaledSize(92, 13, 50, 13);
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

    /** Begrenzt die maximale Zeichenanzahl. -1 = unbegrenzt (Standard). */
    public TextBox withMaxLength(int maxLength) {
        this.maxLength = maxLength;
        this.text = clampLength(this.text);
        return this;
    }

    /** Beschränkt die Eingabe auf Ziffern (plus ein optionales '.' und führendes '-'). */
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

    /** Hält den Cursor (Textende) innerhalb der sichtbaren Box, indem der Text horizontal scrollt. */
    private void updateViewOffset() {
        int innerW = Math.max(1, width - PAD_X * 2);
        // WICHTIG: textWidth() statt font.width() - der Text wird über
        // UISettings.drawText mit fontScale skaliert gezeichnet, daher muss
        // auch der Scroll-Offset die skalierte Breite verwenden. Sonst scrollt
        // die Box zu wenig/zu viel und der Cursor bzw. das Textende wirkt
        // abgeschnitten, sobald fontScale != 1 ist.
        int textW = UISettings.textWidth(Minecraft.getInstance().font, text);
        int maxOffset = Math.max(0, textW - innerW);
        viewOffset = Math.min(viewOffset, maxOffset);
        // Cursor steht immer am Textende -> bei Bedarf ganz nach rechts scrollen.
        viewOffset = maxOffset;
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // Größe bei jedem Frame neu aus UISettings.scale ableiten, sonst
        // bleibt die Box (und damit auch innerW/viewOffset) auf dem Stand
        // beim Erzeugen eingefroren.
        syncScaledSize(baseWidth, baseHeight, 50, 13);
        updateViewOffset();

        Theme.Palette p = Theme.getActive().palette;

        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label + ":",
                x, y - 11, p.accent, false);

        boolean hov = isHovered(mouseX, mouseY);
        focusAnim = focused
                ? Math.min(1f, focusAnim + UISettings.animStep(0.2f))
                : Math.max(0f, focusAnim - UISettings.animStep(0.2f));

        int bgCol = lerpColor(p.bg, p.bgHover, hov || focused ? 1f : 0f);
        drawRoundedRect(guiGraphics, x, y, width, height, UISettings.withPanelAlpha(bgCol));
        drawRoundedOutline(guiGraphics, x, y, width, height,
                lerpColor(p.border, p.accent, focusAnim));

        boolean showCursor = focused && ((System.currentTimeMillis() / 500) % 2 == 0);
        String cursorStr = showCursor ? "|" : "";
        String displayStr = text.isEmpty() && !focused ? "Type here..." : text + (focused ? cursorStr : "");
        int textColor = text.isEmpty() && !focused ? p.textDim : p.text;

        // Scissor sorgt dafür, dass Text NIE über den Rand der Box hinausragt,
        // egal wie lang die Eingabe ist.
        guiGraphics.enableScissor(x + 1, y + 1, x + width - 1, y + height - 1);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, displayStr,
                x + PAD_X - viewOffset, y + (height / 2) - 4, textColor, false);
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
            focused = isHovered(mouseX, mouseY);

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