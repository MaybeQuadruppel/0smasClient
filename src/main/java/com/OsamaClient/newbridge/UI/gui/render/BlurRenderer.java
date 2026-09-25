package com.OsamaClient.newbridge.UI.gui.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Background blur behind the GUI: dual-Kawase down/up sampling of the main render target through our own
 * half/quarter/eighth resolution textures, then blended back onto the main target. Our own pipelines only.
 */
public final class BlurRenderer {

    private static final int LEVELS = 3;
    private static final int PASSES = LEVELS * 2; // down x3, up x2, composite x1

    private final GpuTexture[] tex = new GpuTexture[LEVELS];
    private final GpuTextureView[] views = new GpuTextureView[LEVELS];
    private int w, h;
    private MappableRingBuffer vertices;
    private final ByteBuffer data = ByteBuffer.allocateDirect(PASSES * 4 * UiPipelines.BLUR_STRIDE).order(ByteOrder.nativeOrder());

    private void ensureTextures(int width, int height) {
        if (width == w && height == h && tex[0] != null) return;
        closeTextures();
        w = width;
        h = height;
        for (int i = 0; i < LEVELS; i++) {
            int lw = Math.max(1, width >> (i + 1)), lh = Math.max(1, height >> (i + 1));
            tex[i] = RenderSystem.getDevice().createTexture("newbridge blur " + i,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, lw, lh, 1, 1);
            views[i] = RenderSystem.getDevice().createTextureView(tex[i]);
        }
    }

    private void quad(float offset, int mode, int outW, int outH, float alpha) {
        float ix = 1f / outW, iy = 1f / outH;
        float[][] corners = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
        for (float[] c : corners) {
            data.putFloat(c[0]).putFloat(c[1]).putFloat(0f);
            data.putFloat(0f).putFloat(0f).putFloat(offset).putFloat(mode);
            data.putFloat(ix).putFloat(iy).putFloat(alpha).putFloat(0f);
        }
    }

    /** Blurs what is currently on the main target and blends it back with {@code alpha} (0..1). */
    public void render(float alpha, float strength) {
        if (alpha <= 0.001f) return;
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        ensureTextures(main.width, main.height);

        // pass list: src -> dst
        GpuTextureView[] src = {main.getColorTextureView(), views[0], views[1], views[2], views[1], views[0]};
        GpuTextureView[] dst = {views[0], views[1], views[2], views[1], views[0], main.getColorTextureView()};
        int[] mode = {0, 0, 0, 1, 1, 1};

        data.clear();
        for (int i = 0; i < PASSES; i++) {
            GpuTextureView d = dst[i];
            quad(strength, mode[i], d.getWidth(0), d.getHeight(0), i == PASSES - 1 ? alpha : 1f);
        }
        data.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        int bytes = data.remaining();
        if (vertices == null) {
            vertices = new MappableRingBuffer(() -> "newbridge blur quads",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE | GpuBuffer.USAGE_COPY_DST, bytes);
        }
        encoder.writeToBuffer(vertices.currentBuffer().slice(0L, bytes), data);
        RenderSystem.AutoStorageIndexBuffer quadIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = quadIndices.getBuffer(6);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        for (int i = 0; i < PASSES; i++) {
            RenderPipeline pipeline = i == PASSES - 1 ? UiPipelines.BLUR_COMPOSITE : UiPipelines.BLUR;
            try (RenderPass pass = encoder.createRenderPass(() -> "newbridge blur", dst[i], Optional.empty(), null,
                    OptionalDouble.empty())) {
                pass.setPipeline(pipeline);
                pass.bindTexture("Sampler0", src[i], sampler);
                pass.setVertexBuffer(0, vertices.currentBuffer().slice());
                pass.setIndexBuffer(indexBuffer, quadIndices.type());
                pass.drawIndexed(6, 1, 0, i * 4, 0);
            }
        }
        vertices.rotate();
    }

    private void closeTextures() {
        for (int i = 0; i < LEVELS; i++) {
            if (views[i] != null) { views[i].close(); views[i] = null; }
            if (tex[i] != null) { tex[i].close(); tex[i] = null; }
        }
    }

    public void close() {
        closeTextures();
        if (vertices != null) { vertices.close(); vertices = null; }
    }
}
