package com.OsamaClient.newbridge.Hacks.Dupeing;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;

/** Delays your keep-alive / pong replies so the server thinks your ping is higher. */
public class PingSpoof extends Module {

    public static PingSpoof INSTANCE;
    public double delayMs = 300;

    public PingSpoof() {
        super("PingSpoof", "Delays keep-alive and pong replies to fake a higher ping.", Category.DUPE);
        INSTANCE = this;
        settings.add(new Slider("Delay", 50, 3000, delayMs, 50, v -> { delayMs = v; if (enabled) PacketHub.pingDelayMs = (int) (double) v; })
                .withDescription("Extra milliseconds before replies are sent (keep below the server timeout)."));
    }

    @Override
    public void onEnable() { PacketHub.pingDelayMs = (int) delayMs; }

    @Override
    public void onDisable() { PacketHub.pingDelayMs = 0; }
}
