package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.Hacks.Client.Profiles;
import com.OsamaClient.newbridge.Hacks.Movement.GoTo;
import com.OsamaClient.newbridge.Hacks.Visual.Nametags;
import com.OsamaClient.newbridge.Hacks.Visual.render.RenderTypes;
import com.OsamaClient.newbridge.Hacks.Visual.render.chams.ChamsBufferSource;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.ClickGui;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import com.OsamaClient.newbridge.event.Render3DEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Waypoints pro Server/Welt und Dimension.
 *
 *  - Beam + Box in der Welt, Label mit Name + Entfernung auf dem Bildschirm
 *  - Automatischer "Death"-Waypoint beim Sterben (inkl. Koordinaten im Chat)
 *  - .wp goto <name> läuft per Pathfinder (GoTo) zum Waypoint
 *
 * Befehle: .wp add <name> [x y z] | .wp del <name> | .wp list | .wp goto <name> | .wp clear
 * Gespeichert in {@code config/0smasclient-waypoints.json}.
 */
public class Waypoints extends Module {

    private static final Logger LOG = LoggerFactory.getLogger("newbridge/Waypoints");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Waypoints INSTANCE;

    /** Alias, damit bestehender Code {@code Waypoints.Waypoint} weiter benutzen kann. */
    public static final class Waypoint {
        private Waypoint() {}
    }

    /** Alle Daten liegen im WaypointStore (headless testbar). */
    public final WaypointStore store = new WaypointStore(
            FabricLoader.getInstance().getConfigDir().resolve("0smasclient-waypoints.json"));

    private int color = 0xFF38BDF8;
    private int deathColor = 0xFFFF4D4D;
    private boolean beams = true;
    private boolean labels = true;
    private boolean autoDeath = true;
    private double maxDistance = 5000;
    private double iconScale = 1.0;
    private boolean alwaysNames = false;

    private boolean wasDead;
    private final ChamsBufferSource bufferSource = new ChamsBufferSource();
    private static final UiRenderer renderer = new UiRenderer();
    private static final Ui ui = new Ui(renderer);

    public Waypoints() {
        super("Waypoints", "Saved locations with beams, labels, death point and auto-pathing", Category.WORLD);
        INSTANCE = this;
        this.settings.add(new ColorPicker("Color", color, c -> color = c)
                .withDescription("Color for new waypoints."));
        this.settings.add(new ColorPicker("Death Color", deathColor, c -> deathColor = c)
                .withDescription("Color of the automatic death waypoint."));
        this.settings.add(new ToggleButton("Beams", beams, v -> beams = v)
                .withDescription("Draws a vertical beam at every waypoint."));
        this.settings.add(new ToggleButton("Icons", labels, v -> labels = v)
                .withDescription("Shows the waypoint icon with distance on screen."));
        this.settings.add(new ToggleButton("Auto Death Waypoint", autoDeath, v -> autoDeath = v)
                .withDescription("Creates/updates a 'Death' waypoint when you die."));
        this.settings.add(new Slider("Max Distance", 64, 30000, maxDistance, 16, v -> maxDistance = v)
                .withDescription("Waypoints further away are hidden."));
        this.settings.add(new Slider("Icon Scale", 0.5, 3.0, iconScale, 0.05, v -> iconScale = v)
                .withDescription("Size of the on-screen waypoint icons."));
        this.settings.add(new ToggleButton("Always Show Names", alwaysNames, v -> alwaysNames = v)
                .withDescription("Off: like Xaero, the name only appears when you look near the waypoint."));
        ToggleButton[] open = new ToggleButton[1];
        open[0] = new ToggleButton("Open Manager", false, v -> {
            if (v) {
                open[0].enabled = false;
                com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(new WaypointsWindow());
            }
        }).withDescription("Opens the waypoint manager (also: .wp)");
        this.settings.add(0, open[0]);
        this.enabled = false;
        EntryPoint.EVENT_BUS.subscribe(this);
    }

    // ------------------------------------------------------------------ storage

    /** Waypoints des aktuellen Servers (alle Dimensionen, Live-Liste). */
    public List<WaypointStore.Waypoint> current(Minecraft mc) {
        return store.list(Profiles.serverKey(mc));
    }

