package com.qdrppl.newbridge.UI;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import com.qdrppl.newbridge.UI.components.Component;
import com.qdrppl.newbridge.UI.components.BlockPicker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class ClickGuiScreen extends Screen {
    private Module selectedModule = null;
    private final int METEOR_PURPLE = 0xFFA000FF;

    public ClickGuiScreen() {
        super(net.minecraft.network.chat.Component.literal("BridgeMod ClickGUI"));
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        guiGraphics.fill(0, 0, this.width, this.height, 0x90000000);

        if (selectedModule == null) {
            Module hoveredModule = null;
            int x = 10;
            for (Module.Category cat : Module.Category.values()) {
                List<Module> modules = ModuleManager.getModulesByCategory(cat);
                if (modules.isEmpty()) continue;

                int catWidth = 80;
                int headerHeight = 16;
                int moduleHeight = 14;
                int frameHeight = headerHeight + (modules.size() * moduleHeight) + 4;

                guiGraphics.fill(x - 2, 10, x + catWidth + 2, 10 + headerHeight, 0xFF080808);
                guiGraphics.fill(x - 2, 10 + headerHeight, x + catWidth + 2, 10 + frameHeight, 0x90101010);
                guiGraphics.renderOutline(x - 2, 10, catWidth + 4, frameHeight, METEOR_PURPLE);
                guiGraphics.drawString(this.font, cat.name(), x + 4, 10 + (headerHeight / 2) - 4, METEOR_PURPLE, true);

                int currY = 10 + headerHeight + 2;
                for (Module m : modules) {
                    if (mouseX >= x && mouseX <= x + catWidth && mouseY >= currY && mouseY <= currY + moduleHeight) {
                        guiGraphics.fill(x, currY, x + catWidth, currY + moduleHeight, 0xCF404040);
                        hoveredModule = m;
                    }

                    guiGraphics.drawString(this.font, m.name, x + 4, currY + (moduleHeight / 2) - 4, m.enabled ? 0xFF55FF55 : 0xFFFF5555, true);
                    currY += moduleHeight;
                }
                x += catWidth + 12;
            }

            if (hoveredModule != null && hoveredModule.description != null && !hoveredModule.description.isEmpty()) {
                int maxTooltipWidth = 200;

                List<net.minecraft.network.chat.FormattedText> wrappedLines = this.font.getSplitter().splitLines(
                        net.minecraft.network.chat.Component.literal(hoveredModule.description),
                        maxTooltipWidth,
                        Style.EMPTY
                );

                int tooltipWidth = 0;
                for (net.minecraft.network.chat.FormattedText line : wrappedLines) {
                    int lineWidth = this.font.width(line);
                    if (lineWidth > tooltipWidth) tooltipWidth = lineWidth;
                }

                int lineHeight = this.font.lineHeight;
                int padding = 3;
                int totalHeight = (wrappedLines.size() * lineHeight) + (padding * 2);

                int tooltipX = mouseX + 12;
                int tooltipY = mouseY - 12;

                if (tooltipX + tooltipWidth + padding * 2 > this.width) {
                    tooltipX = mouseX - tooltipWidth - 20;
                }
                if (tooltipY + totalHeight > this.height) {
                    tooltipY = mouseY - totalHeight;
                }

                guiGraphics.fill(tooltipX - padding, tooltipY - padding, tooltipX + tooltipWidth + padding, tooltipY + totalHeight - padding, 0xFF000000);
                guiGraphics.renderOutline(tooltipX - padding, tooltipY - padding, tooltipWidth + padding * 2, totalHeight, METEOR_PURPLE);
                int currentY = tooltipY;
                for (net.minecraft.network.chat.FormattedText line : wrappedLines) {
                    guiGraphics.drawString(this.font, line.getString(), tooltipX, currentY, 0xFFFFFFFF, false);
                    currentY += lineHeight;
                }
            }

        } else {
            boolean backHovered = mouseX >= 10 && mouseX <= 90 && mouseY >= 10 && mouseY <= 26;
            guiGraphics.fill(8, 8, 92, 28, backHovered ? 0xCF404040 : 0x90202020);
            guiGraphics.renderOutline(8, 8, 84, 20, METEOR_PURPLE);
            guiGraphics.drawString(this.font, "< Zurück", 12, 14, backHovered ? METEOR_PURPLE : 0xFFFFFFFF, true);
            guiGraphics.drawString(this.font, "Settings: " + selectedModule.name, 100, 14, 0xFFFFFFFF, true);

            int leftY = 35;
            int rightX = 140;

            for (Component comp : selectedModule.settings) {
                if (comp instanceof BlockPicker) {
                    comp.x = rightX;
                    comp.y = 35;
                    comp.render(guiGraphics, mouseX, mouseY);
                } else {
                    comp.x = 15;
                    comp.y = leftY;
                    comp.render(guiGraphics, mouseX, mouseY);
                    leftY += comp.height + 3;
                }
            }
        }
        super.render(guiGraphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        if (selectedModule == null) {
            int x = 10;
            int catWidth = 80;
            int headerHeight = 16;
            int moduleHeight = 14;

            for (Module.Category cat : Module.Category.values()) {
                List<Module> modules = ModuleManager.getModulesByCategory(cat);
                if (modules.isEmpty()) {
                    x += catWidth + 12;
                    continue;
                }

                int currY = 10 + headerHeight + 2;
                for (Module m : modules) {
                    if (mouseX >= x && mouseX <= x + catWidth && mouseY >= currY && mouseY <= currY + moduleHeight) {
                        if (button == 1) {
                            selectedModule = m;
                        } else if (button == 0) {
                            m.toggle();
                        }
                        return true;
                    }
                    currY += moduleHeight;
                }
                x += catWidth + 12;
            }
        } else {
            if (mouseX >= 10 && mouseX <= 90 && mouseY >= 10 && mouseY <= 26) {
                selectedModule = null;
                return true;
            }
            for (Component comp : selectedModule.settings) {
                if (comp.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (selectedModule != null) {
            for (Component comp : selectedModule.settings) {
                comp.mouseReleased(event.x(), event.y(), event.button());
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedModule != null) {
            for (Component comp : selectedModule.settings) {
                if (comp instanceof BlockPicker) {
                    if (((BlockPicker) comp).mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (selectedModule != null) {
            for (Component comp : selectedModule.settings) {
                if (comp.charTyped(event)) return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (selectedModule != null) {
            for (Component comp : selectedModule.settings) {
                if (comp.keyPressed(event)) return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}