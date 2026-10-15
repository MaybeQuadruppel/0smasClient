package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Combat.trap.TrapPlanner;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.TeamUtils;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.OsamaClient.newbridge.mixin.combat.LocalPlayerRotationAccessor;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Fullbox: baut den nächsten Gegner komplett mit Blöcken ein (Boden, Füße, Kopf, Deckel).
 *
 * Silent Aim: Für jeden Block wird die Rotation zur Stützfläche NUR an den Server geschickt,
 * die Kamera bewegt sich nicht. Im nächsten Tick sendet Vanilla automatisch wieder die echte
 * Rotation (die "zuletzt gesendete" Rotation wird dafür auf den Spoof-Wert gesetzt).
 */
public class Fullbox extends Module {

    public static Fullbox INSTANCE;

    private double range = 4.5;
    public double range() { return range; }
    public void clampRange(double max) { if (range > max) range = max; }
    private double blocksPerTick = 2;
    private double delay = 1;
    private String blockMode = "Obsidian";
    private boolean floor = true, top = true, head = true;
    private boolean targetSelf = false, center = true;
    private boolean silent = true, swapBack = true, swing = true;
    private boolean ignoreTeammates = true, onlyOnGround = false, render = true;
    private int color = 0xFFA78BFA;
    public final EntityFilterPicker filter = new EntityFilterPicker("Targets");

    private int cooldown;
    private Entity target;
    private List<BlockPos> lastPlan = List.of();
    private ChamsBufferSource buffer;
    /** Für Tests/HUD: Anzahl seit dem Einschalten platzierter Blöcke. */
    public int placedCount;

    public Fullbox() {
        super("Fullbox", "Traps the nearest enemy in blocks (floor, feet, head, roof) with silent rotations", Category.COMBAT);
        INSTANCE = this;
        filter.filters.put("Hostiles", false);
        filter.filters.put("Animals", false);
        settings.add(new Slider("Range", 2.0, 6.0, range, 0.1, v -> range = v)
                .withDescription("Max distance from your eyes to a placement face."));
        settings.add(new ModeButton("Target", List.of("Enemy", "Self"), 0, v -> targetSelf = v.equals("Self"))
                .withDescription("Enemy = trap the nearest target. Self = surround your own feet (Head / Roof close you in completely)."));
        settings.add(new ToggleButton("Center", center, v -> center = v)
                .withDescription("Self mode: move to the middle of your block first so nothing overlaps you."));
        settings.add(new Slider("Blocks/Tick", 1, 6, blocksPerTick, 1, v -> blocksPerTick = v)
                .withDescription("How many blocks may be placed per tick."));
        settings.add(new Slider("Delay", 0, 10, delay, 1, v -> delay = v)
                .withDescription("Ticks to wait between placement rounds."));
        settings.add(new ModeButton("Block", List.of("Obsidian", "Any Solid", "Cobweb"), 0, v -> blockMode = v)
                .withDescription("Which block to trap with (Any Solid: obsidian first, then any full block)."));
        settings.add(new ToggleButton("Floor", floor, v -> floor = v).withDescription("Places a block under the target if missing."));
        settings.add(new ToggleButton("Head", head, v -> head = v).withDescription("Closes the ring at head height."));
        settings.add(new ToggleButton("Roof", top, v -> top = v).withDescription("Places a block above the target's head."));
        settings.add(new ToggleButton("Silent Rotate", silent, v -> silent = v)
                .withDescription("Rotations only go to the server, your camera never moves."));
        settings.add(new ToggleButton("Swap Back", swapBack, v -> swapBack = v)
                .withDescription("Switches back to your previous hotbar slot after placing."));
        settings.add(new ToggleButton("Swing", swing, v -> swing = v).withDescription("Swing the hand when placing."));
        settings.add(new ToggleButton("Only On Ground", onlyOnGround, v -> onlyOnGround = v)
                .withDescription("Only trap targets that stand on the ground."));
        settings.add(new ToggleButton("Ignore Teammates", ignoreTeammates, v -> ignoreTeammates = v)
                .withDescription("Never trap teammates."));
        settings.add(new ToggleButton("Hold Key", false, v -> holdToActivate = v)
                .withDescription("Only trap while the module key is held (set the key in the module's bind). Off = key toggles."));
        settings.add(new ToggleButton("Render", render, v -> render = v).withDescription("Shows the planned trap blocks."));
        settings.add(new ColorPicker("Color", color, c -> color = c).withDescription("Color of the planned blocks."));
        settings.add(filter.withDescription("Which entities can be trapped."));
        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onEnable() {
        cooldown = 0;
        placedCount = 0;
    }

    @Override
    public void onDisable() {
        target = null;
        lastPlan = List.of();
    }

    public Entity target() { return target; }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) return;
        LocalPlayer player = mc.player;
        target = targetSelf ? player : findTarget(mc);
        if (targetSelf && center && player.onGround()) {
            double cx = Math.floor(player.getX()) + 0.5, cz = Math.floor(player.getZ()) + 0.5;
            if (Math.abs(player.getX() - cx) > 0.2 || Math.abs(player.getZ() - cz) > 0.2)
                player.setDeltaMovement((cx - player.getX()) * 0.5, player.getDeltaMovement().y, (cz - player.getZ()) * 0.5);
        }
        if (target == null) {
            lastPlan = List.of();
            return;
        }
        AABB box = target.getBoundingBox();
        lastPlan = TrapPlanner.plan(mc.level, box, new TrapPlanner.Options(floor && !targetSelf, true, head, top));
        if (lastPlan.isEmpty()) return;
        if (cooldown > 0) { cooldown--; return; }

