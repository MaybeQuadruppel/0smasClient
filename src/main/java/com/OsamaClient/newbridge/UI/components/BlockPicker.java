package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.Hacks.Visual.ESP.RenderUtils;
import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BlockPicker extends Component {

    private final String label;
    public final Set<Block> selectedBlocks = new HashSet<>();
    private boolean open = false;
    private int scrollOffset = 0;
    private final int maxVisible = 10;
    private String searchQuery = "";

    private Block activeColorBlock = null;
    private int draggingSlider = 0; // 0 = none, 1 = Hue, 2 = Alpha

    private static final int BASE_BOX_HEIGHT = 16;
    private static final int BASE_TOTAL_HEIGHT = 28; // 12px Label + 16px Box

    public BlockPicker(String label) {
        super(0, 0, 100, BASE_TOTAL_HEIGHT);
        this.label = label;
        syncScaledSize(100, BASE_TOTAL_HEIGHT, 60, BASE_TOTAL_HEIGHT);
    }

    public String getLabel() { return this.label; }

    public BlockPicker withDescription(String description) {
        this.description = description;
        return this;
    }

    private List<Block> getFilteredBlocks() {
        return BuiltInRegistries.BLOCK.stream()
                .filter(b -> b != Blocks.AIR)
                .filter(b -> b.getName().getString().toLowerCase().contains(searchQuery.toLowerCase()))
                .sorted((b1, b2) -> {
                    boolean s1 = selectedBlocks.contains(b1);
                    boolean s2 = selectedBlocks.contains(b2);
                    if (s1 && !s2) return -1;
                    if (!s1 && s2) return 1;
                    return b1.getName().getString().compareTo(b2.getName().getString());
                })
                .collect(Collectors.toList());
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

        // Label oberhalb der Box
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label + ":", x, y + 1, p.accent, false);

        int btnY = getButtonY();
        int btnH = getButtonHeight();
        boolean hov = isButtonHovered(mouseX, mouseY);
        int btnBg = lerpColor(p.bg, p.bgHover, hov ? 1f : 0f);

        // Haupt-Button mit Panel-Alpha
        drawRoundedRect(guiGraphics, x, btnY, width, btnH, UISettings.withPanelAlpha(btnBg));
        drawRoundedOutline(guiGraphics, x, btnY, width, btnH, open ? p.accent : p.border);

        String arrow = open ? " \u25b2" : " \u25bc";
        int selCount = selectedBlocks.size();
        String btnLabel = selCount > 0 ? selCount + " selected" + arrow : "Choose blocks" + arrow;
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, btnLabel, x + UISettings.scaled(6), btnY + (btnH / 2) - UISettings.scaled(4), open ? p.accent : p.text, false);

        if (!open) return;

        // Dropdown Menü
        List<Block> blocks = getFilteredBlocks();
        boolean showColorMenu = activeColorBlock != null && selectedBlocks.contains(activeColorBlock);

        int itemH = UISettings.scaled(14);
        int dropW = width + UISettings.scaled(50);
        int searchH = UISettings.scaled(16);
        int listH = maxVisible * itemH;
        int colorSliderH = showColorMenu ? UISettings.scaled(38) : 0;
        int dropH = searchH + 2 + listH + colorSliderH;
        int dropX = x;
        int dropY = btnY + btnH + UISettings.scaled(3);

        drawShadow(guiGraphics, dropX, dropY, dropW, dropH);
        drawRoundedRect(guiGraphics, dropX, dropY, dropW, dropH + 2, UISettings.withPanelAlpha(p.bg));
        drawRoundedOutline(guiGraphics, dropX, dropY, dropW, dropH + 2, p.accent);

        // Suchfeld
        drawRoundedRect(guiGraphics, dropX + 2, dropY + 2, dropW - 4, searchH, UISettings.withPanelAlpha(p.bgHover));
        guiGraphics.fill(dropX + 2, dropY + searchH + 1, dropX + dropW - 2, dropY + searchH + 2, p.border);

        boolean showCursor = open && ((System.currentTimeMillis() / 500) % 2 == 0);
        String cursor = showCursor ? "|" : "";
        String display = searchQuery.isEmpty() ? "\u2315 Search..." : "\u2315 " + searchQuery + cursor;
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, display, dropX + UISettings.scaled(6), dropY + (searchH / 2) - UISettings.scaled(3), searchQuery.isEmpty() ? p.textDim : p.text, false);

        int listY = dropY + searchH + 3;
        int selBgColor = (p.accent & 0x00FFFFFF) | 0x35000000;

        for (int i = 0; i < maxVisible; i++) {
            int index = i + scrollOffset;
            if (index >= blocks.size()) break;

            Block block = blocks.get(index);
            boolean selected = selectedBlocks.contains(block);
            int itemY = listY + i * itemH;
            boolean itemHov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY <= itemY + itemH;

            if (selected) guiGraphics.fill(dropX + 2, itemY, dropX + dropW - 2, itemY + itemH, selBgColor);
            else if (itemHov) guiGraphics.fill(dropX + 2, itemY, dropX + dropW - 2, itemY + itemH, UISettings.withPanelAlpha(p.bgHover));

            int blockColor = RenderUtils.BLOCK_COLORS.getOrDefault(block, 0x6600FFFF);
            int displayColor = selected ? p.text : (itemHov ? p.text : p.textDim);

            if (selected) {
                UISettings.drawText(guiGraphics, Minecraft.getInstance().font, "\u2714", dropX + UISettings.scaled(5), itemY + (itemH / 2) - UISettings.scaled(4), p.accent, false);

                int previewSize = UISettings.scaled(7);
                int previewX = dropX + dropW - UISettings.scaled(16);
                int previewY = itemY + (itemH / 2) - (previewSize / 2);
                drawRoundedRect(guiGraphics, previewX, previewY, previewSize, previewSize, blockColor | 0xFF000000);

                if (activeColorBlock == block) {
                    drawRoundedOutline(guiGraphics, previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, p.accent);
                }
            }

            String name = block.getName().getString();
            if (name.length() > 20) name = name.substring(0, 17) + "…";
            UISettings.drawText(guiGraphics, Minecraft.getInstance().font, name, dropX + UISettings.scaled(selected ? 17 : 7), itemY + (itemH / 2) - UISettings.scaled(4), displayColor, false);
        }

        // Scrollbar
        if (blocks.size() > maxVisible) {
            int sbX = dropX + dropW - UISettings.scaled(4);
            float tp = scrollOffset / (float)(blocks.size() - maxVisible);
            int th = Math.max(UISettings.scaled(14), (int)((maxVisible / (float) blocks.size()) * listH));
            int ty = listY + (int)(tp * (listH - th));
            guiGraphics.fill(sbX, listY, sbX + UISettings.scaled(2), listY + listH, p.bgHover);
            guiGraphics.fill(sbX, ty, sbX + UISettings.scaled(2), ty + th, p.accent);
        }

        // Farbregler für gewählten Block
        if (showColorMenu) {
            int startSliderY = listY + listH + UISettings.scaled(4);
            int sliderX = dropX + UISettings.scaled(6);
            int sliderW = dropW - UISettings.scaled(12);

            guiGraphics.fill(dropX + 2, startSliderY - UISettings.scaled(3), dropX + dropW - 2, startSliderY - UISettings.scaled(2), p.border);

            int currentARGB = RenderUtils.BLOCK_COLORS.getOrDefault(activeColorBlock, 0x6600FFFF);
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
                    RenderUtils.BLOCK_COLORS.put(activeColorBlock, finalARGB);
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
            activeColorBlock = null;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!open) return false;
        char c = (char) event.codepoint();
        if (c >= 32 && c != 127) {
            searchQuery += c;
            scrollOffset = 0;
            Sounds.select();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!open) return false;
        int key = event.key();
        if (key == 256 || key == 257 || key == 335) {
            open = false;
            activeColorBlock = null;
            Sounds.deselect();
            return true;
        }
        if (key == 259 && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            scrollOffset = 0;
            Sounds.select();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (open && mouseX >= x && mouseX <= x + width + UISettings.scaled(50)) {
            int oldScroll = scrollOffset;
            if (amount > 0 && scrollOffset > 0) scrollOffset--;
            else if (amount < 0 && scrollOffset < Math.max(0, getFilteredBlocks().size() - maxVisible)) scrollOffset++;

            if (scrollOffset != oldScroll) {
                Sounds.select();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int imx = (int) mouseX, imy = (int) mouseY;

        if (isButtonHovered(imx, imy) && button == 0) {
            open = !open;
            activeColorBlock = null;
            if (open) Sounds.select(); else Sounds.deselect();
            return true;
        }

        if (open) {
            int itemH = UISettings.scaled(14);
            int dropX = x, dropW = width + UISettings.scaled(50);
            int searchH = UISettings.scaled(16);
            int dropY = getButtonY() + getButtonHeight() + UISettings.scaled(3);
            int listY = dropY + searchH + 3;
            int listH = maxVisible * itemH;

            boolean showColorMenu = activeColorBlock != null && selectedBlocks.contains(activeColorBlock);

            if (showColorMenu) {
                int startSliderY = listY + listH + UISettings.scaled(4);
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

            if (imx >= dropX && imx <= dropX + dropW && imy >= listY && imy <= listY + listH) {
                int idx = ((imy - listY) / itemH) + scrollOffset;
                List<Block> blocks = getFilteredBlocks();

                if (idx >= 0 && idx < blocks.size()) {
                    Block b = blocks.get(idx);

                    if (button == 0) {
                        if (selectedBlocks.contains(b)) {
                            selectedBlocks.remove(b);
                            if (b.equals(activeColorBlock)) activeColorBlock = null;
                            Sounds.deselect();
                        } else {
                            selectedBlocks.add(b);
                            Sounds.select();
                        }
                    } else if (button == 1) {
                        if (selectedBlocks.contains(b)) {
                            activeColorBlock = b.equals(activeColorBlock) ? null : b;
                            if (activeColorBlock != null) Sounds.select(); else Sounds.deselect();
                        }
                    }
                }
                return true;
            }

            if (imx < dropX || imx > dropX + dropW || imy < getButtonY() || imy > listY + listH + (showColorMenu ? UISettings.scaled(38) : 0)) {
                open = false;
                activeColorBlock = null;
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