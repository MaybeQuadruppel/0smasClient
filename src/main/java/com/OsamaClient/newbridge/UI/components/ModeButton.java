package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.Consumer;

public class ModeButton extends Component {

    private final String label;
    private final List<String> modes;
    private int index;
    private final Consumer<String> onChange;

    private float hoverAnim = 0f;
    private boolean expanded = false;

    private static final int BASE_HEIGHT = 11; // Kopfzeile
    private static final int OPTION_H = 10;

    public ModeButton(String label, List<String> modes, int startIndex, Consumer<String> onChange) {
        super(0, 0, 100, BASE_HEIGHT);
        this.label    = label;
        this.modes    = modes;
        this.index    = startIndex;
        this.onChange = onChange;
        syncScaledSize(100, BASE_HEIGHT, 60, BASE_HEIGHT);
    }

    public ModeButton withDescription(String description) {
        this.description = description;
        return this;
    }

    private int rowH()   { return UISettings.scaled(BASE_HEIGHT); }
    private int optH()   { return UISettings.scaled(OPTION_H); }

    @Override
    public void render(Object graphics, int mouseX, int mouseY) {
        if (!(graphics instanceof GuiGraphicsExtractor guiGraphics)) return;

        if (this.width <= 0) this.width = UISettings.scaled(baseWidth);
        int rowH = rowH();
        this.baseHeight = BASE_HEIGHT;
        this.height = expanded ? rowH + modes.size() * optH() : rowH;

        Theme.Palette p = Theme.getActive().palette;
        boolean headerHov = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + rowH;
        hoverAnim = approach(hoverAnim, headerHov ? 1f : 0f, 0.3f);

        int pad = UISettings.scaled(3);
        int textY = y + (rowH - UISettings.scaled(7)) / 2;

        if (hoverAnim > 0.01f) {
            guiGraphics.fill(x, y, x + width, y + rowH, withAlpha(p.bgHover, hoverAnim * 0.4f));
        }

        // Kopfzeile inline: "Label: Value" (Wert in Akzent)
        String labStr = label + ": ";
        String valText = modes.get(index);
        int labW = UISettings.textWidth(Minecraft.getInstance().font, labStr);
        guiGraphics.enableScissor(x + pad, y, x + width - pad, y + rowH);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, labStr,
                x + pad, textY, lerpColor(p.textDim, p.text, hoverAnim), false);
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, valText,
                x + pad + labW, textY, lerpColor(p.accent, p.text, hoverAnim * 0.4f), false);
        guiGraphics.disableScissor();

        if (!expanded) return;

        // Aufgeklappte Optionsliste
        int oy = y + rowH;
        int oh = optH();
        for (int i = 0; i < modes.size(); i++) {
            boolean sel = i == index;
            boolean oHov = mouseX >= x && mouseX <= x + width && mouseY >= oy && mouseY <= oy + oh;
            if (oHov && !sel) {
                guiGraphics.fill(x, oy, x + width, oy + oh, withAlpha(p.bgHover, 0.4f));
            }
            if (sel) {
                guiGraphics.fill(x, oy, x + UISettings.scaled(1), oy + oh, p.accent); // Cursor links
            }
            int otY = oy + (oh - UISettings.scaled(7)) / 2;
            guiGraphics.enableScissor(x + pad + UISettings.scaled(3), oy, x + width - pad, oy + oh);
            UISettings.drawText(guiGraphics, Minecraft.getInstance().font, modes.get(i),
                    x + pad + UISettings.scaled(3), otY,
                    sel ? p.accent : (oHov ? p.text : p.textDim), false);
            guiGraphics.disableScissor();
            oy += oh;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int rowH = rowH();
        boolean inHeader = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + rowH;

        if (inHeader) {
            if (button == 0) {              // Liste auf/zu
                expanded = !expanded;
                Sounds.select();
                return true;
            }
            if (button == 1) {              // Schnell-Zyklus rückwärts
                index = (index - 1 + modes.size()) % modes.size();
                onChange.accept(modes.get(index));
                Sounds.deselect();
                return true;
            }
            return false;
        }

        if (expanded && button == 0) {      // Option in der Liste wählen
            int oy = y + rowH, oh = optH();
            for (int i = 0; i < modes.size(); i++) {
                if (mouseX >= x && mouseX <= x + width && mouseY >= oy && mouseY <= oy + oh) {
                    index = i;
                    onChange.accept(modes.get(index));
                    Sounds.select();
                    return true;
                }
                oy += oh;
            }
        }
        return false;
    }

    public String getLabel()  { return label; }
    public int    getIndex()  { return index; }
    public void setIndex(int i) {
        this.index = Math.min(modes.size() - 1, Math.max(0, i));
        onChange.accept(modes.get(this.index));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
}