    public String serverKey(Minecraft mc) {
        return Profiles.serverKey(mc);
    }

    public int newColor() { return color; }

    public static String dim(Minecraft mc) {
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    private WaypointStore.Waypoint find(Minecraft mc, String name) {
        return store.find(serverKey(mc), name);
    }

    // ------------------------------------------------------------------ logic

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            wasDead = false;
            return;
        }
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead && autoDeath) {
            BlockPos p = mc.player.blockPosition();
            store.setDeath(serverKey(mc), p.getX(), p.getY(), p.getZ(), dim(mc), deathColor);
            msg("§c[Waypoints] Death point saved at §f" + p.getX() + " " + p.getY() + " " + p.getZ()
                    + "§c  (.wp goto Death)");
        }
        wasDead = dead;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (!enabled || !beams) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameRenderer.mainCamera() == null) return;

        Camera camera = mc.gameRenderer.mainCamera();
        double cx = camera.position().x, cy = camera.position().y, cz = camera.position().z;
        Matrix4f matrix = event.getPoseStack().last().pose();
        Matrix3f normal = event.getPoseStack().last().normal();
        String dim = dim(mc);
        float top = (float) (mc.level.getMaxY() - cy);

        VertexConsumer fill = null;
        for (WaypointStore.Waypoint w : current(mc)) {
            if (!w.dimension.equals(dim) || w.hidden) continue;
            double dx = w.x + 0.5 - cx, dz = w.z + 0.5 - cz;
            double distSq = dx * dx + dz * dz;
            if (distSq > maxDistance * maxDistance || distSq < 4.0) continue;
            if (fill == null) fill = bufferSource.getBuffer(RenderTypes.storageEspFillSeeThrough());
            // Beacon-artiger Strahl: zwei sich kreuzende, halbtransparente Flächen ab dem Waypoint nach oben.
            // Breite wächst mit der Entfernung leicht mit, damit er auch weit weg sichtbar bleibt.
            float half = (float) Math.min(1.5, 0.12 + Math.sqrt(distSq) * 0.004);
            float x = (float) dx, z = (float) dz, y0 = (float) (w.y - cy);
            int col = ColorUtil.alpha(w.color | 0xFF000000, 0.32f);
            beamQuad(matrix, normal, fill, x - half, z, x + half, z, y0, top, col);
            beamQuad(matrix, normal, fill, x, z - half, x, z + half, y0, top, col);
        }
        if (fill != null) bufferSource.uploadAndDraw();
    }

    private static void beamQuad(Matrix4f m, Matrix3f n, VertexConsumer c,
                                 float x1, float z1, float x2, float z2, float y0, float y1, int color) {
        int a = (color >>> 24) & 0xFF, r = (color >>> 16) & 0xFF, g = (color >>> 8) & 0xFF, b = color & 0xFF;
        Vector3f nv = new Vector3f(z2 - z1, 0, x1 - x2).normalize().mul(n);
        // Vorder- und Rückseite, damit der Strahl aus jeder Richtung sichtbar ist
        c.addVertex(m, x1, y0, z1).setColor(r, g, b, a).setNormal(nv.x(), nv.y(), nv.z());
        c.addVertex(m, x2, y0, z2).setColor(r, g, b, a).setNormal(nv.x(), nv.y(), nv.z());
        c.addVertex(m, x2, y1, z2).setColor(r, g, b, a).setNormal(nv.x(), nv.y(), nv.z());
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setNormal(nv.x(), nv.y(), nv.z());
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setNormal(-nv.x(), -nv.y(), -nv.z());
        c.addVertex(m, x2, y1, z2).setColor(r, g, b, a).setNormal(-nv.x(), -nv.y(), -nv.z());
        c.addVertex(m, x2, y0, z2).setColor(r, g, b, a).setNormal(-nv.x(), -nv.y(), -nv.z());
        c.addVertex(m, x1, y0, z1).setColor(r, g, b, a).setNormal(-nv.x(), -nv.y(), -nv.z());
    }

    /**
     * Xaero-Style Icons, aufgerufen aus GameRendererUiMixin:
     * farbiges Quadrat mit Anfangsbuchstabe (Death = Kreuz), Entfernung darunter,
     * voller Name nur wenn man in die Nähe des Waypoints schaut (oder "Always Show Names").
     */
    public static void draw() {
        Waypoints self = INSTANCE;
        if (self == null || !self.enabled || !self.labels) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden() || mc.gameRenderer.mainCamera() == null) return;
        if (ClickGui.INSTANCE.isOpen() || mc.gui.screen() != null) return;

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        String dim = dim(mc);

        // weit entfernte zuerst zeichnen, nahe liegen dann oben drauf
        List<WaypointStore.Waypoint> visible = new ArrayList<>();
        for (WaypointStore.Waypoint w : self.current(mc)) if (w.dimension.equals(dim) && !w.hidden) visible.add(w);
        visible.sort((a, b) -> Double.compare(
                camPos.distanceToSqr(b.x + 0.5, b.y + 0.5, b.z + 0.5),
                camPos.distanceToSqr(a.x + 0.5, a.y + 0.5, a.z + 0.5)));

        renderer.begin();
        ui.scale = Theme.scale() * (float) self.iconScale;
        ui.alpha = 1f;
        ui.hoverBlocked = true;
        float centerX = ui.width() / 2f, centerY = ui.height() / 2f;

        for (WaypointStore.Waypoint w : visible) {
            double dist = Math.sqrt(camPos.distanceToSqr(w.x + 0.5, w.y + 0.5, w.z + 0.5));
            if (dist > self.maxDistance || dist < 1.5) continue;
            Vector3f fb = Nametags.projectToFramebuffer(w.x + 0.5, w.y + 1.0, w.z + 0.5, camPos, camera, mc);
            if (fb == null) continue;
            float x = fb.x() / ui.scale, y = fb.y() / ui.scale;
            drawIcon(w, x, y, dist, self.alwaysNames || Math.hypot(x - centerX, y - centerY) < 40.0);
        }
        renderer.flush();
        UiFont.collect(600);
    }

    private static void drawIcon(WaypointStore.Waypoint w, float x, float y, double dist, boolean showName) {
        final float s = 11f;               // Kantenlänge des Icons
        float ix = x - s / 2f, iy = y - s / 2f;
        int base = w.color | 0xFF000000;

        // Schatten, Fläche, dunkler Rand – wie bei Xaero
        ui.round(ix + 0.8f, iy + 0.8f, s, s, 1.5f, 0x66000000);
        ui.round(ix, iy, s, s, 1.5f, ColorUtil.alpha(base, 0.88f));
        ui.outline(ix, iy, s, s, 1.5f, 1f, 0xCC000000);

        int fg = luminance(base) > 0.6f ? 0xFF111111 : 0xFFFFFFFF;
        if (w.death) {
            float p = 3f;
            ui.line(ix + p, iy + p, ix + s - p, iy + s - p, 1.6f, fg);
            ui.line(ix + s - p, iy + p, ix + p, iy + s - p, 1.6f, fg);
        } else {
            String letter = w.name.isEmpty() ? "?" : w.name.substring(0, 1).toUpperCase(Locale.ROOT);
            ui.textCentered(letter, x, iy, s, Theme.FONT_SMALL, fg);
        }

        float small = Theme.FONT_SMALL * 0.85f;
        String distText = dist >= 1000 ? String.format(Locale.ROOT, "%.1fkm", dist / 1000.0)
                : String.format(Locale.ROOT, "%dm", (int) Math.round(dist));
        float lh = small * 1.4f;
        ui.textCentered(distText, x + 0.6f, iy + s + 1.6f, lh, small, 0xAA000000);
        ui.textCentered(distText, x, iy + s + 1f, lh, small, 0xFFFFFFFF);

        if (showName) {
            float nameSize = Theme.FONT_SMALL;
            float nh = nameSize * 1.4f;
            float nw = ui.textWidth(w.name, nameSize) + 6f;
            float ny = iy - nh - 2f;
            ui.round(x - nw / 2f, ny, nw, nh, 1.5f, 0x99000000);
            ui.textCentered(w.name, x, ny, nh, nameSize, 0xFFFFFFFF);
        }
    }

    private static float luminance(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    // ------------------------------------------------------------------ commands

    public void handleCommand(String[] args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (args.length < 2) {
            // ".wp" ohne Argumente: Waypoint-Manager öffnen
            com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.openLater(new WaypointsWindow());
            return;
        }
        String key = serverKey(mc);
        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "add", "set" -> {
                if (args.length != 3 && args.length != 6) { usage(); return; }
                String name = args[2];
                int x, y, z;
                if (args.length == 6) {
                    try {
                        x = Integer.parseInt(args[3]);
                        y = Integer.parseInt(args[4]);
                        z = Integer.parseInt(args[5]);
                    } catch (NumberFormatException e) {
                        msg("§c[Waypoints] Invalid coordinates.");
                        return;
                    }
                } else {
                    BlockPos p = mc.player.blockPosition();
                    x = p.getX(); y = p.getY(); z = p.getZ();
                }
                store.put(key, new WaypointStore.Waypoint(name, x, y, z, dim(mc), color, false));
                msg("§a[Waypoints] Added §f" + name + "§a at " + x + " " + y + " " + z);
            }
            case "del", "delete", "remove" -> {
                if (args.length < 3) { usage(); return; }
                boolean removed = store.remove(key, args[2]);
                msg(removed ? "§e[Waypoints] Removed §f" + args[2] : "§c[Waypoints] Not found: §f" + args[2]);
            }
            case "list", "ls" -> {
                List<WaypointStore.Waypoint> list = store.sorted(key);
                if (list.isEmpty()) { msg("§e[Waypoints] No waypoints on this server."); return; }
                for (WaypointStore.Waypoint w : list) {
                    double d = Math.sqrt(mc.player.distanceToSqr(w.x + 0.5, w.y, w.z + 0.5));
                    String here = w.dimension.equals(dim(mc)) ? String.format(Locale.ROOT, "%.0fm", d) : w.dimension;
                    msg("§7- §f" + w.name + "§7: " + w.x + " " + w.y + " " + w.z + " §8(" + here + ")");
                }
            }
            case "goto", "go" -> {
                if (args.length < 3) { usage(); return; }
                WaypointStore.Waypoint w = find(mc, args[2]);
                if (w == null) { msg("§c[Waypoints] Not found: §f" + args[2]); return; }
                goTo(w);
            }
            case "clear" -> {
                store.clear(key);
                msg("§e[Waypoints] Cleared all waypoints on this server.");
            }
            case "ui", "gui", "menu" -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.openLater(new WaypointsWindow());
            default -> usage();
        }
    }

    /** Startet GoTo zum Waypoint (nur gleiche Dimension). */
    public boolean goTo(WaypointStore.Waypoint w) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (!w.dimension.equals(dim(mc))) {
            msg("§c[Waypoints] " + w.name + " is in " + w.dimension);
            return false;
        }
        if (ModuleManager.getModuleByName("GoTo") instanceof GoTo goTo) {
            goTo.setTarget(w.x, w.y, w.z);
            msg("§a[Waypoints] Pathing to §f" + w.name);
            return true;
        }
        return false;
    }

    private static void usage() {
        msg("§6[Waypoints] .wp (opens manager) | .wp add <name> [x y z] | .wp del <name> | .wp list | .wp goto <name> | .wp clear");
    }

    static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }

    // ------------------------------------------------------------------ geometry

    static void line(Matrix4f m, Matrix3f n, VertexConsumer c,
                     float x1, float y1, float z1, float x2, float y2, float z2, int color, float width) {
        int a = (color >>> 24) & 0xFF, r = (color >>> 16) & 0xFF, g = (color >>> 8) & 0xFF, b = color & 0xFF;
        Vector3f nv = new Vector3f(x2 - x1, y2 - y1, z2 - z1);
        if (nv.lengthSquared() < 1.0E-8f) nv.set(0, 1, 0);
        nv.normalize().mul(n);
        c.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setNormal(nv.x(), nv.y(), nv.z()).setLineWidth(width);
        c.addVertex(m, x2, y2, z2).setColor(r, g, b, a).setNormal(nv.x(), nv.y(), nv.z()).setLineWidth(width);
    }

}
