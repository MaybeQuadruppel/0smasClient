package com.OsamaClient.newbridge.mixin.camera;

import com.OsamaClient.newbridge.Hacks.Misc.Freecam;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bugfix: früher wurde hier zusätzlich aiStep() (und zwischenzeitlich auch
 * travel()) komplett gecancelt und deltaMovement jeden Tick auf 0 gezwungen,
 * solange Freecam aktiv war. Das hat aber nicht nur die WASD-Bewegung
 * gestoppt, sondern JEDE Physik inklusive Schwerkraft - der echte Spieler
 * blieb dadurch z.B. mitten im Sprung in der Luft hängen, statt normal zu
 * fallen. Freecam soll nur die KAMERA entkoppeln, nicht den echten Spieler
 * einfrieren - der soll ganz normal weiter der Physik unterliegen (fallen,
 * Momentum behalten, etc.), genau wie ohne Freecam.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void onIsControlledCamera(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive) {
            cir.setReturnValue(false); // Zeigt deinen eigenen Körper an
        }
    }

    @Inject(method = "isMoving", at = @At("HEAD"), cancellable = true)
    private void onIsMoving(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.isActive) {
            cir.setReturnValue(false);
        }
    }

    /**
     * Bugfix: Space/Shift steuern in Freecam die Kamera hoch/runter - das
     * sind aber dieselben physischen Tasten, mit denen dein echter Spieler
     * normalerweise springt bzw. sneakt. Seit aiStep() nicht mehr komplett
     * gecancelt wird (siehe Klassen-Doku), bekommt der echte Spieler diese
     * Tasten also wieder ganz normal mit - er sneakt beim Runterfliegen und
     * versucht bei jedem Tick mit gehaltenem Space erneut zu springen
     * ("hüpft auf der Stelle").
     *
     * Fix hier ist bewusst NICHT wieder aiStep() komplett zu canceln (das
     * killt auch die Schwerkraft, siehe vorheriger Bug), sondern gezielt nur
     * die beiden Auslöser zu neutralisieren, BEVOR aiStep() sie verarbeitet:
     * jumping (öffentliches Feld auf LivingEntity) und der Shift-Key-Status.
     * Der Rest von aiStep() (Schwerkraft, Momentum, Kollisionen, ...) läuft
     * danach völlig normal weiter, nur eben ohne Sprung-/Sneak-Auslösung.
     */
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStepHead(CallbackInfo ci) {
        if (Freecam.isActive) {
            LocalPlayer player = (LocalPlayer) (Object) this;
            player.setJumping(false);
            player.setShiftKeyDown(false);
        }
    }
}