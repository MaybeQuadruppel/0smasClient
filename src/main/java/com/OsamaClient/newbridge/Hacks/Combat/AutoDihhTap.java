package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.Utils.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

public class AutoDihhTap extends Module {

    private static final double REACH_SQ = 20.25D;      // 4.5 Blöcke
    private static final int TIMEOUT_TICKS = 60;        // Watchdog pro Sequenz-Schritt
    private static final int CRYSTAL_WAIT_TICKS = 2;    // Mindestwartezeit zwischen den beiden Crystals
    private static final int RETRIGGER_COOLDOWN = 10;

    // --- Minecraft-Cooldowns ---
    /** Ab welcher Attack-Strength (0..1) der Schwerthit als "voll aufgeladen" gilt. */
    private static final float ATTACK_READY = 0.9F;
    /**
     * Der zweite Crystal wird platziert, sobald die Hurt-Zeit (i-Frames) des Ziels nur noch
     * so viele Ticks übrig hat. Platzieren + Zielen + Schlagen dauern ca. 3 Ticks, damit die
     * Explosion genau dann trifft, wenn die i-Frames vorbei sind.
     */
    private static final int HURT_LEAD_TICKS = 3;

    private final Aim aim = new Aim();
    private float vYaw, vPitch;   // virtuelle (gespoofte) Rotation
    private boolean spoofing;
    private double rotationSpeed = 7.0;
    private String mode = "Auto";

    private Step currentStep = Step.NONE;
    private int stepTicks;
    private int cooldown;
    private boolean secondRound;
    private LivingEntity currentTarget;
    private BlockPos obsidianPos;
    private Direction placeFace = Direction.UP;

    public AutoDihhTap() {
        super("AutoDihhTap", "Runs the crystal 'DihhTap' sequence for you.", Category.COMBAT);

        this.settings.add(new Slider("Rotation Speed", 1.0, 20.0, 7.0, val -> rotationSpeed = val).withDescription("Controls how fast the camera rotates during the crystal sequence."));
        this.settings.add(new ModeButton("Activation", Arrays.asList("Auto", "Manual"), 0, val -> mode = val).withDescription("Sets whether the sequence triggers automatically or manually."));
    }

    // ------------------------------------------------------------------ TICK

    @Override
    public void onTick(Minecraft client) {
        if (!enabled || client.player == null || client.level == null || client.gameMode == null) {
            if (currentStep != Step.NONE || aim.active) reset();
            return;
        }

        if (cooldown > 0) cooldown--;

        if (currentStep == Step.NONE && !aim.active) {
            if (cooldown == 0 && mode.equals("Auto")
                    && client.hitResult instanceof EntityHitResult eh
                    && eh.getEntity() instanceof LivingEntity living
                    && living.isAlive()
                    && !(living instanceof ArmorStand)
                    && canAttackNow(client.player, living)) {
                currentTarget = living;
                setStep(Step.LOOK_AT_TARGET);
            }
            if (currentStep == Step.NONE) return;
        }

        // Silent Aim: virtuelle Rotation gilt für diesen ganzen Tick (Reset am Tick-Ende in MinecraftMixin)
        if (!spoofing) {
            vYaw = client.player.getYRot();
            vPitch = client.player.getXRot();
            spoofing = true;
        }
        Tracker.applySpoof(client.player, vYaw, vPitch);

        // Rotation läuft: nur drehen, Aktion folgt im NÄCHSTEN Tick (Rotations-Paket kommt zuerst beim Server an)
        if (aim.active) {
            aim.tick(client.player, (float) (rotationSpeed * 3.0));
            Tracker.applySpoof(client.player, vYaw, vPitch);
            return;
        }

        if (++stepTicks > TIMEOUT_TICKS) {
            reset();
            return;
        }

        runStep(client);
    }

    // ------------------------------------------------------------- SEQUENZ

