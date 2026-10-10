package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;

/** Respawns you automatically after dying. */
public class AutoRespawn extends Module {

    private double delayTicks = 10.0;
    private int counter;

    public AutoRespawn() {
        super("AutoRespawn", "Automatically clicks respawn when you die.", Category.PLAYER);
        settings.add(new Slider("Delay (ticks)", 0.0, 100.0, delayTicks, 1.0, v -> delayTicks = v)
                .withDescription("Wait time before respawning"));
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) {
            counter = 0;
            return;
        }
        if (mc.player.isDeadOrDying()) {
            if (++counter >= delayTicks) {
                mc.player.respawn();
                counter = 0;
            }
        } else {
            counter = 0;
        }
    }
}