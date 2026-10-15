package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteEditor.EmoteEditor;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteWheel;
import com.OsamaClient.newbridge.Hacks.Visual.HudOverlay;
import com.OsamaClient.newbridge.Hacks.Visual.ModuleList;
import com.OsamaClient.newbridge.Hacks.Visual.Nametags;
import com.OsamaClient.newbridge.Hacks.Visual.TeammateList;
import com.OsamaClient.newbridge.UI.gui.ClickGui;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererUiMixin {

    /**
     * HudOverlay and TeammateList are drawn BEFORE the vanilla GUI render, so the real item icons and player faces
     * that HudOverlayItemLayer extracted for them land on top of their panels. While the layout editor is open they
     * are drawn after instead (the editor screen's blur would otherwise smear them).
     */
    @Inject(
            method = "render()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"
            )
    )
    private void newbridge$drawHudBefore(CallbackInfo ci) {
        com.OsamaClient.newbridge.Hacks.Visual.HudLayout.nextFrame();
        if (!HudOverlay.editing()) {
            HudOverlay.draw();
            TeammateList.draw();
        }
    }

    @Inject(
            method = "render()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V",
                    shift = At.Shift.AFTER
            )
    )
    private void newbridge$drawClickGui(CallbackInfo ci) {
        {
            var scr = net.minecraft.client.Minecraft.getInstance().gui.screen();
            com.OsamaClient.newbridge.Utils.TextInputGate.want(scr instanceof com.OsamaClient.newbridge.UI.gui.ClickGuiScreen
                    || com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.isOpen());
        }
        ClickGui.INSTANCE.frame();
        EmoteWheel.INSTANCE.frame();
        if (com.OsamaClient.newbridge.Hacks.Client.quick.QuickMenu.INSTANCE != null) com.OsamaClient.newbridge.Hacks.Client.quick.QuickMenu.INSTANCE.frame();
        EmoteEditor.INSTANCE.frame();
        com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.frame();
        com.OsamaClient.newbridge.Hacks.Visual.minimap.Minimap.draw();
        ModuleList.draw();
        if (HudOverlay.editing()) {
            HudOverlay.draw();
            TeammateList.draw();
        }
        Nametags.draw();
        com.OsamaClient.newbridge.Hacks.Visual.DamageIndicator.draw();
        com.OsamaClient.newbridge.Hacks.Visual.SplitChat.draw();
        com.OsamaClient.newbridge.Hacks.Client.CheckMeter.draw();
        com.OsamaClient.newbridge.Hacks.Misc.Waypoints.draw();
    }
}
