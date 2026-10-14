package com.OsamaClient.newbridge.Hacks.Donut;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.TeamUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Warns when another player comes close (base guard / AFK farming), optionally runs a command. */
public class PlayerAlert extends Module {

    public static PlayerAlert INSTANCE;

    public double radius = 64.0;
    public boolean sound = true;
    public boolean ignoreTeammates = true;
    public boolean disconnect = false;

    private final Set<UUID> inside = new HashSet<>();
    private int alerts;

    public PlayerAlert() {
        super("PlayerAlert", "Alerts you when another player gets close, e.g. while AFK at your base (DonutSMP).", Category.Donut);
        INSTANCE = this;
        settings.add(new Slider("Radius", 8.0, 256.0, radius, 1.0, v -> radius = v).withDescription("Alert distance in blocks"));
        settings.add(new ToggleButton("Sound", sound, v -> sound = v).withDescription("Play a sound on alert"));
        settings.add(new ToggleButton("Ignore Teammates", ignoreTeammates, v -> ignoreTeammates = v));
        settings.add(new ToggleButton("Disconnect", disconnect, v -> disconnect = v).withDescription("Leave the server when somebody comes close"));
    }

    public int alerts() { return alerts; }

    public static boolean within(double distanceSq, double radius) { return distanceSq <= radius * radius; }

    @Override
    public void onEnable() { inside.clear(); alerts = 0; }

    @Override
    public void onTick(Minecraft mc) {
        var me = mc.player;
        if (me == null || mc.level == null) return;
        Set<UUID> now = new HashSet<>();
        for (Player other : mc.level.players()) {
            if (other == me || other.isSpectator()) continue;
            if (ignoreTeammates && TeamUtils.isTeammate(other)) continue;
            if (!within(me.distanceToSqr(other), radius)) continue;
            now.add(other.getUUID());
            if (inside.add(other.getUUID())) alert(mc, other);
        }
        inside.retainAll(now);
    }

    private void alert(Minecraft mc, Player other) {
        alerts++;
        mc.player.sendSystemMessage(Component.literal("§c[PlayerAlert] §f" + other.getName().getString()
                + " is " + Math.round(mc.player.distanceTo(other)) + " blocks away"));
        if (sound) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
        if (disconnect && mc.getConnection() != null) mc.getConnection().getConnection().disconnect(Component.literal("PlayerAlert"));
    }
}
