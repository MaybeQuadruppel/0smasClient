package com.qdrppl.newbridge.UI;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import com.qdrppl.newbridge.UI.components.Component;
import com.qdrppl.newbridge.UI.components.BlockPicker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class ClickGuiScreen extends Screen {
    private Module selectedModule = null;
    private final int METEOR_PURPLE = 0xFFA000FF;

    public ClickGuiScreen() {
        super(net.minecraft.network.chat.Component.literal("BridgeMod ClickGUI"));
    }

    // In 26.1.2 nutzt die render-Methode den GuiGraphicsExtractor und DeltaTracker
    @Override
    public void render(@NotNull GuiGraphicsExtractor extractor, int mouseX, int mouseY, DeltaTracker delta) {
        // Hintergrund-Overlay - 'fill' ist jetzt eine Instanzmethode des Extractors
        extractor.fill(0, 0, this.width, this.height, 0x90000000);

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

                // Header & Body
                extractor.fill(x - 2, 10, x + catWidth + 2, 10 + headerHeight, 0xFF080808);
                extractor.fill(x - 2, 10 + headerHeight, x + catWidth + 2, 10 + frameHeight, 0x90101010);

                // Outline Hilfsmethode nutzen
                drawOutline(extractor, x - 2, 10, catWidth + 4, frameHeight, METEOR_PURPLE);

                extractor.textRenderer().this.font, cat.name(), x + 4, 10 + (headerHeight / 2) - 4, METEOR_PURPLE, true);

                int currY = 10 + headerHeight + 2;
                for (Module m : modules) {
                    if (mouseX >= x && mouseX <= x + catWidth && mouseY >= currY && mouseY <= currY + moduleHeight) {
                        extractor.fill(x, currY, x + catWidth, currY + moduleHeight, 0xCF404040);
                        hoveredModule = m;
                    }

                    extractor.drawString(this.font, m.name, x + 4, currY + (moduleHeight / 2) - 4, m.enabled ? 0xFF55FF55 : 0xFFFF5555, true);
                    currY += moduleHeight;
                }
                x += catWidth + 12;
            }

            // Tooltip Rendering
            if (hoveredModule != null && hoveredModule.description != null && !hoveredModule.description.isEmpty()) {
                extractor.renderTooltip(this.font, net.minecraft.network.chat.Component.literal(hoveredModule.description), mouseX, mouseY);
            }

        } else {
            // Settings View
            boolean backHovered = mouseX >= 10 && mouseX <= 90 && mouseY >= 10 && mouseY <= 26;
            extractor.fill(8, 8, 92, 28, backHovered ? 0xCF404040 : 0x90202020);
            drawOutline(extractor, 8, 8, 84, 20, METEOR_PURPLE);

            extractor.drawString(this.font, "< Zurück", 12, 14, backHovered ? METEOR_PURPLE : 0xFFFFFFFF, true);
            extractor.drawString(this.font, "Settings: " + selectedModule.name, 100, 14, 0xFFFFFFFF, true);

            int leftY = 35;
            int rightX = 140;

            for (Component comp : selectedModule.settings) {
                if (comp instanceof BlockPicker) {
                    comp.x = rightX;
                    comp.y = 35;
                    comp.render(extractor, mouseX, mouseY);
                } else {
                    comp.x = 15;
                    comp.y = leftY;
                    comp.render(extractor, mouseX, mouseY);
                    leftY += comp.height + 3;
                }
            }
        }
        super.render(extractor, mouseX, mouseY, delta);
    }

    private void drawOutline(GuiGraphicsExtractor extractor, int x, int y, int w, int h, int color) {
        extractor.fill(x, y, x + w, y + 1, color); // Oben
        extractor.fill(x, y + h - 1, x + w, y + h, color); // Unten
        extractor.fill(x, y, x + 1, y + h, color); // Links
        extractor.fill(x + w - 1, y, x + w, y + h, color); // Rechts
    }

    // mouseClicked, keyPressed etc. bleiben strukturell gleich,
    // nutzen aber jetzt double/int statt Event-Objekte.
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}