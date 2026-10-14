package com.OsamaClient.newbridge.Hacks.Visual.minimap;

import com.OsamaClient.newbridge.Hacks.Misc.WaypointStore;
import com.OsamaClient.newbridge.Hacks.Misc.Waypoints;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.ClickGui;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiImage;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import com.OsamaClient.newbridge.Utils.TeamUtils;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.Locale;

/**
 * Minimap im Xaero-Stil: Gelände aus Vanilla-Kartenfarben, Spielerpfeil in der Mitte, Mobs und
 * Spieler als Punkte, Waypoints als Icons (am Rand angeheftet, wenn außerhalb), Norden-Markierung,
 * Koordinaten darunter. Rund oder eckig, optional mitdrehend.
 */
public class Minimap extends Module {

    public static Minimap INSTANCE;
    private static final int RES = 160;          // interne Auflösung des Kartenbilds
    private static final long REFRESH_MS = 2500; // Chunk spätestens nach dieser Zeit neu einlesen

    public double size = 110, zoom = 1.0, opacity = 1.0;
    public String shape = "Circle", corner = "Top Left";
    public boolean rotate = true, entities = true, players = true, waypoints = true, coords = true, caveAuto = true;

    private final Long2ObjectOpenHashMap<int[]> chunks = new Long2ObjectOpenHashMap<>();
    private final Long2LongOpenHashMap stamps = new Long2LongOpenHashMap();
    private ClientLevel cachedLevel;
    private UiImage image;
    private static UiRenderer renderer;
    private static Ui ui;

    public Minimap() {
        super("Minimap", "Xaero-style minimap with terrain, entities and waypoints", Category.VISUAL);
        INSTANCE = this;
        settings.add(new Slider("Size", 60, 220, size, 2, v -> size = v).withDescription("On-screen size of the map."));
        settings.add(new Slider("Zoom", 0.25, 4.0, zoom, 0.25, v -> zoom = v).withDescription("Blocks per map pixel (higher = more area)."));
        settings.add(new ModeButton("Shape", List.of("Circle", "Square"), 0, v -> shape = v).withDescription("Round or square map."));
        settings.add(new ModeButton("Position", List.of("Top Left", "Top Right", "Bottom Left", "Bottom Right"), 0, v -> corner = v)
                .withDescription("Screen corner of the map."));
        settings.add(new ToggleButton("Rotate", rotate, v -> rotate = v).withDescription("Map turns with you (up = where you look)."));
        settings.add(new ToggleButton("Mobs", entities, v -> entities = v).withDescription("Shows mobs as dots (red = hostile)."));
        settings.add(new ToggleButton("Players", players, v -> players = v).withDescription("Shows other players (white, teammates green)."));
        settings.add(new ToggleButton("Waypoints", waypoints, v -> waypoints = v).withDescription("Shows waypoints, pinned to the edge when far away."));
        settings.add(new ToggleButton("Coordinates", coords, v -> coords = v).withDescription("Shows your coordinates under the map."));
        settings.add(new ToggleButton("Auto Cave Mode", caveAuto, v -> caveAuto = v)
                .withDescription("In the Nether the map shows your floor instead of the bedrock roof."));
        settings.add(new Slider("Opacity", 0.2, 1.0, opacity, 0.05, v -> opacity = v).withDescription("Map transparency."));
    }

    @Override
    public void onDisable() {
        chunks.clear();
        stamps.clear();
    }

    // ------------------------------------------------------------------ chunk cache

