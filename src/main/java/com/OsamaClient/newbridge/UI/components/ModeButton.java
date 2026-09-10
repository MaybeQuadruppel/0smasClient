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

    public ModeButton(String label, List<String> modes, int startIndex,
                      Consumer<String> onChange) {
        this.label    = label;
        this.modes    = modes;
        this.index    = startIndex;
        this.onChange = onChange;
        // Mindestgröße: verhindert, dass Label- und Value-Text bei sehr
        // kleiner UI-Scale zu wenig Platz haben und ineinander clippen.
        syncScaledSize(100, 14, 60, 14);
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // Größe bei jedem Frame aus dem aktuellen UISettings.scale neu ableiten -
        // sonst bleibt die Box beim Verschieben des Scale-Reglers auf der Größe
        // stehen, mit der sie erzeugt wurde.
        syncScaledSize(baseWidth, baseHeight, 60, 14);

        Theme.Palette p = Theme.getActive().palette;
        float hover = stepHover(mouseX, mouseY);

        drawRoundedRect(   guiGraphics, x, y, width, height,
                UISettings.withPanelAlpha(lerpColor(p.bg, p.bgHover, hover)));
        drawRoundedOutline(guiGraphics, x, y, width, height,
                lerpColor(p.border, p.borderHover, hover * 0.7f));

        // Vertikale Zentrierung berücksichtigt fontScale (Glyphenhöhe skaliert
        // mit) - sonst wirkt der Text bei anderer Font-Size zunehmend nach
        // oben/unten "verschoben" statt mittig in der Box zu sitzen.
        int textOffsetY = Math.round(4 * UISettings.fontScale);

        // 1. Vorbereiten: Val-Text berechnen, um zu wissen, wo er beginnt
        String valText = "\u25C4 " + modes.get(index) + " \u25BA";

        // WICHTIG: nicht font.width(...) direkt verwenden - UISettings.drawText
        // skaliert den Text per fontScale, die rohe Font-Breite passt dann nicht
        // mehr zur tatsächlich gezeichneten Breite und der Text rutscht rechts
        // aus der Box bzw. clippt in das Label hinein.
        int valWidth = UISettings.textWidth(Minecraft.getInstance().font, valText);
        int valStartX = x + width - valWidth - 5;

        // 2. Scissor & Zeichnen: Label
        guiGraphics.enableScissor(x + 1, y + 1, Math.max(x + 1, valStartX - 2), y + height - 1);

        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label,
                x + 6, y + (height / 2) - textOffsetY,
                lerpColor(p.textDim, p.text, hover), false);

        guiGraphics.disableScissor();

        // 3. Scissor & Zeichnen: Value-Text
        guiGraphics.enableScissor(Math.max(x + 1, valStartX - 2), y + 1, x + width - 1, y + height - 1);

        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, valText,
                valStartX,
                y + (height / 2) - textOffsetY,
                lerpColor(p.textDim, lerpColor(p.accent, p.text, hover), hover),
                false);

        guiGraphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            if (button == 0) {
                index = (index + 1) % modes.size();
                Sounds.select();
            } else if (button == 1) {
                index = (index - 1 + modes.size()) % modes.size();
                Sounds.deselect();
            } else {
                return false;
            }
            onChange.accept(modes.get(index));
            return true;
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