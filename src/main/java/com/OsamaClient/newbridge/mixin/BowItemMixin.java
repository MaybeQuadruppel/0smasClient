package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.Hacks.Combat.AutoCart;
import com.OsamaClient.newbridge.Hacks.Combat.Util.AutoCartUtil;
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
        if (!level.isClientSide() || !(entity instanceof net.minecraft.client.player.LocalPlayer)) return;
        AutoCart module = AutoCart.INSTANCE;
        if (module == null || !module.enabled) return;
        int useTicks = itemStack.getUseDuration(entity) - remainingTime;
        float power = BowItem.getPowerForTime(useTicks);
        if (power >= 0.1f) {
            BlockPos landing = AutoCartUtil.predictLanding(entity, power);
            if (landing != null) {
                AutoCart.setLanding(landing);
            }
        }
    }
}