    /*
     * GrimAC-Regeln, an die sich die Sequenz hält:
     *  - Slot-Wechsel nur VOR einer Aktion im selben Tick, nie direkt NACH use/attack
     *    (PacketOrderE) -> nach jeder Aktion kommt ein Tick-Wechsel, bevor der Slot geändert wird.
     *  - Vor jeder Platzierung/jedem Schlag wird erst gedreht (eigener Tick), die Aktion folgt
     *    im nächsten Tick (RotationPlace / Reach).
     *  - Cursor liegt immer in der Mitte der Fläche (FabricatedPlace).
     *  - Yaw bleibt numerisch durchgehend (AimModulo360).
     */
    private void runStep(Minecraft client) {
        LocalPlayer player = client.player;
        Vec3 eye = player.getEyePosition();

        switch (currentStep) {
            case LOOK_AT_TARGET -> {
                if (currentTarget == null || !currentTarget.isAlive()) { reset(); return; }
                // Warten, bis Attack-Cooldown und Hurt-Cooldown des Ziels bereit sind
                if (!canAttackNow(player, currentTarget)) return;

                switchToSword(player); // Wechsel VOR dem Schlag = ok
                if (client.hitResult instanceof EntityHitResult eh && eh.getEntity() == currentTarget) {
                    attack(client, currentTarget);
                }
                Vec3 right = player.getLookAngle().cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                aimAt(currentTarget.position().add(right.scale(1.3)), Step.PREPARE_BASE);
            }

            case PREPARE_BASE -> {
                if (findSlot(player, Items.END_CRYSTAL) == -1) { reset(); return; }

                HitResult pickResult = player.pick(4.5D, 1.0F, false); // eigener Ray mit gespoofter Rotation
                if (!(pickResult instanceof BlockHitResult bh) || bh.getType() != HitResult.Type.BLOCK) { reset(); return; }
                BlockPos hitPos = bh.getBlockPos();

                if (client.level.getBlockState(hitPos).is(Blocks.OBSIDIAN)) {
                    obsidianPos = hitPos;
                } else {
                    BlockPos placePos = hitPos.relative(bh.getDirection());
                    if (!client.level.getBlockState(placePos).canBeReplaced()
                            || eye.distanceToSqr(Vec3.atCenterOf(placePos)) > REACH_SQ
                            || !switchToItem(player, Items.OBSIDIAN)) { // Wechsel VOR dem Use = ok
                        reset();
                        return;
                    }
                    // Cursor = Mitte der geklickten Fläche
                    BlockHitResult place = new BlockHitResult(faceCenter(hitPos, bh.getDirection()), bh.getDirection(), hitPos, false);
                    client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, place);
                    com.OsamaClient.newbridge.Utils.SwingUtil.swing(player, InteractionHand.MAIN_HAND);
                    obsidianPos = placePos;
                    // Slot-Wechsel auf den Crystal passiert ERST im Schritt PLACE_CRYSTAL (anderer Tick)
                }

                if (eye.distanceToSqr(Vec3.atCenterOf(obsidianPos)) > REACH_SQ
                        || !client.level.getBlockState(obsidianPos.above()).isAir()) {
                    reset();
                    return;
                }
                setStep(Step.AIM_BASE);
            }

            case AIM_BASE -> {
                // Erst auf die Fläche des Obsidians drehen, DANACH (nächster Tick) den Crystal setzen
                placeFace = bestFace(client, eye, obsidianPos);
                aimAt(faceCenter(obsidianPos, placeFace), Step.PLACE_CRYSTAL);
            }

            case PLACE_CRYSTAL -> {
                if (eye.distanceToSqr(Vec3.atCenterOf(obsidianPos)) > REACH_SQ
                        || !switchToItem(player, Items.END_CRYSTAL)) { // Wechsel VOR dem Use = ok
                    reset();
                    return;
                }
                clickFace(client, obsidianPos, placeFace);
                setStep(Step.AIM_CRYSTAL);
            }

            case AIM_CRYSTAL -> {
                EndCrystal crystal = findCrystal(client);
                if (crystal == null) {
                    if (stepTicks > 10) reset();
                    return;
                }
                aimAt(crystal.getBoundingBox().getCenter(), Step.HIT_CRYSTAL);
            }

            case HIT_CRYSTAL -> {
                EndCrystal crystal = findCrystal(client);
                if (crystal == null) {
                    if (stepTicks > 5) reset();
                    return;
                }
                attack(client, crystal);
                if (!secondRound) {
                    secondRound = true;
                    setStep(Step.WAIT);
                } else {
                    // NICHT im selben Tick den Slot wechseln (PacketOrderE: attacking) -> nächster Tick
                    setStep(Step.SWITCH_BACK);
                }
            }

            case WAIT -> {
                if (currentTarget == null || !currentTarget.isAlive()) {
                    setStep(Step.SWITCH_BACK);
                    return;
                }
                // Hurt-Cooldown (i-Frames) des Ziels abwarten, damit der zweite Crystal voll zählt
                if (stepTicks >= CRYSTAL_WAIT_TICKS && currentTarget.hurtTime <= HURT_LEAD_TICKS) {
                    setStep(Step.AIM_BASE);
                }
            }

            case SWITCH_BACK -> {
                // Eigener Tick nach dem letzten Schlag: jetzt darf der Slot gewechselt werden
                switchToSword(player);
                // Sanft zurück zur echten Blickrichtung drehen, statt am Ende einen Rotations-Snap zu senden
                Vec3 back = directionFromRotation(Tracker.getRealPitch(), Tracker.getRealYaw());
                aimAt(eye.add(back.scale(20.0D)), Step.DONE);
            }

            case DONE -> {
                reset();
                cooldown = RETRIGGER_COOLDOWN;
            }

            default -> reset();
        }
    }

    // --------------------------------------------------------------- HELPER

    /** Attack-Cooldown des Spielers voll aufgeladen UND das Ziel hat keine aktiven i-Frames (hurtTime). */
    private static boolean canAttackNow(LocalPlayer player, LivingEntity target) {
        return player.getAttackStrengthScale(0.5F) >= ATTACK_READY && target.hurtTime == 0;
    }

    private void setStep(Step step) {
        this.currentStep = step;
        this.stepTicks = 0;
        // Solange die Sequenz läuft, keine Vanilla-Rechtsklicks des Spielers (SupressMixin)
        Tracker.isAiming = step != Step.NONE;
    }

    private void aimAt(Vec3 point, Step next) {
        aim.start(point, () -> setStep(next));
    }

    private void reset() {
        aim.cancel();
        spoofing = false;
        currentStep = Step.NONE;
        stepTicks = 0;
        secondRound = false;
        currentTarget = null;
        obsidianPos = null;
        Tracker.isAiming = false;
    }

    private EndCrystal findCrystal(Minecraft client) {
        if (obsidianPos == null) return null;
        List<EndCrystal> list = client.level.getEntitiesOfClass(EndCrystal.class, new AABB(obsidianPos.above()));
        Vec3 eye = client.player.getEyePosition();
        EndCrystal best = null;
        double bestDist = Double.MAX_VALUE;
        for (EndCrystal c : list) {
            double d = c.distanceToSqr(eye);
            if (d < bestDist) { bestDist = d; best = c; }
        }
        return best;
    }

    /** Wählt die sichtbare Fläche (Auge auf der Außenseite), die frei liegt und am nächsten ist – klappt auch über/unter dem Spieler. */
    private static Direction bestFace(Minecraft client, Vec3 eye, BlockPos pos) {
        Direction best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            Vec3 center = faceCenter(pos, d);
            Vec3 normal = new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
            if (eye.subtract(center).dot(normal) <= 0.0D) continue;
            if (!client.level.getBlockState(pos.relative(d)).canBeReplaced()) continue;
            double dist = eye.distanceToSqr(center);
            if (dist < bestDist) { bestDist = dist; best = d; }
        }
        return best != null ? best : Direction.UP;
    }

    private static Vec3 faceCenter(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
    }

    private static Vec3 directionFromRotation(float pitch, float yaw) {
        float yawRad = yaw * ((float) Math.PI / 180.0F);
        float pitchRad = pitch * ((float) Math.PI / 180.0F);
        double cosPitch = Math.cos(pitchRad);
        return new Vec3(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
    }

    private void clickFace(Minecraft client, BlockPos pos, Direction face) {
        if (client.player == null || client.gameMode == null) return;
        BlockHitResult hit = new BlockHitResult(faceCenter(pos, face), face, pos, false);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        com.OsamaClient.newbridge.Utils.SwingUtil.swing(client.player, InteractionHand.MAIN_HAND);
    }

    private void attack(Minecraft client, net.minecraft.world.entity.Entity entity) {
        if (client.player == null || client.gameMode == null) return;
        client.gameMode.attack(client.player, entity);
        com.OsamaClient.newbridge.Utils.SwingUtil.swing(client.player, InteractionHand.MAIN_HAND);
    }

    private boolean switchToSword(LocalPlayer player) {
        return switchToItem(player, Items.NETHERITE_SWORD) || switchToItem(player, Items.DIAMOND_SWORD);
    }

    private int findSlot(LocalPlayer player, Item item) {
        for (int i = 0; i < 9; ++i) {
            if (player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private boolean switchToItem(LocalPlayer player, Item item) {
        int slot = findSlot(player, item);
        if (slot == -1) return false;
        player.getInventory().setSelectedSlot(slot);
        return true;
    }

    // ----------------------------------------------------------- LIFECYCLE

    @Override
    public void onEnable() {
        reset();
        cooldown = 0;
    }

    @Override
    public void onDisable() {
        reset();
    }

    public void onToggle() {
        enabled = !enabled;
        if (enabled) onEnable();
        else onDisable();
    }

    public String getMode() {
        return this.mode;
    }

    public void triggerManual(LivingEntity target) {
        if (this.enabled && target != null && this.currentStep == Step.NONE) {
            this.currentTarget = target;
            setStep(Step.LOOK_AT_TARGET);
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    private enum Step {
        NONE, LOOK_AT_TARGET, PREPARE_BASE, AIM_BASE, PLACE_CRYSTAL, AIM_CRYSTAL,
        HIT_CRYSTAL, WAIT, SWITCH_BACK, DONE
    }

    // ------------------------------------------------ AIM (aus SafeAnchor)

    private final class Aim {
        boolean active;
        private Vec3 target;
        private float currentSpeed;
        private Runnable onDone;

        void start(Vec3 target, Runnable onDone) {
            this.target = target;
            this.onDone = onDone;
            this.currentSpeed = 0.0F;
            this.active = true;
        }

        void cancel() {
            this.active = false;
            this.target = null;
            this.onDone = null;
            this.currentSpeed = 0.0F;
        }

        void tick(LocalPlayer player, float maxSpeed) {
            if (!active || target == null) return;

            Vec3 eye = player.getEyePosition();
            double dx = target.x - eye.x;
            double dy = target.y - eye.y;
            double dz = target.z - eye.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float targetYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            float targetPitch = Mth.clamp((float) -(Mth.atan2(dy, horizontal) * (180.0D / Math.PI)), -90.0F, 90.0F);

            // Kürzester Winkelunterschied; vYaw wird immer nur ADDITIV verändert,
            // damit der Zahlenwert durchgehend bleibt (AimModulo360).
            float yawDelta = Mth.wrapDegrees(targetYaw - vYaw);
            float pitchDelta = targetPitch - vPitch;
            float distance = (float) Math.sqrt(yawDelta * yawDelta + pitchDelta * pitchDelta);

            if (distance <= 0.5F) {
                vYaw += yawDelta;       // NICHT vYaw = targetYaw (würde auf -180..180 springen)
                vPitch = targetPitch;
                finish();
                return;
            }

            float easingScale = Mth.clamp(distance / 20.0F, 0.4F, 1.0F);
            float targetSpeed = Math.max(1.0F, maxSpeed * easingScale);
            float acceleration = Math.max(2.0F, maxSpeed * 0.8F);

            if (currentSpeed < targetSpeed) {
                currentSpeed = Math.min(targetSpeed, currentSpeed + acceleration);
            } else {
                currentSpeed = Math.max(targetSpeed, currentSpeed - acceleration);
            }

            float step = Math.min(distance, currentSpeed);
            float scale = step / distance;
            vYaw += yawDelta * scale;
            vPitch += pitchDelta * scale;

            if (step >= distance) finish();
        }

        private void finish() {
            Runnable cb = onDone;
            this.active = false;
            this.target = null;
            this.onDone = null;
            if (cb != null) cb.run();
        }
    }
}