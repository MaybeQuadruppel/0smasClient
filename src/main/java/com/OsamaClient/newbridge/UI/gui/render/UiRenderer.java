package com.OsamaClient.newbridge.UI.gui.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Our own immediate-mode renderer on blaze3d. Works in framebuffer pixels (0..fbW x 0..fbH, y down), collects SDF
 * shapes and glyphs in submission order and draws them with one RenderPass on the main render target.
 * Nothing here touches Minecraft's GUI rendering.
 */
public final class UiRenderer {

    private static final int TYPE_SDF = 0, TYPE_TEXT = 1;

    private final QuadBuffer sdf = new QuadBuffer(UiPipelines.SDF_STRIDE, 4096);
    private final QuadBuffer text = new QuadBuffer(UiPipelines.TEXT_STRIDE, 4096);

    // ordered draw commands (parallel arrays, reused)
    private int cmds;
    private int[] cmdType = new int[64], cmdFirst = new int[64], cmdCount = new int[64];
    private int[] cmdClip = new int[64 * 4];
    private GpuTextureView[] cmdTex = new GpuTextureView[64];

    // clip stack in framebuffer px, top-left origin; clip[0] < 0 means "no clip"
    private final int[] clipStack = new int[32 * 4];
    private int clipDepth;
    private final int[] clip = {-1, 0, 0, 0};

    private int fbW, fbH;

    private MappableRingBuffer sdfGpu, textGpu;
    private ProjectionMatrixBuffer projectionBuffer;
    private final Projection projection = new Projection();
    private final Matrix4f modelView = new Matrix4f().setTranslation(0f, 0f, -6000f);

    public int width() { return fbW; }
    public int height() { return fbH; }

    public void begin() {
        RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        fbW = target.width;
        fbH = target.height;
        sdf.reset();
        text.reset();
        cmds = 0;
        clipDepth = 0;
        clip[0] = -1;
    }

    // ------------------------------------------------------------------ clipping

    /** Intersects the current clip with the given pixel rect. Must be balanced with {@link #popClip()}. */
    public void pushClip(float x, float y, float w, float h) {
        System.arraycopy(clip, 0, clipStack, clipDepth * 4, 4);
        clipDepth++;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        int x1 = (int) Math.ceil(x + w), y1 = (int) Math.ceil(y + h);
        if (clip[0] >= 0) {
            x0 = Math.max(x0, clip[0]);
            y0 = Math.max(y0, clip[1]);
            x1 = Math.min(x1, clip[0] + clip[2]);
            y1 = Math.min(y1, clip[1] + clip[3]);
        }
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        clip[0] = x0;
        clip[1] = y0;
        clip[2] = Math.max(0, x1 - x0);
        clip[3] = Math.max(0, y1 - y0);
    }

    public void popClip() {
        if (clipDepth == 0) return;
        clipDepth--;
        System.arraycopy(clipStack, clipDepth * 4, clip, 0, 4);
    }

    /** True if the rect is completely outside the current clip (callers may skip drawing). */
    public boolean clipped(float x, float y, float w, float h) {
        if (clip[0] < 0) return x > fbW || y > fbH || x + w < 0 || y + h < 0;
        return x > clip[0] + clip[2] || y > clip[1] + clip[3] || x + w < clip[0] || y + h < clip[1];
    }

    // ------------------------------------------------------------------ primitives

    /**
     * One SDF quad. Coordinates in framebuffer pixels. mode 0 = fill, 1 = outline (param = thickness),
     * 2 = glow (param = radius). Colors are ARGB for the corners top-left, top-right, bottom-right, bottom-left.
     */
    public void shape(float x, float y, float w, float h, float radius, int mode, float param,
                      int cTL, int cTR, int cBR, int cBL) {
        if (w <= 0 || h <= 0) return;
        if (((cTL | cTR | cBR | cBL) >>> 24) == 0) return;
        float e = mode == 2 ? param + 1f : 1f;
        if (clipped(x - e, y - e, w + 2 * e, h + 2 * e)) return;
        command(TYPE_SDF, null, sdf.vertexCount());
        float hw = w * 0.5f, hh = h * 0.5f;
        float cx = x + hw, cy = y + hh;
        float x0 = x - e, y0 = y - e, x1 = x + w + e, y1 = y + h + e;
        ByteBuffer b = sdf.reserve(4);
        sdfVertex(b, x0, y0, cx, cy, cTL, mode, param, hw, hh, radius);
        sdfVertex(b, x0, y1, cx, cy, cBL, mode, param, hw, hh, radius);
        sdfVertex(b, x1, y1, cx, cy, cBR, mode, param, hw, hh, radius);
        sdfVertex(b, x1, y0, cx, cy, cTR, mode, param, hw, hh, radius);
    }

    private static void sdfVertex(ByteBuffer b, float x, float y, float cx, float cy, int color, int mode, float param,
                                  float hw, float hh, float radius) {
        b.putFloat(x).putFloat(y).putFloat(0f);
        QuadBuffer.putColor(b, color);
        b.putFloat(x - cx).putFloat(y - cy).putFloat(mode).putFloat(param);
        b.putFloat(hw).putFloat(hh).putFloat(radius).putFloat(0f);
    }

