package com.OsamaClient.newbridge.mixin.camera;

import com.OsamaClient.newbridge.Hacks.Misc.Freecam;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bugfix: In Freecam steuern WASD/Space/Shift die Kamera - der echte Spieler
 * hat die Tasten aber trotzdem mitbekommen: er lief mit, sprang und sneakte.
 * Da ClientConnectionMixin in Freecam alle Bewegungspakete verwirft, lief der
 * Spieler nur clientseitig weg und wurde beim Beenden von Freecam vom Server
 * zurückgesetzt (Rubberband). Der Versuch in LocalPlayerMixin (jumping/shift am
 * Anfang von aiStep() zurücksetzen) greift nicht, weil aiStep() danach selbst
 * input.tick() aufruft und die Tasten neu einliest.
 *
 * Deshalb wird hier direkt nach dem Einlesen der Input geleert. Die Freecam-
 * Bewegung liest die KeyMappings selbst (Freecam.renderMovement) und ist davon
 * nicht betroffen; Schwerkraft/Physik des Spielers laufen normal weiter.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("RETURN"))
    private void newbridge$blockInputInFreecam(CallbackInfo ci) {
        if (Freecam.isActive) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
        }
    }
}
