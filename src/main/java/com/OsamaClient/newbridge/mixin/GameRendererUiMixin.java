package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.UI.gui.ClickGui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the ClickGUI with our own renderer right after vanilla's GUI pass, so it sits on top of everything. */
@Mixin(GameRenderer.class)
public abstract class GameRendererUiMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V", shift = At.Shift.AFTER)
    )
    private void newbridge$drawClickGui(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        ClickGui.INSTANCE.frame();
    }
}