    /** One textured glyph quad in framebuffer pixels. */
    public void glyph(GpuTextureView atlas, float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1, int argb) {
        if ((argb >>> 24) == 0) return;
        if (clipped(x0, y0, x1 - x0, y1 - y0)) return;
        command(TYPE_TEXT, atlas, text.vertexCount());
        ByteBuffer b = text.reserve(4);
        b.putFloat(x0).putFloat(y0).putFloat(0f).putFloat(u0).putFloat(v0);
        QuadBuffer.putColor(b, argb);
        b.putFloat(x0).putFloat(y1).putFloat(0f).putFloat(u0).putFloat(v1);
        QuadBuffer.putColor(b, argb);
        b.putFloat(x1).putFloat(y1).putFloat(0f).putFloat(u1).putFloat(v1);
        QuadBuffer.putColor(b, argb);
        b.putFloat(x1).putFloat(y0).putFloat(0f).putFloat(u1).putFloat(v0);
        QuadBuffer.putColor(b, argb);
    }

    /** Appends 4 vertices to the last command if its state matches, otherwise starts a new command. */
    private void command(int type, GpuTextureView tex, int firstVertex) {
        if (cmds > 0) {
            int i = cmds - 1;
            int c = i * 4;
            if (cmdType[i] == type && cmdTex[i] == tex && cmdClip[c] == clip[0] && cmdClip[c + 1] == clip[1]
                    && cmdClip[c + 2] == clip[2] && cmdClip[c + 3] == clip[3]) {
                cmdCount[i] += 4;
                return;
            }
        }
        if (cmds == cmdType.length) {
            int n = cmds * 2;
            cmdType = Arrays.copyOf(cmdType, n);
            cmdFirst = Arrays.copyOf(cmdFirst, n);
            cmdCount = Arrays.copyOf(cmdCount, n);
            cmdTex = Arrays.copyOf(cmdTex, n);
            cmdClip = Arrays.copyOf(cmdClip, n * 4);
        }
        cmdType[cmds] = type;
        cmdTex[cmds] = tex;
        cmdFirst[cmds] = firstVertex;
        cmdCount[cmds] = 4;
        System.arraycopy(clip, 0, cmdClip, cmds * 4, 4);
        cmds++;
    }

    // ------------------------------------------------------------------ flush

    public void flush() {
        if (cmds == 0) return;
        Minecraft mc = Minecraft.getInstance();
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        sdfGpu = upload(encoder, sdfGpu, sdf, "newbridge ui sdf");
        textGpu = upload(encoder, textGpu, text, "newbridge ui text");

        int maxIndices = 0;
        for (int i = 0; i < cmds; i++) maxIndices = Math.max(maxIndices, cmdCount[i] / 4 * 6);
        RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = quadIndices.getBuffer(maxIndices);

        if (projectionBuffer == null) projectionBuffer = new ProjectionMatrixBuffer("newbridge ui");
        projection.setupOrtho(1000f, 11000f, fbW, fbH, true);
        GpuBufferSlice projSlice = projectionBuffer.getBuffer(projection);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        try (RenderPass pass = encoder.createRenderPass(() -> "newbridge ui", target.getColorTextureView(),
                Optional.empty(), null, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("Projection", projSlice);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setIndexBuffer(indexBuffer, quadIndices.type());

            int boundType = -1;
            GpuTextureView boundTex = null;
            boolean scissorOn = false;
            for (int i = 0; i < cmds; i++) {
                int type = cmdType[i];
                if (type != boundType) {
                    RenderPipeline pipeline = type == TYPE_SDF ? UiPipelines.SDF : UiPipelines.TEXT;
                    pass.setPipeline(pipeline);
                    pass.setVertexBuffer(0, (type == TYPE_SDF ? sdfGpu : textGpu).currentBuffer().slice());
                    boundType = type;
                    boundTex = null;
                }
                if (type == TYPE_TEXT && cmdTex[i] != boundTex) {
                    pass.bindTexture("Sampler0", cmdTex[i], sampler);
                    boundTex = cmdTex[i];
                }
                int c = i * 4;
                if (cmdClip[c] >= 0) {
                    int sx = cmdClip[c], sy = cmdClip[c + 1], sw = cmdClip[c + 2], sh = cmdClip[c + 3];
                    if (sw == 0 || sh == 0) continue;
                    pass.enableScissor(sx, Math.max(0, fbH - (sy + sh)), sw, sh);
                    scissorOn = true;
                } else if (scissorOn) {
                    pass.disableScissor();
                    scissorOn = false;
                }
                pass.drawIndexed(cmdCount[i] / 4 * 6, 1, 0, cmdFirst[i], 0);
            }
        }

        if (sdfGpu != null) sdfGpu.rotate();
        if (textGpu != null) textGpu.rotate();
    }

    private static MappableRingBuffer upload(CommandEncoder encoder, MappableRingBuffer gpu, QuadBuffer data, String label) {
        int bytes = data.byteSize();
        if (bytes == 0) return gpu;
        if (gpu == null || gpu.size() < bytes) {
            if (gpu != null) gpu.close();
            int size = 1 << 16;
            while (size < bytes) size <<= 1;
            gpu = new MappableRingBuffer(() -> label,
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST, size);
        }
        encoder.writeToBuffer(gpu.currentBuffer().slice(0L, bytes), data.view());
        return gpu;
    }

    /** Frees all GPU resources (on shutdown). */
    public void close() {
        if (sdfGpu != null) { sdfGpu.close(); sdfGpu = null; }
        if (textGpu != null) { textGpu.close(); textGpu = null; }
        if (projectionBuffer != null) { projectionBuffer.close(); projectionBuffer = null; }
    }
}
