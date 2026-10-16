package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.Utils.TeamUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

public class TriggerBot extends Module {

    public double cps = 10.0;
    public double range = 3.5;
    public double randomize = 2.0;
    public boolean useCooldown = false;
    public boolean ignoreFriends = true;
    public boolean ignoreTeammates = true;
    public EntityFilterPicker entityFilter;

    private long nextAttackTime = 0;
    private final Random random = new Random();

    public TriggerBot() {
        super("TriggerBot", "Attacks automatically when your crosshair is on a target.", Category.COMBAT);

        this.entityFilter = new EntityFilterPicker("Targets").withDescription("Selects which types of entities to target.");

        this.settings.add(new ToggleButton("1.9 Cooldown", useCooldown, val -> useCooldown = val).withDescription("Hits exactly when the 1.9+ attack cooldown is full (perfect timing, full damage and crits). CPS and Randomize are not used then."));
        this.settings.add(new Slider("CPS", 0.2, 20.0, cps, val -> cps = val).withDescription("Attack frequency in clicks per second."));
        this.settings.add(new Slider("Range", 1.0, 6.0, range, val -> range = val).withDescription("Maximum distance to trigger attacks."));
        this.settings.add(new Slider("Randomize", 0.0, 5.0, randomize, val -> randomize = val).withDescription("Adds randomness to attack timings to mimic human behavior."));
        this.settings.add(this.entityFilter);
    }

    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null || client.gui.screen() != null) return;

        if (useCooldown ? client.player.getAttackStrengthScale(0.5f) < 0.95f : System.currentTimeMillis() < nextAttackTime) return;

        HitResult hit = client.hitResult;

        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();

            if (entity instanceof LivingEntity target && isTargetValid(target, client)) {
                client.gameMode.attack(client.player, target);
                com.OsamaClient.newbridge.Utils.SwingUtil.swing(client.player, InteractionHand.MAIN_HAND);

                double baseDelay = 1000.0 / cps;
                double randomDelay = (random.nextDouble() - 0.5) * (randomize * 50.0);
                nextAttackTime = System.currentTimeMillis() + (long)(baseDelay + randomDelay);
            }
        }
    }

    private boolean isTargetValid(LivingEntity target, Minecraft client) {
        double dist = client.player.distanceTo(target);
        if (dist > range) return false;
        if (!target.isAlive() || target == client.player) return false;

        if (TeamUtils.ignored(target, ignoreTeammates, ignoreFriends)) {
            return false;
        }

        if (entityFilter.isFilterEnabled("Players") && target instanceof Player) return true;
        if (entityFilter.isFilterEnabled("Hostiles") && target instanceof Enemy) return true;
        if (entityFilter.isFilterEnabled("Animals") && target instanceof Animal) return true;
        if (entityFilter.isFilterEnabled("NPCs") && target instanceof Npc) return true;
        if (entityFilter.isFilterEnabled("ArmorStands") && target instanceof ArmorStand) return true;

        return false;
    }
}