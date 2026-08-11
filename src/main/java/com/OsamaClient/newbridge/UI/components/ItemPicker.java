package com.OsamaClient.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ItemPicker extends Component {

    private final String label;
    public final Set<Item> selectedItems = new HashSet<>();
    private boolean open = false;
    private int scrollOffset = 0;
    private final int maxVisible = 12;
    private final int itemHeight = 13;
    private String searchQuery = "";

    private static final int C_BG         = 0xF20A0A0A;
    private static final int C_BG_HOV     = 0xFF181818;
    private static final int C_SEPARATOR  = 0xFF2C2C2C;
    private static final int C_ACCENT     = 0xFFFFFFFF;
    private static final int C_TEXT       = 0xFFEEEEEE;
    private static final int C_TEXT_DIM   = 0xFF666666;
    private static final int C_SELECTED   = 0xFFFFFFFF;
    private static final int C_SEL_BG     = 0x40FFFFFF;
    private static final int C_SEARCH_BG  = 0xFF000000;
    private static final int C_SCROLLBAR  = 0xFF2C2C2C;
    private static final int C_SCROLLTHM  = 0xFF999999;

    public ItemPicker(String label) {
        this.label = label;
        this.width  = 100;
        this.height = 14;
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
                    boolean s1 = selectedItems.contains(i1);
                    boolean s2 = selectedItems.contains(i2);
                    if (s1 && !s2) return -1;
                    if (!s1 && s2) return 1;
                    return getItemDisplayName(i1).compareTo(getItemDisplayName(i2));
                })
                .collect(Collectors.toList());
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        guiGraphics.text(Minecraft.getInstance().font, label + ":", x, y - 11, C_ACCENT, false);

        boolean hov = isHovered(mouseX, mouseY);
        int btnBg  = lerpColor(C_BG, C_BG_HOV, hov ? 1f : 0f);
        drawRoundedRect(   guiGraphics, x, y, width, height, btnBg);
        drawRoundedOutline(guiGraphics, x, y, width, height, open ? C_ACCENT : C_SEPARATOR);

        String arrow = open ? " \u25b2" : " \u25bc";
        int selCount = selectedItems.size();
        String btnLabel = selCount > 0 ? selCount + " selected" + arrow : "Choose items" + arrow;
        guiGraphics.text(Minecraft.getInstance().font, btnLabel, x + 4, y + (height / 2) - 4, open ? C_ACCENT : C_TEXT, false);

        if (!open) return;

        List<Item> items = getFilteredItems();
        int dropW = width + 50;
        int searchH = 14;
        int listH   = maxVisible * itemHeight;
        int dropH   = searchH + 2 + listH;
        int dropX   = x;
        int dropY   = y + height + 3;

        drawShadow(guiGraphics, dropX, dropY, dropW, dropH);
        drawRoundedRect(guiGraphics, dropX, dropY, dropW, dropH + 2, C_BG);
        drawRoundedOutline(guiGraphics, dropX, dropY, dropW, dropH + 2, C_ACCENT);

        drawRoundedRect(guiGraphics, dropX + 1, dropY + 1, dropW - 2, searchH, C_SEARCH_BG);
        guiGraphics.fill(dropX + 1, dropY + searchH, dropX + dropW - 1, dropY + searchH + 1, C_SEPARATOR);

        boolean showCursor = open && ((System.currentTimeMillis() / 500) % 2 == 0);
        String cursor = showCursor ? "|" : "";
        String display = searchQuery.isEmpty() ? "\u26b2 Search..." : "\u26b2 " + searchQuery + cursor;
        guiGraphics.text(Minecraft.getInstance().font, display, dropX + 5, dropY + (searchH / 2) - 4, searchQuery.isEmpty() ? C_TEXT_DIM : C_TEXT, false);

        int listY = dropY + searchH + 2;
        for (int i = 0; i < maxVisible; i++) {
            int index = i + scrollOffset;
            if (index >= items.size()) break;

            Item item = items.get(index);
            boolean selected = selectedItems.contains(item);
            int itemY = listY + i * itemHeight;
            boolean itemHov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY <= itemY + itemHeight;

            if (selected) guiGraphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemHeight, C_SEL_BG);
            else if (itemHov) guiGraphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemHeight, 0x1AFFFFFF);

            if (selected) guiGraphics.text(Minecraft.getInstance().font, "\u2714", dropX + 5, itemY + (itemHeight / 2) - 4, C_SELECTED, false);

            String name = getItemDisplayName(item);
            if (name.length() > 22) name = name.substring(0, 19) + "\u2026";
            guiGraphics.text(Minecraft.getInstance().font, name, dropX + (selected ? 17 : 7), itemY + (itemHeight / 2) - 4, selected ? C_SELECTED : (itemHov ? C_TEXT : C_TEXT_DIM), false);
        }

        if (items.size() > maxVisible) {
            int sbX = dropX + dropW - 4;
            float tp = scrollOffset / (float)(items.size() - maxVisible);
            int th = Math.max(16, (int)((maxVisible / (float) items.size()) * listH));
            int ty = listY + (int)(tp * (listH - th));
            guiGraphics.fill(sbX, listY, sbX + 3, listY + listH, C_SCROLLBAR);
            guiGraphics.fill(sbX, ty, sbX + 3, ty + th, C_SCROLLTHM);
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!open) return false;
        char c = (char) event.codepoint();
        if (c >= 32 && c != 127) { searchQuery += c; scrollOffset = 0; return true; }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!open) return false;
        if (event.key() == 259 && !searchQuery.isEmpty()) { searchQuery = searchQuery.substring(0, searchQuery.length() - 1); scrollOffset = 0; return true; }
        if (event.key() == 256) { open = false; return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (open && mouseX >= x && mouseX <= x + width + 50) {
            if (amount > 0 && scrollOffset > 0) scrollOffset--;
            else if (amount < 0 && scrollOffset < Math.max(0, getFilteredItems().size() - maxVisible)) scrollOffset++;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY) && button == 0) { open = !open; return true; }

        if (open) {
            int dropX = x, dropW = width + 50, listY = y + height + 5 + 14;
            int listH = maxVisible * itemHeight;

            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= listY && mouseY <= listY + listH) {
                int idx = (int)((mouseY - listY) / itemHeight) + scrollOffset;
                List<Item> items = getFilteredItems();

                if (idx >= 0 && idx < items.size()) {
                    Item item = items.get(idx);
                    if (button == 0) {
                        if (selectedItems.contains(item)) {
                            selectedItems.remove(item);
                        } else {
                            selectedItems.add(item);
                        }
                    }
                }
                return true;
            }

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