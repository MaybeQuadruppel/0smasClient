package com.OsamaClient.newbridge.Hacks.Troll;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Random;

/** Makes your character spin / jitter so other players see you derping around. */
public class Derp extends Module {

    private String mode = "Spin";       // Spin | Jitter | Random
    private String pitchMode = "Wobble"; // Keep | Down | Up | Wobble
    private double speed = 30.0;

    private final Random random = new Random();
    private float yaw;
    private int tick;

    public Derp() {
        super("Derp", "Spins and jitters your head so others see you derping.", Category.TROLL);

        settings.add(new ModeButton("Mode", List.of("Spin", "Jitter", "Random"), 0, m -> mode = m)
                .withDescription("How your yaw moves"));
        settings.add(new ModeButton("Pitch", List.of("Keep", "Down", "Up", "Wobble"), 3, m -> pitchMode = m)
                .withDescription("How your pitch moves"));
        settings.add(new Slider("Speed", 5.0, 90.0, speed, 5.0, v -> speed = v)
                .withDescription("Degrees per tick"));
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) yaw = mc.player.getYRot();
        tick = 0;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        tick++;

        switch (mode) {
            case "Spin" -> yaw += (float) speed;
            case "Jitter" -> yaw += (tick % 2 == 0 ? 1 : -1) * (float) speed;
            default -> yaw = random.nextFloat() * 360f - 180f;
        }

        float pitch = switch (pitchMode) {
            case "Down" -> 90f;
            case "Up" -> -90f;
            case "Wobble" -> (float) (Math.sin(tick * 0.3) * 90.0);
            default -> mc.player.getXRot();
        };

        TrollUtil.rotate(mc, yaw, pitch, 1f);
    }
}