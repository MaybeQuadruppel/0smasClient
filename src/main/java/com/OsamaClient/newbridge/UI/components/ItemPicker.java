package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ItemPicker extends Component {

    private final String label;
    public final Map<Item, Integer> selectedItems = new HashMap<>();

    private boolean open = false;
    private int scrollOffset = 0;
    private final int maxVisible = 12;
    private final int itemHeight;
    private String searchQuery = "";

    // -- Slider & Color State --
    private Item activeColorItem = null;
    private int draggingSlider = 0; // 0 = none, 1 = Hue, 2 = Alpha
    private static final int DEFAULT_COLOR = 0xFFFFD700; // Gold

    public ItemPicker(String label) {
        this.label = label;
        this.width = UISettings.scaled(100);
        this.height = UISettings.scaled(14);
        this.itemHeight = UISettings.scaled(13); // Skaliere auch die Listenelemente
    }

    public String getLabel() { return this.label; }

    private String getItemDisplayName(Item item) {
        return item.getName(item.getDefaultInstance()).getString();
    }

    private List<Item> getFilteredItems() {
        return BuiltInRegistries.ITEM.stream()
                .filter(i -> i != Items.AIR)
                .filter(i -> getItemDisplayName(i).toLowerCase().contains(searchQuery.toLowerCase()))
                .sorted((i1, i2) -> {
                    boolean s1 = selectedItems.containsKey(i1);
                    boolean s2 = selectedItems.containsKey(i2);
                    if (s1 && !s2) return -1;
                    if (!s1 && s2) return 1;
                    return getItemDisplayName(i1).compareTo(getItemDisplayName(i2));
                })
                .collect(Collectors.toList());
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        Theme.Palette p = Theme.getActive().palette;

        String title = label + (open ? " (R-Click: Color):" : ":");
        guiGraphics.text(Minecraft.getInstance().font, title, x, y - 11, p.accent, false);

        boolean hov = isHovered(mouseX, mouseY);
        int btnBg = lerpColor(p.bg, p.bgHover, hov ? 1f : 0f);
        drawRoundedRect(guiGraphics, x, y, width, height, UISettings.withPanelAlpha(btnBg));
        drawRoundedOutline(guiGraphics, x, y, width, height, open ? p.accent : p.border);

        String arrow = open ? " \u25b2" : " \u25bc";
        int selCount = selectedItems.size();
        String btnLabel = selCount > 0 ? selCount + " selected" + arrow : "Choose items" + arrow;
        guiGraphics.text(Minecraft.getInstance().font, btnLabel, x + 4, y + (height / 2) - 4, open ? p.accent : p.text, false);

        if (!open) return;

        List<Item> items = getFilteredItems();
        boolean showColorMenu = activeColorItem != null && selectedItems.containsKey(activeColorItem);

        int dropW = width + UISettings.scaled(50);
        int searchH = UISettings.scaled(14);
        int listH   = maxVisible * itemHeight;
        int colorSliderH = showColorMenu ? UISettings.scaled(36) : 0;
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
        int selBgColor = (p.accent & 0x00FFFFFF) | 0x40000000; // Semi-transparenter Akzent

        for (int i = 0; i < maxVisible; i++) {
            int index = i + scrollOffset;
            if (index >= items.size()) break;

            Item item = items.get(index);
            boolean selected = selectedItems.containsKey(item);
            int itemY = listY + i * itemHeight;
            boolean itemHov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY <= itemY + itemHeight;

            if (selected) guiGraphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemHeight, selBgColor);
            else if (itemHov) guiGraphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemHeight, 0x1AFFFFFF);

            int itemColor = selected ? selectedItems.get(item) : (itemHov ? p.text : p.textDim);

            if (selected) guiGraphics.text(Minecraft.getInstance().font, "\u2714", dropX + 5, itemY + (itemHeight / 2) - 4, itemColor, false);

            String name = getItemDisplayName(item);
            if (name.length() > 18) name = name.substring(0, 15) + "\u2026";
            guiGraphics.text(Minecraft.getInstance().font, name, dropX + (selected ? 17 : 7), itemY + (itemHeight / 2) - 4, itemColor, false);

            // Farbbox für selektierte Items
            if (selected) {
                int previewSize = UISettings.scaled(7);
                int previewX = dropX + dropW - UISettings.scaled(16);
                int previewY = itemY + (itemHeight / 2) - (previewSize / 2);
                drawRoundedRect(guiGraphics, previewX, previewY, previewSize, previewSize, itemColor | 0xFF000000);

                if (activeColorItem != null && activeColorItem.equals(item)) {
                    drawRoundedOutline(guiGraphics, previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, p.accent);
                }
            }
        }

        if (items.size() > maxVisible) {
            int sbX = dropX + dropW - 4;
            float tp = scrollOffset / (float)(items.size() - maxVisible);
            int th = Math.max(16, (int)((maxVisible / (float) items.size()) * listH));
            int ty = listY + (int)(tp * (listH - th));
            guiGraphics.fill(sbX, listY, sbX + 3, listY + listH, p.bgHover);
            guiGraphics.fill(sbX, ty, sbX + 3, ty + th, p.border);
        }

        // Color Menu Rendering
        if (showColorMenu) {
            int startSliderY = listY + listH + 4;
            int sliderX = dropX + 6;
            int sliderW = dropW - 12;

            guiGraphics.fill(dropX + 1, startSliderY - 3, dropX + dropW - 1, startSliderY - 2, p.border);

            int currentARGB = selectedItems.get(activeColorItem);
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
                    selectedItems.put(activeColorItem, finalARGB);
                }
            }

            // Hue Slider
            for (int i = 0; i < sliderW; i++) {
                int col = java.awt.Color.HSBtoRGB(i / (float) sliderW, 1f, 1f) | 0xFF000000;
                guiGraphics.fill(sliderX + i, startSliderY, sliderX + i + 1, startSliderY + 4, col);
            }
            int thumbX1 = sliderX + (int) (currentHue * sliderW);
            guiGraphics.fill(Math.max(sliderX, Math.min(sliderX + sliderW - 2, thumbX1 - 1)), startSliderY - 1, Math.max(sliderX, Math.min(sliderX + sliderW - 1, thumbX1 + 2)), startSliderY + 5, 0xFFFFFFFF);

            // Alpha Slider
            int alphaSliderY = startSliderY + 12;
            guiGraphics.text(Minecraft.getInstance().font, "Opacity: " + (int) ((currentAlpha / 255f) * 100) + "%", sliderX, alphaSliderY, p.textDim, false);
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
            activeColorItem = null;
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
        if (key == 256 || key == 257 || key == 335) { // ESC / Enter / NumPad Enter
            open = false;
            activeColorItem = null;
            Sounds.deselect();
            return true;
        }
        if (key == 259 && !searchQuery.isEmpty()) { // Backspace
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
            else if (amount < 0 && scrollOffset < Math.max(0, getFilteredItems().size() - maxVisible)) scrollOffset++;

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
            activeColorItem = null;
            if (open) Sounds.select(); else Sounds.deselect();
            return true;
        }

        if (open) {
            int dropX = x, dropW = width + UISettings.scaled(50), listY = y + height + 5 + UISettings.scaled(14);
            int listH = maxVisible * itemHeight;

            boolean showColorMenu = activeColorItem != null && selectedItems.containsKey(activeColorItem);

            // Klickprüfung für die Farbslider
            if (showColorMenu) {
                int startSliderY = listY + listH + 4;
                int sliderX = dropX + 6;
                int sliderW = dropW - 12;

                if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= startSliderY - 2 && mouseY <= startSliderY + 7 && button == 0) {
                    this.draggingSlider = 1;
                    Sounds.select();
                    return true;
                }
                if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= startSliderY + 20 && mouseY <= startSliderY + 28 && button == 0) {
                    this.draggingSlider = 2;
                    Sounds.select();
                    return true;
                }
            }

            // Klickprüfung für die Item-Liste
            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= listY && mouseY <= listY + listH) {
                int idx = (int)((mouseY - listY) / itemHeight) + scrollOffset;
                List<Item> items = getFilteredItems();

                if (idx >= 0 && idx < items.size()) {
                    Item item = items.get(idx);

                    if (button == 0) { // Linksklick = Toggle Auswahl
                        if (selectedItems.containsKey(item)) {
                            selectedItems.remove(item);
                            if (item.equals(activeColorItem)) activeColorItem = null;
                            Sounds.deselect();
                        } else {
                            selectedItems.put(item, DEFAULT_COLOR);
                            Sounds.select();
                        }
                    }
                    else if (button == 1) { // Rechtsklick = Color Slider ein/ausklappen
                        if (selectedItems.containsKey(item)) {
                            activeColorItem = item.equals(activeColorItem) ? null : item;
                            if (activeColorItem != null) Sounds.select(); else Sounds.deselect();
                        }
                    }
                }
                return true;
            }

            if (mouseX < dropX || mouseX > dropX + dropW || mouseY < y + height) {
                open = false;
                activeColorItem = null;
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