        int slot = findBlockSlot(player);
        if (slot < 0) return;

        Vec3 eye = player.getEyePosition();
        List<TrapPlanner.Placement> placements = TrapPlanner.nextPlacements(mc.level, lastPlan, eye, range,
                (int) blocksPerTick, p -> blockedByEntity(mc, p));
        if (placements.isEmpty()) return;

        int prevSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(slot);
        float lastYaw = Float.NaN, lastPitch = Float.NaN;
        for (TrapPlanner.Placement p : placements) {
            Vec3 hit = p.hitPoint();
            if (silent) {
                float[] rot = gcd(mc, player, TrapPlanner.rotationTo(eye, hit));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(rot[0], rot[1], player.onGround(), player.horizontalCollision));
                lastYaw = rot[0];
                lastPitch = rot[1];
            }
            BlockHitResult bhr = new BlockHitResult(hit, p.face(), p.support(), false);
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, bhr);
            if (swing) com.OsamaClient.newbridge.Utils.SwingUtil.swing(player, InteractionHand.MAIN_HAND);
            placedCount++;
        }
        if (silent && !Float.isNaN(lastYaw)) {
            LocalPlayerRotationAccessor acc = (LocalPlayerRotationAccessor) player;
            acc.newbridge$setYRotLast(lastYaw);
            acc.newbridge$setXRotLast(lastPitch);
        }
        if (swapBack) player.getInventory().setSelectedSlot(prevSlot);
        cooldown = (int) delay;
    }

    /** Rotation relativ zur echten Kamera, mausartig gerundet (sieht wie echte Mausbewegung aus). */
    private static float[] gcd(Minecraft mc, LocalPlayer player, float[] want) {
        double sens = mc.options.sensitivity().get();
        float f = (float) (sens * 0.6F + 0.2F);
        float step = f * f * f * 1.2F;
        float dy = Mth.wrapDegrees(want[0] - player.getYRot());
        float dp = want[1] - player.getXRot();
        dy = Math.round(dy / step) * step;
        dp = Math.round(dp / step) * step;
        return new float[]{player.getYRot() + dy, Mth.clamp(player.getXRot() + dp, -90f, 90f)};
    }

    private boolean blockedByEntity(Minecraft mc, BlockPos p) {
        AABB cell = new AABB(p);
        for (Entity e : mc.level.getEntities(null, cell)) {
            if (e.isAlive() && e.blocksBuilding && e.getBoundingBox().intersects(cell)) return true;
        }
        return false;
    }

    private Entity findTarget(Minecraft mc) {
        Entity best = null;
        double bestD = Double.MAX_VALUE;
        double r = range + 2.0;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(r), x -> x instanceof LivingEntity)) {
            if (!isValidTarget(mc, e)) continue;
            if (onlyOnGround && !e.onGround()) continue;
            double d = mc.player.distanceToSqr(e);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private boolean isValidTarget(Minecraft mc, Entity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (ignoreTeammates && TeamUtils.isTeammate(e)) return false;
        if (filter.isFilterEnabled("Players") && e instanceof Player) return true;
        if (filter.isFilterEnabled("Hostiles") && e instanceof Enemy) return true;
        if (filter.isFilterEnabled("Animals") && e instanceof Animal) return true;
        if (filter.isFilterEnabled("NPCs") && e instanceof Npc) return true;
        if (filter.isFilterEnabled("ArmorStands") && e instanceof ArmorStand) return true;
        return false;
    }

    private int findBlockSlot(LocalPlayer player) {
        int fallback = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!(s.getItem() instanceof BlockItem bi)) continue;
            Block b = bi.getBlock();
            switch (blockMode) {
                case "Obsidian" -> { if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) return i; }
                case "Cobweb" -> { if (b == Blocks.COBWEB) return i; }
                default -> {
                    if (b == Blocks.OBSIDIAN) return i;
                    if (fallback < 0 && b.defaultBlockState().isCollisionShapeFullBlock(player.level(), BlockPos.ZERO)
                            && !(b instanceof net.minecraft.world.level.block.FallingBlock)
                            && !(b instanceof net.minecraft.world.level.block.EntityBlock)) fallback = i;
                }
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------ render

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!enabled || !render || lastPlan.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer.mainCamera() == null) return;
        if (buffer == null) buffer = new ChamsBufferSource();
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        Matrix4f m = event.getPoseStack().last().pose();
        Matrix3f n = event.getPoseStack().last().normal();
        VertexConsumer lines = buffer.getBuffer(RenderTypes.storageEspLinesSeeThrough());
        for (BlockPos p : new ArrayList<>(lastPlan)) {
            float x1 = (float) (p.getX() - cam.x), y1 = (float) (p.getY() - cam.y), z1 = (float) (p.getZ() - cam.z);
            box(m, n, lines, x1 + 0.02f, y1 + 0.02f, z1 + 0.02f, x1 + 0.98f, y1 + 0.98f, z1 + 0.98f, color);
        }
        buffer.uploadAndDraw();
    }

    private static void box(Matrix4f m, Matrix3f n, VertexConsumer c, float x1, float y1, float z1, float x2, float y2, float z2, int col) {
        float[][] e = {
                {x1, y1, z1, x2, y1, z1}, {x2, y1, z1, x2, y1, z2}, {x2, y1, z2, x1, y1, z2}, {x1, y1, z2, x1, y1, z1},
                {x1, y2, z1, x2, y2, z1}, {x2, y2, z1, x2, y2, z2}, {x2, y2, z2, x1, y2, z2}, {x1, y2, z2, x1, y2, z1},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x2, y1, z2, x2, y2, z2}, {x1, y1, z2, x1, y2, z2}};
        int a = (col >>> 24) & 0xFF, r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;
        for (float[] l : e) {
            Vector3f nv = new Vector3f(l[3] - l[0], l[4] - l[1], l[5] - l[2]).normalize().mul(n);
            c.addVertex(m, l[0], l[1], l[2]).setColor(r, g, b, a).setNormal(nv.x(), nv.y(), nv.z()).setLineWidth(2f);
            c.addVertex(m, l[3], l[4], l[5]).setColor(r, g, b, a).setNormal(nv.x(), nv.y(), nv.z()).setLineWidth(2f);
        }
    }
}
