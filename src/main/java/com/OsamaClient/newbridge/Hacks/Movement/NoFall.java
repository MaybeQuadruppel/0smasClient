package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NoFall extends Module {

    public enum Mode {
        PACKET, CATCH
    }

    public Mode noFallMode = Mode.PACKET;
    public double fallDistTrigger = 2.5;

    public NoFall() {
        super("NoFall","Lets you take no Falldamage", Category.MOVEMENT);


        this.settings.add(new Slider("Min Distance", 1.0, 5.0, fallDistTrigger, val -> fallDistTrigger = val));
        this.settings.add(new ToggleButton("Packet Mode", true, val -> {
            if (val) {
                this.noFallMode = Mode.PACKET;
            } else {
                this.noFallMode = Mode.CATCH;
            }
        }));
    }

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null) return;


        if (client.player.fallDistance > fallDistTrigger) {

            if (noFallMode == Mode.PACKET) {
                if (client.getConnection() != null) {
                    client.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(true, client.player.horizontalCollision));
                    client.player.fallDistance = 0;
                }
            }

            else if (noFallMode == Mode.CATCH) {
                if (client.player.fallDistance > 3.0) {
                    client.player.setDeltaMovement(client.player.getDeltaMovement().x, 0.1, client.player.getDeltaMovement().z);
                    client.player.fallDistance = 0;
                }
            }
        }
    }

    @Override
    public void onDisable() {
    }
}