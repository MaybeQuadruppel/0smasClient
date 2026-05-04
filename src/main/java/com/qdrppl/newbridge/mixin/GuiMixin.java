package com.qdrppl.newbridge.mixin;

import com.qdrppl.newbridge.Hacks.Visual.ModuleList;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(Gui.class)
public class GuiMixin {

//    @Inject(method = "extractRenderState", at = @At("RETURN"))
//    public void onRenderHUD(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
//
//
//        GuiGraphicsExtractor extractor = GuiGraphicsExtractor.(GuiRenderer);
//
//        ModuleList.draw(extractor);
//    }
}