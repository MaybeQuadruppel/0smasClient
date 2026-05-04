//package com.qdrppl.newbridge.mixin;
//
//import com.qdrppl.newbridge.Hacks.Misc.FullBright;
//import net.minecraft.client.renderer.GameRenderer;
//import net.minecraft.world.entity.LivingEntity;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//
//@Mixin(GameRenderer.class)
//public class FullBrightMixin {
//
//    @Inject(method = "getNightVisionScale", at = @At("RETURN"), cancellable = true)
//    private static void onGetNightVisionScale(LivingEntity entity, float f, CallbackInfoReturnable<Float> cir) {
//        FullBright module = FullBright.instance;
//        if (module != null && module.enabled) {
//
//            cir.setReturnValue(1.0f);
//        }
//    }
//}