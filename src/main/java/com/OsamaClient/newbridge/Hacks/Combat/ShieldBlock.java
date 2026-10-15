package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.Utils.TeamUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.List;

/** Raises your offhand shield while an enemy is close. */
public class ShieldBlock extends Module {

    public static ShieldBlock INSTANCE;

    public double range = 4;
    public String targets = "Both";
    private boolean held;

    public ShieldBlock() {
        super("ShieldBlock", "Blocks with the offhand shield while an enemy is close.", Category.COMBAT);
        INSTANCE = this;
        settings.add(new Slider("Range", 2, 8, range, 0.5, v -> range = v).withDescription("Raise the shield when an enemy is this close."));
        settings.add(new ModeButton("Targets", List.of("Both", "Players", "Mobs"), 0, v -> targets = v).withDescription("Which entities trigger blocking."));
    }

    @Override
    public void onDisable() { release(Minecraft.getInstance()); }

    private void release(Minecraft mc) {
        if (held && mc.options != null) mc.options.keyUse.setDown(false);
        held = false;
    }

    private boolean threat(Minecraft mc) {
        double r2 = range * range;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !e.isAlive() || mc.player.distanceToSqr(e) > r2) continue;
            boolean player = e instanceof Player;
            boolean mob = e instanceof Enemy;
            if (player && !targets.equals("Mobs") && !TeamUtils.isTeammate(e)) return true;
            if (mob && !targets.equals("Players")) return true;
        }
        return false;
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) return;
        boolean hasShield = p.getOffhandItem().is(Items.SHIELD);
        if (hasShield && threat(mc)) {
            mc.options.keyUse.setDown(true);
            held = true;
        } else {
            release(mc);
        }
    }
}
