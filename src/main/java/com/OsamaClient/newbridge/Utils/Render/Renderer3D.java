package com.OsamaClient.newbridge.Utils.Render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

public class Renderer3D {
    public enum ShapeMode {
        LINES, SIDES, BOTH;
        public boolean lines() { return this == LINES || this == BOTH; }
        public boolean sides() { return this == SIDES || this == BOTH; }
    }

    public static class Dir {
        public static final int DOWN = 1, UP = 2, NORTH = 4, SOUTH = 8, WEST = 16, EAST = 32;
        public static boolean isNot(int excludeDir, int dir) { return (excludeDir & dir) == 0; }
    }
    // -------------------------------------------------------------

    // Normale Meshes (mit Depth-Test)
    public final MeshBuilder lines;
    public final MeshBuilder triangles;
    private final RenderPipeline linesPipeline;
    private final RenderPipeline trianglesPipeline;

    // ESP Meshes (ohne Depth-Test, durch Wände sichtbar)
    public final MeshBuilder linesNoDepth;
    public final MeshBuilder trianglesNoDepth;
    private final RenderPipeline linesPipelineNoDepth;
    private final RenderPipeline trianglesPipelineNoDepth;

    // Konstruktor für normale Nutzung (Fallback)
    public Renderer3D(RenderPipeline lines, RenderPipeline triangles) {
        this(lines, triangles, lines, triangles);
    }

    // Konstruktor mit separaten Pipelines für ESP (Depth-Test aus)
    public Renderer3D(RenderPipeline lines, RenderPipeline triangles, RenderPipeline linesNoDepth, RenderPipeline trianglesNoDepth) {
        this.lines = new MeshBuilder(lines);
        this.triangles = new MeshBuilder(triangles);
        this.linesNoDepth = new MeshBuilder(linesNoDepth);
        this.trianglesNoDepth = new MeshBuilder(trianglesNoDepth);

        this.linesPipeline = lines;
        this.trianglesPipeline = triangles;
        this.linesPipelineNoDepth = linesNoDepth;
        this.trianglesPipelineNoDepth = trianglesNoDepth;
    }

    public void begin() {
        lines.begin();
        triangles.begin();
        linesNoDepth.begin();
        trianglesNoDepth.begin();
    }

    public void render(PoseStack matrices) {
        // 1. Normales Rendering (mit Depth)
        MeshRenderer.begin()
                .attachments(Minecraft.getInstance().gameRenderer.mainRenderTarget())
                .pipeline(linesPipeline)
                .mesh(lines, matrices)
                .end();

        MeshRenderer.begin()
                .attachments(Minecraft.getInstance().gameRenderer.mainRenderTarget())
                .pipeline(trianglesPipeline)
                .mesh(triangles, matrices)
                .end();

        // 2. ESP Rendering (ohne Depth / durch Wände)
        MeshRenderer.begin()
                .attachments(Minecraft.getInstance().gameRenderer.mainRenderTarget())
                .pipeline(linesPipelineNoDepth)
                .mesh(linesNoDepth, matrices)
                .end();

        MeshRenderer.begin()
                .attachments(Minecraft.getInstance().gameRenderer.mainRenderTarget())
                .pipeline(trianglesPipelineNoDepth)
                .mesh(trianglesNoDepth, matrices)
                .end();
    }

    // ==========================================
    // NORMALE METHODEN (Mit Tiefenprüfung)
    // ==========================================

    public void boxLines(double x1, double y1, double z1, double x2, double y2, double z2, Color color, int excludeDir) {
        drawBoxLines(lines, x1, y1, z1, x2, y2, z2, color, excludeDir);
    }

    public void boxSides(double x1, double y1, double z1, double x2, double y2, double z2, Color color, int excludeDir) {
        drawBoxSides(triangles, x1, y1, z1, x2, y2, z2, color, excludeDir);
    }

    public void box(double x1, double y1, double z1, double x2, double y2, double z2, Color sideColor, Color lineColor, ShapeMode mode, int excludeDir) {
        if (mode.lines()) boxLines(x1, y1, z1, x2, y2, z2, lineColor, excludeDir);
        if (mode.sides()) boxSides(x1, y1, z1, x2, y2, z2, sideColor, excludeDir);
    }