    @Override
    public void onTick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        if (mc.level != cachedLevel) {
            chunks.clear();
            stamps.clear();
            cachedLevel = mc.level;
        }
        boolean cave = caveMode(mc);
        int radiusChunks = (int) Math.ceil(RES * zoom * 0.75 / 16.0) + 1;
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        long now = System.currentTimeMillis();
        int budget = 6;
        // nahe Chunks zuerst
        for (int r = 0; r <= radiusChunks && budget > 0; r++) {
            for (int dx = -r; dx <= r && budget > 0; dx++) {
                for (int dz = -r; dz <= r && budget > 0; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int cx = pcx + dx, cz = pcz + dz;
                    long key = ChunkPos.pack(cx, cz);
                    long stamp = stamps.getOrDefault(key, 0L);
                    if (now - stamp < REFRESH_MS) continue;
                    if (!mc.level.hasChunk(cx, cz)) continue;
                    chunks.put(key, scanChunk(mc, cx, cz, cave));
                    stamps.put(key, now);
                    budget--;
                }
            }
        }
        // zu weit entfernte Chunks vergessen (Speicher)
        if (chunks.size() > (radiusChunks * 2 + 6) * (radiusChunks * 2 + 6)) {
            chunks.long2ObjectEntrySet().removeIf(e -> {
                int cx = ChunkPos.getX(e.getLongKey()), cz = ChunkPos.getZ(e.getLongKey());
                boolean far = Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) > radiusChunks + 3;
                if (far) stamps.remove(e.getLongKey());
                return far;
            });
        }
    }

    private boolean caveMode(Minecraft mc) {
        return caveAuto && mc.level.dimensionType().hasCeiling();
    }

    private int[] scanChunk(Minecraft mc, int cx, int cz, boolean cave) {
        ClientLevel level = mc.level;
        int minY = level.getMinY();
        final int W = 17;                   // 16 + eine Reihe nördlich + eine Spalte westlich für die Schattierung
        int[] tops = new int[W * W];
        int caveFrom = mc.player.blockPosition().getY() + 1;
        for (int lz = -1; lz < 16; lz++) {
            for (int lx = -1; lx < 16; lx++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                int top = cave ? MapSampler.scanCave(level, x, z, caveFrom, minY)
                        : MapSampler.scanTop(level, x, z, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, minY);
                tops[(lz + 1) * W + (lx + 1)] = top;
            }
        }
        var blockColors = mc.getBlockColors();
        MapSampler.Tint tint = (state, lvl, pos) -> {
            if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                return level.getBiome(pos).value().getWaterColor();
            }
            var sources = blockColors.getTintSources(state);
            if (sources.isEmpty() || !(lvl instanceof net.minecraft.client.renderer.block.BlockAndTintGetter bt)) return -1;
            return sources.get(0).colorInWorld(state, bt, pos);
        };
        int[] colors = new int[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                int i = (lz + 1) * W + (lx + 1);
                colors[lz * 16 + lx] = MapSampler.columnColor(level, x, tops[i], z, tops[i - W], tops[i - 1], minY, tint);
            }
        }
        return colors;
    }

    private int colorAt(int x, int z) {
        int[] c = chunks.get(ChunkPos.pack(x >> 4, z >> 4));
        return c == null ? 0 : c[(z & 15) * 16 + (x & 15)];
    }

    // ------------------------------------------------------------------ drawing (GameRendererUiMixin)

    public static void draw() {
        Minimap self = INSTANCE;
        if (self == null || !self.enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden()) return;
        if (ClickGui.INSTANCE.isOpen() || mc.gui.screen() != null && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        if (renderer == null) {
            renderer = new UiRenderer();
            ui = new Ui(renderer);
        }
        renderer.begin();
        ui.scale = Theme.scale();
        ui.alpha = (float) self.opacity;
        ui.hoverBlocked = true;
        self.render(mc);
        renderer.flush();
        UiFont.collect(600);
    }

    private void render(Minecraft mc) {
        float s = (float) size;
        float margin = 6f;
        boolean right = corner.endsWith("Right"), bottom = corner.startsWith("Bottom");
        float textH = coords ? 10f : 0f;
        float mx = right ? ui.width() - s - margin : margin;
        float my = bottom ? ui.height() - s - margin - textH : margin;
        boolean circle = "Circle".equals(shape);
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double px = Mth.lerp(partial, mc.player.xo, mc.player.getX());
        double pz = Mth.lerp(partial, mc.player.zo, mc.player.getZ());
        float yaw = mc.player.getViewYRot(partial);
        double bpp = zoom;

        fillImage(px, pz, bpp, circle, yaw);

        // Rahmen + Karte
        float g = Theme.glow();
        float radius = circle ? s / 2f : Theme.radius();
        if (g > 0) ui.glow(mx, my, s, s, radius, 7f, Theme.accent(0.20f * g));
        ui.round(mx - 1.5f, my - 1.5f, s + 3f, s + 3f, circle ? s / 2f + 1.5f : radius + 1.5f, 0xFF0C0C11);
        ui.round(mx, my, s, s, radius, 0xFF15151A);
        ui.image(image, mx, my, s, s);
        // weiche Innenschattierung am Rand (Tiefe) + sauberer Doppelrahmen
        ui.outline(mx, my, s, s, radius, 7f, 0x38000000);
        ui.outline(mx - 1f, my - 1f, s + 2f, s + 2f, circle ? s / 2f + 1f : radius + 1f, 2.6f, 0xFF0C0C11);
        ui.outline(mx - 0.5f, my - 0.5f, s + 1f, s + 1f, circle ? s / 2f + 0.5f : radius + 0.5f, 1.2f, Theme.accent(0.95f));

        float cx = mx + s / 2f, cy = my + s / 2f;
        float scale = s / RES;              // UI-Einheiten pro Kartenpixel
        float edge = s / 2f - 4f;

        // Mobs / Spieler
        if (entities || players) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) continue;
                boolean isPlayer = e instanceof Player;
                if (isPlayer ? !players : !entities) continue;
                double ex = Mth.lerp(partial, e.xo, e.getX()) - px, ez = Mth.lerp(partial, e.zo, e.getZ()) - pz;
                double[] m = MinimapMath.worldToMap(ex, ez, bpp, rotate, yaw);
                double sx = m[0] * scale, sy = m[1] * scale;
                if (circle ? sx * sx + sy * sy > edge * edge : Math.max(Math.abs(sx), Math.abs(sy)) > edge) continue;
                int col = isPlayer ? (TeamUtils.isTeammate(e) ? 0xFF4ADE80 : 0xFFFFFFFF) : e instanceof Enemy ? 0xFFFF4D4D : 0xFFFFD43B;
                float d = isPlayer ? 2.2f : 1.6f;
                ui.round((float) (cx + sx) - d / 2f - 0.4f, (float) (cy + sy) - d / 2f - 0.4f, d + 0.8f, d + 0.8f, d, 0xC0000000);
                ui.round((float) (cx + sx) - d / 2f, (float) (cy + sy) - d / 2f, d, d, d / 2f, col);
            }
        }

        // Waypoints
        if (waypoints && Waypoints.INSTANCE != null) {
            String dim = Waypoints.dim(mc);
            for (WaypointStore.Waypoint w : Waypoints.INSTANCE.current(mc)) {
                if (w.hidden || !w.dimension.equals(dim)) continue;
                double[] m = MinimapMath.worldToMap(w.x + 0.5 - px, w.z + 0.5 - pz, bpp, rotate, yaw);
                double[] c = MinimapMath.clampToEdge(m[0] * scale, m[1] * scale, edge, circle);
                float ix = (float) (cx + c[0]) - 3f, iy = (float) (cy + c[1]) - 3f;
                ui.rect(ix - 0.5f, iy - 0.5f, 7f, 7f, 0xC0000000);
                ui.rect(ix, iy, 6f, 6f, w.color | 0xFF000000);
                ui.textCentered(w.initial(), ix + 3f, iy, 6f, Theme.FONT_SMALL * 0.7f, 0xFFFFFFFF);
            }
        }

        // Himmelsrichtungen auf dem Rahmen (N rot und größer)
        float ringR = s / 2f;
        String[] dirs = {"N", "E", "S", "W"};
        double[][] dv = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int d = 0; d < 4; d++) {
            double[] m = MinimapMath.worldToMap(dv[d][0] * 1000, dv[d][1] * 1000, 1, rotate, yaw);
            double len = Math.sqrt(m[0] * m[0] + m[1] * m[1]);
            double[] p = MinimapMath.clampToEdge(m[0] / len * ringR * 2, m[1] / len * ringR * 2, ringR - 0.5f, circle);
            float r = d == 0 ? 4.6f : 3.6f;
            float px0 = (float) (cx + p[0]), py0 = (float) (cy + p[1]);
            ui.round(px0 - r, py0 - r, r * 2f, r * 2f, r, d == 0 ? 0xF0E5484D : 0xE0101018);
            ui.textCentered(dirs[d], px0, py0 - r, r * 2f, Theme.FONT_SMALL * (d == 0 ? 0.78f : 0.62f),
                    d == 0 ? 0xFFFFFFFF : 0xFFB8BCC8);
        }

        // Spielerpfeil (zeigt nach oben bei Drehung, sonst in Blickrichtung)
        float ang = rotate ? 0f : (float) Math.toRadians(yaw + 180f);
        drawArrow(cx, cy, 4.2f, ang);

        // Koordinaten in einer kleinen Pille unter der Karte
        if (coords) {
            String t = String.format(Locale.ROOT, "%d, %d, %d", (int) Math.floor(px), (int) Math.floor(mc.player.getY()), (int) Math.floor(pz));
            float fs = Theme.FONT_SMALL * 0.85f;
            float tw = ui.textWidth(t, fs);
            float pw = tw + 12f, ph = 11f, py2 = my + s + 3f;
            ui.round(cx - pw / 2f, py2, pw, ph, ph / 2f, 0xC0101018);
            ui.outline(cx - pw / 2f, py2, pw, ph, ph / 2f, 1f, Theme.accent(0.45f));
            ui.textCentered(t, cx, py2, ph, fs, Theme.TEXT);
        }
    }

    private void drawArrow(float cx, float cy, float r, float ang) {
        double sin = Math.sin(ang), cos = Math.cos(ang);
        float[][] pts = {{0, -r}, {r * 0.78f, r * 0.85f}, {0, r * 0.4f}, {-r * 0.78f, r * 0.85f}};
        float[][] t = new float[4][2];
        for (int i = 0; i < 4; i++) {
            t[i][0] = (float) (cx + pts[i][0] * cos - pts[i][1] * sin);
            t[i][1] = (float) (cy + pts[i][0] * sin + pts[i][1] * cos);
        }
        // Füllung: ein Fächer dicker Linien von der Spitze + vom Heck, dann heller Umriss
        float fill = Math.max(1.6f, ui.scale * 1.5f);
        for (int k = 0; k <= 4; k++) {
            float f = k / 4f;
            float lx = t[1][0] + (t[3][0] - t[1][0]) * f, ly = t[1][1] + (t[3][1] - t[1][1]) * f;
            ui.line(t[0][0], t[0][1], (lx + t[2][0]) * 0.5f, (ly + t[2][1]) * 0.5f, fill, Theme.accent(1f));
        }
        float th = Math.max(1.2f, ui.scale * 1.1f);
        for (int i = 0; i < 4; i++) {
            float[] a = t[i], b = t[(i + 1) % 4];
            ui.line(a[0], a[1], b[0], b[1], th + 1.6f, 0xE0000000);
        }
        for (int i = 0; i < 4; i++) {
            float[] a = t[i], b = t[(i + 1) % 4];
            ui.line(a[0], a[1], b[0], b[1], th, 0xFFFFFFFF);
        }
    }

    private void fillImage(double px, double pz, double bpp, boolean circle, float yaw) {
        if (image == null) {
            image = new UiImage("minimap", RES, RES);
            image.smooth = true;
        }
        double half = RES / 2.0;
        double r2 = half * half;
        double yr = Math.toRadians(yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr);     // Blickrichtung
        double rx = -Math.cos(yr), rz = -Math.sin(yr);    // rechte Hand
        long lastKey = Long.MIN_VALUE;
        int[] lastChunk = null;
        for (int j = 0; j < RES; j++) {
            for (int i = 0; i < RES; i++) {
                double sx = i + 0.5 - half, sy = j + 0.5 - half;
                if (circle && sx * sx + sy * sy > r2 - RES) { // 1px Rand frei, der Rahmen deckt ihn ab
                    image.setPixel(i, j, 0);
                    continue;
                }
                double mx = sx * bpp, my = sy * bpp;
                double wx = rotate ? fx * -my + rx * mx : mx;
                double wz = rotate ? fz * -my + rz * mx : my;
                int bx = (int) Math.floor(px + wx), bz = (int) Math.floor(pz + wz);
                long key = ChunkPos.pack(bx >> 4, bz >> 4);
                if (key != lastKey) {
                    lastChunk = chunks.get(key);
                    lastKey = key;
                }
                int c = lastChunk == null ? 0 : lastChunk[(bz & 15) * 16 + (bx & 15)];
                image.setPixel(i, j, c == 0 ? 0xFF15151A : c);
            }
        }
        image.markDirty();
    }

    // für Tests
    public int cachedChunks() { return chunks.size(); }
}
