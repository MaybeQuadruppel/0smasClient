package com.qdrppl.newbridge.mixin;

import com.qdrppl.newbridge.Hacks.Combat.AutoCart;
import com.qdrppl.newbridge.Hacks.Combat.Util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BowItem.class)
public class BowItemMixin {

    @Inject(method = "releaseUsing", at = @At("HEAD"))
    private void onBowFired(ItemStack itemStack, Level level, LivingEntity entity, int remainingTime, CallbackInfoReturnable<Boolean> cir) {

        if (level == null || !level.isClientSide()) return;


        AutoCart module = AutoCart.INSTANCE;
        if (module == null || !module.enabled) return;


        if (!(entity instanceof LivingEntity livingUser)) return;


        int useTicks = itemStack.getUseDuration(livingUser) - remainingTime;
        float power = BowItem.getPowerForTime(useTicks);

        if (power < 0.1f) return;


        BlockPos landing = Util.predictLanding(livingUser, power * 3.0f);
        if (landing != null) {
            AutoCart.setLanding(landing);
            module.performActions(landing);
        }
    }
}