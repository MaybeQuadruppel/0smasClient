package com.OsamaClient.newbridge.Utils.Render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;

public class Renderer2D {
    public static Renderer2D COLOR;
    public static Renderer2D TEXTURE;

    private final boolean textured;

    public final MeshBuilder triangles;
    public final MeshBuilder lines;

    private final RenderPipeline coloredPipeline;
    private final RenderPipeline texturedPipeline;
    private final RenderPipeline linesPipeline;

    public Renderer2D(boolean textured, RenderPipeline coloredPipeline, RenderPipeline texturedPipeline, RenderPipeline linesPipeline) {
        this.textured = textured;
        this.coloredPipeline = coloredPipeline;
        this.texturedPipeline = texturedPipeline;
        this.linesPipeline = linesPipeline;

        this.triangles = new MeshBuilder(textured ? texturedPipeline : coloredPipeline);
        this.lines = new MeshBuilder(linesPipeline);
    }

    public static void init(RenderPipeline colored, RenderPipeline texturedPipeline, RenderPipeline lines) {
        COLOR = new Renderer2D(false, colored, texturedPipeline, lines);
        TEXTURE = new Renderer2D(true, colored, texturedPipeline, lines);
    }

    public void begin() {
        triangles.begin();
        lines.begin();
    }

    public void end() {
        if (lines.isBuilding()) lines.end();
        if (triangles.isBuilding()) triangles.end();
    }

    public void render() {
        render(null, null, null);
    }

    public void render(GpuTextureView textureView, GpuSampler sampler) {
        if (!textured)
            throw new IllegalStateException("Tried to render with a texture with a non-textured Renderer2D");

        render("u_Texture", textureView, sampler);
    }

    public void render(String samplerName, GpuTextureView samplerView, GpuSampler sampler) {
        if (lines.isBuilding()) lines.end();
        if (triangles.isBuilding()) triangles.end();

        PoseStack matrices = new PoseStack();

        MeshRenderer.begin()
                .attachments(Minecraft.getInstance().gameRenderer.mainRenderTarget())
                .pipeline(linesPipeline)
                .mesh(lines, matrices)
                .end();

        MeshRenderer.begin()
                .attachments(Minecraft.getInstance().gameRenderer.mainRenderTarget())
                .pipeline(textured ? texturedPipeline : coloredPipeline)
                .mesh(triangles, matrices)
                .end();
    }

    // ==========================================
    // GEOMETRY METHODEN
    // ==========================================

    public void triangle(double x1, double y1, double x2, double y2, double x3, double y3, Color color) {
        triangles.ensureCapacity(3, 3);
        triangles.triangle(
                triangles.vec2(x1, y1).color(color).next(),
                triangles.vec2(x2, y2).color(color).next(),
                triangles.vec2(x3, y3).color(color).next()
        );
    }

    public void line(double x1, double y1, double x2, double y2, Color color) {
        lines.ensureCapacity(2, 2);
        int i1 = lines.vec2(x1, y1).color(color).next();
        int i2 = lines.vec2(x2, y2).color(color).next();
        lines.line(i1, i2);
    }

    public void boxLines(double x, double y, double width, double height, Color color) {
        lines.ensureCapacity(4, 8);

        int i1 = lines.vec2(x, y).color(color).next();
        int i2 = lines.vec2(x, y + height).color(color).next();
        int i3 = lines.vec2(x + width, y + height).color(color).next();
        int i4 = lines.vec2(x + width, y).color(color).next();

        lines.line(i1, i2);
        lines.line(i2, i3);
        lines.line(i3, i4);
        lines.line(i4, i1);
    }

    public void quad(double x, double y, double width, double height, Color cTopLeft, Color cTopRight, Color cBottomRight, Color cBottomLeft) {
        triangles.ensureCapacity(4, 6);

        triangles.quad(
                triangles.vec2(x, y).color(cTopLeft).next(),
                triangles.vec2(x, y + height).color(cBottomLeft).next(),
                triangles.vec2(x + width, y + height).color(cBottomRight).next(),
                triangles.vec2(x + width, y).color(cTopRight).next()
        );
    }

    public void quad(double x, double y, double width, double height, Color color) {
        quad(x, y, width, height, color, color, color, color);
    }
}