package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteEditor.EmoteEditor;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteWheel;
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

@Mixin(GameRenderer.class)
public abstract class GameRendererUiMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/DeltaTracker;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V",
                    shift = At.Shift.AFTER
            )
    )
    private void newbridge$drawClickGui(
            DeltaTracker deltaTracker,
            boolean advanceGameTime,
            CallbackInfo ci
    ) {
        ClickGui.INSTANCE.frame();
        EmoteWheel.INSTANCE.frame();
        EmoteEditor.INSTANCE.frame();
        com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.frame();
        com.OsamaClient.newbridge.Hacks.Visual.minimap.Minimap.draw();
        ModuleList.draw();
        HudOverlay.draw();
        TeammateList.draw();
        Nametags.draw();
        com.OsamaClient.newbridge.Hacks.Misc.Waypoints.draw();
    }
}
