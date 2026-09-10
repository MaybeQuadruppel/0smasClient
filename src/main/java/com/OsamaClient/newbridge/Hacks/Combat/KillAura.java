package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.Utils.TeamUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.Random;
import java.util.stream.StreamSupport;

public class KillAura extends Module {

    public static KillAura INSTANCE;

    // Settings
    public float range = 3.8f;
    public float cps = 12.0f;
    public boolean useCooldown = true; // Nutzt 1.9+ Waffen-Cooldown statt reinem CPS
    public boolean rotate = true;     // Dreht den Spieler zum Ziel
    public boolean ignoreTeammates = true;
    public EntityFilterPicker entityFilter;

    // Interne Variablen
    private long nextAttackTime = 0;
    private final Random random = new Random();
    public LivingEntity currentTarget = null;

    public KillAura() {
        super("KillAura", "Attacks surrounding entities automatically", Category.COMBAT);
        INSTANCE = this;

        this.entityFilter = new EntityFilterPicker("Targets").withDescription("Selects which types of entities to target.");

        this.settings.add(new Slider("Range", 1.0, 6.0, (double) range, val -> range = val.floatValue()).withDescription("Maximum distance to attack entities."));
        this.settings.add(new Slider("CPS", 1.0, 20.0, (double) cps, val -> cps = val.floatValue()).withDescription("Clicks per second for attacking."));
        this.settings.add(new ToggleButton("1.9 Cooldown", useCooldown, val -> useCooldown = val).withDescription("Waits for the 1.9+ attack cooldown before swinging."));
        this.settings.add(new ToggleButton("Rotations", rotate, val -> rotate = val).withDescription("Automatically rotates towards the target entity."));
        this.settings.add(new ToggleButton("Ignore Teammates", ignoreTeammates, val -> ignoreTeammates = val).withDescription("Prevents attacking teammates."));
        this.settings.add(this.entityFilter);
    }

    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null || !client.player.isAlive()) {
            currentTarget = null;
            return;
        }

        currentTarget = getBestTarget(client);

        if (currentTarget == null) return;

        if (rotate) {
            applyRotations(client, currentTarget);
        }

        if (canAttack(client)) {
            attackTarget(client, currentTarget);
        }
    }

    private void attackTarget(Minecraft client, LivingEntity target) {
        client.gameMode.attack(client.player, target);
        client.player.swing(InteractionHand.MAIN_HAND);

        double baseDelay = 1000.0 / cps;
        double randomDelay = (random.nextDouble() - 0.5) * 30.0;
        nextAttackTime = System.currentTimeMillis() + (long) (baseDelay + randomDelay);
    }

    private boolean canAttack(Minecraft client) {
        if (useCooldown) {
            return client.player.getAttackStrengthScale(0.5f) >= 0.9f;
        } else {
            return System.currentTimeMillis() >= nextAttackTime;
        }
    }

    public boolean isValidTarget(Entity entity, Minecraft client) {
        if (!(entity instanceof LivingEntity target) || target == client.player || !target.isAlive()) {
            return false;
        }

        if (client.player.distanceTo(target) > range) {
            return false;
        }

        if (ignoreTeammates && TeamUtils.isTeammate(target)) {
            return false;
        }

        if (entityFilter.isFilterEnabled("Players") && target instanceof Player) return true;
        if (entityFilter.isFilterEnabled("Hostiles") && target instanceof Enemy) return true;
        if (entityFilter.isFilterEnabled("Animals") && target instanceof Animal) return true;
        if (entityFilter.isFilterEnabled("NPCs") && target instanceof Npc) return true;
        if (entityFilter.isFilterEnabled("ArmorStands") && target instanceof ArmorStand) return true;

        return false;
    }

    private LivingEntity getBestTarget(Minecraft client) {
        return StreamSupport.stream(client.level.entitiesForRendering().spliterator(), false)
                .filter(e -> isValidTarget(e, client))
                .map(e -> (LivingEntity) e)
                .min(Comparator.comparingDouble(e -> client.player.distanceTo(e)))
                .orElse(null);
    }

    private void applyRotations(Minecraft client, LivingEntity target) {
        double dx = target.getX() - client.player.getX();
        double dz = target.getZ() - client.player.getZ();
        double dy = (target.getY() + (target.getEyeHeight() * 0.75)) - (client.player.getY() + client.player.getEyeHeight());

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, distanceXZ)));

        float yawDiff = Mth.wrapDegrees(targetYaw - client.player.getYRot());
        float pitchDiff = Mth.wrapDegrees(targetPitch - client.player.getXRot());

        double sensitivity = client.options.sensitivity().get();
        float f = (float) (sensitivity * 0.6F + 0.2F);
        float gcd = f * f * f * 1.2F;

        float roundedYaw = Math.round(yawDiff / gcd) * gcd;
        float roundedPitch = Math.round(pitchDiff / gcd) * gcd;

        client.player.setYRot(client.player.getYRot() + roundedYaw);
        client.player.setXRot(Mth.clamp(client.player.getXRot() + roundedPitch, -90, 90));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        currentTarget = null;
    }
}