package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.Hacks.Visual.HealthIndicator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class HealthIndicatorMixin {

    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void onGetNameTag(net.minecraft.world.entity.Entity entity, CallbackInfoReturnable<Component> cir) {
        HealthIndicator module = HealthIndicator.instance;
        if (module == null || !module.enabled) return;

        if (entity instanceof LivingEntity living) {
            Component original = cir.getReturnValue();
            if (original == null) return;

            float health = living.getHealth();
            float maxHealth = living.getMaxHealth();
            float ratio = health / maxHealth;

            ChatFormatting color;
            if (ratio >= HealthIndicator.greenThreshold) {
                color = ChatFormatting.GREEN;
            } else if (ratio >= HealthIndicator.yellowThreshold) {
                color = ChatFormatting.YELLOW;
            } else {
                color = ChatFormatting.RED;
            }

            Component healthComponent = Component.literal(" [" + String.format("%.1f", health) + "]")
                    .withStyle(color);

            MutableComponent newName = Component.empty()
                    .append(original)
                    .append(healthComponent);

            cir.setReturnValue(newName);
        }
    }
}