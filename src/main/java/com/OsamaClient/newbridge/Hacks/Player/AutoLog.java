package com.OsamaClient.newbridge.Hacks.Player;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.TeamUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * AutoLog: trennt die Verbindung, bevor es gefährlich wird – bei wenig Leben, wenn die Totems
 * ausgehen oder wenn ein fremder Spieler in die Nähe kommt.
 */
public class AutoLog extends Module {

    public static AutoLog INSTANCE;

    public double health = 4;          // Herzen (0 = aus)
    public double totems = 0;          // ausloggen, wenn Totems <= Wert (nur wenn "Totem Check" an)
    public double playerRange = 0;     // Blöcke (0 = aus)
    public boolean totemCheck = false, ignoreTeammates = true, onlySurvival = true, disableAfter = true;

    /** Für Tests: nicht wirklich trennen, nur den Grund merken. */
    public boolean dryRun;
    public String lastReason;

    /** Zustand für die (headless testbare) Entscheidung. */
    public record State(float health, float absorption, int totems, double nearestEnemy, boolean creative) {}

    public AutoLog() {
        super("AutoLog", "Disconnects on low health, no totems left or when a player comes close", Category.PLAYER);
        INSTANCE = this;
        settings.add(new Slider("Health", 0, 10, health, 0.5, v -> health = v)
                .withDescription("Log out at or below this many hearts (absorption counts). 0 = off."));
        settings.add(new ToggleButton("Totem Check", totemCheck, v -> totemCheck = v)
                .withDescription("Also log out when you run out of totems."));
        settings.add(new Slider("Totems Left", 0, 5, totems, 1, v -> totems = v)
                .withDescription("Log out when you have this many totems or fewer (with Totem Check)."));
        settings.add(new Slider("Player Range", 0, 128, playerRange, 1, v -> playerRange = v)
                .withDescription("Log out when another player is this close. 0 = off."));
        settings.add(new ToggleButton("Ignore Teammates", ignoreTeammates, v -> ignoreTeammates = v)
                .withDescription("Teammates never trigger the player check."));
        settings.add(new ToggleButton("Only Survival", onlySurvival, v -> onlySurvival = v)
                .withDescription("Never logs out in creative/spectator."));
        settings.add(new ToggleButton("Disable After", disableAfter, v -> disableAfter = v)
                .withDescription("Turns AutoLog off after it triggered, so you can reconnect."));
    }

    /** Grund zum Ausloggen oder null. */
    public String reason(State s) {
        if (onlySurvival && s.creative()) return null;
        float hearts = (s.health() + s.absorption()) / 2f;
        if (health > 0 && hearts <= health) return String.format(Locale.ROOT, "health %.1f hearts", hearts);
        if (totemCheck && s.totems() <= (int) totems) return "totems left: " + s.totems();
        if (playerRange > 0 && s.nearestEnemy() <= playerRange) return String.format(Locale.ROOT, "player %.0f blocks away", s.nearestEnemy());
        return null;
    }

    @Override
    public void onTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.getConnection() == null || !p.isAlive()) return;
        int totemCount = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) totemCount += p.getInventory().getItem(i).getCount();
        }
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) totemCount += p.getOffhandItem().getCount();
        double nearest = Double.MAX_VALUE;
        if (playerRange > 0) {
            for (Player o : mc.level.players()) {
                if (o == p || !o.isAlive() || (ignoreTeammates && TeamUtils.isTeammate(o))) continue;
                nearest = Math.min(nearest, p.distanceTo(o));
            }
        }
        State s = new State(p.getHealth(), p.getAbsorptionAmount(), totemCount, nearest, p.isCreative() || p.isSpectator());
        String r = reason(s);
        if (r == null) return;
        lastReason = r;
        if (disableAfter) toggle();
        if (dryRun) return;
        mc.getConnection().getConnection().disconnect(Component.literal("§c[AutoLog] §f" + r));
    }
}
