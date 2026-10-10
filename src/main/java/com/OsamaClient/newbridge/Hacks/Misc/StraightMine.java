package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class StraightMine extends Module {

    private BlockPos currentMiningPos = null;

    public StraightMine() {
        super("Straight Mine", "Mines forward cleanly and waits for blocks to be fully removed", Category.MISC);
    }

    @Override
    public void onEnable() {
        currentMiningPos = null;
        super.onEnable();
    }

    @Override
    public void onTick(Minecraft client) {
        if (!this.enabled || client.player == null || client.level == null || client.gameMode == null) return;

        // Wenn ein Menu, Inventar oder Chat offen ist -> Abbau abbrechen
        if (client.gui.screen() != null) {
            releaseKeys(client);
            currentMiningPos = null;
            return;
        }

        // Prüfen, worauf das Fadenkreuz zeigt
        if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) client.hitResult;
            BlockPos targetPos = blockHit.getBlockPos();
            Direction side = blockHit.getDirection();

            // Solange der Zielblock NOCH NICHT Luft ist:
            if (!client.level.getBlockState(targetPos).isAir()) {

                // WICHTIG: Vorwärtslaufen STOPPEN, damit kein Server-Desync entsteht!
                client.options.keyUp.setDown(false);
                client.options.keyAttack.setDown(true);

                // Wenn es ein neuer Block ist, Abbau sauber starten
                if (currentMiningPos == null || !currentMiningPos.equals(targetPos)) {
                    currentMiningPos = targetPos;
                    client.gameMode.startDestroyBlock(targetPos, side);
                } else {
                    // Laufenden Abbau fortsetzen
                    client.gameMode.continueDestroyBlock(targetPos, side);
                }

                client.player.swing(InteractionHand.MAIN_HAND);
                return; // Tick beenden – wir laufen erst weiter, wenn der Block weg ist
            }
        }

        // Sobald der Block vor uns 100% zerstört ist (isAir == true):
        currentMiningPos = null;
        client.gameMode.stopDestroyBlock();
        client.options.keyAttack.setDown(false);

        // Erst JETZT Schritt nach vorne machen
        client.options.keyUp.setDown(true);
    }

    @Override
    public void onDisable() {
        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            releaseKeys(client);
        }
        currentMiningPos = null;
        super.onDisable();
    }

    private void releaseKeys(Minecraft client) {
        if (client.options != null) {
            client.options.keyAttack.setDown(false);
            client.options.keyUp.setDown(false);
        }
        if (client.gameMode != null) {
            client.gameMode.stopDestroyBlock();
        }
    }
}