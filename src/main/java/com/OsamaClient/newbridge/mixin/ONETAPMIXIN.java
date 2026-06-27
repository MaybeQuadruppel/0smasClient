package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.UI.components.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class ONETAPMIXIN {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttack(Player player, Entity target, CallbackInfo ci) {
        if (!(target instanceof LivingEntity living)) return;
        Minecraft mc = Minecraft.getInstance();

        for (var module : ModuleManager.modules) {
            if (module.enabled) {
                module.onAttack(mc, living);
            }
        }
    }
}