    public void box(AABB box, Color sideColor, Color lineColor, ShapeMode mode, int excludeDir) {
        box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, sideColor, lineColor, mode, excludeDir);
    }

    // ==========================================
    // ESP METHODEN (Ohne Tiefenprüfung / Durch Wände)
    // ==========================================

    public void lineNoDepth(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        linesNoDepth.ensureCapacity(2, 2);
        int i1 = linesNoDepth.vec3(x1, y1, z1).color(color).next();
        int i2 = linesNoDepth.vec3(x2, y2, z2).color(color).next();
        linesNoDepth.line(i1, i2);
    }

    // ==========================================
    // NORMALE METHODEN (Mit Tiefenprüfung)
    // ==========================================

    public void line(double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
        lines.ensureCapacity(2, 2);
        int i1 = lines.vec3(x1, y1, z1).color(color).next();
        int i2 = lines.vec3(x2, y2, z2).color(color).next();
        lines.line(i1, i2);
    }

    public void boxLinesNoDepth(double x1, double y1, double z1, double x2, double y2, double z2, Color color, int excludeDir) {
        drawBoxLines(linesNoDepth, x1, y1, z1, x2, y2, z2, color, excludeDir);
    }

    public void boxSidesNoDepth(double x1, double y1, double z1, double x2, double y2, double z2, Color color, int excludeDir) {
        drawBoxSides(trianglesNoDepth, x1, y1, z1, x2, y2, z2, color, excludeDir);
    }

    public void boxNoDepth(double x1, double y1, double z1, double x2, double y2, double z2, Color sideColor, Color lineColor, ShapeMode mode, int excludeDir) {
        if (mode.lines()) boxLinesNoDepth(x1, y1, z1, x2, y2, z2, lineColor, excludeDir);
        if (mode.sides()) boxSidesNoDepth(x1, y1, z1, x2, y2, z2, sideColor, excludeDir);
    }

    public void boxNoDepth(AABB box, Color sideColor, Color lineColor, ShapeMode mode, int excludeDir) {
        boxNoDepth(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, sideColor, lineColor, mode, excludeDir);
    }

    // ==========================================
    // INTERNE HILFSMETHODEN (DRY Prinzip)
    // ==========================================

    private void drawBoxLines(MeshBuilder builder, double x1, double y1, double z1, double x2, double y2, double z2, Color color, int excludeDir) {
        builder.ensureCapacity(8, 24);

        int blb = builder.vec3(x1, y1, z1).color(color).next();
        int blf = builder.vec3(x1, y1, z2).color(color).next();
        int brb = builder.vec3(x2, y1, z1).color(color).next();
        int brf = builder.vec3(x2, y1, z2).color(color).next();
        int tlb = builder.vec3(x1, y2, z1).color(color).next();
        int tlf = builder.vec3(x1, y2, z2).color(color).next();
        int trb = builder.vec3(x2, y2, z1).color(color).next();
        int trf = builder.vec3(x2, y2, z2).color(color).next();

        if (excludeDir == 0) {
            builder.line(blb, tlb); builder.line(blf, tlf); builder.line(brb, trb); builder.line(brf, trf);
            builder.line(blb, blf); builder.line(brb, brf); builder.line(blb, brb); builder.line(blf, brf);
            builder.line(tlb, tlf); builder.line(trb, trf); builder.line(tlb, trb); builder.line(tlf, trf);
        } else {
            if (Dir.isNot(excludeDir, Dir.WEST) && Dir.isNot(excludeDir, Dir.NORTH)) builder.line(blb, tlb);
            if (Dir.isNot(excludeDir, Dir.WEST) && Dir.isNot(excludeDir, Dir.SOUTH)) builder.line(blf, tlf);
            if (Dir.isNot(excludeDir, Dir.EAST) && Dir.isNot(excludeDir, Dir.NORTH)) builder.line(brb, trb);
            if (Dir.isNot(excludeDir, Dir.EAST) && Dir.isNot(excludeDir, Dir.SOUTH)) builder.line(brf, trf);
            if (Dir.isNot(excludeDir, Dir.WEST) && Dir.isNot(excludeDir, Dir.DOWN)) builder.line(blb, blf);
            if (Dir.isNot(excludeDir, Dir.EAST) && Dir.isNot(excludeDir, Dir.DOWN)) builder.line(brb, brf);
            if (Dir.isNot(excludeDir, Dir.NORTH) && Dir.isNot(excludeDir, Dir.DOWN)) builder.line(blb, brb);
            if (Dir.isNot(excludeDir, Dir.SOUTH) && Dir.isNot(excludeDir, Dir.DOWN)) builder.line(blf, brf);
            if (Dir.isNot(excludeDir, Dir.WEST) && Dir.isNot(excludeDir, Dir.UP)) builder.line(tlb, tlf);
            if (Dir.isNot(excludeDir, Dir.EAST) && Dir.isNot(excludeDir,Dir.UP)) builder.line(trb, trf);
            if (Dir.isNot(excludeDir, Dir.NORTH) && Dir.isNot(excludeDir, Dir.UP)) builder.line(tlb, trb);
            if (Dir.isNot(excludeDir, Dir.SOUTH) && Dir.isNot(excludeDir, Dir.UP)) builder.line(tlf, trf);
        }
    }

    private void drawBoxSides(MeshBuilder builder, double x1, double y1, double z1, double x2, double y2, double z2, Color color, int excludeDir) {
        builder.ensureCapacity(8, 36);

        int blb = builder.vec3(x1, y1, z1).color(color).next();
        int blf = builder.vec3(x1, y1, z2).color(color).next();
        int brb = builder.vec3(x2, y1, z1).color(color).next();
        int brf = builder.vec3(x2, y1, z2).color(color).next();
        int tlb = builder.vec3(x1, y2, z1).color(color).next();
        int tlf = builder.vec3(x1, y2, z2).color(color).next();
        int trb = builder.vec3(x2, y2, z1).color(color).next();
        int trf = builder.vec3(x2, y2, z2).color(color).next();

        if (excludeDir == 0) {
            builder.quad(blb, blf, tlf, tlb);
            builder.quad(brb, trb, trf, brf);
            builder.quad(blb, tlb, trb, brb);
            builder.quad(blf, brf, trf, tlf);
            builder.quad(blb, brb, brf, blf);
            builder.quad(tlb, tlf, trf, trb);
        } else {
            if (Dir.isNot(excludeDir, Dir.WEST)) builder.quad(blb, blf, tlf, tlb);
            if (Dir.isNot(excludeDir, Dir.EAST)) builder.quad(brb, trb, trf, brf);
            if (Dir.isNot(excludeDir, Dir.NORTH)) builder.quad(blb, tlb, trb, brb);
            if (Dir.isNot(excludeDir, Dir.SOUTH)) builder.quad(blf, brf, trf, tlf);
            if (Dir.isNot(excludeDir, Dir.DOWN)) builder.quad(blb, brb, brf, blf);
            if (Dir.isNot(excludeDir, Dir.UP)) builder.quad(tlb, tlf, trf, trb);
        }
    }

    public void boxRotatedNoDepth(double x, double y, double z, double w, double h, float yaw, Color sideColor, Color lineColor, ShapeMode mode) {
        float rad = (float) Math.toRadians(yaw);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        double c1x = -w * cos - (-w) * sin;
        double c1z = -w * sin + (-w) * cos;
        double c2x = -w * cos - w * sin;
        double c2z = -w * sin + w * cos;
        double c3x = w * cos - (-w) * sin;
        double c3z = w * sin + (-w) * cos;
        double c4x = w * cos - w * sin;
        double c4z = w * sin + w * cos;

        if (mode.lines()) {
            linesNoDepth.ensureCapacity(8, 24);
            int blb = linesNoDepth.vec3(x + c1x, y, z + c1z).color(lineColor).next();
            int blf = linesNoDepth.vec3(x + c2x, y, z + c2z).color(lineColor).next();
            int brb = linesNoDepth.vec3(x + c3x, y, z + c3z).color(lineColor).next();
            int brf = linesNoDepth.vec3(x + c4x, y, z + c4z).color(lineColor).next();
            int tlb = linesNoDepth.vec3(x + c1x, y + h, z + c1z).color(lineColor).next();
            int tlf = linesNoDepth.vec3(x + c2x, y + h, z + c2z).color(lineColor).next();
            int trb = linesNoDepth.vec3(x + c3x, y + h, z + c3z).color(lineColor).next();
            int trf = linesNoDepth.vec3(x + c4x, y + h, z + c4z).color(lineColor).next();

            linesNoDepth.line(blb, tlb); linesNoDepth.line(blf, tlf); linesNoDepth.line(brb, trb); linesNoDepth.line(brf, trf);
            linesNoDepth.line(blb, blf); linesNoDepth.line(brb, brf); linesNoDepth.line(blb, brb); linesNoDepth.line(blf, brf);
            linesNoDepth.line(tlb, tlf); linesNoDepth.line(trb, trf); linesNoDepth.line(tlb, trb); linesNoDepth.line(tlf, trf);
        }

        if (mode.sides()) {
            trianglesNoDepth.ensureCapacity(8, 36);
            int blb = trianglesNoDepth.vec3(x + c1x, y, z + c1z).color(sideColor).next();
            int blf = trianglesNoDepth.vec3(x + c2x, y, z + c2z).color(sideColor).next();
            int brb = trianglesNoDepth.vec3(x + c3x, y, z + c3z).color(sideColor).next();
            int brf = trianglesNoDepth.vec3(x + c4x, y, z + c4z).color(sideColor).next();
            int tlb = trianglesNoDepth.vec3(x + c1x, y + h, z + c1z).color(sideColor).next();
            int tlf = trianglesNoDepth.vec3(x + c2x, y + h, z + c2z).color(sideColor).next();
            int trb = trianglesNoDepth.vec3(x + c3x, y + h, z + c3z).color(sideColor).next();
            int trf = trianglesNoDepth.vec3(x + c4x, y + h, z + c4z).color(sideColor).next();

            trianglesNoDepth.quad(blb, blf, tlf, tlb);
            trianglesNoDepth.quad(brb, trb, trf, brf);
            trianglesNoDepth.quad(blb, tlb, trb, brb);
            trianglesNoDepth.quad(blf, brf, trf, tlf);
            trianglesNoDepth.quad(blb, brb, brf, blf);
            trianglesNoDepth.quad(tlb, tlf, trf, trb);
        }
    }
}