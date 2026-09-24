package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class EntityFilterPicker extends Component {

    private final String label;
    public final Map<String, Boolean> filters = new LinkedHashMap<>();
    public final Map<String, Integer> colors = new LinkedHashMap<>();
    private boolean open = false;

    private String activeColorFilter = null;
    private int draggingSlider = 0; // 0 = none, 1 = Hue, 2 = Alpha

    private static final int DEFAULT_COLOR = 0x6600FFFF;
    private static final int BASE_BOX_HEIGHT = 16;
    private static final int BASE_TOTAL_HEIGHT = 28; // 12px Label + 16px Box

    public EntityFilterPicker(String label) {
        super(0, 0, 100, BASE_TOTAL_HEIGHT);
        this.label = label;
        syncScaledSize(100, BASE_TOTAL_HEIGHT, 60, BASE_TOTAL_HEIGHT);

        filters.put("Players", true);
        filters.put("Hostiles", true);
        filters.put("Animals", false);
        filters.put("NPCs", false);
        filters.put("ArmorStands", false);
    }

    public String getLabel() { return this.label; }

    public EntityFilterPicker withDescription(String description) {
        this.description = description;
        return this;
    }

    public boolean isFilterEnabled(String key) {
        return filters.getOrDefault(key, false);
    }

    public int getColor(String key) {
        return colors.getOrDefault(key, DEFAULT_COLOR);
    }

    private int getActiveCount() {
        int count = 0;
        for (boolean b : filters.values()) if (b) count++;
        return count;
    }

    private int getButtonY() {
        return y + UISettings.scaled(12);
    }

    private int getButtonHeight() {
        return UISettings.scaled(BASE_BOX_HEIGHT);
    }

    private boolean isButtonHovered(int mouseX, int mouseY) {
        int btnY = getButtonY();
        int btnH = getButtonHeight();
        return mouseX >= x && mouseX <= x + width && mouseY >= btnY && mouseY <= btnY + btnH;
    }

    @Override
    public void render(Object graphics, int mouseX, int mouseY) {
        if (!(graphics instanceof GuiGraphicsExtractor guiGraphics)) return;

        syncHeight(BASE_TOTAL_HEIGHT, UISettings.scaled(BASE_TOTAL_HEIGHT));
        Theme.Palette p = Theme.getActive().palette;

        // 1. Label oberhalb der Box
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label + ":", x, y + 1, p.accent, false);

        int btnY = getButtonY();
        int btnH = getButtonHeight();
        boolean hov = isButtonHovered(mouseX, mouseY);

        // ZWINGE Opacity auf 255 (0xFF000000) für den geschlossenen Button, damit er solide ist
        int btnBg = lerpColor(p.bg, p.bgHover, hov ? 1f : 0f);
        int solidBtnBg = (btnBg & 0x00FFFFFF) | 0xFF000000;

        // 2. Haupt-Button (Massiv)
        drawRoundedRect(guiGraphics, x, btnY, width, btnH, solidBtnBg);
        drawRoundedOutline(guiGraphics, x, btnY, width, btnH, open ? p.accent : (p.border | 0xFF000000));

        String arrow = open ? " \u25b2" : " \u25bc";
        String btnLabel = getActiveCount() + " active" + arrow;
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, btnLabel, x + UISettings.scaled(6), btnY + (btnH / 2) - UISettings.scaled(4), open ? p.accent : p.text, false);

        if (!open) return;

        // 3. Dropdown Menü
        List<String> keys = new ArrayList<>(filters.keySet());
        int itemH = UISettings.scaled(15);
        int dropW = width + UISettings.scaled(40);
        int listH = keys.size() * itemH;

        boolean showColorMenu = activeColorFilter != null && filters.getOrDefault(activeColorFilter, false);
        int colorSliderH = showColorMenu ? UISettings.scaled(38) : 0;
        int dropH = listH + colorSliderH;
        int dropX = x;
        int dropY = btnY + btnH + UISettings.scaled(3);

        // ZWINGE Opacity auf 255 für das offene Dropdown-Menü
        int solidDropBg = (p.bg & 0x00FFFFFF) | 0xFF000000;

        drawShadow(guiGraphics, dropX, dropY, dropW, dropH);

        // Hier wurde der Panel Alpha entfernt, das Menü ist jetzt zu 100% deckend!
        drawRoundedRect(guiGraphics, dropX, dropY, dropW, dropH + 2, solidDropBg);
        drawRoundedOutline(guiGraphics, dropX, dropY, dropW, dropH + 2, p.accent);

        int currentY = dropY + 2;
        int selBgColor = (p.accent & 0x00FFFFFF) | 0x35000000;

        for (String key : keys) {
            boolean active = filters.getOrDefault(key, false);
            boolean itemHov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= currentY && mouseY <= currentY + itemH;

            // Hover-Effekt ebenfalls anpassen, damit es sich nicht komisch mit Transparenz vermischt
            if (active) {
                guiGraphics.fill(dropX + 2, currentY, dropX + dropW - 2, currentY + itemH, selBgColor);
            } else if (itemHov) {
                int solidHover = (p.bgHover & 0x00FFFFFF) | 0xFF000000;
                guiGraphics.fill(dropX + 2, currentY, dropX + dropW - 2, currentY + itemH, solidHover);
            }

            int filterColor = getColor(key);
            int displayColor = active ? p.text : (itemHov ? p.text : p.textDim);

            if (active) {
                UISettings.drawText(guiGraphics, Minecraft.getInstance().font, "\u2714", dropX + UISettings.scaled(5), currentY + (itemH / 2) - UISettings.scaled(4), p.accent, false);
            }

            UISettings.drawText(guiGraphics, Minecraft.getInstance().font, key, dropX + UISettings.scaled(active ? 17 : 7), currentY + (itemH / 2) - UISettings.scaled(4), displayColor, false);

            if (active) {
                int previewSize = UISettings.scaled(8);
                int previewX = dropX + dropW - UISettings.scaled(14);
                int previewY = currentY + (itemH / 2) - (previewSize / 2);
                drawRoundedRect(guiGraphics, previewX, previewY, previewSize, previewSize, filterColor | 0xFF000000);

                if (activeColorFilter != null && activeColorFilter.equals(key)) {
                    drawRoundedOutline(guiGraphics, previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, p.accent);
                }
            }

            currentY += itemH;
        }

        // 4. Farbregler für aktiven Filter (Hue & Opacity)
        if (showColorMenu) {
            int startSliderY = dropY + listH + UISettings.scaled(4);
            int sliderX = dropX + UISettings.scaled(6);
            int sliderW = dropW - UISettings.scaled(12);

            guiGraphics.fill(dropX + 2, startSliderY - UISettings.scaled(3), dropX + dropW - 2, startSliderY - UISettings.scaled(2), p.border | 0xFF000000);

            int currentARGB = getColor(activeColorFilter);
            int currentAlpha = (currentARGB >> 24) & 0xFF;

            float[] hsb = new float[3];
            java.awt.Color awtColor = new java.awt.Color(currentARGB & 0xFFFFFF);
            java.awt.Color.RGBtoHSB(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue(), hsb);
            float currentHue = hsb[0];

            if (draggingSlider == 1) {
                currentHue = Math.min(1f, Math.max(0f, (mouseX - sliderX) / (float) sliderW));
            } else if (draggingSlider == 2) {
                currentAlpha = (int) (Math.min(1f, Math.max(0f, (mouseX - sliderX) / (float) sliderW)) * 255);
            }

            if (draggingSlider != 0) {
                int rgb = java.awt.Color.HSBtoRGB(currentHue, 1f, 1f) & 0xFFFFFF;
                int finalARGB = (currentAlpha << 24) | rgb;
                if (finalARGB != currentARGB) {
                    colors.put(activeColorFilter, finalARGB);
                }
            }

            // Hue Bar
            for (int i = 0; i < sliderW; i++) {
                int col = java.awt.Color.HSBtoRGB(i / (float) sliderW, 1f, 1f) | 0xFF000000;
                guiGraphics.fill(sliderX + i, startSliderY, sliderX + i + 1, startSliderY + UISettings.scaled(4), col);
            }
            int thumbX1 = sliderX + (int) (currentHue * sliderW);
            guiGraphics.fill(Math.max(sliderX, Math.min(sliderX + sliderW - 2, thumbX1 - 1)), startSliderY - 1, Math.max(sliderX, Math.min(sliderX + sliderW - 1, thumbX1 + 2)), startSliderY + UISettings.scaled(5), 0xFFFFFFFF);

            // Opacity Bar
            int alphaSliderY = startSliderY + UISettings.scaled(13);
            UISettings.drawText(guiGraphics, Minecraft.getInstance().font, "Opacity: " + (int) ((currentAlpha / 255f) * 100) + "%", sliderX, alphaSliderY, p.textDim, false);
            int barY = alphaSliderY + UISettings.scaled(10);

            for (int i = 0; i < sliderW; i++) {
                float pct = i / (float) sliderW;
                int alphaVal = (int) (pct * 255);
                int gray = (alphaVal << 24) | 0xFFFFFF;
                guiGraphics.fill(sliderX + i, barY, sliderX + i + 1, barY + UISettings.scaled(4), gray);
            }
            int thumbX2 = sliderX + (int) ((currentAlpha / 255f) * sliderW);
            guiGraphics.fill(Math.max(sliderX, Math.min(sliderX + sliderW - 2, thumbX2 - 1)), barY - 1, Math.max(sliderX, Math.min(sliderX + sliderW - 1, thumbX2 + 2)), barY + UISettings.scaled(5), 0xFFFFFFFF);
        } else {
            activeColorFilter = null;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int imx = (int) mouseX, imy = (int) mouseY;

        if (isButtonHovered(imx, imy) && button == 0) {
            open = !open;
            activeColorFilter = null;
            if (open) Sounds.select(); else Sounds.deselect();
            return true;
        }

        if (open) {
            List<String> keys = new ArrayList<>(filters.keySet());
            int itemH = UISettings.scaled(15);
            int dropX = x;
            int dropW = width + UISettings.scaled(40);
            int listH = keys.size() * itemH;
            int dropY = getButtonY() + getButtonHeight() + UISettings.scaled(3);

            boolean showColorMenu = activeColorFilter != null && filters.getOrDefault(activeColorFilter, false);
            if (showColorMenu) {
                int startSliderY = dropY + listH + UISettings.scaled(4);
                int sliderX = dropX + UISettings.scaled(6);
                int sliderW = dropW - UISettings.scaled(12);

                if (imx >= sliderX && imx <= sliderX + sliderW && imy >= startSliderY - 2 && imy <= startSliderY + 8 && button == 0) {
                    this.draggingSlider = 1;
                    Sounds.select();
                    return true;
                }
                if (imx >= sliderX && imx <= sliderX + sliderW && imy >= startSliderY + UISettings.scaled(20) && imy <= startSliderY + UISettings.scaled(28) && button == 0) {
                    this.draggingSlider = 2;
                    Sounds.select();
                    return true;
                }
            }

            if (imx >= dropX && imx <= dropX + dropW && imy >= dropY && imy <= dropY + listH) {
                int idx = (imy - dropY) / itemH;

                if (idx >= 0 && idx < keys.size()) {
                    String key = keys.get(idx);
                    if (button == 0) {
                        boolean newState = !filters.getOrDefault(key, false);
                        filters.put(key, newState);
                        if (!newState && key.equals(activeColorFilter)) activeColorFilter = null;
                        if (newState) Sounds.select(); else Sounds.deselect();
                    } else if (button == 1) {
                        if (filters.getOrDefault(key, false)) {
                            activeColorFilter = key.equals(activeColorFilter) ? null : key;
                            if (activeColorFilter != null) Sounds.select(); else Sounds.deselect();
                        }
                    }
                }
                return true;
            }

            if (imx < dropX || imx > dropX + dropW || imy < getButtonY() || imy > dropY + listH + (showColorMenu ? UISettings.scaled(38) : 0)) {
                open = false;
                activeColorFilter = null;
                Sounds.deselect();
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.draggingSlider = 0;
        return false;
    }
}