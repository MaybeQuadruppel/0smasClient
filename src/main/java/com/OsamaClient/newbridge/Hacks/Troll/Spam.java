package com.OsamaClient.newbridge.Hacks.Troll;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Spam extends Module {

    /** Freaky / NSFW Trash Talk Pool. */
    private static final List<String> FREAKY_POOL = List.of(
            "give it to me daddy",
            "its deep",
            "ur making me wet",
            "hitter daddy harder",
            "yes right there",
            "dont stop now",
            "so big...",
            "you like that dont you",
            "beg for it"
    );

    /** Toxic / Gamer Trash Talk Pool. */
    private static final List<String> TOXIC_POOL = List.of(
            "ez",
            "ez clap",
            "ezzzz",
            "ass",
            "absolute ass",
            "Get good, scrub.",
            "You're ass at this game.",
            "Bro is dogwater.",
            "Trash. Complete trash.",
            "Sit down, you're getting farmed.",
            "Free kill, thanks.",
            "Touch grass and come back when you can aim.",
            "Is your monitor even turned on?",
            "Mad? Cry about it."
    );

    private final Random random = new Random();

    // Settings (defaults)
    private String trigger = "On Hit";      // On Hit | Nearby | Both
    private String channel = "Chat";        // Chat | Whisper
    private String pool = "Freaky";         // Freaky | Toxic | All
    private double delaySec = 3.0;
    private double range = 10.0;
    private boolean randomSuffix = true;
    private String customMessages = "";

    private int cooldownTicks = 0;

    public Spam() {
        super("Spam", "Sends trash talk to players you hit or who are nearby.", Category.TROLL);

        settings.add(new ModeButton("Trigger", List.of("On Hit", "Nearby", "Both"), 0, m -> trigger = m)
                .withDescription("When messages are sent"));
        settings.add(new ModeButton("Channel", List.of("Chat", "Whisper"), 0, m -> channel = m)
                .withDescription("Public chat or private /msg"));
        settings.add(new ModeButton("Pool", List.of("Freaky", "Toxic", "All"), 0, m -> pool = m)
                .withDescription("Which message pool to use"));
        settings.add(new Slider("Delay (s)", 0.5, 10.0, delaySec, 0.5, v -> delaySec = v)
                .withDescription("Minimum time between two messages"));
        settings.add(new Slider("Range", 3.0, 30.0, range, 1.0, v -> range = v)
                .withDescription("Range for Nearby mode"));
        settings.add(new ToggleButton("Random Suffix", randomSuffix, v -> randomSuffix = v)
                .withDescription("Appends random chars (bypasses duplicate-message filters)"));
        settings.add(new TextBox("Custom (separate with |)", "", v -> customMessages = v)
                .withDescription("Your own messages, separated by |"));
    }

    @Override
    public void onEnable() {
        cooldownTicks = 0;
    }

    @Override
    public void onAttack(Minecraft mc, LivingEntity target) {
        if (!(target instanceof Player p) || p == mc.player) return;
        if (!trigger.equals("On Hit") && !trigger.equals("Both")) return;
        trySend(mc, p);
    }

    @Override
    public void onTick(Minecraft mc) {
        if (cooldownTicks > 0) cooldownTicks--;
        if (mc.player == null || mc.level == null) return;
        if (!trigger.equals("Nearby") && !trigger.equals("Both")) return;
        if (cooldownTicks > 0) return;

        Player nearest = null;
        double best = range;
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            double d = mc.player.distanceTo(p);
            if (d <= best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest != null) trySend(mc, nearest);
    }

    private void trySend(Minecraft mc, Player target) {
        if (cooldownTicks > 0 || mc.player == null || mc.player.connection == null) return;

        String msg = pickMessage();
        if (msg == null) return;
        if (randomSuffix) msg += " [" + Integer.toString(random.nextInt(36 * 36 * 36), 36) + "]";

        if (channel.equals("Whisper")) {
            mc.player.connection.sendCommand("msg " + target.getName().getString() + " " + msg);
        } else {
            mc.player.connection.sendChat(msg);
        }
        cooldownTicks = Math.max(1, (int) Math.round(delaySec * 20));
    }

    private String pickMessage() {
        List<String> all = new ArrayList<>();
        if (pool.equals("Freaky") || pool.equals("All")) all.addAll(FREAKY_POOL);
        if (pool.equals("Toxic") || pool.equals("All")) all.addAll(TOXIC_POOL);
        if (customMessages != null && !customMessages.isBlank()) {
            Arrays.stream(customMessages.split("\\|"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(all::add);
        }
        if (all.isEmpty()) return null;
        return all.get(random.nextInt(all.size()));
    }
}