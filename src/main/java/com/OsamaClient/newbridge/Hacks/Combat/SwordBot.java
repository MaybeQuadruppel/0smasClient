package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.Hacks.Client.LegitMode;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * ComboBot: a melee bot that walks up to a target, keeps it in combo range, aims smoothly, strafes around it, times
 * critical hits from jumps, resets sprint after every hit (W-tap / S-tap) for extra knockback, picks the best weapon,
 * breaks shields with an axe and backs off when your own health gets low.
 * It drives the normal movement keys, so it behaves like a player holding them.
 */
public class SwordBot extends Module {

    public static SwordBot INSTANCE;

    // ── settings
    public EntityFilterPicker targets;
    public String priority = "Closest", critMode = "Jump", resetMode = "W-Tap", strafeMode = "Circle";
    public double lockRange = 8.0, loseRange = 12.0, attackRange = 3.0, comboDistance = 2.6;
    public double cooldown = 0.92, turnSpeed = 0.35, fov = 180, retreatHealth = 0, resetTicks = 3;
    public boolean ignoreFriends = true;
    public boolean ignoreTeammates = true, throughWalls = false, autoWeapon = true, shieldBreak = true, requireWeapon = false;

    // ── state
    private LivingEntity target;
    private int resetLeft;
    private boolean critArmed;
    private int strafeDir = 1, strafeTicks;
    private int prevSlot = -1;
    private final Random random = new Random();

    /** Tests: hits sent, crit hits (sent while falling), and the current target name. */
    public int hits, critHits, wtaps;
    public String targetName = "";

    public SwordBot() {
        super("ComboBot", "Melee bot: chases a target, aims, strafes, times crits and W-taps for long combos.", Category.COMBAT);
        INSTANCE = this;
        targets = new EntityFilterPicker("Targets");
        targets.filters.put("Hostiles", false);
        settings.add(targets.withDescription("Which entity types the bot fights."));
        settings.add(new ModeButton("Priority", List.of("Closest", "Lowest Health", "Crosshair"), 0, v -> priority = v)
                .withDescription("Which target is picked when several are in range."));
        settings.add(new Slider("Lock Range", 3, 16, lockRange, 0.5, v -> lockRange = v).withDescription("Targets closer than this are picked up."));
        settings.add(new Slider("Lose Range", 4, 24, loseRange, 0.5, v -> loseRange = v).withDescription("The bot gives up a target that gets further away than this."));
        settings.add(new Slider("Attack Range", 2.5, 4.5, attackRange, 0.1, v -> attackRange = v).withDescription("Hits are only sent inside this distance (vanilla reach is 3.0; Legit Mode caps it)."));
        settings.add(new Slider("Combo Distance", 1.5, 3.5, comboDistance, 0.1, v -> comboDistance = v).withDescription("Distance the bot tries to hold: it walks in when further away and stops pushing when closer."));
        settings.add(new Slider("Cooldown", 0.5, 1.0, cooldown, 0.02, v -> cooldown = v).withDescription("Attack charge needed before a hit (1.0 = full damage)."));
        settings.add(new ModeButton("Crits", List.of("Jump", "Off"), 0, v -> critMode = v).withDescription("Jump: jump before the hit and strike while falling, for critical damage."));
        settings.add(new ModeButton("Sprint Reset", List.of("W-Tap", "S-Tap", "Off"), 0, v -> resetMode = v)
                .withDescription("After each hit release W (or tap S) for a moment, so the next hit is a sprint hit with extra knockback."));
        settings.add(new Slider("Reset Ticks", 1, 8, resetTicks, 1, v -> resetTicks = v).withDescription("How long the sprint reset lasts."));
        settings.add(new ModeButton("Strafe", List.of("Circle", "Random", "Off"), 0, v -> strafeMode = v)
                .withDescription("Circle: walk around the target and change direction when hit. Random: change direction at random."));
        settings.add(new Slider("Turn Speed", 0.05, 1.0, turnSpeed, 0.05, v -> turnSpeed = v).withDescription("How fast the camera turns to the target (1 = instant)."));
        settings.add(new Slider("FOV", 30, 360, fov, 10, v -> fov = v).withDescription("Only targets inside this field of view are picked up."));
        settings.add(new Slider("Retreat Health", 0, 10, retreatHealth, 0.5, v -> retreatHealth = v).withDescription("Back off and stop attacking at or below this many hearts (0 = never)."));
        settings.add(new ToggleButton("Through Walls", throughWalls, v -> throughWalls = v).withDescription("Also lock onto targets you cannot see."));
        settings.add(new ToggleButton("Auto Weapon", autoWeapon, v -> autoWeapon = v).withDescription("Switch to the strongest sword or axe in the hotbar while fighting."));
        settings.add(new ToggleButton("Shield Break", shieldBreak, v -> shieldBreak = v).withDescription("Hit a blocking target with an axe to disable its shield."));
        settings.add(new ToggleButton("Require Weapon", requireWeapon, v -> requireWeapon = v).withDescription("Only fight while you hold a sword or an axe."));
    }

