package com.OsamaClient.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.LinkedHashMap;
import java.util.Map;

public class EntityFilterPicker extends Component {

    private final String label;
    public final Map<String, Boolean> filters = new LinkedHashMap<>();
    private boolean open = false;

    // GUI Farben (Passend zu ItemPicker/ClickGui)
    private static final int C_BG         = 0xF20A0A0A;
    private static final int C_BG_HOV     = 0xFF181818;
    private static final int C_SEPARATOR  = 0xFF2C2C2C;
    private static final int C_ACCENT     = 0xFFFFFFFF;
    private static final int C_TEXT       = 0xFFEEEEEE;
    private static final int C_TEXT_DIM   = 0xFF666666;
    private static final int C_SELECTED   = 0xFFFFFFFF;
    private static final int C_SEL_BG     = 0x40FFFFFF;

    public EntityFilterPicker(String label) {
        this.label = label;
        this.width = 100;
        this.height = 14;

        // Standard-Filter initialisieren
        filters.put("Players", true);
        filters.put("Hostiles", true);
        filters.put("Animals", false);
        filters.put("NPCs", false);
        filters.put("ArmorStands", false);
    }

    public boolean isFilterEnabled(String key) {
        return filters.getOrDefault(key, false);
    }

    private int getActiveCount() {
        int count = 0;
        for (boolean b : filters.values()) if (b) count++;
        return count;
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        // Label über dem Button
        guiGraphics.text(Minecraft.getInstance().font, label + ":", x, y - 11, C_ACCENT, false);

        boolean hov = isHovered(mouseX, mouseY);
        int btnBg = lerpColor(C_BG, C_BG_HOV, hov ? 1f : 0f);

        // Haupt-Button zeichnen
        drawRoundedRect(guiGraphics, x, y, width, height, btnBg);
        drawRoundedOutline(guiGraphics, x, y, width, height, open ? C_ACCENT : C_SEPARATOR);

        String arrow = open ? " \u25b2" : " \u25bc";
        String btnLabel = getActiveCount() + " active" + arrow;
        guiGraphics.text(Minecraft.getInstance().font, btnLabel, x + 4, y + (height / 2) - 4, open ? C_ACCENT : C_TEXT, false);

        if (!open) return;

        // Dropdown-Menü Rendern
        int dropW = width + 40;
        int itemH = 14;
        int dropH = filters.size() * itemH;
        int dropX = x;
        int dropY = y + height + 3;

        drawShadow(guiGraphics, dropX, dropY, dropW, dropH);
        drawRoundedRect(guiGraphics, dropX, dropY, dropW, dropH + 2, C_BG);
        drawRoundedOutline(guiGraphics, dropX, dropY, dropW, dropH + 2, C_ACCENT);

        int currentY = dropY + 1;
        for (Map.Entry<String, Boolean> entry : filters.entrySet()) {
            boolean active = entry.getValue();
            boolean itemHov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= currentY && mouseY <= currentY + itemH;

            // Hintergründe bei Selection/Hover
            if (active) {
                guiGraphics.fill(dropX + 1, currentY, dropX + dropW - 1, currentY + itemH, C_SEL_BG);
            } else if (itemHov) {
                guiGraphics.fill(dropX + 1, currentY, dropX + dropW - 1, currentY + itemH, 0x1AFFFFFF);
            }

            // Häkchen bei aktiven Filtern
            if (active) {
                guiGraphics.text(Minecraft.getInstance().font, "\u2714", dropX + 5, currentY + (itemH / 2) - 4, C_SELECTED, false);
            }

            // Filter Name (z.B. "Players", "NPCs")
            guiGraphics.text(Minecraft.getInstance().font, entry.getKey(), dropX + (active ? 17 : 7), currentY + (itemH / 2) - 4, active ? C_SELECTED : (itemHov ? C_TEXT : C_TEXT_DIM), false);

            currentY += itemH;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Haupt-Button Klick
        if (isHovered(mouseX, mouseY) && button == 0) {
            open = !open;
            return true;
        }

        // Klick in die Liste
        if (open) {
            int dropX = x;
            int dropW = width + 40;
            int dropY = y + height + 3;
            int itemH = 14;
            int dropH = filters.size() * itemH;

            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= dropY && mouseY <= dropY + dropH) {
                int idx = (int) ((mouseY - dropY) / itemH);
                int currentIdx = 0;

                for (Map.Entry<String, Boolean> entry : filters.entrySet()) {
                    if (currentIdx == idx) {
                        filters.put(entry.getKey(), !entry.getValue()); // Umschalten (On/Off)
                        return true;
                    }
                    currentIdx++;
                }
            }

            // Schließen, wenn man außerhalb klickt
            if (mouseX < dropX || mouseX > dropX + dropW || mouseY < y + height) {
                open = false;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }
}