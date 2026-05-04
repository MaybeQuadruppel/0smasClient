package com.qdrppl.newbridge.UI.components;

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
    private final int itemHeight = 13;
    private String searchQuery = "";

    // Schwarz-Weiß Palette
    private static final int C_BG         = 0xF20A0A0A;
    private static final int C_BG_HOV     = 0xFF181818;
    private static final int C_HEADER     = 0xFF181818;
    private static final int C_SEPARATOR  = 0xFF2C2C2C;
    private static final int C_ACCENT     = 0xFFFFFFFF;
    private static final int C_TEXT       = 0xFFEEEEEE;
    private static final int C_TEXT_DIM   = 0xFF666666;
    private static final int C_SELECTED   = 0xFFFFFFFF;
    private static final int C_SEL_BG     = 0x40FFFFFF;
    private static final int C_SEARCH_BG  = 0xFF000000;
    private static final int C_SCROLLBAR  = 0xFF2C2C2C;
    private static final int C_SCROLLTHM  = 0xFF999999;

    public BlockPicker(String label) {
        this.label = label;
        this.width  = 100;
        this.height = 14;
    }

    // --- Minimaler Getter für Config.java ---
    public String getLabel() {
        return this.label;
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

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        guiGraphics.text(Minecraft.getInstance().font, label + ":", x, y - 11, C_ACCENT, false);

        boolean hov = isHovered(mouseX, mouseY);
        int btnBg  = lerpColor(C_BG, C_BG_HOV, hov ? 1f : 0f);
        drawRoundedRect(   guiGraphics, x, y, width, height, btnBg);
        drawRoundedOutline(guiGraphics, x, y, width, height, open ? C_ACCENT : C_SEPARATOR);

        String arrow = open ? " \u25b2" : " \u25bc";
        int selCount = selectedBlocks.size();
        String btnLabel = selCount > 0 ? selCount + " selected" + arrow : "Choose blocks" + arrow;
        guiGraphics.text(Minecraft.getInstance().font, btnLabel, x + 4, y + (height / 2) - 4, open ? C_ACCENT : C_TEXT, false);

        if (!open) return;

        List<Block> blocks   = getFilteredBlocks();
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

        String cursor  = (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "";
        String display = searchQuery.isEmpty() ? "\u26b2 Search..." : "\u26b2 " + searchQuery + cursor;
        guiGraphics.text(Minecraft.getInstance().font, display, dropX + 5, dropY + (searchH / 2) - 4, searchQuery.isEmpty() ? C_TEXT_DIM : C_TEXT, false);

        int listY = dropY + searchH + 2;
        for (int i = 0; i < maxVisible; i++) {
            int index = i + scrollOffset;
            if (index >= blocks.size()) break;

            Block block = blocks.get(index);
            boolean selected = selectedBlocks.contains(block);
            int itemY = listY + i * itemHeight;
            boolean itemHov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY <= itemY + itemHeight;

            if (selected) guiGraphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemHeight, C_SEL_BG);
            else if (itemHov) guiGraphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemHeight, 0x1AFFFFFF);

            if (selected) guiGraphics.text(Minecraft.getInstance().font, "\u2714", dropX + 5, itemY + (itemHeight / 2) - 4, C_SELECTED, false);

            String name = block.getName().getString();
            if (name.length() > 22) name = name.substring(0, 19) + "\u2026";
            guiGraphics.text(Minecraft.getInstance().font, name, dropX + (selected ? 17 : 7), itemY + (itemHeight / 2) - 4, selected ? C_SELECTED : (itemHov ? C_TEXT : C_TEXT_DIM), false);
        }

        if (blocks.size() > maxVisible) {
            int sbX = dropX + dropW - 4;
            float tp = scrollOffset / (float)(blocks.size() - maxVisible);
            int th = Math.max(16, (int)((maxVisible / (float) blocks.size()) * listH));
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
            else if (amount < 0 && scrollOffset < Math.max(0, getFilteredBlocks().size() - maxVisible)) scrollOffset++;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) { open = !open; return true; }
        if (open) {
            int dropX = x, dropW = width + 50, listY = y + height + 5 + 14;
            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= listY && mouseY <= listY + maxVisible * itemHeight) {
                int idx = (int)((mouseY - listY) / itemHeight) + scrollOffset;
                List<Block> blocks = getFilteredBlocks();
                if (idx >= 0 && idx < blocks.size()) {
                    Block b = blocks.get(idx);
                    if (selectedBlocks.contains(b)) selectedBlocks.remove(b);
                    else selectedBlocks.add(b);
                }
                return true;
            }
            if (mouseX < dropX || mouseX > dropX + dropW || mouseY < y + height) open = false;
        }
        return false;
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
}