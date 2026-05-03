package com.qdrppl.newbridge.UI;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import com.qdrppl.newbridge.UI.components.Component;
import com.qdrppl.newbridge.UI.components.BlockPicker;
import net.minecraft.client.gui.; // Die finale Rendering-Klasse in 26.1.2
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class ClickGuiScreen extends Screen {
    private Module selectedModule = null;
    private final int METEOR_PURPLE = 0xFFA000FF;

    public ClickGuiScreen() {
        super(net.minecraft.network.chat.Component.literal("BridgeMod ClickGUI"));
    }

    @Override
    public void render(@NotNull Graphics graphics, int mouseX, int mouseY, float delta) {
        // In 26.1.2 wird der Hintergrund oft über eine dedizierte Methode gezeichnet
        graphics.fill(0, 0, this.width, this.height, 0x90000000);

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

                // Panel-Zeichnung
                graphics.fill(x - 2, 10, x + catWidth + 2, 10 + headerHeight, 0xFF080808);
                graphics.fill(x - 2, 10 + headerHeight, x + catWidth + 2, 10 + frameHeight, 0x90101010);

                drawOutline(graphics, x - 2, 10, catWidth + 4, frameHeight, METEOR_PURPLE);

                // drawString wurde zu drawCenteredText oder einfach drawText
                graphics.drawText(this.font, cat.name(), x + 4, 10 + (headerHeight / 2) - 4, METEOR_PURPLE, true);

                int currY = 10 + headerHeight + 2;
                for (Module m : modules) {
                    if (mouseX >= x && mouseX <= x + catWidth && mouseY >= currY && mouseY <= currY + moduleHeight) {
                        graphics.fill(x, currY, x + catWidth, currY + moduleHeight, 0xCF404040);
                        hoveredModule = m;
                    }

                    graphics.drawText(this.font, m.name, x + 4, currY + (moduleHeight / 2) - 4, m.enabled ? 0xFF55FF55 : 0xFFFF5555, true);
                    currY += moduleHeight;
                }
                x += catWidth + 12;
            }

            if (hoveredModule != null && hoveredModule.description != null) {
                graphics.renderTooltip(this.font, net.minecraft.network.chat.Component.literal(hoveredModule.description), mouseX, mouseY);
            }

        } else {
            // Settings Ansicht
            boolean backHovered = mouseX >= 10 && mouseX <= 90 && mouseY >= 10 && mouseY <= 26;
            graphics.fill(8, 8, 92, 28, backHovered ? 0xCF404040 : 0x90202020);
            drawOutline(graphics, 8, 8, 84, 20, METEOR_PURPLE);

            graphics.drawText(this.font, "< Zurück", 12, 14, backHovered ? METEOR_PURPLE : 0xFFFFFFFF, true);
            graphics.drawText(this.font, "Settings: " + selectedModule.name, 100, 14, 0xFFFFFFFF, true);

            for (Component comp : selectedModule.settings) {
                comp.render(graphics, mouseX, mouseY);
            }
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    private void drawOutline(Graphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // mouseClicked Logik bleibt identisch
        if (selectedModule == null) {
            // ... (Klick-Detection)
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
}