package com.OsamaClient.newbridge.Utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

public class TeamUtils {

    private static final int WHITE = 0xFFFFFF, GRAY = 0xAAAAAA, DARK_GRAY = 0x555555;

    /** Friend (Friends module) - own colour in the ESP / minimap / lists, separate from teammates. */
    public static boolean isFriend(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity == null || entity == mc.player || !(entity instanceof LivingEntity)) return false;
        return com.OsamaClient.newbridge.Hacks.Client.Friends.isFriend(entity);
    }

    /** Per-module skip: teammates and friends are switched separately ("Ignore Teammates" / "Ignore Friends"). */
    public static boolean ignored(Entity entity, boolean ignoreTeam, boolean ignoreFriends) {
        if (com.OsamaClient.newbridge.Hacks.Client.Targets.active()) {
            var t = com.OsamaClient.newbridge.Hacks.Client.Targets.INSTANCE;
            ignoreTeam = t.ignoreTeammates;
            ignoreFriends = t.ignoreFriends;
        }
        return (ignoreTeam && isTeamMember(entity)) || (ignoreFriends && isFriend(entity));
    }

    /** Skip target for combat modules: a real teammate, or a friend while Friends > Count As Teammate is on. */
    public static boolean isTeammate(Entity entity) {
        return ignored(entity, true, true);
    }

    /** Real team mate only (scoreboard team, name colour, armor colour) - friends are not included. */
    public static boolean isTeamMember(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || entity == null || entity == mc.player || !(entity instanceof LivingEntity living)) {
            return false;
        }

        // 1. Scoreboard-Teams (Hypixel Bedwars/Skywars/... nutzen pro Team ein eigenes Scoreboard-Team)
        Scoreboard scoreboard = mc.level.getScoreboard();
        PlayerTeam myTeam = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        PlayerTeam theirTeam = scoreboard.getPlayersTeam(entity.getScoreboardName());
        Boolean byTeam = compareTeams(myTeam, theirTeam);
        if (byTeam != null) return byTeam;

        // 2. Farbe im sichtbaren Namen - nur wenn beide eine Farbe haben, die keine Standardfarbe ist
        //    (Ränge in Lobbys sind keine Teams; Weiß/Grau zählt deshalb nicht).
        Integer myColor = teamColorOf(mc.player.getDisplayName());
        Integer theirColor = teamColorOf(entity.getDisplayName());
        if (myColor != null && theirColor != null && myTeam == null && theirTeam == null) {
            return myColor.equals(theirColor);
        }

        // 2b. Server mit eigenen Nametags: der Entity-Name ist ueberschrieben, aber Tab-Liste und
        //     PlayerInfo-Team tragen oft weiterhin die Teamfarbe.
        if (entity instanceof net.minecraft.world.entity.player.Player && mc.getConnection() != null) {
            var conn = mc.getConnection();
            var myInfo = conn.getPlayerInfo(mc.player.getUUID());
            var theirInfo = conn.getPlayerInfo(entity.getUUID());
            if (myInfo != null && theirInfo != null) {
                Boolean byInfoTeam = compareTeams(myInfo.getTeam(), theirInfo.getTeam());
                if (byInfoTeam != null) return byInfoTeam;
                Integer myTab = teamColorOf(myInfo.getTabListDisplayName());
                Integer theirTab = teamColorOf(theirInfo.getTabListDisplayName());
                if (myTab != null && theirTab != null) return myTab.equals(theirTab);
            }
        }

        // 3. Gefärbte Lederrüstung (Bedwars: Helm + Brustplatte bleiben in Teamfarbe, Hose/Schuhe werden getauscht)
        Integer myArmor = getLeatherArmorColor(mc.player);
        Integer theirArmor = getLeatherArmorColor(living);
        if (myArmor != null && theirArmor != null) {
            return myArmor.equals(theirArmor);
        }

        return false;
    }

    /**
     * Vergleicht zwei Scoreboard-Teams. true = gleiches Team, false = sicher verschieden,
     * null = keine Aussage möglich (kein Team / keine unterscheidbaren Merkmale).
     */
    public static Boolean compareTeams(PlayerTeam mine, PlayerTeam theirs) {
        if (mine == null || theirs == null) return null;
        if (mine == theirs || mine.getName().equals(theirs.getName())) return true;
        // gesetzte Teamfarbe: gleiche Farbe = gleiches Team (kein Team-Objekt-Vergleich von Optionals!)
        var c1 = mine.getColor();
        var c2 = theirs.getColor();
        if (c1.isPresent() && c2.isPresent()) return c1.get() == c2.get();
        // Hypixel: Farbe steckt im Team-Prefix/-Suffix ("§cR ", "§9B ")
        Integer p1 = teamColorOf(mine.getPlayerPrefix()), p2 = teamColorOf(theirs.getPlayerPrefix());
        if (p1 == null) p1 = teamColorOf(mine.getPlayerSuffix());
        if (p2 == null) p2 = teamColorOf(theirs.getPlayerSuffix());
        if (p1 != null && p2 != null) return p1.equals(p2);
        // Teams mit unterschiedlichem Namen und ohne Farbhinweis: verschiedene Teams (Hypixel-Scoreboard-Teams sind eindeutig)
        return false;
    }

    /** Erste "echte" Farbe einer Komponente (Weiß/Grau/Dunkelgrau werden ignoriert), sonst null. */
    public static Integer teamColorOf(Component component) {
        Integer c = getFirstExplicitColor(component);
        if (c == null) return null;
        int rgb = c & 0xFFFFFF;
        if (rgb == WHITE || rgb == GRAY || rgb == DARK_GRAY) return null;
        return rgb;
    }

    private static Integer getFirstExplicitColor(Component component) {
        if (component == null) return null;

        if (component.getStyle().getColor() != null) {
            int v = component.getStyle().getColor().getValue() & 0xFFFFFF;
            // ein weißer/grauer Abschnitt (z. B. Leerzeichen) darf eine spätere Teamfarbe nicht verdecken
            if (v != WHITE && v != GRAY && v != DARK_GRAY) return v;
        }
        for (Component sibling : component.getSiblings()) {
            Integer c = getFirstExplicitColor(sibling);
            if (c != null) return c;
        }
        return null;
    }

    private static Integer getLeatherArmorColor(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            ItemStack piece = entity.getItemBySlot(slot);
            if (piece.isEmpty()) continue;
            DyedItemColor dyed = piece.get(DataComponents.DYED_COLOR);
            if (dyed != null) return dyed.rgb();
        }
        return null;
    }
}