    // ------------------------------------------------------------------ targets

    public boolean valid(Minecraft mc, Entity e) {
        if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive() || le.isDeadOrDying()) return false;
        if (AntiBot.isBot(e)) return false;
        if (TeamUtils.ignored(e, ignoreTeammates, ignoreFriends)) return false;
        boolean typeOk = (targets.isFilterEnabled("Players") && e instanceof Player)
                || (targets.isFilterEnabled("Hostiles") && e instanceof Enemy)
                || (targets.isFilterEnabled("Animals") && e instanceof Animal)
                || (targets.isFilterEnabled("NPCs") && e instanceof Npc)
                || (targets.isFilterEnabled("ArmorStands") && e instanceof ArmorStand);
        if (!typeOk) return false;
        if (e instanceof Player p && (p.isSpectator() || p.isCreative())) return false;
        return throughWalls || mc.player.hasLineOfSight(e);
    }

    private double angleTo(Minecraft mc, Entity e) {
        Vec3 d = e.getBoundingBox().getCenter().subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f;
        return Math.abs(Mth.wrapDegrees(yaw - mc.player.getYRot()));
    }

    public LivingEntity findTarget(Minecraft mc) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!valid(mc, e) || mc.player.distanceTo(e) > lockRange) continue;
            if (fov < 360 && angleTo(mc, e) > fov / 2) continue;
            LivingEntity le = (LivingEntity) e;
            double score = switch (priority) {
                case "Lowest Health" -> le.getHealth() + le.getAbsorptionAmount();
                case "Crosshair" -> angleTo(mc, e);
                default -> mc.player.distanceTo(e);
            };
            if (score < bestScore) { bestScore = score; best = le; }
        }
        return best;
    }

    /** Distance from the eyes to the closest point of the target's hitbox (what the server checks). */
    public static double reach(Minecraft mc, Entity t) {
        Vec3 eye = mc.player.getEyePosition();
        AABB b = t.getBoundingBox();
        double x = Mth.clamp(eye.x, b.minX, b.maxX), y = Mth.clamp(eye.y, b.minY, b.maxY), z = Mth.clamp(eye.z, b.minZ, b.maxZ);
        return eye.distanceTo(new Vec3(x, y, z));
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.gui.screen() != null) { release(mc); return; }

        if (target != null && (!valid(mc, target) || mc.player.distanceTo(target) > loseRange)) {
            target = null;
            release(mc);
        }
        if (target == null) target = findTarget(mc);
        if (target == null) {
            targetName = "";
            // keep the weapon a moment after losing the target, so short losses do not flip the hotbar
            if (++noTargetTicks > 20) restoreSlot(mc);
            return;
        }
        noTargetTicks = 0;
        targetName = target.getName().getString();

        if (requireWeapon && !isWeapon(mc.player.getMainHandItem())) { release(mc); return; }

        boolean retreat = retreatHealth > 0 && mc.player.getHealth() + mc.player.getAbsorptionAmount() <= retreatHealth * 2;
        aim(mc, target);
        double dist = reach(mc, target);

        if (retreat) {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(true);
            mc.options.keyJump.setDown(false);
            strafe(mc);
            return;
        }

        if (autoWeapon) pickWeapon(mc, shieldBreak && target.isBlocking());
        move(mc, dist);
        strafe(mc);
        attackLogic(mc, dist);
    }

    private void move(Minecraft mc, double dist) {
        if (resetLeft > 0) {
            resetLeft--;
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(resetMode.equals("S-Tap"));
            mc.player.setSprinting(false);
            return;
        }
        mc.options.keyDown.setDown(false);
        boolean push = dist > comboDistance;
        mc.options.keyUp.setDown(push);
        if (push && !mc.player.isSprinting() && mc.player.getFoodData().getFoodLevel() > 6) mc.player.setSprinting(true);
    }

    private void strafe(Minecraft mc) {
        switch (strafeMode) {
            case "Circle" -> {
                // switch direction when we bump into something or after a while, so we keep circling
                if (mc.player.horizontalCollision || ++strafeTicks > 60 + random.nextInt(40)) { strafeDir = -strafeDir; strafeTicks = 0; }
            }
            case "Random" -> {
                if (++strafeTicks > 8 + random.nextInt(16)) { strafeDir = random.nextInt(3) - 1; strafeTicks = 0; }
            }
            default -> strafeDir = 0;
        }
        mc.options.keyLeft.setDown(strafeDir < 0);
        mc.options.keyRight.setDown(strafeDir > 0);
    }

    private void attackLogic(Minecraft mc, double dist) {
        double range = LegitMode.cap(attackRange);
        boolean charged = mc.player.getAttackStrengthScale(0.5f) >= cooldown;
        if (dist > range + 0.6) { critArmed = false; mc.options.keyJump.setDown(false); return; }
        boolean wantCrit = critMode.equals("Jump") && !mc.player.isInWater() && !mc.player.onClimbable() && !mc.player.isPassenger();
        if (wantCrit && charged && mc.player.onGround() && !critArmed) {
            // jump now, hit on the way down
            mc.options.keyJump.setDown(true);
            critArmed = true;
            return;
        }
        if (critArmed) {
            mc.options.keyJump.setDown(false);
            boolean falling = !mc.player.onGround() && mc.player.getDeltaMovement().y < 0;
            if (!falling && !mc.player.onGround()) return; // still going up
            critArmed = false;
            if (dist <= range && charged) hit(mc, falling);
            return;
        }
        if (!wantCrit && charged && dist <= range) hit(mc, false);
    }

    private void hit(Minecraft mc, boolean crit) {
        mc.gameMode.attack(mc.player, target);
        com.OsamaClient.newbridge.Utils.SwingUtil.swing(mc.player, InteractionHand.MAIN_HAND);
        hits++;
        if (crit) critHits++;
        if (!resetMode.equals("Off")) { resetLeft = (int) resetTicks; wtaps++; }
        if (strafeMode.equals("Circle") && random.nextInt(3) == 0) strafeDir = -strafeDir;
    }

    private void aim(Minecraft mc, Entity t) {
        Vec3 eye = mc.player.getEyePosition();
        AABB b = t.getBoundingBox();
        // aim at the closest point of the upper body, with a little jitter so it does not look locked
        Vec3 p = new Vec3(Mth.clamp(eye.x, b.minX + 0.1, b.maxX - 0.1),
                Mth.clamp(eye.y, b.minY + b.getYsize() * 0.45, b.maxY - 0.1),
                Mth.clamp(eye.z, b.minZ + 0.1, b.maxZ - 0.1));
        Vec3 d = p.subtract(eye);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90f + (float) (random.nextGaussian() * 0.6);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, h)) + (float) (random.nextGaussian() * 0.4);
        float s = (float) turnSpeed;
        mc.player.setYRot(mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()) * s);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + Mth.wrapDegrees(pitch - mc.player.getXRot()) * s, -90f, 90f));
    }

    // ------------------------------------------------------------------ weapons

    private static boolean isWeapon(ItemStack st) {
        return st.is(net.minecraft.tags.ItemTags.AXES) || st.is(net.minecraft.tags.ItemTags.SWORDS);
    }

    private static double damage(ItemStack st) {
        double d = 1;
        var mods = st.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS);
        if (mods != null) for (var e : mods.modifiers())
            if (e.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) d += e.modifier().amount();
        return d;
    }

    private void pickWeapon(Minecraft mc, boolean wantAxe) {
        var inv = mc.player.getInventory();
        int best = -1;
        double bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!isWeapon(st)) continue;
            double score = damage(st) + (wantAxe && st.is(net.minecraft.tags.ItemTags.AXES) ? 100 : 0) + (!wantAxe && st.is(net.minecraft.tags.ItemTags.SWORDS) ? 0.5 : 0);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        if (best >= 0 && best != inv.getSelectedSlot()) {
            if (prevSlot < 0) prevSlot = inv.getSelectedSlot();
            inv.setSelectedSlot(best);
        }
    }

    private int noTargetTicks;

    private void restoreSlot(Minecraft mc) {
        if (prevSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
        prevSlot = -1;
    }

    // ------------------------------------------------------------------ keys

    private void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        critArmed = false;
        resetLeft = 0;
    }

    public LivingEntity target() { return target; }

    @Override
    public void onEnable() { hits = critHits = wtaps = 0; target = null; prevSlot = -1; }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        release(mc);
        restoreSlot(mc);
        target = null;
        targetName = "";
    }
}
