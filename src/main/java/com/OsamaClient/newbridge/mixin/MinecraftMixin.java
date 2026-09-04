package com.OsamaClient.newbridge.mixin;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.event.TickEvent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        // Passe 'post' an den Methodennamen deines EventBusses an (z. B. post, call, dispatch)
        EntryPoint.EVENT_BUS.post(new TickEvent());
    }
}