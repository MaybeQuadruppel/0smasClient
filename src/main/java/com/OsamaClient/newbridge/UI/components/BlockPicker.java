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
    private final int maxVisible = 12;
    private final int itemHeight;
    private String searchQuery = "";

    private Block activeColorBlock = null;
    private int draggingSlider = 0; // 0 = none, 1 = Hue, 2 = Alpha

    public BlockPicker(String label) {
        this.label = label;
        this.width  = UISettings.scaled(100);
        this.height = UISettings.scaled(14);
        this.itemHeight = UISettings.scaled(13);
    }

    public String getLabel() { return this.label; }

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

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        Theme.Palette p = Theme.getActive().palette;

        guiGraphics.text(Minecraft.getInstance().font, label + ":", x, y - 11, p.accent, false);

        boolean hov = isHovered(mouseX, mouseY);
        int btnBg  = lerpColor(p.bg, p.bgHover, hov ? 1f : 0f);
        drawRoundedRect(guiGraphics, x, y, width, height, UISettings.withPanelAlpha(btnBg));
        drawRoundedOutline(guiGraphics, x, y, width, height, open ? p.accent : p.border);

        String arrow = open ? " \u25b2" : " \u25bc";
        int selCount = selectedBlocks.size();
        String btnLabel = selCount > 0 ? selCount + " selected" + arrow : "Choose blocks" + arrow;
        guiGraphics.text(Minecraft.getInstance().font, btnLabel, x + 4, y + (height / 2) - 4, open ? p.accent : p.text, false);

        if (!open) return;

        List<Block> blocks   = getFilteredBlocks();
        int dropW = width + UISettings.scaled(50);
        int searchH = UISettings.scaled(14);
        int listH   = maxVisible * itemHeight;

        int colorSliderH = (activeColorBlock != null) ? UISettings.scaled(36) : 0;
        int dropH   = searchH + 2 + listH + colorSliderH;
        int dropX   = x;
        int dropY   = y + height + 3;

        drawShadow(guiGraphics, dropX, dropY, dropW, dropH);
        drawRoundedRect(guiGraphics, dropX, dropY, dropW, dropH + 2, UISettings.withPanelAlpha(p.bg));
        drawRoundedOutline(guiGraphics, dropX, dropY, dropW, dropH + 2, p.accent);

        drawRoundedRect(guiGraphics, dropX + 1, dropY + 1, dropW - 2, searchH, p.bgHover);
        guiGraphics.fill(dropX + 1, dropY + searchH, dropX + dropW - 1, dropY + searchH + 1, p.border);

        boolean showCursor = open && ((System.currentTimeMillis() / 500) % 2 == 0);
        String cursor = showCursor ? "|" : "";
        String display = searchQuery.isEmpty() ? "\u26b2 Search..." : "\u26b2 " + searchQuery + cursor;
        guiGraphics.text(Minecraft.getInstance().font, display, dropX + 5, dropY + (searchH / 2) - 4, searchQuery.isEmpty() ? p.textDim : p.text, false);

        int listY = dropY + searchH + 2;
        int selBgColor = (p.accent & 0x00FFFFFF) | 0x40000000;

        for (int i = 0; i < maxVisible; i++) {
            int index = i + scrollOffset;
            if (index >= blocks.size()) break;

            Block block = blocks.get(index);
            boolean selected = selectedBlocks.contains(block);
            int itemY = listY + i * itemHeight;
            boolean itemHov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY <= itemY + itemHeight;

            if (selected) guiGraphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemHeight, selBgColor);
            else if (itemHov) guiGraphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemHeight, 0x1AFFFFFF);

            int blockColor = RenderUtils.BLOCK_COLORS.getOrDefault(block, 0x6600FFFF);
            int displayColor = selected ? (blockColor | 0xFF000000) : (itemHov ? p.text : p.textDim);

            if (selected) {
                guiGraphics.text(Minecraft.getInstance().font, "\u2714", dropX + 5, itemY + (itemHeight / 2) - 4, displayColor, false);

                int previewSize = UISettings.scaled(7);
                int previewX = dropX + dropW - UISettings.scaled(14);
                int previewY = itemY + (itemHeight / 2) - (previewSize / 2);
                drawRoundedRect(guiGraphics, previewX, previewY, previewSize, previewSize, blockColor | 0xFF000000);

                if (activeColorBlock == block) {
                    drawRoundedOutline(guiGraphics, previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, p.accent);
                }
            }

            String name = block.getName().getString();
            if (name.length() > 22) name = name.substring(0, 19) + "\u2026";
            guiGraphics.text(Minecraft.getInstance().font, name, dropX + (selected ? 17 : 7), itemY + (itemHeight / 2) - 4, displayColor, false);
        }

        if (blocks.size() > maxVisible) {
            int sbX = dropX + dropW - 4;
            float tp = scrollOffset / (float)(blocks.size() - maxVisible);
            int th = Math.max(16, (int)((maxVisible / (float) blocks.size()) * listH));
            int ty = listY + (int)(tp * (listH - th));
            guiGraphics.fill(sbX, listY, sbX + 3, listY + listH, p.bgHover);
            guiGraphics.fill(sbX, ty, sbX + 3, ty + th, p.border);
        }

        if (activeColorBlock != null && selectedBlocks.contains(activeColorBlock)) {
            int startSliderY = listY + listH + UISettings.scaled(4);
            int sliderX = dropX + UISettings.scaled(6);
            int sliderW = dropW - UISettings.scaled(12);

            guiGraphics.fill(dropX + 1, startSliderY - 3, dropX + dropW - 1, startSliderY - 2, p.border);

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

            for (int i = 0; i < sliderW; i++) {
                int col = java.awt.Color.HSBtoRGB(i / (float) sliderW, 1f, 1f) | 0xFF000000;
                guiGraphics.fill(sliderX + i, startSliderY, sliderX + i + 1, startSliderY + 4, col);
            }
            int thumbX1 = sliderX + (int) (currentHue * sliderW);
            guiGraphics.fill(Math.max(sliderX, Math.min(sliderX + sliderW - 2, thumbX1 - 1)), startSliderY - 1, Math.max(sliderX, Math.min(sliderX + sliderW - 1, thumbX1 + 2)), startSliderY + 5, p.text);

            int alphaSliderY = startSliderY + UISettings.scaled(12);
            guiGraphics.text(Minecraft.getInstance().font, "Opacity: " + (int)((currentAlpha / 255f) * 100) + "%", sliderX, alphaSliderY, p.textDim, false);
            int barY = alphaSliderY + UISettings.scaled(10);

            for (int i = 0; i < sliderW; i++) {
                float pct = i / (float) sliderW;
                int alphaVal = (int)(pct * 255);
                int gray = (alphaVal << 24) | 0xFFFFFF;
                guiGraphics.fill(sliderX + i, barY, sliderX + i + 1, barY + 4, gray);
            }
            int thumbX2 = sliderX + (int) ((currentAlpha / 255f) * sliderW);
            guiGraphics.fill(Math.max(sliderX, Math.min(sliderX + sliderW - 2, thumbX2 - 1)), barY - 1, Math.max(sliderX, Math.min(sliderX + sliderW - 1, thumbX2 + 2)), barY + 5, p.text);
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
        if (isHovered(mouseX, mouseY) && button == 0) {
            open = !open;
            activeColorBlock = null;
            if (open) Sounds.select(); else Sounds.deselect();
            return true;
        }

        if (open) {
            int dropX = x, dropW = width + UISettings.scaled(50), listY = y + height + 5 + UISettings.scaled(14);
            int listH = maxVisible * itemHeight;

            if (activeColorBlock != null && selectedBlocks.contains(activeColorBlock)) {
                int startSliderY = listY + listH + UISettings.scaled(4);
                if (mouseX >= dropX + UISettings.scaled(6) && mouseX <= dropX + dropW - UISettings.scaled(6) && mouseY >= startSliderY - 2 && mouseY <= startSliderY + 7 && button == 0) {
                    this.draggingSlider = 1;
                    Sounds.select();
                    return true;
                }
                if (mouseX >= dropX + UISettings.scaled(6) && mouseX <= dropX + dropW - UISettings.scaled(6) && mouseY >= startSliderY + UISettings.scaled(20) && mouseY <= startSliderY + UISettings.scaled(28) && button == 0) {
                    this.draggingSlider = 2;
                    Sounds.select();
                    return true;
                }
            }

            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= listY && mouseY <= listY + listH) {
                int idx = (int)((mouseY - listY) / itemHeight) + scrollOffset;
                List<Block> blocks = getFilteredBlocks();

                if (idx >= 0 && idx < blocks.size()) {
                    Block b = blocks.get(idx);
                    if (button == 0) {
                        if (selectedBlocks.contains(b)) {
                            selectedBlocks.remove(b);
                            if (activeColorBlock == b) activeColorBlock = null;
                            Sounds.deselect();
                        } else {
                            selectedBlocks.add(b);
                            Sounds.select();
                        }
                    } else if (button == 1) {
                        if (selectedBlocks.contains(b)) {
                            activeColorBlock = (activeColorBlock == b) ? null : b;
                            if (activeColorBlock != null) Sounds.select(); else Sounds.deselect();
                        }
                    }
                }
                return true;
            }

            if (mouseX < dropX || mouseX > dropX + dropW || mouseY < y + height) {
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