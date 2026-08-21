package com.OsamaClient.newbridge.Hacks.Visual.ESP;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.BlockPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockESP extends Module {
    public double rangeInChunks = 4.0;
    public double scanDelay = 2.0;

    public String renderMode = "Fill";
    public boolean renderTracers = false;

    private final BlockPicker blockPicker;
    private int tickCounter = 0;
    private boolean isScanning = false;

    private final List<RenderBox> renderBoxes = new CopyOnWriteArrayList<>();

    public BlockESP() {
        super("BlockESP", "Look-Up Blocks with Chams", Category.VISUAL);
        this.blockPicker = new BlockPicker("Block List").withDescription("Selects which blocks to highlight with ESP.");
        this.settings.add(this.blockPicker);
        this.settings.add(new Slider("Chunk Range", 1.0, 16.0, rangeInChunks, val -> rangeInChunks = val).withDescription("Sets the chunk render and scan range for block ESP."));
        this.settings.add(new Slider("Scan Delay (s)", 0.5, 10.0, scanDelay, val -> scanDelay = val).withDescription("Sets the delay in seconds between block scans."));

        List<String> modes = List.of("Fill", "Outline", "Both", "None");
        this.settings.add(new ModeButton("Mode", modes, modes.indexOf(renderMode), val -> renderMode = val).withDescription("Selects box rendering style (Fill, Outline, Both, None)."));
        this.settings.add(new ToggleButton("Tracers", renderTracers, val -> renderTracers = val).withDescription("Draws tracer lines from player to target blocks."));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.level == null || client.player == null || !this.enabled) return;

        if (blockPicker.selectedBlocks.isEmpty()) {
            renderBoxes.clear();
            return;
        }

        if (tickCounter++ % (int) (scanDelay * 20) != 0) return;
        if (isScanning) return;

        ClientLevel level = client.level;
        int pX = client.player.chunkPosition().x();
        int pZ = client.player.chunkPosition().z();
        int radius = (int) rangeInChunks;

        new Thread(() -> {
            isScanning = true;
            try {
                List<RenderBox> found = new ArrayList<>();

                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        LevelChunk chunk = level.getChunk(pX + x, pZ + z);
                        if (chunk != null) {
                            scanChunk(level, chunk, found);
                        }
                    }
                }
                renderBoxes.clear();
                renderBoxes.addAll(found);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isScanning = false;
            }
        }).start();
    }

    private void scanChunk(ClientLevel level, LevelChunk chunk, List<RenderBox> found) {
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];

            if (section == null || section.hasOnlyAir()) continue;
            if (!section.maybeHas(state -> blockPicker.selectedBlocks.contains(state.getBlock()))) continue;

            int yBase = chunk.getSectionYFromSectionIndex(i) << 4;
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        Block block = state.getBlock();

                        if (blockPicker.selectedBlocks.contains(block)) {
                            pos.set(minX + x, yBase + y, minZ + z);
                            int color = 0x6600FFFF;
                            try {
                                color = RenderUtils.BLOCK_COLORS.getOrDefault(block, 0x6600FFFF);
                            } catch (Exception ignored) {}

                            AABB bounds = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
                            found.add(new RenderBox(bounds, color));
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled || renderBoxes.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer.mainCamera() == null) return;

        PoseStack poseStack = event.getPoseStack();
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();

        boolean drawFill = (renderMode.equals("Fill") || renderMode.equals("Both")) && !renderMode.equals("None");
        boolean drawOutline = (renderMode.equals("Outline") || renderMode.equals("Both")) && !renderMode.equals("None");

        ChamsBufferSource bufferSource = new ChamsBufferSource();
        Camera camera = mc.gameRenderer.mainCamera();
        double camX = camera.position().x;
        double camY = camera.position().y;
        double camZ = camera.position().z;

        float startX = 0f, startY = 0f, startZ = 0f;
        if (renderTracers) {
            float pitch = camera.xRot();
            float yaw = camera.yRot();
            float f = (float) Math.PI / 180.0F;

            float dirX = -((float) Math.sin(yaw * f)) * ((float) Math.cos(pitch * f));
            float dirY = -((float) Math.sin(pitch * f));
            float dirZ = ((float) Math.cos(yaw * f)) * ((float) Math.cos(pitch * f));

            float offset = 50.0f;
            startX = dirX * offset;
            startY = dirY * offset;
            startZ = dirZ * offset;
        }

        // --- SCHLEIFE 1: FILLS ---
        if (drawFill) {
            VertexConsumer fillConsumer = bufferSource.getBuffer(RenderTypes.storageEspFillSeeThrough());
            for (RenderBox box : renderBoxes) {
                float minX = (float) (box.bounds().minX - camX);
                float minY = (float) (box.bounds().minY - camY);
                float minZ = (float) (box.bounds().minZ - camZ);
                float maxX = (float) (box.bounds().maxX - camX);
                float maxY = (float) (box.bounds().maxY - camY);
                float maxZ = (float) (box.bounds().maxZ - camZ);

                renderFilledBox(matrix, normalMatrix, fillConsumer, minX, minY, minZ, maxX, maxY, maxZ, box.argb());
            }
        }

        // --- SCHLEIFE 2: OUTLINES ---
        if (drawOutline) {
            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
            for (RenderBox box : renderBoxes) {
                float minX = (float) (box.bounds().minX - camX);
                float minY = (float) (box.bounds().minY - camY);
                float minZ = (float) (box.bounds().minZ - camZ);
                float maxX = (float) (box.bounds().maxX - camX);
                float maxY = (float) (box.bounds().maxY - camY);
                float maxZ = (float) (box.bounds().maxZ - camZ);

                renderBoxOutline(matrix, normalMatrix, lineConsumer, minX, minY, minZ, maxX, maxY, maxZ, box.argb());
            }
        }

        // --- SCHLEIFE 3: TRACERS ---
        if (renderTracers) {
            VertexConsumer tracerConsumer = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
            for (RenderBox box : renderBoxes) {
                float minX = (float) (box.bounds().minX - camX);
                float minY = (float) (box.bounds().minY - camY);
                float minZ = (float) (box.bounds().minZ - camZ);
                float maxX = (float) (box.bounds().maxX - camX);
                float maxY = (float) (box.bounds().maxY - camY);
                float maxZ = (float) (box.bounds().maxZ - camZ);

                float targetX = minX + ((maxX - minX) / 2.0f);
                float targetY = minY + ((maxY - minY) / 2.0f);
                float targetZ = minZ + ((maxZ - minZ) / 2.0f);

                line(matrix, normalMatrix, tracerConsumer, startX, startY, startZ, targetX, targetY, targetZ, box.argb());
            }
        }

        bufferSource.uploadAndDraw();
    }

    private static void renderFilledBox(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        addQuad(matrix, normalMatrix, consumer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0, -1, 0, r, g, b, a);
        addQuad(matrix, normalMatrix, consumer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, 0, 1, 0, r, g, b, a);
        addQuad(matrix, normalMatrix, consumer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, 0, 0, -1, r, g, b, a);
        addQuad(matrix, normalMatrix, consumer, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, 0, 0, 1, r, g, b, a);
        addQuad(matrix, normalMatrix, consumer, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, -1, 0, 0, r, g, b, a);
        addQuad(matrix, normalMatrix, consumer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, 1, 0, 0, r, g, b, a);
    }

    private static void addQuad(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float x3, float y3, float z3, float x4, float y4, float z4,
                                float nx, float ny, float nz, int r, int g, int b, int a) {

        Vector3f normal = new Vector3f(nx, ny, nz);
        normal.mul(normalMatrix);

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(matrix, x4, y4, z4).setColor(r, g, b, a).setUv(0f, 0f).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(normal.x(), normal.y(), normal.z());
    }

    private static void renderBoxOutline(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        line(matrix, normalMatrix, consumer, x1, y1, z1, x2, y1, z1, color);
        line(matrix, normalMatrix, consumer, x2, y1, z1, x2, y1, z2, color);
        line(matrix, normalMatrix, consumer, x2, y1, z2, x1, y1, z2, color);
        line(matrix, normalMatrix, consumer, x1, y1, z2, x1, y1, z1, color);

        line(matrix, normalMatrix, consumer, x1, y2, z1, x2, y2, z1, color);
        line(matrix, normalMatrix, consumer, x2, y2, z1, x2, y2, z2, color);
        line(matrix, normalMatrix, consumer, x2, y2, z2, x1, y2, z2, color);
        line(matrix, normalMatrix, consumer, x1, y2, z2, x1, y2, z1, color);

        line(matrix, normalMatrix, consumer, x1, y1, z1, x1, y2, z1, color);
        line(matrix, normalMatrix, consumer, x2, y1, z1, x2, y2, z1, color);
        line(matrix, normalMatrix, consumer, x2, y1, z2, x2, y2, z2, color);
        line(matrix, normalMatrix, consumer, x1, y1, z2, x1, y2, z2, color);
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer, float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        line(matrix, normalMatrix, consumer, x1, y1, z1, x2, y2, z2, r, g, b, a);
    }

    private static void line(Matrix4f matrix, Matrix3f normalMatrix, VertexConsumer consumer,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             int r, int g, int b, int a) {

        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;

        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len != 0) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dy = 1.0f;
        }

        Vector3f normal = new Vector3f(dx, dy, dz);
        normal.mul(normalMatrix);

        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z()).setLineWidth(5.0f);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(normal.x(), normal.y(), normal.z()).setLineWidth(5.0f);
    }

    @Override
    public void onDisable() {
        renderBoxes.clear();
        super.onDisable();
    }

    private record RenderBox(AABB bounds, int argb) {}
}