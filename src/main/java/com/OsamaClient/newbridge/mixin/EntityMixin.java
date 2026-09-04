package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.Hacks.Visual.TrueSight;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    // isInvisibleTo überschreibt die Sichtbarkeit nur für DICH als Spieler
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void onIsInvisibleTo(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (TrueSight.instance != null && TrueSight.instance.enabled) {
            // Gibt 'false' (Nein) zurück, um das Entity halb-transparent zu rendern
            cir.setReturnValue(false);
        }
    }
}