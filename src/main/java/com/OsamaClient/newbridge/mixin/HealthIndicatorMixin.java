package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.Hacks.Visual.HealthIndicator;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
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

            String color;
            if (ratio >= HealthIndicator.greenThreshold) {
                color = "§a";
            } else if (ratio >= HealthIndicator.yellowThreshold) {
                color = "§e";
            } else {
                color = "§c";
            }
            Component newName = Component.literal(original.getString() + " " + color + "[" + String.format("%.1f", health) + "]");

            cir.setReturnValue(newName);
        }
    }
}