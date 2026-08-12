package com.OsamaClient.newbridge.mixin.NoRender;

import com.OsamaClient.newbridge.Hacks.Visual.NoRender;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class RenderEffectLocalPlayerMixin {

    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void onHasEffect(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {

        if ((Object) this instanceof LocalPlayer) {
            if (NoRender.INSTANCE != null && NoRender.INSTANCE.enabled) {

                if (NoRender.INSTANCE.blindness && effect.is(MobEffects.BLINDNESS)) {
                    cir.setReturnValue(false);
                }
                if (NoRender.INSTANCE.darkness && effect.is(MobEffects.DARKNESS)) {
                    cir.setReturnValue(false);
                }
                if (NoRender.INSTANCE.nausea && effect.is(MobEffects.NAUSEA)) {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}