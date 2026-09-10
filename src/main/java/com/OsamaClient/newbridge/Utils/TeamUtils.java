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

    public static boolean isTeammate(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || entity == null || entity == mc.player || !(entity instanceof LivingEntity living)) {
            return false;
        }

        // 1. Scoreboard-Team
        Scoreboard scoreboard = mc.level.getScoreboard();
        PlayerTeam myTeam = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        if (myTeam != null) {
            PlayerTeam theirTeam = scoreboard.getPlayersTeam(entity.getScoreboardName());
            return theirTeam != null && theirTeam == myTeam;
        }

        // 2. Fallback: Erster expliziter Farbcode im sichtbaren Namensschild
        Integer myColor = getFirstExplicitColor(mc.player.getDisplayName());
        Integer theirColor = getFirstExplicitColor(entity.getDisplayName());
        if (myColor != null && theirColor != null) {
            return myColor.equals(theirColor);
        }

        // 3. Fallback: Gefärbte Lederrüstung
        Integer myArmor = getLeatherArmorColor(mc.player);
        Integer theirArmor = getLeatherArmorColor(living);
        if (myArmor != null && theirArmor != null) {
            return myArmor.equals(theirArmor);
        }

        return false;
    }

    private static Integer getFirstExplicitColor(Component component) {
        if (component == null) return null;

        if (component.getStyle().getColor() != null) {
            return component.getStyle().getColor().getValue();
        }
        for (Component sibling : component.getSiblings()) {
            Integer c = getFirstExplicitColor(sibling);
            if (c != null) return c;
        }
        return null;
    }

    private static Integer getLeatherArmorColor(LivingEntity entity) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty()) return null;
        DyedItemColor dyed = chest.get(DataComponents.DYED_COLOR);
        return dyed != null ? dyed.rgb() : null;
    }
}