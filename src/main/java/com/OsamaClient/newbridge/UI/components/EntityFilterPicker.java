package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.ClickGuiScreen;
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

        filters.put("Players", true);
        filters.put("Hostiles", true);
        filters.put("Animals", false);
        filters.put("NPCs", false);
        filters.put("ArmorStands", false);
    }

    public String getLabel() { return this.label; }

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

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        guiGraphics.text(Minecraft.getInstance().font, label + ":", x, y - 11, C_ACCENT, false);

        boolean hov = isHovered(mouseX, mouseY);
        int btnBg = lerpColor(C_BG, C_BG_HOV, hov ? 1f : 0f);

        drawRoundedRect(guiGraphics, x, y, width, height, btnBg);
        drawRoundedOutline(guiGraphics, x, y, width, height, open ? C_ACCENT : C_SEPARATOR);

        String arrow = open ? " \u25b2" : " \u25bc";
        String btnLabel = getActiveCount() + " active" + arrow;
        guiGraphics.text(Minecraft.getInstance().font, btnLabel, x + 4, y + (height / 2) - 4, open ? C_ACCENT : C_TEXT, false);

        if (!open) return;

        List<String> keys = new ArrayList<>(filters.keySet());

        int dropW = width + 40;
        int itemH = 14;
        int listH = keys.size() * itemH;

        boolean showColorMenu = activeColorFilter != null && filters.getOrDefault(activeColorFilter, false);
        int colorSliderH = showColorMenu ? 36 : 0;
        int dropH = listH + colorSliderH;
        int dropX = x;
        int dropY = y + height + 3;

        drawShadow(guiGraphics, dropX, dropY, dropW, dropH);
        drawRoundedRect(guiGraphics, dropX, dropY, dropW, dropH + 2, C_BG);
        drawRoundedOutline(guiGraphics, dropX, dropY, dropW, dropH + 2, C_ACCENT);

        int listY = dropY + 1;
        int currentY = listY;
        for (String key : keys) {
            boolean active = filters.getOrDefault(key, false);
            boolean itemHov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= currentY && mouseY <= currentY + itemH;

            if (active) {
                guiGraphics.fill(dropX + 1, currentY, dropX + dropW - 1, currentY + itemH, C_SEL_BG);
            } else if (itemHov) {
                guiGraphics.fill(dropX + 1, currentY, dropX + dropW - 1, currentY + itemH, 0x1AFFFFFF);
            }

            if (active) {
                guiGraphics.text(Minecraft.getInstance().font, "\u2714", dropX + 5, currentY + (itemH / 2) - 4, C_SELECTED, false);
            }

            guiGraphics.text(Minecraft.getInstance().font, key, dropX + (active ? 17 : 7), currentY + (itemH / 2) - 4, active ? C_SELECTED : (itemHov ? C_TEXT : C_TEXT_DIM), false);

            if (active) {
                int previewSize = 7;
                int previewX = dropX + dropW - 14;
                int previewY = currentY + (itemH / 2) - (previewSize / 2);
                drawRoundedRect(guiGraphics, previewX, previewY, previewSize, previewSize, getColor(key) | 0xFF000000);

                if (activeColorFilter != null && activeColorFilter.equals(key)) {
                    drawRoundedOutline(guiGraphics, previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, C_ACCENT);
                }
            }

            currentY += itemH;
        }

        if (showColorMenu) {
            int startSliderY = listY + listH + 4;
            int sliderX = dropX + 6;
            int sliderW = dropW - 12;

            guiGraphics.fill(dropX + 1, startSliderY - 3, dropX + dropW - 1, startSliderY - 2, C_SEPARATOR);

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
                    float pitch = (draggingSlider == 1)
                            ? 0.8f + (currentHue * 0.8f)
                            : 0.8f + ((currentAlpha / 255f) * 0.8f);
                    ClickGuiScreen.playGuiSound(pitch, 0.08f);
                }
            }

            for (int i = 0; i < sliderW; i++) {
                int col = java.awt.Color.HSBtoRGB(i / (float) sliderW, 1f, 1f) | 0xFF000000;
                guiGraphics.fill(sliderX + i, startSliderY, sliderX + i + 1, startSliderY + 4, col);
            }
            int thumbX1 = sliderX + (int) (currentHue * sliderW);
            guiGraphics.fill(Math.max(sliderX, Math.min(sliderX + sliderW - 2, thumbX1 - 1)), startSliderY - 1, Math.max(sliderX, Math.min(sliderX + sliderW - 1, thumbX1 + 2)), startSliderY + 5, 0xFFFFFFFF);

            int alphaSliderY = startSliderY + 12;
            guiGraphics.text(Minecraft.getInstance().font, "Opacity: " + (int) ((currentAlpha / 255f) * 100) + "%", sliderX, alphaSliderY, C_TEXT_DIM, false);
            int barY = alphaSliderY + 10;

            for (int i = 0; i < sliderW; i++) {
                float pct = i / (float) sliderW;
                int alphaVal = (int) (pct * 255);
                int gray = (alphaVal << 24) | 0xFFFFFF;
                guiGraphics.fill(sliderX + i, barY, sliderX + i + 1, barY + 4, gray);
            }
            int thumbX2 = sliderX + (int) ((currentAlpha / 255f) * sliderW);
            guiGraphics.fill(Math.max(sliderX, Math.min(sliderX + sliderW - 2, thumbX2 - 1)), barY - 1, Math.max(sliderX, Math.min(sliderX + sliderW - 1, thumbX2 + 2)), barY + 5, 0xFFFFFFFF);
        } else {
            activeColorFilter = null;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) {
            open = !open;
            activeColorFilter = null;
            ClickGuiScreen.playGuiSound(open ? 1.1f : 0.85f, 0.25f);
            return true;
        }

        if (open) {
            List<String> keys = new ArrayList<>(filters.keySet());
            int dropX = x;
            int dropW = width + 40;
            int itemH = 14;
            int listH = keys.size() * itemH;
            int dropY = y + height + 3;
            int listY = dropY + 1;

            boolean showColorMenu = activeColorFilter != null && filters.getOrDefault(activeColorFilter, false);
            if (showColorMenu) {
                int startSliderY = listY + listH + 4;
                int sliderX = dropX + 6;
                int sliderW = dropW - 12;

                if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= startSliderY - 2 && mouseY <= startSliderY + 7 && button == 0) {
                    this.draggingSlider = 1;
                    ClickGuiScreen.playGuiSound(1.0f, 0.2f);
                    return true;
                }
                if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= startSliderY + 20 && mouseY <= startSliderY + 28 && button == 0) {
                    this.draggingSlider = 2;
                    ClickGuiScreen.playGuiSound(1.0f, 0.2f);
                    return true;
                }
            }

            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= listY && mouseY <= listY + listH) {
                int idx = (int) ((mouseY - listY) / itemH);

                if (idx >= 0 && idx < keys.size()) {
                    String key = keys.get(idx);
                    if (button == 0) {
                        boolean newState = !filters.getOrDefault(key, false);
                        filters.put(key, newState);
                        if (!newState && key.equals(activeColorFilter)) activeColorFilter = null;
                        ClickGuiScreen.playGuiSound(newState ? 1.15f : 0.85f, 0.25f);
                    } else if (button == 1) {
                        if (filters.getOrDefault(key, false)) {
                            activeColorFilter = key.equals(activeColorFilter) ? null : key;
                            ClickGuiScreen.playGuiSound(activeColorFilter != null ? 1.3f : 0.9f, 0.25f);
                        }
                    }
                }
                return true;
            }

            if (mouseX < dropX || mouseX > dropX + dropW || mouseY < y + height) {
                open = false;
                activeColorFilter = null;
                ClickGuiScreen.playGuiSound(0.85f, 0.2f);
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