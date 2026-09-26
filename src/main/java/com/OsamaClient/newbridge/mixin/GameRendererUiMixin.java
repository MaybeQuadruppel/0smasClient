package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.Hacks.Visual.HudOverlay;
import com.OsamaClient.newbridge.Hacks.Visual.ModuleList;
import com.OsamaClient.newbridge.Hacks.Visual.Nametags;
import com.OsamaClient.newbridge.Hacks.Visual.TeammateList;
import com.OsamaClient.newbridge.UI.gui.ClickGui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the ClickGUI, the ArrayList, the teammate list, nametags and the HUD info overlay with our own
 * renderer right after vanilla's GUI pass, so they sit on top of everything. All of these use the same
 * custom Ui/UiRenderer pipeline and therefore all have to be driven from here (they are NOT
 * HudElementRegistry entries anymore).
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererUiMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.AFTER)
    )
    private void newbridge$drawClickGui(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ClickGui.INSTANCE.frame();
        ModuleList.draw();
        HudOverlay.draw();
        TeammateList.draw();
        Nametags.draw();
    }
}