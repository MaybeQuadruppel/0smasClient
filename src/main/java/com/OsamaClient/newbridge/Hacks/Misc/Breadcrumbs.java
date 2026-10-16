package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Movement.GoTo;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Zeichnet die Spur, die man gelaufen ist (verblasst mit dem Alter), und führt einen
 * per Pathfinder wieder zurück.
 *
 * Befehle: .trail back [blöcke]  – läuft so viele Blöcke Spur zurück (ohne Zahl: zum Anfang)
 *          .trail clear          – Spur löschen
 *
 * Teleports (> 32 Blöcke Sprung) und Dimensionswechsel starten ein neues Segment,
 * damit keine Linien quer durch die Welt gezogen werden.
 */
public class Breadcrumbs extends Module {

    public static Breadcrumbs INSTANCE;

    private record Crumb(Vec3 pos, String dim, boolean segmentStart) {}

    private final Deque<Crumb> crumbs = new ArrayDeque<>();
    private int color = 0xFFF59E0B;
    private double spacing = 1.0;
    private double maxPoints = 2000;
    private boolean fade = true;
    private final ChamsBufferSource bufferSource = new ChamsBufferSource();

    public Breadcrumbs() {
        super("Breadcrumbs", "Draws the trail you walked and paths you back along it (.trail back)", Category.WORLD);
        INSTANCE = this;
        this.settings.add(new ColorPicker("Color", color, c -> color = c).withDescription("Trail color."));
        this.settings.add(new Slider("Spacing", 0.3, 5.0, spacing, 0.1, v -> spacing = v)
                .withDescription("Distance in blocks between two trail points."));
        this.settings.add(new Slider("Max Points", 100, 10000, maxPoints, 100, v -> maxPoints = v)
                .withDescription("Older points are dropped beyond this count."));
        this.settings.add(new ToggleButton("Fade", fade, v -> fade = v)
                .withDescription("Older parts of the trail become more transparent."));
        EntryPoint.EVENT_BUS.subscribe(this);
    }

    private static String dim(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.player.isDeadOrDying()) return;
        Vec3 pos = mc.player.position();
        String dim = dim(mc);
        Crumb last = crumbs.peekLast();
        if (last == null || !last.dim().equals(dim)) {
            crumbs.addLast(new Crumb(pos, dim, true));
        } else {
            double d = last.pos().distanceTo(pos);
            if (d > 32.0) crumbs.addLast(new Crumb(pos, dim, true));        // Teleport -> neues Segment
            else if (d >= spacing) crumbs.addLast(new Crumb(pos, dim, false));
        }
        while (crumbs.size() > (int) maxPoints) {
            crumbs.pollFirst();
            Crumb first = crumbs.peekFirst();
            if (first != null && !first.segmentStart()) {
                crumbs.pollFirst();
                crumbs.addFirst(new Crumb(first.pos(), first.dim(), true));
            }
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!enabled || crumbs.size() < 2) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer.mainCamera() == null) return;
        Camera cam = mc.gameRenderer.mainCamera();
        double cx = cam.position().x, cy = cam.position().y, cz = cam.position().z;
        Matrix4f m = event.getPoseStack().last().pose();
        Matrix3f n = event.getPoseStack().last().normal();
        String dim = dim(mc);

        VertexConsumer lines = bufferSource.getBuffer(RenderTypes.storageEspLinesSeeThrough());
        int total = crumbs.size(), i = 0;
        Crumb prev = null;
        for (Crumb c : crumbs) {
            i++;
            if (prev != null && !c.segmentStart() && c.dim().equals(dim) && prev.dim().equals(dim)) {
                float age = fade ? 0.15f + 0.85f * (i / (float) total) : 1f;
                int col = (((int) (((color >>> 24) & 0xFF) * age)) << 24) | (color & 0x00FFFFFF);
                Waypoints.line(m, n, lines,
                        (float) (prev.pos().x - cx), (float) (prev.pos().y + 0.05 - cy), (float) (prev.pos().z - cz),
                        (float) (c.pos().x - cx), (float) (c.pos().y + 0.05 - cy), (float) (c.pos().z - cz),
                        col, 2f);
            }
            prev = c;
        }
        bufferSource.uploadAndDraw();
    }

    public void handleCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "clear" -> {
                crumbs.clear();
                msg("§e[Breadcrumbs] Trail cleared.");
            }
            case "back" -> {
                double want = Double.MAX_VALUE;
                if (args.length >= 3) {
                    try { want = Double.parseDouble(args[2]); } catch (NumberFormatException e) {
                        msg("§c[Breadcrumbs] Usage: .trail back [blocks]");
                        return;
                    }
                }
                Vec3 target = pointBack(dim(mc), want);
                if (target == null) { msg("§c[Breadcrumbs] No trail recorded in this dimension."); return; }
                if (ModuleManager.getModuleByName("GoTo") instanceof GoTo goTo) {
                    BlockPos p = BlockPos.containing(target);
                    goTo.setTarget(p.getX(), p.getY(), p.getZ());
                    msg("§a[Breadcrumbs] Pathing back to §f" + p.getX() + " " + p.getY() + " " + p.getZ());
                }
            }
            default -> msg("§6[Breadcrumbs] .trail back [blocks] | .trail clear");
        }
    }

    /** Geht die Spur vom Ende aus {@code blocks} weit zurück (innerhalb des aktuellen Segments). */
    private Vec3 pointBack(String dim, double blocks) {
        List<Crumb> list = new ArrayList<>(crumbs);
        double walked = 0;
        Crumb prev = null;
        for (int i = list.size() - 1; i >= 0; i--) {
            Crumb c = list.get(i);
            if (!c.dim().equals(dim)) break;
            if (prev != null) {
                walked += prev.pos().distanceTo(c.pos());
                if (walked >= blocks) return c.pos();
            }
            prev = c;
            if (c.segmentStart()) break;
        }
        return prev == null ? null : prev.pos();
    }

    /** Anzahl gespeicherter Spurpunkte (für Tests / HUD). */
    public int size() {
        return crumbs.size();
    }

    @Override
    public void onDisable() {
        // Spur bleibt erhalten, damit man nach dem Wieder-Einschalten noch zurückfindet.
    }

    private static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }
}
