package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.Hacks.Misc.FullBright;
import net.minecraft.client.renderer.Lightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Lightmap.class)
public class FullBrightMixin {

    @ModifyArg(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 5
            ),
            index = 0
    )
    private float newbridge$overrideBrightness(float brightness) {
        FullBright module = FullBright.instance;
        if (module != null && module.enabled) {
            return (float) FullBright.amount;
        }
        return brightness;
    }
}
