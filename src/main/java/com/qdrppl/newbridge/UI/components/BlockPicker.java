package com.qdrppl.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.CharacterEvent;
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
    private final int itemHeight = 12;
    private String searchQuery = "";

    public BlockPicker(String label) {
        this.label = label;
        this.width = 100;
        this.height = 14;
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
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(Minecraft.getInstance().font, "§lBlocks:", x, y - 12, 0xFFA000FF, false);

        guiGraphics.fill(x, y, x + width, y + height, 0x90202020);
        guiGraphics.renderOutline(x, y, width, height, 0xFFA000FF);
        guiGraphics.drawString(Minecraft.getInstance().font, label + (open ? " [^]" : " [v]"), x + 4, y + 3, 0xFFFFFFFF, true);

        if (open) {
            List<Block> blocks = getFilteredBlocks();
            int currentY = y + height + 2;
            int dropWidth = width + 40;

            // Suchfeld
            guiGraphics.fill(x, currentY, x + dropWidth, currentY + 14, 0xFF000000);
            guiGraphics.renderOutline(x, currentY, dropWidth, 14, 0xFFFFFFFF);
            String display = searchQuery.isEmpty() ? "§8Suche..." : searchQuery + "_";
            guiGraphics.drawString(Minecraft.getInstance().font, display, x + 4, currentY + 3, 0xFFFFFFFF, false);

            int listY = currentY + 16;
            guiGraphics.fill(x, listY, x + dropWidth, listY + (maxVisible * itemHeight), 0xCF080808);
            guiGraphics.renderOutline(x, listY, dropWidth, maxVisible * itemHeight, 0xFFA000FF);

            for (int i = 0; i < maxVisible; i++) {
                int index = i + scrollOffset;
                if (index >= blocks.size()) break;

                Block b = blocks.get(index);
                boolean sel = selectedBlocks.contains(b);
                int iy = listY + (i * itemHeight);

                if (sel) guiGraphics.fill(x + 1, iy, x + dropWidth - 1, iy + itemHeight, 0x60A000FF);

                String name = b.getName().getString();
                if (name.length() > 24) name = name.substring(0, 21) + "...";
                guiGraphics.drawString(Minecraft.getInstance().font, name, x + 4, iy + 2, sel ? 0xFF55FF55 : 0xFFFFFFFF, false);
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!open) return false;

        char c = (char) event.codepoint();

        if (c >= 32 && c != 127) {
            searchQuery += c;
            scrollOffset = 0;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!open) return false;

        int keyCode = event.key();

        if (keyCode == 259) {
            if (!searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                scrollOffset = 0;
            }
            return true;
        }

        if (keyCode == 256) {
            open = false;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (open && mouseX >= x && mouseX <= x + width + 40) {
            if (amount > 0 && scrollOffset > 0) {
                scrollOffset--;
            } else if (amount < 0 && scrollOffset < Math.max(0, getFilteredBlocks().size() - maxVisible)) {
                scrollOffset++;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered(mouseX, mouseY)) {
            open = !open;
            return true;
        }

        if (open) {
            int currentY = y + height + 2;
            int listY = currentY + 16;
            int dropWidth = width + 40;

            if (mouseX >= x && mouseX <= x + dropWidth && mouseY >= listY && mouseY <= listY + (maxVisible * itemHeight)) {
                int idx = (int)((mouseY - listY) / itemHeight) + scrollOffset;
                List<Block> blocks = getFilteredBlocks();
                if (idx >= 0 && idx < blocks.size()) {
                    Block b = blocks.get(idx);
                    if (selectedBlocks.contains(b)) selectedBlocks.remove(b);
                    else selectedBlocks.add(b);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }
}