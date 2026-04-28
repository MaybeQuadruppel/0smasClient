package com.qdrppl.newbridge.UI;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

public class ModuleList extends Module {
    public static ModuleList INSTANCE;

    public ModuleList() {
        super("ArrayList", "Shows all active Modules in the Game as a List", Category.MISC);
        this.enabled = true;
        INSTANCE = this;
    }

    public static void draw(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        int yOffset = 4;
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        List<Module> activeModules = ModuleManager.modules.stream()
                .filter(m -> m.enabled)
                .filter(m -> m != INSTANCE)
                .sorted((m1, m2) -> mc.font.width(m2.name) - mc.font.width(m1.name))
                .toList();

        for (Module mod : activeModules) {
            int textWidth = mc.font.width(mod.name);
            int x = screenWidth - textWidth - 4;

            guiGraphics.fill(x - 2, yOffset - 1, screenWidth, yOffset + mc.font.lineHeight, 0x90000000);
            guiGraphics.drawString(mc.font, mod.name, x, yOffset, 0xFFA000FF, true);
            guiGraphics.fill(screenWidth - 1, yOffset - 1, screenWidth, yOffset + mc.font.lineHeight, 0xFFA000FF);

            yOffset += mc.font.lineHeight + 1;
        }
    }
}