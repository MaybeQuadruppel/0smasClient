package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Smooth limb bending by vertex skinning: the cube of a limb stays ONE vanilla box (perfect texture,
 * no gaps); at render time its side faces are cut into thin strips and every vertex is rotated around
 * the joint by an angle that grows smoothly with its distance below the joint. Armor and skin overlays
 * (children of the limb part) inherit the bend automatically.
 */
public final class BendRender {

    /** Half width of the blend zone around the joint, model pixels. */
    public static final float RADIUS = 4f;
    /** Strip height, model pixels. */
    public static final float STRIP = 1.5f;
    public static final int MAX_STRIPS = 12;
    private static final float SCALE = 1f / 16f;

    /** Bend of the part currently being rendered (inherited by its child parts), or null. */
    public static BendHolder current;

    private static final Quaternionf Q = new Quaternionf();
    private static final Vector3f P = new Vector3f();
    private static final Vector3f N = new Vector3f();
    private static final Vector3f NT = new Vector3f();
    private static final Vector3f OUT = new Vector3f();

    private BendRender() {}

    /** Smooth 0..1 weight: 0 above the joint zone, 1 below it (y in model pixels, limb local). */
    public static float weight(float yPx, float pivotY) {
        float t = (yPx - (pivotY - RADIUS)) / (2f * RADIUS);
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    /** Vertex positions are in model units (pixels / 16), rotations in radians. Result written to OUT / NT. */
    private static void deform(float x, float y, float z, float nx, float ny, float nz, BendHolder b) {
        float yPx = y * 16f;
        float w = weight(yPx, b.newbridge$bendPivotY());
        if (w <= 0f) {
            P.set(x, y, z);
            N.set(nx, ny, nz);
            return;
        }
        float pv = b.newbridge$bendPivotY() * SCALE;
        Q.rotationZYX(b.newbridge$bendZ() * w, b.newbridge$bendY() * w, b.newbridge$bendX() * w);
        P.set(x, y - pv, z);
        Q.transform(P);
        P.y += pv;
        N.set(nx, ny, nz);
        Q.transform(N);
    }

    /** One output vertex. */
    public interface Sink {
        void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    /**
     * Emits the (strip-subdivided, deformed) polygons of a cube in model space (no pose applied).
     * Used by the renderer and by the headless tests.
     */
    public static void tessellate(ModelPart.Polygon[] polygons, BendHolder bend, Sink sink) {
        for (ModelPart.Polygon poly : polygons) {
            ModelPart.Vertex[] v = poly.vertices();
            float nx = poly.normal().x(), ny = poly.normal().y(), nz = poly.normal().z();

            int off = -1;
            if (Math.abs(v[0].worldY() - v[1].worldY()) > 1.0E-5f) off = 0;
            else if (Math.abs(v[1].worldY() - v[2].worldY()) > 1.0E-5f) off = 1;

            float pivot = bend.newbridge$bendPivotY();
            float minY = Math.min(Math.min(v[0].worldY(), v[1].worldY()), Math.min(v[2].worldY(), v[3].worldY())) * 16f;
            float maxY = Math.max(Math.max(v[0].worldY(), v[1].worldY()), Math.max(v[2].worldY(), v[3].worldY())) * 16f;
            boolean inZone = maxY > pivot - RADIUS && minY < pivot + RADIUS;

            if (off < 0 || !inZone) {
                for (ModelPart.Vertex vx : v) emit(sink, bend, vx.worldX(), vx.worldY(), vx.worldZ(), vx.u(), vx.v(), nx, ny, nz);
                continue;
            }

            ModelPart.Vertex a0 = v[off], a1 = v[(off + 1) & 3], a2 = v[(off + 2) & 3], a3 = v[(off + 3) & 3];
            float heightPx = Math.abs(a0.worldY() - a1.worldY()) * 16f;
            int strips = Math.max(1, Math.min(MAX_STRIPS, (int) Math.ceil(heightPx / STRIP)));
            for (int i = 0; i < strips; i++) {
                float t0 = (float) i / strips, t1 = (float) (i + 1) / strips;
                emitLerp(sink, bend, a0, a1, t0, nx, ny, nz);
                emitLerp(sink, bend, a0, a1, t1, nx, ny, nz);
                emitLerp(sink, bend, a3, a2, t1, nx, ny, nz);
                emitLerp(sink, bend, a3, a2, t0, nx, ny, nz);
            }
        }
    }

    private static void emitLerp(Sink sink, BendHolder b, ModelPart.Vertex p, ModelPart.Vertex q, float t,
                                 float nx, float ny, float nz) {
        emit(sink, b,
                p.worldX() + (q.worldX() - p.worldX()) * t,
                p.worldY() + (q.worldY() - p.worldY()) * t,
                p.worldZ() + (q.worldZ() - p.worldZ()) * t,
                p.u() + (q.u() - p.u()) * t,
                p.v() + (q.v() - p.v()) * t,
                nx, ny, nz);
    }

    private static void emit(Sink sink, BendHolder b, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        deform(x, y, z, nx, ny, nz, b);
        sink.vertex(P.x, P.y, P.z, u, v, N.x, N.y, N.z);
    }

    /** Replacement for ModelPart.Cube#compile while a bend is active. */
    public static void compile(ModelPart.Cube cube, PoseStack.Pose pose, VertexConsumer consumer,
                               int light, int overlay, int color, BendHolder bend) {
        Matrix4f m = pose.pose();
        tessellate(cube.polygons, bend, (x, y, z, u, v, nx, ny, nz) -> {
            m.transformPosition(x, y, z, OUT);
            float px = OUT.x, py = OUT.y, pz = OUT.z;
            pose.transformNormal(nx, ny, nz, NT);
            consumer.addVertex(px, py, pz, color, u, v, overlay, light, NT.x, NT.y, NT.z);
        });
    }
}
