package com.OsamaClient.newbridge.Utils.Render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.*;

public class NametagUtils {
    private static final Vector4f vec4 = new Vector4f();
    private static final Vector4f mmMat4 = new Vector4f();
    private static final Vector4f pmMat4 = new Vector4f();
    private static final Vector3d camera = new Vector3d();
    private static final Vector3d cameraNegated = new Vector3d();
    private static final Matrix4f model = new Matrix4f();
    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4fStack modelViewStack = new Matrix4fStack(16);
    private static int stackDepth = 0;

    private static double windowScale;
    public static double scale;

    private NametagUtils() {}

    public static void onRender(Matrix4fc modelView) {
        model.set(modelView);
        NametagUtils.projection.set(RenderUtils.projection);

        Vec3 camPos = net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera().position();
        camera.set(camPos.x, camPos.y, camPos.z);
        cameraNegated.set(camera);
        cameraNegated.negate();

        windowScale = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
    }

    public static boolean to2D(Vector3d pos, double scale) {
        return to2D(pos, scale, true);
    }

    public static boolean to2D(Vector3d pos, double scale, boolean distanceScaling) {
        return to2D(pos, scale, distanceScaling, false);
    }

    public static boolean to2D(Vector3d pos, double scale, boolean distanceScaling, boolean allowBehind) {
        NametagUtils.scale = scale;
        if (distanceScaling) {
            NametagUtils.scale *= getScale(pos);
        }

        vec4.set((float)(cameraNegated.x + pos.x), (float)(cameraNegated.y + pos.y), (float)(cameraNegated.z + pos.z), 1.0f);

        vec4.mul(model, mmMat4);
        mmMat4.mul(projection, pmMat4);

        boolean behind = pmMat4.w <= 0.f;
        if (behind && !allowBehind) return false;

        toScreen(pmMat4);
        var client = net.minecraft.client.Minecraft.getInstance();
        double x = pmMat4.x * client.getWindow().getWidth();
        double y = pmMat4.y * client.getWindow().getHeight();

        if (behind) {
            x = client.getWindow().getWidth() - x;
            y = client.getWindow().getHeight() - y;
        }

        if (Double.isInfinite(x) || Double.isInfinite(y)) return false;

        pos.set(x / windowScale, client.getWindow().getHeight() - y / windowScale, allowBehind ? pmMat4.w : pmMat4.z);
        return true;
    }

    public static void begin(Vector3d pos) {
        modelViewStack.pushMatrix();
        modelViewStack.translate((float) pos.x, (float) pos.y, 0);
        modelViewStack.scale((float) scale, (float) scale, 1.0f);
        stackDepth++;
    }

    public static void begin(Vector3d pos, GuiGraphicsExtractor graphics) {
        begin(pos);
        var matrices = graphics.pose();
        var client = net.minecraft.client.Minecraft.getInstance();
        matrices.pushMatrix();
        float guiScale = (float) client.getWindow().getGuiScale();
        matrices.scale(1.0f / guiScale, 1.0f / guiScale);
        matrices.translate((float) pos.x, (float) pos.y);
        matrices.scale((float) scale, (float) scale);
    }

    public static void end() {
        if (stackDepth > 0) {
            modelViewStack.popMatrix();
            stackDepth--;
        }
    }

    public static void end(GuiGraphicsExtractor graphics) {
        end();
        graphics.pose().popMatrix();
    }

    private static double getScale(Vector3d pos) {
        double dist = camera.distance(pos);
        return Mth.clamp(1.0 - dist * 0.01, 0.5, Double.MAX_VALUE);
    }

    private static void toScreen(Vector4f vec) {
        float newW = 1.0f / vec.w * 0.5f;
        vec.x = vec.x * newW + 0.5f;
        vec.y = vec.y * newW + 0.5f;
        vec.z = vec.z * newW + 0.5f;
        vec.w = newW;
    }
}