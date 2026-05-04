package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy; // HostileEntity -> Enemy Interface in MojMaps oft bevorzugt
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

public class TriggerBot extends Module {

    public double cps = 10.0;
    public double range = 3.5;
    public double randomize = 2.0;
    public boolean targetPlayers = true;
    public boolean targetHostile = true;

    private long nextAttackTime = 0;
    private final Random random = new Random();

    public TriggerBot() {
        super("TriggerBot ","Just A Triggerbot", Category.COMBAT);

        this.settings.add(new Slider("CPS", 0.2, 20.0, cps, val -> cps = val));
        this.settings.add(new Slider("Range", 1.0, 6.0, range, val -> range = val));
        this.settings.add(new Slider("Randomize", 0.0, 5.0, randomize, val -> randomize = val));
        this.settings.add(new ToggleButton("Target Players", targetPlayers, val -> targetPlayers = val));
        this.settings.add(new ToggleButton("Target Hostile", targetHostile, val -> targetHostile = val));
    }

    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null || client.screen != null) return;

        if (System.currentTimeMillis() < nextAttackTime) return;

        HitResult hit = client.hitResult;

        if (hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();

            if (entity instanceof LivingEntity target && isTargetValid(target, client)) {
                client.gameMode.attack(client.player, target);
                client.player.swing(InteractionHand.MAIN_HAND);

                double baseDelay = 1000.0 / cps;
                double randomDelay = (random.nextDouble() - 0.5) * (randomize * 50.0);
                nextAttackTime = System.currentTimeMillis() + (long)(baseDelay + randomDelay);
            }
        }
    }

    private boolean isTargetValid(LivingEntity target, Minecraft client) {
        double dist = client.player.distanceTo(target);
        if (dist > range) return false;

        if (!target.isAlive()) return false;

        if (target instanceof Player && !targetPlayers) return false;
        if (target instanceof Enemy && !targetHostile) return false;

        return true;
    }
}