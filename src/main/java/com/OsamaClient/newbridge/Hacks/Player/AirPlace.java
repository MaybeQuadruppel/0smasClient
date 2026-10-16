package com.OsamaClient.newbridge.Hacks.Player;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * AirPlace: right click places the held block where you are looking even if no block is there.
 * Based on the AirPlace module of the Autism client (GPL-3.0).
 */
public class AirPlace extends Module {

    public static AirPlace INSTANCE;

    public double range = 5;
    public boolean preview = true, fill = true;
    public int previewColor = 0xFF4ADE80;
    private final ChamsBufferSource bufferSource = new ChamsBufferSource();
    private int cooldown;
    /** Number of blocks placed (tests / debugging). */
    public int placed;

    public AirPlace() {
        super("AirPlace", "Place blocks in mid air by right clicking where you look.", Category.PLAYER);
        INSTANCE = this;
        settings.add(new Slider("Range", 1, 6, range, 0.5, v -> range = v).withDescription("How far away you can place."));
        settings.add(new ToggleButton("Preview", preview, v -> preview = v).withDescription("Shows a ghost box where the block will be placed (only while you aim into the air with a block in hand)."));
        settings.add(new ColorPicker("Preview Color", previewColor, v -> previewColor = v).withDescription("Colour of the ghost box."));
        settings.add(new ToggleButton("Preview Fill", fill, v -> fill = v).withDescription("Fills the ghost box (off: only the outline)."));
        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        if (cooldown > 0) { cooldown--; return; }
        if (!mc.options.keyUse.isDown() || p.isHandsBusy()) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.MISS) return;
        InteractionHand hand = hand(p);
        if (hand == null) return;
        HitResult picked = p.pick(Math.max(1, Math.min(6, range)), 0f, false);
        if (!(picked instanceof BlockHitResult hit)) return;
        BlockPos pos = hit.getBlockPos();
        if (mc.level.isOutsideBuildHeight(pos) || !mc.level.getBlockState(pos).canBeReplaced()) return;
        InteractionResult r = mc.gameMode.useItemOn(p, hand, hit);
        if (r instanceof InteractionResult.Success) {
            com.OsamaClient.newbridge.Utils.SwingUtil.swing(p, hand);
            placed++;
        }
        cooldown = 3;
    }

    /** Block position AirPlace would use right now, or null (same checks as the placement itself). */
    private BlockPos target(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) return null;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.MISS) return null;
        if (hand(p) == null) return null;
        HitResult picked = p.pick(Math.max(1, Math.min(6, range)), 0f, false);
        if (!(picked instanceof BlockHitResult hit)) return null;
        BlockPos pos = hit.getBlockPos();
        if (mc.level.isOutsideBuildHeight(pos) || !mc.level.getBlockState(pos).canBeReplaced()) return null;
        return pos;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!enabled || !preview) return;
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = target(mc);
        if (pos == null || mc.gameRenderer.mainCamera() == null) return;
        var cam = mc.gameRenderer.mainCamera().position();
        PoseStack ps = event.getPoseStack();
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        AABB b = new AABB(pos).inflate(0.002);
        float x1 = (float) (b.minX - cam.x), y1 = (float) (b.minY - cam.y), z1 = (float) (b.minZ - cam.z);
        float x2 = (float) (b.maxX - cam.x), y2 = (float) (b.maxY - cam.y), z2 = (float) (b.maxZ - cam.z);
        int r = (previewColor >> 16) & 0xFF, g = (previewColor >> 8) & 0xFF, bl = previewColor & 0xFF;
        if (fill) {
            VertexConsumer c = bufferSource.getBuffer(RenderTypes.storageEspFillSeeThrough());
            int a = 60;
            quad(m, n, c, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0, -1, 0, r, g, bl, a);
            quad(m, n, c, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, 0, 1, 0, r, g, bl, a);
            quad(m, n, c, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, 0, 0, -1, r, g, bl, a);
            quad(m, n, c, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, 0, 0, 1, r, g, bl, a);
            quad(m, n, c, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, -1, 0, 0, r, g, bl, a);
            quad(m, n, c, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, 1, 0, 0, r, g, bl, a);
        }
        VertexConsumer l = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
        float w = 2f;
        int a = 255;
        ln(m, n, l, x1, y1, z1, x2, y1, z1, r, g, bl, a, w); ln(m, n, l, x2, y1, z1, x2, y1, z2, r, g, bl, a, w);
        ln(m, n, l, x2, y1, z2, x1, y1, z2, r, g, bl, a, w); ln(m, n, l, x1, y1, z2, x1, y1, z1, r, g, bl, a, w);
        ln(m, n, l, x1, y2, z1, x2, y2, z1, r, g, bl, a, w); ln(m, n, l, x2, y2, z1, x2, y2, z2, r, g, bl, a, w);
        ln(m, n, l, x2, y2, z2, x1, y2, z2, r, g, bl, a, w); ln(m, n, l, x1, y2, z2, x1, y2, z1, r, g, bl, a, w);
        ln(m, n, l, x1, y1, z1, x1, y2, z1, r, g, bl, a, w); ln(m, n, l, x2, y1, z1, x2, y2, z1, r, g, bl, a, w);
        ln(m, n, l, x2, y1, z2, x2, y2, z2, r, g, bl, a, w); ln(m, n, l, x1, y1, z2, x1, y2, z2, r, g, bl, a, w);
        bufferSource.uploadAndDraw();
    }

    private static void quad(Matrix4f m, Matrix3f nm, VertexConsumer c, float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4, float nx, float ny, float nz, int r, int g, int b, int a) {
        Vector3f nv = new Vector3f(nx, ny, nz).mul(nm);
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nv.x(), nv.y(), nv.z());
        c.addVertex(m, x2, y2, z2).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nv.x(), nv.y(), nv.z());
        c.addVertex(m, x3, y3, z3).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nv.x(), nv.y(), nv.z());
        c.addVertex(m, x4, y4, z4).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(nv.x(), nv.y(), nv.z());
    }

    private static void ln(Matrix4f m, Matrix3f nm, VertexConsumer c, float x1, float y1, float z1, float x2, float y2, float z2,
                           int r, int g, int b, int a, float w) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len != 0) { dx /= len; dy /= len; dz /= len; } else dy = 1f;
        Vector3f nv = new Vector3f(dx, dy, dz).mul(nm);
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setNormal(nv.x(), nv.y(), nv.z()).setLineWidth(w);
        c.addVertex(m, x2, y2, z2).setColor(r, g, b, a).setNormal(nv.x(), nv.y(), nv.z()).setLineWidth(w);
    }

    private static InteractionHand hand(net.minecraft.world.entity.player.Player p) {
        ItemStack main = p.getMainHandItem();
        if (main.getItem() instanceof BlockItem) return InteractionHand.MAIN_HAND;
        if (main.isEmpty() && p.getOffhandItem().getItem() instanceof BlockItem) return InteractionHand.OFF_HAND;
        return null;
    }
}
