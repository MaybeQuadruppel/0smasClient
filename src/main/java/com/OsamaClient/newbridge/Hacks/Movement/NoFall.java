package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.MaceItem;

import java.util.List;

public class NoFall extends Module {

    public String noFallMode = "Packet";
    public double fallDistTrigger = 2.5;
    public boolean pauseOnMace = true;

    public NoFall() {
        super("NoFall", "Attempts to prevent you from taking fall damage.", Category.MOVEMENT);

        List<String> modes = List.of("Packet", "Catch");
        this.settings.add(new ModeButton("Mode", modes, modes.indexOf(noFallMode), val -> noFallMode = val)
                .withDescription("The way you are saved from fall damage."));

        this.settings.add(new Slider("Min Distance", 1.0, 5.0, fallDistTrigger, val -> fallDistTrigger = val)
                .withDescription("Sets the minimum fall distance required to trigger NoFall protection."));

        this.settings.add(new ToggleButton("Pause on Mace", pauseOnMace, val -> pauseOnMace = val)
                .withDescription("Pauses NoFall when using a mace to allow smash attacks."));
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null) return;

        if (pauseOnMace && client.player.getMainHandItem().getItem() instanceof MaceItem) {
            return;
        }

        if (client.player.fallDistance >= fallDistTrigger) {

            if (noFallMode.equals("Packet")) {
                if (client.getConnection() != null) {


                    client.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                            client.player.getX(),
                            client.player.getY() + 0.1,
                            client.player.getZ(),
                            false,
                            client.player.horizontalCollision
                    ));

                    client.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                            client.player.getX(),
                            client.player.getY(),
                            client.player.getZ(),
                            true,
                            client.player.horizontalCollision
                    ));

                    // Falldistanz im Client zurücksetzen
                    client.player.fallDistance = 0.0f;
                }

            } else if (noFallMode.equals("Catch")) {
                client.player.setDeltaMovement(
                        client.player.getDeltaMovement().x,
                        0.1,
                        client.player.getDeltaMovement().z
                );
                client.player.fallDistance = 0.0f;
            }
        }
    }

    @Override
    public void onDisable() {
    }
}