package com.OsamaClient.newbridge.mixin.combat;

import com.OsamaClient.newbridge.Hacks.Combat.WallHit;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla berechnet mc.hitResult in Minecraft.tick() per pick() NACH dem Fabric-START_CLIENT_TICK
 * und direkt VOR handleKeybinds() neu. Ein im Tick-Event gesetztes hitResult wird dadurch
 * überschrieben. Hier – nach pick(), vor dem Angriffs-Klick – ist der richtige Zeitpunkt.
 */
@Mixin(Minecraft.class)
public class MinecraftHandleKeybindsMixin {

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void newbridge$redirectHitResult(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        if (ModuleManager.getModuleByName("WallHit") instanceof WallHit wallHit && wallHit.enabled) {
            wallHit.redirect(mc);
        }
    }
}
