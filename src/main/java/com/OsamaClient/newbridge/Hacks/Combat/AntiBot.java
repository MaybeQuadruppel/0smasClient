package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AntiBot: fake players (NPCs, anticheat decoys) are ignored by KillAura, AimAssist, BowAimbot and WallHit.
 * Detection ideas from the Autism client's AntiBot (GPL-3.0): invalid username, missing tab-list entry,
 * duplicate names and impossible pitch.
 */
public class AntiBot extends Module {

    public static AntiBot INSTANCE;
    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    public boolean checkName = true;
    public boolean checkTabList = true;
    public boolean checkDuplicates = true;
    private final Set<Integer> duplicates = new HashSet<>();

    public AntiBot() {
        super("AntiBot", "Ignores fake players when targeting.", Category.COMBAT);
        INSTANCE = this;
        settings.add(new ToggleButton("Invalid Names", checkName, v -> checkName = v).withDescription("Names that cannot be real usernames."));
        settings.add(new ToggleButton("Missing Tab Entry", checkTabList, v -> checkTabList = v).withDescription("Players that are not in the tab list."));
        settings.add(new ToggleButton("Duplicate Names", checkDuplicates, v -> checkDuplicates = v).withDescription("Two players with the same name."));
    }

    @Override
    public void onTick(Minecraft mc) {
        duplicates.clear();
        if (mc.level == null || !checkDuplicates) return;
        var players = mc.level.players();
        for (int i = 0; i < players.size(); i++)
            for (int j = i + 1; j < players.size(); j++) {
                var a = players.get(i).getGameProfile();
                var b = players.get(j).getGameProfile();
                if (a.name().equals(b.name()) && !a.id().equals(b.id())) {
                    duplicates.add(players.get(i).getId());
                    duplicates.add(players.get(j).getId());
                }
            }
    }

    @Override
    public void onDisable() { duplicates.clear(); }

    /** True if the entity looks like a bot and AntiBot is on. */
    public static boolean isBot(Entity e) {
        AntiBot m = INSTANCE;
        if (m == null || !m.enabled || !(e instanceof Player pl)) return false;
        var mc = Minecraft.getInstance();
        if (pl == mc.player) return false;
        if (Math.abs(pl.getXRot()) > 90f) return true;
        if (m.checkName && !NAME.matcher(pl.getGameProfile().name()).matches()) return true;
        if (m.checkDuplicates && m.duplicates.contains(pl.getId())) return true;
        return m.checkTabList && pl.tickCount >= 40 && mc.getConnection() != null
                && mc.getConnection().getPlayerInfo(pl.getUUID()) == null;
    }
}
