package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.Utils.Tracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Scaffold extends Module {

    public static Scaffold INSTANCE;

    public boolean sprint = false;
    public boolean tower = true;
    public boolean autoSneak = true;
    public float rotationSpeed = 180.0F;
    public int delay = 0;

    private static final double REACH = 4.0D;
    private static final double SNEAK_LOOKAHEAD = 0.3D;
    private static final int HOLD_TICKS = 10;
    private static final float PRE_AIM_PITCH = 82.0F;

    private float vYaw, vPitch;
    private float lastDy = 0f;
    private int tickCounter = 0;
    private boolean spoofing;
    private boolean sneakForced;
    private int sneakTicks = 0;
    private int originalSlot = -1;
    private int cooldown;
    private int ticksSincePlace = 1000;

    public Scaffold() {
        super("Scaffold", "Legit silent bridging with working Tower & Movement Correction", Category.MISC);
        INSTANCE = this;
        this.settings.add(new ToggleButton("Allow Sprint", sprint, val -> sprint = val).withDescription("Allows sprinting while bridging."));
        this.settings.add(new ToggleButton("Tower", tower, val -> tower = val).withDescription("Places blocks underneath you while jumping straight up."));
        this.settings.add(new ToggleButton("Auto Sneak", autoSneak, val -> autoSneak = val).withDescription("Sneaks automatically at the edge."));
        this.settings.add(new Slider("Rotation Speed", 30.0, 360.0, (double) rotationSpeed, val -> rotationSpeed = val.floatValue()).withDescription("Max rotation per tick."));
        this.settings.add(new Slider("Delay", 0.0, 5.0, 0.0, val -> delay = val.intValue()).withDescription("Ticks to wait between placements."));
    }

    public void onUpdate(Minecraft client) {}

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        if (sneakForced) mc.options.keyShift.setDown(false);
        if (mc.player != null && originalSlot != -1) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
        }
        resetState();
    }

    private void resetState() {
        spoofing = false;
        sneakForced = false;
        sneakTicks = 0;
        originalSlot = -1;
        cooldown = 0;
        ticksSincePlace = 1000;
        lastDy = 0f;
        tickCounter = 0;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (!enabled || mc.player == null || mc.level == null || mc.gameMode == null) {
            if (sneakForced && mc.options != null) {
                mc.options.keyShift.setDown(false);
                sneakForced = false;
            }
            spoofing = false;
            sneakTicks = 0;
            return;
        }

        LocalPlayer player = mc.player;
        if (cooldown > 0) cooldown--;
        if (ticksSincePlace < 1000) ticksSincePlace++;

        Vec3 moveDir = moveDirection(mc, player);
        boolean moving = moveDir != null;
        BlockPos underFeet = BlockPos.containing(player.getX(), player.getY() - 1.0, player.getZ());
        boolean edge = edgeNear(mc, player, moveDir, underFeet);

        if (edge && (!sprint || autoSneak)) {
            player.setSprinting(false);
            mc.options.keySprint.setDown(false);
        }

        if (autoSneak) {
            updateSneak(mc, player, edge);
        }

        int state = 0;

        // TOWER-FIX: Wenn Tower an ist und die Leertaste gedrückt wird (ohne Bewegung oder beim Springen),
        // wird direkt der Block unter den Füßen anvisiert und platziert.
        boolean isTowering = tower && mc.options.keyJump.isDown();
        if (isTowering && isFree(mc, underFeet) && cooldown == 0) {
            BlockPos support = underFeet.below();
            if (!isFree(mc, support) && ensureBlockInHand(player)) {
                // Blick beim Towering direkt steil nach unten auf den Stützblock richten
                rotate(player, player.getYRot(), 88.0f);
                HitResult pick = player.pick(REACH + 0.5D, 1.0F, false);
                if (pick instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK && bh.getBlockPos().equals(support)) {
                    mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, bh);
                    player.swing(InteractionHand.MAIN_HAND);
                    cooldown = delay;
                    state = 2;
                }
            }
        }

        // Normales Bridging an Kanten
        boolean canPlaceNow = !autoSneak || !edge || sneakTicks >= 1;
        if (state == 0 && isFree(mc, underFeet) && !isTowering && cooldown == 0 && canPlaceNow) {
            state = tryPlace(mc, player, underFeet, moveDir);
        }

        if (state == 0 && edge && moving && !isTowering) {
            if (preAim(mc, player, moveDir, underFeet)) state = 1;
        }

        if (state == 2) ticksSincePlace = 0;

        if (state == 0) {
            if (spoofing && ticksSincePlace < HOLD_TICKS) {
                Tracker.applySpoof(player, vYaw, vPitch);
            } else {
                returnToReal(player);
            }
        }
    }

    private boolean edgeNear(Minecraft mc, LocalPlayer player, Vec3 dir, BlockPos underFeet) {
        if (isFree(mc, underFeet)) return true;
        if (dir == null) return false;
        BlockPos ahead = BlockPos.containing(
                player.getX() + dir.x * SNEAK_LOOKAHEAD,
                player.getY() - 1.0,
                player.getZ() + dir.z * SNEAK_LOOKAHEAD);
        return isFree(mc, ahead);
    }

    private void updateSneak(Minecraft mc, LocalPlayer player, boolean edge) {
        boolean want = player.onGround() && edge;

        if (want) {
            if (!sneakForced) {
                sneakTicks = 0;
            }
            mc.options.keyShift.setDown(true);
            sneakForced = true;
            sneakTicks++;
        } else {
            if (sneakForced) {
                mc.options.keyShift.setDown(false);
                sneakForced = false;
                sneakTicks = 0;
            }
        }
    }

    private Vec3 moveDirection(Minecraft mc, LocalPlayer player) {
        boolean[] k = Tracker.physicalMoveKeys(mc);
        float forward = (k[0] ? 1 : 0) - (k[1] ? 1 : 0);
        float strafe = (k[2] ? 1 : 0) - (k[3] ? 1 : 0);
        if (forward == 0 && strafe == 0) return null;

        double yawRad = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        double lx = Math.cos(yawRad), lz = Math.sin(yawRad);
        double dx = fx * forward + lx * strafe;
        double dz = fz * forward + lz * strafe;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-6) return null;
        return new Vec3(dx / len, 0.0, dz / len);
    }

    private int tryPlace(Minecraft mc, LocalPlayer player, BlockPos target, Vec3 moveDir) {
        Vec3 eye = player.getEyePosition();

        BlockPos bestSupport = null;
        Direction bestFace = null;
        Vec3 bestCenter = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction d : Direction.values()) {
            BlockPos support = target.relative(d);
            if (isFree(mc, support)) continue;

            Direction face = d.getOpposite();
            Vec3 center = faceCenter(support, face);
            Vec3 normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            if (eye.subtract(center).dot(normal) <= 0.0D) continue;

            double dist = eye.distanceToSqr(center);
            if (dist > REACH * REACH) continue;
            if (dist < bestDist) {
                bestDist = dist;
                bestSupport = support;
                bestFace = face;
                bestCenter = center;
            }
        }
        if (bestFace == null) return 0;
        if (!ensureBlockInHand(player)) return 0;

        double[] ang = aimRotation(eye, bestSupport, bestFace, bestCenter, preferredYaw(player, moveDir));
        rotate(player, (float) ang[0], (float) ang[1]);

        HitResult pick = player.pick(REACH + 0.5D, 1.0F, false);
        if (!(pick instanceof BlockHitResult bh)
                || bh.getType() != HitResult.Type.BLOCK
                || !bh.getBlockPos().equals(bestSupport)
                || bh.getDirection() != bestFace) {
            return 1;
        }

        mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, bh);
        player.swing(InteractionHand.MAIN_HAND);
        cooldown = delay;
        return 2;
    }

    private boolean preAim(Minecraft mc, LocalPlayer player, Vec3 dir, BlockPos underFeet) {
        Direction d = Math.abs(dir.x) > Math.abs(dir.z)
                ? (dir.x > 0 ? Direction.EAST : Direction.WEST)
                : (dir.z > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos support = isFree(mc, underFeet) ? underFeet.relative(d.getOpposite()) : underFeet;
        if (isFree(mc, support)) return false;
        if (!ensureBlockInHand(player)) return false;

        rotate(player, preferredYaw(player, dir), PRE_AIM_PITCH);
        return true;
    }

    private float preferredYaw(LocalPlayer player, Vec3 dir) {
        if (dir != null) return (float) (Math.atan2(-dir.z, -dir.x) * (180.0D / Math.PI)) - 90.0F;
        return spoofing ? vYaw : player.getYRot();
    }

    private double[] aimRotation(Vec3 eye, BlockPos support, Direction face, Vec3 center, float preferred) {
        double[] offsets = {0.0, 45.0, -45.0, 90.0, -90.0, 135.0, -135.0, 180.0};
        for (double off : offsets) {
            double yaw = preferred + off;
            double yr = Math.toRadians(yaw);
            double th = (center.x - eye.x) * -Math.sin(yr) + (center.z - eye.z) * Math.cos(yr);
            double pitch = th < 0.02D
                    ? 90.0D
                    : Mth.clamp(-Math.toDegrees(Math.atan2(center.y - eye.y, th)), -90.0D, 90.0D);
            if (hitsFace(eye, yaw, pitch, support, face)) return new double[]{yaw, pitch};
        }
        return anglesTo(eye, center);
    }

    private static boolean hitsFace(Vec3 eye, double yaw, double pitch, BlockPos support, Direction face) {
        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        double cp = Math.cos(pr);
        double[] dv = {-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp};
        double[] e = {eye.x, eye.y, eye.z};
        double[] min = {support.getX(), support.getY(), support.getZ()};

        int a = face.getAxis().ordinal();
        if (Math.abs(dv[a]) < 1.0E-9) return false;
        double plane = min[a] + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0);
        double t = (plane - e[a]) / dv[a];
        if (t <= 0.0 || t > REACH) return false;

        for (int i = 0; i < 3; i++) {
            if (i == a) continue;
            double p = e[i] + dv[i] * t;
            if (p < min[i] + 0.02 || p > min[i] + 0.98) return false;
        }
        return true;
    }

    private boolean ensureBlockInHand(LocalPlayer player) {
        if (isGoodBlock(player.getMainHandItem())) return true;
        for (int i = 0; i < 9; i++) {
            if (isGoodBlock(player.getInventory().getItem(i))) {
                if (originalSlot == -1) originalSlot = player.getInventory().getSelectedSlot();
                player.getInventory().setSelectedSlot(i);
                return true;
            }
        }
        return false;
    }

    private boolean isGoodBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem bi)) return false;
        Block block = bi.getBlock();
        if (block instanceof EntityBlock || block instanceof FallingBlock) return false;
        return block.defaultBlockState().isCollisionShapeFullBlock(Minecraft.getInstance().level, BlockPos.ZERO);
    }

    private boolean isFree(Minecraft mc, BlockPos pos) {
        BlockState s = mc.level.getBlockState(pos);
        return s.isAir() || s.canBeReplaced();
    }

    private static float mouseGcd() {
        double sensitivity = Minecraft.getInstance().options.sensitivity().get();
        float f = (float) (sensitivity * 0.6D + 0.2D);
        return f * f * f * 1.2F;
    }

    private static float quantize(float value, float gcd) {
        return Math.round(value / gcd) * gcd;
    }

    private void rotate(LocalPlayer player, float targetYaw, float targetPitch) {
        if (!spoofing) {
            vYaw = player.getYRot();
            vPitch = player.getXRot();
            spoofing = true;
        }
        float gcd = mouseGcd();

        float dy = Mth.wrapDegrees(targetYaw - vYaw);

        tickCounter++;
        float antiDuplicate = ((tickCounter % 2 == 0) ? 0.02f : -0.02f);
        if (Math.abs(dy - lastDy) < 0.001f) {
            dy += antiDuplicate;
        }
        lastDy = dy;

        float dp = targetPitch - vPitch;
        vYaw += quantize(Mth.clamp(dy, -rotationSpeed, rotationSpeed), gcd);
        vPitch = Mth.clamp(vPitch + quantize(Mth.clamp(dp, -rotationSpeed, rotationSpeed), gcd), -90.0F, 90.0F);
        Tracker.applySpoof(player, vYaw, vPitch);
    }

    private void returnToReal(LocalPlayer player) {
        if (!spoofing) return;

        Tracker.applySpoof(player, vYaw, vPitch);
        float dy = Tracker.getRealYaw() - vYaw;
        float dp = Tracker.getRealPitch() - vPitch;
        if (Math.max(Math.abs(dy), Math.abs(dp)) <= rotationSpeed) {
            vYaw = Tracker.getRealYaw();
            vPitch = Tracker.getRealPitch();
            Tracker.applySpoof(player, vYaw, vPitch);
            spoofing = false;
            lastDy = 0f;
            tickCounter = 0;
            return;
        }

        float gcd = mouseGcd();
        vYaw += quantize(Mth.clamp(dy, -rotationSpeed, rotationSpeed), gcd);
        vPitch = Mth.clamp(vPitch + quantize(Mth.clamp(dp, -rotationSpeed, rotationSpeed), gcd), -90.0F, 90.0F);
        Tracker.applySpoof(player, vYaw, vPitch);
    }

    private static Vec3 faceCenter(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
    }

    private static double[] anglesTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double yaw = Math.atan2(dz, dx) * (180.0D / Math.PI) - 90.0D;
        double pitch = -(Math.atan2(dy, horizontal) * (180.0D / Math.PI));
        return new double[]{yaw, Mth.clamp(pitch, -90.0D, 90.0D)};
    }
}