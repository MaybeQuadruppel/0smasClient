package com.OsamaClient.newbridge.mixin.NoRender;

import com.OsamaClient.newbridge.Hacks.Visual.NoRender;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Hud.class)
public class NoGuiMixin {

    // 1. KÜRBIS-OVERLAY (Setzt die Transparenz/Alpha auf 0.0)
    @ModifyArgs(
            method = "extractCameraOverlays",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V",
                    ordinal = 0
            )
    )
    private void onExtractPumpkinOverlay(Args args) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.enabled && NoRender.INSTANCE.pumpkin) {
            args.set(2, 0f); // Argument Index 2 ist die float Alpha -> 0f = komplett unsichtbar
        }
    }

    // 2. PULVER-SCHNEE OVERLAY
    @ModifyArgs(
            method = "extractCameraOverlays",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Hud;extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V",
                    ordinal = 1
            )
    )
    private void onExtractPowderedSnowOverlay(Args args) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.enabled && NoRender.INSTANCE.powderedSnow) {
            args.set(2, 0f);
        }
    }

    // 3. PORTAL-EFFECT OVERLAY
    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onExtractPortalOverlay(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.enabled && NoRender.INSTANCE.portal) {
            ci.cancel();
        }
    }

    // 4. TRANK-EFFECT ICONS (Oben rechts)
    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void onExtractStatusEffectOverlay(CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.enabled && NoRender.INSTANCE.potions) {
            ci.cancel();
        }
    }

    // 5. VIGNETTE (Dunkler Bildschirmrand)
    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void onExtractVignetteOverlay(CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.enabled && NoRender.INSTANCE.vignette) {
            ci.cancel();
        }
    }

    // 6. FERNROHR (Spyglass)
    @Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onExtractSpyglassOverlay(GuiGraphicsExtractor graphics, float scale, CallbackInfo ci) {
        if (NoRender.INSTANCE != null && NoRender.INSTANCE.enabled && NoRender.INSTANCE.spyglass) {
            ci.cancel();
        }
    }
}