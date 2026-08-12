package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

public class FastPlace extends Module {
    public static FastPlace INSTANCE;
    public double delay = 0; // 0 = Maximal schnell (kein Delay)

    private Field rightClickDelayField;

    public FastPlace() {
        super("FastPlace", "Removes placement delay for blocks", Category.MISC);
        INSTANCE = this;

        this.settings.add(new Slider("Delay", 0.0, 4.0, delay, val -> delay = val).withDescription("Sets the block placement delay in ticks."));

        try {
            for (Field field : Minecraft.class.getDeclaredFields()) {
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                }
            }
            // Mojang-Mapping Name für rightClickDelay
            rightClickDelayField = Minecraft.class.getDeclaredField("rightClickDelay");
            rightClickDelayField.setAccessible(true);
        } catch (Exception ignored) {}

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    @Override
    public void onTick(Minecraft client) {
        if (!this.enabled || client.player == null) return;

        try {
            if (rightClickDelayField != null) {
                int currentDelay = rightClickDelayField.getInt(client);
                if (currentDelay > (int) delay) {
                    rightClickDelayField.setInt(client, (int) delay);
                }
            }
        } catch (Exception e) {
            // Falls Feldname abweicht
        }
    }
}