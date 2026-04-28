package com.qdrppl.newbridge.mixin;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class UIMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;

        Module listControl = ModuleManager.modules.stream()
                .filter(m -> m.name.equalsIgnoreCase("ModuleList"))
                .findFirst()
                .orElse(null);

        if (listControl == null || !listControl.enabled) return;

        int y = 4;
        int screenWidth = mc.getWindow().getGuiScaledWidth();

        java.util.List<Module> activeModules = ModuleManager.modules.stream()
                .filter(m -> m.enabled)
                .filter(m -> m != listControl) // Verstecke das Listen-Modul selbst
                .sorted((m1, m2) -> mc.font.width(m2.name) - mc.font.width(m1.name))
                .toList();

        for (Module mod : activeModules) {
            int textWidth = mc.font.width(mod.name);
            int x = screenWidth - textWidth - 4;

            guiGraphics.fill(x - 2, y - 1, screenWidth, y + mc.font.lineHeight, 0x90000000);

            guiGraphics.drawString(mc.font, mod.name, x, y, 0xFFA000FF, true);

            guiGraphics.fill(screenWidth - 1, y - 1, screenWidth, y + mc.font.lineHeight, 0xFFA000FF);

            y += mc.font.lineHeight + 1;
        }
    }
}