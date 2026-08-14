package com.OsamaClient.newbridge.Utils.Render;


import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public class RenderUtils {
    private static final Minecraft mc = Minecraft.getInstance();

    public static Vec3 center;
    public static final Matrix4f projection = new Matrix4f();

    private RenderUtils() {}

    // Items zeichnen
    public static void drawItem(GuiGraphicsExtractor graphics, ItemStack itemStack, int x, int y, float scale, boolean overlay, String countOverride, boolean disableGuiScale) {
        Matrix3x2fStack matrices = graphics.pose();
        matrices.pushMatrix();

        if (disableGuiScale) {
            matrices.scale(1.0f / (float) mc.getWindow().getGuiScale());
        }

        matrices.scale(scale, scale);

        int scaledX = (int) (x / scale);
        int scaledY = (int) (y / scale);

        graphics.item(itemStack, scaledX, scaledY);
        if (overlay) {
            graphics.itemDecorations(mc.font, itemStack, scaledX, scaledY, countOverride);
        }

        matrices.popMatrix();
    }

    public static void drawItem(GuiGraphicsExtractor graphics, ItemStack itemStack, int x, int y, float scale, boolean overlay) {
        drawItem(graphics, itemStack, x, y, scale, overlay, null, true);
    }

    // Bildschirm-Zentrum für Nametags / 3D-Renderings aktualisieren
    public static void updateScreenCenter(Matrix4fc projection, Matrix4fc view) {
        RenderUtils.projection.set(projection);

        Matrix4f invProjection = new Matrix4f(projection).invert();
        Matrix4f invView = new Matrix4f(view).invert();

        Vector4f center4 = new Vector4f(0, 0, 0, 1).mul(invProjection).mul(invView);
        center4.div(center4.w);

        Vec3 camera = mc.gameRenderer.mainCamera().position();
        center = new Vec3(camera.x + center4.x, camera.y + center4.y, camera.z + center4.z);
    }
}