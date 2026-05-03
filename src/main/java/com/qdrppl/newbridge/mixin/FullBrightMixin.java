package com.qdrppl.newbridge.mixin;

import com.qdrppl.newbridge.Hacks.Misc.FullBright;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightTexture.class)
public class FullBrightMixin {

    @Inject(method = "getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F", at = @At("RETURN"), cancellable = true)
    private static void onGetDimensionBrightness(DimensionType dimensionType, int lightLevel, CallbackInfoReturnable<Float> cir) {
        applyFullBright(cir);
    }

    @Inject(method = "getBrightness(FI)F", at = @At("RETURN"), cancellable = true)
    private static void onGetBrightness(float ambientDarkness, int lightLevel, CallbackInfoReturnable<Float> cir) {
        applyFullBright(cir);
    }

    private static void applyFullBright(CallbackInfoReturnable<Float> cir) {
        FullBright module = FullBright.instance;
        if (module != null && module.enabled) {
            float brightness = (float) Math.max(0.0, Math.min(1.0, FullBright.amount / 16.0));
            cir.setReturnValue(Math.max(cir.getReturnValueF(), brightness));
        }
    }
}
