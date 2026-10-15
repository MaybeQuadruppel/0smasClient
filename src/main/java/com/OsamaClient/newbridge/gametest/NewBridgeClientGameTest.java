package com.OsamaClient.newbridge.gametest;

import com.OsamaClient.newbridge.Hacks.Client.Profiles;
import com.OsamaClient.newbridge.Hacks.Combat.Fullbox;
import com.OsamaClient.newbridge.Hacks.Visual.Nametags;
import com.OsamaClient.newbridge.Hacks.Misc.Breadcrumbs;
import com.OsamaClient.newbridge.Hacks.Misc.Waypoints;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Goal;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.GoalBlock;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Navigator;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteEditor.EmoteEditor;
import com.OsamaClient.newbridge.Hacks.Visual.ESP.PlayerESP;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import com.OsamaClient.newbridge.UI.gui.input.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automatische Ingame-Tests (Fabric Client GameTest). Start: Gradle-Task {@code runClientGameTest}.
 *
 * Baut in einer Superflat-Testwelt kleine Parcours in der Luft (y≈100) und prüft:
 *  - Pathing: Sprung auf 1-Block-Stufe OHNE Blöcke zu setzen
 *  - Pathing: Wand mit eingeschlossener Lava + Kies -> nie Lava freilegen, nie unter Kies graben
 *  - Pathing: 3-Block-Drop ohne Fallschaden
 *  - Fullbox: Schlag wird auf ein Ziel neben dem Fadenkreuz umgeleitet, Kamera bleibt stehen
 *  - Profiles, Waypoints (inkl. Death-Waypoint), Breadcrumbs, EmoteEditor Undo
 *  - Screenshots für ESP-Split und Waypoints (run-gametest/screenshots)
 *
 * Ergebnis: run-gametest/newbridge-gametest-report.txt (+ Log). Schlägt ein Test fehl,
 * wird am Ende ein AssertionError geworfen (Prozess endet mit Fehler).
 */
public class NewBridgeClientGameTest implements FabricClientGameTest {

    private static final Logger LOG = LoggerFactory.getLogger("newbridge/gametest");
    private static final int Y = 100; // Füße stehen auf y=100, Boden ist y=99

    private final List<String> report = new ArrayList<>();
    private int failures;
    private int ox, oz;

    @FunctionalInterface
    private interface Check { void run() throws Exception; }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            TestServerContext srv = sp.getServer();
            srv.runCommand("gamemode survival @a");
            srv.runCommand("difficulty peaceful");
            srv.runCommand("time set noon");

            BlockPos start = ctx.computeOnClient(mc -> mc.player.blockPosition());
            ox = start.getX();
            oz = start.getZ();
            log("Origin " + ox + " " + oz);

            ctx.runOnClient(mc -> { if (!Breadcrumbs.INSTANCE.enabled) Breadcrumbs.INSTANCE.toggle(); });
            run("norender: scoreboard/crosshair/title/item-name hooks applied", () -> testNoRenderHooks());
            run("glide: limits the fall speed", () -> testGlide(ctx, srv));
            run("fastladder: climbs faster than vanilla", () -> testFastLadder(ctx, srv));
            run("antiafk: acts after the interval", () -> testAntiAfk(ctx, srv));
            run("ui tabs + hud keystrokes + enchantment picker (screenshots)", () -> testUiTabsAndHud(ctx, srv));
            run("block picker: block icons render next to the names (screenshot)", () -> testBlockPickerIcons(ctx));
            run("clickburst: one click fires N clicks", () -> testClickBurst(ctx, srv));
            run("spawnerfinder: finds a spawner in loaded chunks", () -> testSpawnerFinder(ctx, srv));
            run("playeralert: radius logic + module runs", () -> testPlayerAlert(ctx));
            run("autoweapon: picks the sword when attacking, switches back", () -> testAutoWeapon(ctx, srv));
            run("autowalk: walks forward and stops when disabled", () -> testAutoWalk(ctx, srv));
            run("pathing: jump onto 1-block step (no pillar)", () -> testStepUp(ctx, srv));
            run("pathing: avoid lava pocket and gravel in wall", () -> testHazards(ctx, srv));
            run("pathing: 3-block drop without fall damage", () -> testDrop(ctx, srv));
            run("pathing: head turns smoothly (no snaps / 180 flips)", () -> testHeadTurn(ctx, srv));
            run("autominer: mines several targets, retargets after each", () -> testAutoMiner(ctx, srv));
            run("nametags: projection matches camera fov, also while sprinting", () -> testProjection(ctx, srv));
            run("breadcrumbs: trail recorded while walking", () -> testBreadcrumbs(ctx));
            run("esp: split visibility screenshot", () -> testEspScreenshot(ctx, srv));
            run("waypoints: add + xaero icon screenshot", () -> testWaypoints(ctx, srv));
            run("fullbox: traps target completely with silent rotations", () -> testFullbox(ctx, srv));
            run("profiles: save / change / load restores", () -> testProfiles(ctx));
            run("emote editor: keyframe + undo/redo", () -> testEmoteEditor(ctx));
            run("emotes: bent knees/elbows/spine render smoothly (screenshots)", () -> testEmoteBend(ctx, srv));
            run("waypoint manager: open, add via editor, edit, screenshot", () -> testWaypointWindow(ctx));
            run("profiles manager: open, select, screenshot", () -> testProfilesWindow(ctx));
            run("clickgui: dock buttons visible (screenshot)", () -> testClickGuiDock(ctx));
            run("minimap: scans chunks and renders (screenshot)", () -> testMinimap(ctx, srv));
            run("autoarmor: equips the better armor from the inventory", () -> testAutoArmor(ctx, srv));
            run("stashlogger: finds a chest stash (+ list screenshot)", () -> testStashLogger(ctx, srv));
            run("macros: macro runs a .client command (creates waypoint)", () -> testMacros(ctx));
            run("autolog: triggers on low health (dry run) and disables itself", () -> testAutoLog(ctx));
            run("waypoints: death waypoint on death", () -> testDeathWaypoint(ctx, srv));
        } finally {
            writeReport(ctx);
        }
        if (failures > 0) throw new AssertionError(failures + " NewBridge gametest(s) failed, see newbridge-gametest-report.txt");
    }

    // ------------------------------------------------------------------ tests

    private void testStepUp(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -3, 14, Y - 1, 3, "stone");        // Boden
        fill(srv, 6, Y, -3, 14, Y, 3, "stone");                 // Stufe: ab x=6 eine Ebene höher
        prepareInventory(srv);
        teleport(ctx, srv, 0, Y, 0);

        int cobbleBefore = count(ctx, Items.COBBLESTONE);
        boolean ok = navigate(ctx, new GoalBlock(ox + 10, Y + 1, oz), 600);
        int cobbleAfter = count(ctx, Items.COBBLESTONE);
        assertTrue(ok, "did not reach goal on the step");
        assertTrue(cobbleAfter == cobbleBefore, "placed blocks (pillared): cobblestone " + cobbleBefore + " -> " + cobbleAfter);
        assertTrue(block(srv, 5, Y, 0) == Blocks.AIR, "something was placed in front of the step");
    }

    private void testHazards(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 2, -9, 14, Y - 1, 9, "stone");                // Boden (2 dick)
        fill(srv, 5, Y, -9, 7, Y + 2, 6, "stone");                      // 3 dicke Wand, Lücke bei z=7..9
        fill(srv, 6, Y, -2, 6, Y + 1, 2, "lava");                       // eingeschlossene Lava in der Wand
        fill(srv, 5, Y + 2, -6, 7, Y + 2, -3, "gravel");                // Kies oben auf der Wand
        fill(srv, 5, Y + 2, 3, 7, Y + 2, 6, "gravel");
        fill(srv, 5, Y + 3, -9, 7, Y + 3, 6, "stone");                  // Deckel, damit Kies liegt
        prepareInventory(srv);
        teleport(ctx, srv, 1, Y, 0);

        int lavaBefore = countBlocks(srv, Blocks.LAVA);
        int gravelBefore = countBlocks(srv, Blocks.GRAVEL);
        AtomicInteger maxFire = new AtomicInteger();
        boolean ok = navigate(ctx, new GoalBlock(ox + 11, Y, oz), 900,
                mc -> maxFire.accumulateAndGet(mc.player.getRemainingFireTicks(), Math::max));
        int lavaAfter = countBlocks(srv, Blocks.LAVA);
        int gravelAfter = countBlocks(srv, Blocks.GRAVEL);

        assertTrue(ok, "did not reach goal behind the wall");
        assertTrue(maxFire.get() <= 0, "player was on fire (" + maxFire.get() + " ticks)");
        assertTrue(lavaAfter == lavaBefore, "lava escaped/changed: " + lavaBefore + " -> " + lavaAfter);
        assertTrue(gravelAfter == gravelBefore, "gravel moved/mined: " + gravelBefore + " -> " + gravelAfter);
    }

    private void testDrop(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 3, Y - 1, 2, "stone");      // obere Plattform
        fill(srv, 4, Y - 4, -2, 10, Y - 4, 2, "stone");      // 3 Blöcke tiefer
        prepareInventory(srv);
        teleport(ctx, srv, 0, Y, 0);
        float hpBefore = ctx.computeOnClient(mc -> mc.player.getHealth());
        boolean ok = navigate(ctx, new GoalBlock(ox + 8, Y - 3, oz), 400);
        ctx.waitTicks(10);
        float hpAfter = ctx.computeOnClient(mc -> mc.player.getHealth());
        assertTrue(ok, "did not reach lower platform");
        assertTrue(hpAfter >= hpBefore, "took fall damage: " + hpBefore + " -> " + hpAfter);
    }

    private void testHeadTurn(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -6, 16, Y - 1, 6, "stone");
        fill(srv, 4, Y, -6, 4, Y + 1, 2, "stone");    // Wand 1: Weg muss nach +z ausweichen
        fill(srv, 9, Y, -2, 9, Y + 1, 6, "stone");    // Wand 2: Weg muss zurück nach -z
        prepareInventory(srv);
        teleport(ctx, srv, 0, Y, 0);
        float[] last = {Float.NaN};
        AtomicReference<Float> maxStep = new AtomicReference<>(0f);
        AtomicReference<Float> total = new AtomicReference<>(0f);
        boolean ok = navigate(ctx, new GoalBlock(ox + 14, Y, oz), 600, mc -> {
            float yaw = mc.player.getYRot();
            if (!Float.isNaN(last[0])) {
                float d = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - last[0]));
                if (d > maxStep.get()) maxStep.set(d);
                total.set(total.get() + d);
            }
            last[0] = yaw;
        });
        log("head turn: max step " + maxStep.get() + " deg, total " + total.get() + " deg");
        assertTrue(ok, "did not reach goal");
        assertTrue(maxStep.get() <= 45f, "head snapped " + maxStep.get() + " deg in one tick");
        assertTrue(total.get() <= 700f, "head turned " + total.get() + " deg in total (too much shaking)");
    }

    private void testAutoMiner(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -4, Y - 1, -8, 12, Y - 1, 8, "stone");
        int[][] ores = {{6, 0}, {6, 5}, {1, -6}};
        for (int[] o : ores) fill(srv, o[0], Y, o[1], o[0], Y, o[1], "coal_ore");
        prepareInventory(srv);
        teleport(ctx, srv, 0, Y, 0);
        ctx.runOnClient(mc -> {
            com.OsamaClient.newbridge.Hacks.Misc.AutoMiner am =
                    (com.OsamaClient.newbridge.Hacks.Misc.AutoMiner) ModuleManager.getModuleByName("AutoMiner");
            am.blockPicker().selectedBlocks.clear();
            am.blockPicker().toggle(Blocks.COAL_ORE);
            if (am.enabled) am.toggle();
            am.toggle();
        });
        int left = ores.length;
        for (int t = 0; t < 1200 && left > 0; t += 20) {
            ctx.waitTicks(20);
            left = 0;
            for (int[] o : ores) if (block(srv, o[0], Y, o[1]) == Blocks.COAL_ORE) left++;
        }
        ctx.runOnClient(mc -> { if (ModuleManager.getModuleByName("AutoMiner").enabled) ModuleManager.getModuleByName("AutoMiner").toggle(); });
        assertTrue(left == 0, left + " of 3 ores still there (no retarget?)");
    }

    private void testProjection(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -6, 30, Y - 1, 6, "stone");
        teleport(ctx, srv, 0, Y, 0);
        ctx.runOnClient(mc -> { mc.player.setYRot(-90f); mc.player.setXRot(0f); });
        for (int phase = 0; phase < 2; phase++) {
            final boolean sprint = phase == 1;
            ctx.runOnClient(mc -> {
                mc.options.keyUp.setDown(sprint);
                mc.options.keySprint.setDown(sprint);
            });
            ctx.waitTicks(sprint ? 25 : 5);
            String res = ctx.computeOnClient(mc -> {
                var cam = mc.gameRenderer.mainCamera();
                var pos = cam.position();
                double wx = pos.x + 6, wy = pos.y + 1.5, wz = pos.z - 3;
                org.joml.Vector3f p = Nametags.projectToFramebuffer(wx, wy, wz, pos, cam, mc);
                if (p == null) return "null";
                // Referenz: Pinhole-Modell mit dem tatsächlich benutzten (interpolierten) FOV
                double dx = wx - pos.x, dy = wy - pos.y, dz = wz - pos.z;
                org.joml.Vector4f v = new org.joml.Vector4f((float) dx, (float) dy, (float) dz, 1f)
                        .rotate(new org.joml.Quaternionf(cam.rotation()).conjugate());
                float z = -v.z;
                double scaleY = 1.0 / Math.tan(Math.toRadians(cam.getFov() / 2.0));
                int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
                double rx = (1.0 + (v.x * scaleY / ((double) w / h)) / z) * 0.5 * w;
                double ry = (1.0 - (v.y * scaleY) / z) * 0.5 * h;
                double err = Math.hypot(p.x() - rx, p.y() - ry);
                return String.format(java.util.Locale.ROOT, "%.2f fov=%.1f", err, cam.getFov());
            });
            log("projection sprint=" + sprint + ": err(px) " + res);
            assertTrue(!res.equals("null"), "point in front of camera was not projected");
            double err = Double.parseDouble(res.split(" ")[0]);
            assertTrue(err < 2.0, "projection differs from camera fov by " + err + "px (sprint=" + sprint + ")");
        }
        ctx.runOnClient(mc -> { mc.options.keyUp.setDown(false); mc.options.keySprint.setDown(false); });
    }

    private void testBreadcrumbs(ClientGameTestContext ctx) {
        int size = ctx.computeOnClient(mc -> Breadcrumbs.INSTANCE == null ? -1 : Breadcrumbs.INSTANCE.size());
        assertTrue(size > 5, "trail has only " + size + " points");
    }

    private void testEspScreenshot(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -4, 12, Y - 1, 4, "stone");
        fill(srv, 4, Y, -4, 4, Y, 4, "stone");              // 1 Block hohe Mauer: verdeckt nur die Beine
        srv.runCommand(String.format(java.util.Locale.ROOT, "summon villager %d %d %d {NoAI:1b,Tags:[\"gt\"]}", ox + 7, Y, oz));
        teleport(ctx, srv, 0, Y, 0);
        ctx.runOnClient(mc -> {
            PlayerESP esp = PlayerESP.INSTANCE;
            esp.targetPicker.filters.put("NPCs", true);
            esp.renderMode = "Both";
            esp.splitVisibility = true;
            if (!esp.enabled) esp.toggle();
            mc.player.setYRot(-90f);   // nach +X schauen
            mc.player.setXRot(10f);
        });
        ctx.waitTicks(10);
        Path shot = ctx.takeScreenshot("newbridge_esp_split");
        log("ESP screenshot: " + shot);
        ctx.runOnClient(mc -> { if (PlayerESP.INSTANCE.enabled) PlayerESP.INSTANCE.toggle(); });
        srv.runCommand("kill @e[tag=gt]");
    }

    private void testWaypoints(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -4, 4, Y - 1, 4, "stone");
        teleport(ctx, srv, 0, Y, 0);
        ctx.runOnClient(mc -> {
            Waypoints wp = Waypoints.INSTANCE;
            if (!wp.enabled) wp.toggle();
            wp.handleCommand(new String[]{".wp", "clear"});
            wp.handleCommand(new String[]{".wp", "add", "Base", String.valueOf(ox + 40), String.valueOf(Y), String.valueOf(oz + 6)});
            wp.handleCommand(new String[]{".wp", "add", "Mine", String.valueOf(ox + 25), String.valueOf(Y - 20), String.valueOf(oz - 10)});
            wp.handleCommand(new String[]{".wp", "add", "Farm", String.valueOf(ox + 120), String.valueOf(Y), String.valueOf(oz + 30)});
            mc.player.setYRot(-90f);
            mc.player.setXRot(0f);
        });
        ctx.waitTicks(10);
        Path shot = ctx.takeScreenshot("newbridge_waypoints");
        log("Waypoint screenshot: " + shot);
        int n = ctx.computeOnClient(mc -> countWaypoints());
        assertTrue(n == 3, "expected 3 waypoints, got " + n);
    }

    private void testFullbox(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -3, Y - 1, -4, 8, Y - 1, 4, "stone");
        prepareInventory(srv);
        srv.runCommand("item replace entity @a hotbar.3 with obsidian 64");
        teleport(ctx, srv, 0, Y, 0);
        srv.runCommand(String.format(java.util.Locale.ROOT, "summon villager %.1f %d %.1f {NoAI:1b,Tags:[\"gt\"]}", ox + 3.5, Y, oz + 0.5));
        ctx.waitTicks(10);
        float yawBefore = ctx.computeOnClient(mc -> mc.player.getYRot());
        float pitchBefore = ctx.computeOnClient(mc -> mc.player.getXRot());
        ctx.runOnClient(mc -> {
            Fullbox fb = Fullbox.INSTANCE;
            fb.filter.filters.put("NPCs", true);
            for (var c : fb.settings) {
                if (c instanceof com.OsamaClient.newbridge.UI.components.Slider sl && sl.getLabel().equals("Range")) sl.setValue(4.5);
            }
            if (!fb.enabled) fb.toggle();
        });
        ctx.waitTicks(40);
        Path shot = ctx.takeScreenshot("newbridge_fullbox_trap");
        log("Fullbox screenshot: " + shot);
        int placed = ctx.computeOnClient(mc -> Fullbox.INSTANCE.placedCount);
        float yawAfter = ctx.computeOnClient(mc -> mc.player.getYRot());
        float pitchAfter = ctx.computeOnClient(mc -> mc.player.getXRot());
        int slot = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        ctx.runOnClient(mc -> { if (Fullbox.INSTANCE.enabled) Fullbox.INSTANCE.toggle(); });

        int[][] around = {{4, 0, 0}, {2, 0, 0}, {3, 0, 1}, {3, 0, -1}, {4, 1, 0}, {2, 1, 0}, {3, 1, 1}, {3, 1, -1}, {3, 2, 0}};
        int missing = 0;
        StringBuilder holes = new StringBuilder();
        for (int[] d : around) {
            if (block(srv, d[0], Y + d[1], d[2]) != Blocks.OBSIDIAN) { missing++; holes.append(" ").append(d[0]).append(",").append(d[1]).append(",").append(d[2]); }
        }
        srv.runCommand("kill @e[tag=gt]");
        log("Fullbox placed=" + placed + " missing=" + missing);
        assertTrue(missing == 0, "trap not complete, holes at:" + holes);
        assertTrue(Math.abs(yawAfter - yawBefore) < 0.01f && Math.abs(pitchAfter - pitchBefore) < 0.01f,
                "camera moved: " + yawBefore + "/" + pitchBefore + " -> " + yawAfter + "/" + pitchAfter);
        assertTrue(slot == 0, "did not swap back to slot 0 (now " + slot + ")");
    }

    private void testProfiles(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            Module sprint = ModuleManager.getModuleByName("AutoSprint");
            if (sprint.enabled) sprint.toggle();
            Profiles.INSTANCE.save("gametest");
            sprint.toggle(); // jetzt an
        });
        boolean onBefore = ctx.computeOnClient(mc -> ModuleManager.getModuleByName("AutoSprint").enabled);
        ctx.runOnClient(mc -> Profiles.INSTANCE.load("gametest"));
        boolean onAfter = ctx.computeOnClient(mc -> ModuleManager.getModuleByName("AutoSprint").enabled);
        ctx.runOnClient(mc -> Profiles.INSTANCE.delete("gametest"));
        assertTrue(onBefore, "setup: AutoSprint should be on before loading");
        assertTrue(!onAfter, "profile load did not switch AutoSprint back off");
    }

    @SuppressWarnings("unchecked")
    private void testEmoteEditor(ClientGameTestContext ctx) throws Exception {
        ctx.runOnClient(mc -> EmoteEditor.INSTANCE.openNew());
        ctx.waitTicks(5);
        Field f = EmoteEditor.class.getDeclaredField("keyframes");
        f.setAccessible(true);
        AtomicReference<Integer> afterKey = new AtomicReference<>(), afterUndo = new AtomicReference<>(), afterRedo = new AtomicReference<>();
        ctx.runOnClient(mc -> {
            Map<?, java.util.NavigableMap<Float, float[]>> keys = (Map<?, java.util.NavigableMap<Float, float[]>>) f.get(EmoteEditor.INSTANCE);
            EmoteEditor.INSTANCE.keyPressed(Keys.KEY_K, 0);
            afterKey.set(total(keys));
            EmoteEditor.INSTANCE.keyPressed(Keys.KEY_Z, Keys.MOD_CONTROL);
            afterUndo.set(total(keys));
            EmoteEditor.INSTANCE.keyPressed(Keys.KEY_Y, Keys.MOD_CONTROL);
            afterRedo.set(total(keys));
        });
        ctx.takeScreenshot("newbridge_emote_editor");
        ctx.runOnClient(mc -> EmoteEditor.INSTANCE.close());
        ctx.waitTicks(3);
        assertTrue(afterKey.get() > 0, "K did not create a keyframe");
        assertTrue(afterUndo.get() == 0, "undo did not remove the keyframe (" + afterUndo.get() + ")");
        assertTrue(afterRedo.get().equals(afterKey.get()), "redo did not restore (" + afterRedo.get() + ")");
    }

    private void testEmoteBend(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -4, Y - 1, -6, 6, Y - 1, 6, "stone");
        srv.runCommand("clear @a");
        srv.runCommand("item replace entity @a armor.head with diamond_helmet");
        srv.runCommand("item replace entity @a armor.chest with diamond_chestplate");
        srv.runCommand("item replace entity @a armor.legs with iron_leggings");
        srv.runCommand("item replace entity @a armor.feet with diamond_boots");
        teleport(ctx, srv, 0, Y, 0);
        for (int pass = 0; pass < 2; pass++) {
            final boolean sit = pass == 0;
            ctx.runOnClient(mc -> {
                var b = com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation.builder("gt_bend", "test", 2f).hold().fade(0f, 0f);
                var E = com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation.Easing.LINEAR;
                var B = com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimation.Bone.class;
                if (sit) { // sitting: thighs forward, shins down, elbows bent, back slightly bent
                    b.rot(Enum.valueOf(B, "RIGHT_LEG"), 0f, E, -85f, 0f, 0f).rot(Enum.valueOf(B, "LEFT_LEG"), 0f, E, -85f, 0f, 0f)
                     .rot(Enum.valueOf(B, "RIGHT_KNEE"), 0f, E, 90f, 0f, 0f).rot(Enum.valueOf(B, "LEFT_KNEE"), 0f, E, 90f, 0f, 0f)
                     .rot(Enum.valueOf(B, "RIGHT_ARM"), 0f, E, -40f, 0f, 0f).rot(Enum.valueOf(B, "LEFT_ARM"), 0f, E, -40f, 0f, 0f)
                     .rot(Enum.valueOf(B, "RIGHT_ELBOW"), 0f, E, -100f, 0f, 0f).rot(Enum.valueOf(B, "LEFT_ELBOW"), 0f, E, -100f, 0f, 0f)
                     .rot(Enum.valueOf(B, "SPINE"), 0f, E, 25f, 0f, 0f)
                     .pos(Enum.valueOf(B, "ROOT"), 0f, E, 0f, 10f, 0f);
                } else { // walking lunge: one knee deep, other leg back, arms bent sideways
                    b.rot(Enum.valueOf(B, "RIGHT_LEG"), 0f, E, -60f, 0f, 0f).rot(Enum.valueOf(B, "LEFT_LEG"), 0f, E, 25f, 0f, 0f)
                     .rot(Enum.valueOf(B, "RIGHT_KNEE"), 0f, E, 70f, 0f, 0f).rot(Enum.valueOf(B, "LEFT_KNEE"), 0f, E, 20f, 0f, 0f)
                     .rot(Enum.valueOf(B, "RIGHT_ARM"), 0f, E, 0f, 0f, 70f).rot(Enum.valueOf(B, "LEFT_ARM"), 0f, E, 0f, 0f, -70f)
                     .rot(Enum.valueOf(B, "RIGHT_ELBOW"), 0f, E, -70f, 0f, 0f).rot(Enum.valueOf(B, "LEFT_ELBOW"), 0f, E, -120f, 0f, 0f);
                }
                com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimator.INSTANCE.play(mc.player.getUUID(), b.build());
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            });
            ctx.waitTicks(20);
            Path shot = ctx.takeScreenshot(sit ? "newbridge_emote_bend_sit" : "newbridge_emote_bend_lunge");
            log("Emote bend screenshot: " + shot);
        }
        ctx.runOnClient(mc -> {
            com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteAnimator.INSTANCE.clear();
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        });
        ctx.waitTicks(3);
        prepareInventory(srv);
    }

    private void testWaypointWindow(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(
                new com.OsamaClient.newbridge.Hacks.Misc.WaypointsWindow()));
        ctx.waitTicks(5);
        String err = ctx.computeOnClient(mc -> {
            var w = (com.OsamaClient.newbridge.Hacks.Misc.WaypointsWindow) com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.current();
            w.startNew();
            w.nameField().text = "Editor Test";
            w.setColor(0xFFA78BFA);
            w.setCoords("~10", "~", "~-4");
            return w.saveEditor();
        });
        assertTrue(err == null, "editor save failed: " + err);
        ctx.waitTicks(5);
        Path shot = ctx.takeScreenshot("newbridge_waypoint_manager");
        log("Waypoint manager screenshot: " + shot);
        var wp = ctx.computeOnClient(mc -> Waypoints.INSTANCE.store.find(Waypoints.INSTANCE.serverKey(mc), "Editor Test"));
        assertTrue(wp != null, "waypoint from editor not stored");
        assertTrue(wp.color == 0xFFA78BFA, "color not stored");
        int px = ctx.computeOnClient(mc -> mc.player.blockPosition().getX());
        assertTrue(wp.x == px + 10, "relative ~10 not applied: " + wp.x + " vs player " + px);
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close());
        ctx.waitTicks(3);
        boolean closed = ctx.computeOnClient(mc -> mc.gui.screen() == null);
        assertTrue(closed, "window did not close");
    }

    private void testProfilesWindow(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            Profiles.INSTANCE.save("gt_pvp");
            Profiles.INSTANCE.save("gt_mining");
            var w = new com.OsamaClient.newbridge.Hacks.Client.ProfilesWindow();
            com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(w);
            w.select("gt_pvp");
        });
        ctx.waitTicks(5);
        Path shot = ctx.takeScreenshot("newbridge_profiles_manager");
        log("Profiles manager screenshot: " + shot);
        ctx.runOnClient(mc -> {
            com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close();
            Profiles.INSTANCE.delete("gt_pvp");
            Profiles.INSTANCE.delete("gt_mining");
        });
        ctx.waitTicks(3);
    }

    private void testClickGuiDock(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.open());
        ctx.waitTicks(20);
        Path shot = ctx.takeScreenshot("newbridge_clickgui_dock");
        log("ClickGui screenshot: " + shot);
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.close());
        ctx.waitTicks(20);
        int docks = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.dock().size());
        assertTrue(docks >= 2, "dock has " + docks + " entries");
    }

    private void testMinimap(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -6, Y - 1, -6, 16, Y - 1, 12, "grass_block");
        fill(srv, 4, Y, -2, 6, Y + 2, 2, "stone");
        fill(srv, -4, Y - 1, 4, 2, Y - 1, 10, "water");
        teleport(ctx, srv, 0, Y, 0);
        ctx.runOnClient(mc -> {
            var mm = com.OsamaClient.newbridge.Hacks.Visual.minimap.Minimap.INSTANCE;
            if (!mm.enabled) mm.toggle();
            Waypoints.INSTANCE.handleCommand(new String[]{".wp", "add", "MapTest", String.valueOf(ox + 12), String.valueOf(Y), String.valueOf(oz - 8)});
        });
        ctx.waitTicks(60);
        int chunks = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.minimap.Minimap.INSTANCE.cachedChunks());
        Path shot = ctx.takeScreenshot("newbridge_minimap");
        log("Minimap screenshot: " + shot + " chunks=" + chunks);
        ctx.runOnClient(mc -> {
            var mm = com.OsamaClient.newbridge.Hacks.Visual.minimap.Minimap.INSTANCE;
            mm.rotate = false;
            mm.shape = "Square";
        });
        ctx.waitTicks(5);
        ctx.takeScreenshot("newbridge_minimap_square_northup");
        ctx.runOnClient(mc -> {
            var mm = com.OsamaClient.newbridge.Hacks.Visual.minimap.Minimap.INSTANCE;
            mm.rotate = true;
            mm.shape = "Circle";
            if (mm.enabled) mm.toggle();
            Waypoints.INSTANCE.handleCommand(new String[]{".wp", "del", "MapTest"});
        });
        assertTrue(chunks > 0, "no chunks scanned");
    }

    private void testAutoArmor(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 2, Y - 1, 2, "stone");
        teleport(ctx, srv, 0, Y, 0);
        srv.runCommand("clear @a");
        srv.runCommand("item replace entity @a armor.head with iron_helmet");
        srv.runCommand("item replace entity @a inventory.4 with diamond_helmet");
        srv.runCommand("item replace entity @a inventory.5 with diamond_chestplate");
        srv.runCommand("item replace entity @a hotbar.4 with leather_boots");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> {
            var a = com.OsamaClient.newbridge.Hacks.Player.AutoArmor.INSTANCE;
            a.delay = 2;
            if (!a.enabled) a.toggle();
        });
        ctx.waitTicks(40);
        String head = ctx.computeOnClient(mc -> mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem().toString());
        String chest = ctx.computeOnClient(mc -> mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem().toString());
        String feet = ctx.computeOnClient(mc -> mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).getItem().toString());
        int ironLeft = count(ctx, Items.IRON_HELMET);
        ctx.runOnClient(mc -> { var a = com.OsamaClient.newbridge.Hacks.Player.AutoArmor.INSTANCE; if (a.enabled) a.toggle(); });
        srv.runCommand("clear @a");
        log("AutoArmor head=" + head + " chest=" + chest + " feet=" + feet);
        assertTrue(head.contains("diamond_helmet"), "head is " + head);
        assertTrue(chest.contains("diamond_chestplate"), "chest is " + chest);
        assertTrue(feet.contains("leather_boots"), "feet is " + feet);
        assertTrue(ironLeft == 1, "old iron helmet should be back in the inventory");
    }

    private void testStashLogger(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 14, Y - 1, 4, "stone");
        fill(srv, 4, Y, 2, 15, Y, 2, "chest");
        fill(srv, 4, Y + 1, 2, 7, Y + 1, 2, "barrel");
        teleport(ctx, srv, 0, Y, 0);
        ctx.runOnClient(mc -> {
            var st = com.OsamaClient.newbridge.Hacks.Misc.stash.StashLogger.INSTANCE;
            st.store.clear(st.key(mc));
            if (st.enabled) st.toggle();
            st.toggle();
        });
        ctx.waitTicks(60);
        int found = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Misc.stash.StashLogger.INSTANCE.store
                .list(com.OsamaClient.newbridge.Hacks.Misc.stash.StashLogger.INSTANCE.key(mc)).size());
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(
                new com.OsamaClient.newbridge.Hacks.Misc.stash.StashesWindow()));
        ctx.waitTicks(5);
        Path shot = ctx.takeScreenshot("newbridge_stashes");
        log("Stash screenshot: " + shot + " found=" + found);
        ctx.runOnClient(mc -> {
            com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close();
            var st = com.OsamaClient.newbridge.Hacks.Misc.stash.StashLogger.INSTANCE;
            if (st.enabled) st.toggle();
        });
        clearArea(srv);
        assertTrue(found >= 1, "no stash found");
    }

    private void testMacros(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            var m = com.OsamaClient.newbridge.Hacks.Client.macro.Macros.INSTANCE;
            var mac = m.store.add("GameTest");
            mac.actions = ".wp add MacroWP; wait 2; .wp add MacroWP2";
            mac.delay = 1;
            m.run(mac);
        });
        ctx.waitTicks(20);
        boolean a = ctx.computeOnClient(mc -> Waypoints.INSTANCE.store.find(Waypoints.INSTANCE.serverKey(mc), "MacroWP") != null);
        boolean b = ctx.computeOnClient(mc -> Waypoints.INSTANCE.store.find(Waypoints.INSTANCE.serverKey(mc), "MacroWP2") != null);
        ctx.runOnClient(mc -> {
            var m = com.OsamaClient.newbridge.Hacks.Client.macro.Macros.INSTANCE;
            var w = new com.OsamaClient.newbridge.Hacks.Client.macro.MacrosWindow();
            com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(w);
            w.select(m.store.find("GameTest"));
        });
        ctx.waitTicks(5);
        ctx.takeScreenshot("newbridge_macros");
        ctx.runOnClient(mc -> {
            com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close();
            var m = com.OsamaClient.newbridge.Hacks.Client.macro.Macros.INSTANCE;
            m.store.remove(m.store.find("GameTest"));
            Waypoints.INSTANCE.handleCommand(new String[]{".wp", "del", "MacroWP"});
            Waypoints.INSTANCE.handleCommand(new String[]{".wp", "del", "MacroWP2"});
        });
        assertTrue(a, "first macro step (.wp add MacroWP) did not run");
        assertTrue(b, "second step after wait did not run");
    }

    private void testAutoLog(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            var a = com.OsamaClient.newbridge.Hacks.Player.AutoLog.INSTANCE;
            a.dryRun = true;
            a.lastReason = null;
            a.health = 10;          // volle 10 Herzen -> muss sofort auslösen
            a.disableAfter = true;
            if (!a.enabled) a.toggle();
        });
        ctx.waitTicks(5);
        String reason = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Player.AutoLog.INSTANCE.lastReason);
        boolean stillOn = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Player.AutoLog.INSTANCE.enabled);
        ctx.runOnClient(mc -> {
            var a = com.OsamaClient.newbridge.Hacks.Player.AutoLog.INSTANCE;
            a.dryRun = false;
            a.health = 4;
            if (a.enabled) a.toggle();
        });
        assertTrue(reason != null && reason.startsWith("health"), "did not trigger: " + reason);
        assertTrue(!stillOn, "Disable After did not turn the module off");
    }

    private void testDeathWaypoint(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 2, Y - 1, 2, "stone");
        teleport(ctx, srv, 0, Y, 0);
        srv.runCommand("kill @a");
        int waited = ctx.waitFor(mc -> hasDeathWaypoint(), 100);
        log("death waypoint after " + waited + " ticks");
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> {
            if (mc.player != null && mc.player.isDeadOrDying()) mc.player.respawn();
            mc.gui.setScreen(null);
        });
        ctx.waitTicks(20);
        assertTrue(ctx.computeOnClient(mc -> hasDeathWaypoint()), "no death waypoint was created");
    }


    private void testNoRenderHooks() {
        try {
            Class<?> hud = Class.forName("net.minecraft.client.gui.Hud");
            StringBuilder names = new StringBuilder();
            for (var m : hud.getDeclaredMethods()) names.append(m.getName()).append(' ');
            String all = names.toString();
            for (String h : new String[]{"onExtractScoreboard", "onExtractCrosshair", "onExtractTitle", "onExtractSelectedItemName",
                    "onExtractPumpkinOverlay", "onExtractPortalOverlay", "onExtractVignetteOverlay"}) {
                assertTrue(all.contains(h), "NoRender hook missing in Hud: " + h);
            }
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    private void testGlide(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        prepareInventory(srv);
        teleport(ctx, srv, 0, Y + 4, 0);
        ctx.runOnClient(mc -> { var g = com.OsamaClient.newbridge.Hacks.Movement.Glide.INSTANCE; g.fallSpeed = 0.1; g.sneakToDrop = false; if (!g.enabled) g.toggle(); });
        srv.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %d %.1f", ox + 0.5, Y + 40, oz + 0.5));
        ctx.waitTicks(25);
        double vy = ctx.computeOnClient(mc -> mc.player.getDeltaMovement().y);
        double y = ctx.computeOnClient(mc -> mc.player.getY());
        ctx.runOnClient(mc -> { var g = com.OsamaClient.newbridge.Hacks.Movement.Glide.INSTANCE; if (g.enabled) g.toggle(); });
        // ohne Glide waere vy nach ~25 Ticks Fall bei etwa -1.0
        assertTrue(vy > -0.125, "fall speed not limited: " + vy);
        assertTrue(y > Y + 40 - 3.5, "fell too fast: y=" + y);
        assertTrue(com.OsamaClient.newbridge.Hacks.Movement.Glide.limit(-1.0, 0.1) == 0.08 - 0.1 / 0.98, "limit()");
    }

    private void testFastLadder(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 2, Y - 1, 2, "stone");
        fill(srv, -1, Y, 1, 1, Y + 14, 1, "stone");
        for (int i = 0; i < 12; i++) srv.runCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d ladder[facing=north]", ox, Y + i, oz));
        double[] climbed = new double[2];
        for (int run = 0; run < 2; run++) {
            final boolean fast = run == 1;
            srv.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %d %.1f 0 0", ox + 0.5, Y, oz + 0.5));
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> {
                var f = com.OsamaClient.newbridge.Hacks.Movement.FastLadder.INSTANCE;
                f.speed = 0.3; f.onlyWhenMoving = true;
                if (f.enabled != fast) f.toggle();
                mc.options.keyUp.setDown(true);
            });
            double y0 = ctx.computeOnClient(mc -> mc.player.getY());
            log("fastladder run " + run + " y0=" + y0 + " climbable=" + ctx.computeOnClient(mc -> mc.player.onClimbable())
                    + " block=" + ctx.computeOnClient(mc -> mc.level.getBlockState(mc.player.blockPosition()).getBlock().toString())
                    + " pos=" + ctx.computeOnClient(mc -> mc.player.position().toString()) + " Y=" + Y);
            for (int t = 0; t < 4; t++) {
                ctx.waitTicks(5);
                log("fastladder run " + run + " t=" + (t + 1) * 5 + " "
                        + ctx.computeOnClient(mc -> "y=" + mc.player.getY() + " vy=" + mc.player.getDeltaMovement().y
                        + " hc=" + mc.player.horizontalCollision + " fwd=" + mc.player.input.hasForwardImpulse()
                        + " crouch=" + mc.player.isCrouching() + " yaw=" + mc.player.getYRot() + " key=" + mc.options.keyUp.isDown()));
            }
            climbed[run] = ctx.computeOnClient(mc -> mc.player.getY()) - y0;
            ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
        }
        ctx.runOnClient(mc -> { var f = com.OsamaClient.newbridge.Hacks.Movement.FastLadder.INSTANCE; if (f.enabled) f.toggle(); });
        log("fastladder climbed vanilla=" + climbed[0] + " fast=" + climbed[1]);
        assertTrue(climbed[1] > climbed[0] * 1.6 && climbed[1] > 3.0, "not faster: vanilla=" + climbed[0] + " fast=" + climbed[1]);
    }

    private void testAntiAfk(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 2, Y - 1, 2, "stone");
        teleport(ctx, srv, 0, Y, 0);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        float yaw0 = ctx.computeOnClient(mc -> mc.player.getYRot());
        ctx.runOnClient(mc -> { var a = com.OsamaClient.newbridge.Hacks.Misc.AntiAfk.INSTANCE; a.intervalSeconds = 1.0; a.rotate = true; a.swing = true; a.jump = false; if (a.enabled) a.toggle(); a.toggle(); });
        ctx.waitTicks(65);
        int actions = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Misc.AntiAfk.INSTANCE.actions());
        float yaw1 = ctx.computeOnClient(mc -> mc.player.getYRot());
        ctx.runOnClient(mc -> { var a = com.OsamaClient.newbridge.Hacks.Misc.AntiAfk.INSTANCE; a.intervalSeconds = 20.0; if (a.enabled) a.toggle(); });
        assertTrue(actions >= 2, "AntiAFK did not act, actions=" + actions);
        assertTrue(Math.abs(yaw1 - yaw0) > 0.5f || actions >= 3, "head did not move: " + yaw0 + " -> " + yaw1);
    }

    private void testBlockPickerIcons(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
            gui.open();
            var miner = (com.OsamaClient.newbridge.Hacks.Misc.AutoMiner) ModuleManager.getModuleByName("AutoMiner");
            for (var b : new net.minecraft.world.level.block.Block[]{Blocks.DIAMOND_ORE, Blocks.ANCIENT_DEBRIS, Blocks.COBBLESTONE})
                if (!miner.blockPicker().isSelected(b)) miner.blockPicker().toggle(b);
            var u = com.OsamaClient.newbridge.UI.gui.util.UiSounds.class;
            com.OsamaClient.newbridge.UI.gui.util.UiSounds.click();
            com.OsamaClient.newbridge.UI.gui.util.UiSounds.toggle(true);
            com.OsamaClient.newbridge.UI.gui.util.UiSounds.toggle(false);
            com.OsamaClient.newbridge.UI.gui.util.UiSounds.select(true);
            com.OsamaClient.newbridge.UI.gui.util.UiSounds.tick();
            var panel = gui.panel(Module.Category.MISC);
            panel.setCollapsed(false);
            for (var row : panel.rows()) {
                if (!row.module().name.equals("AutoMiner")) continue;
                row.setExpanded(true);
                panel.scrollToRow(row);
                for (var w : row.widgets()) if (w instanceof com.OsamaClient.newbridge.UI.gui.setting.ListPickerWidget lp) lp.setOpen(true);
            }
        });
        ctx.waitTicks(40);
        Path shot = ctx.takeScreenshot("newbridge_blockpicker_icons");
        log("BlockPicker screenshot: " + shot);
        // Sprite lookup must work for common blocks
        for (var b : new net.minecraft.world.level.block.Block[]{Blocks.COBBLESTONE, Blocks.DIAMOND_ORE, Blocks.OAK_PLANKS}) {
            var sprite = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.setting.SettingWidgets.blockIcon(b));
            assertTrue(sprite != null, "no icon for " + b);
        }
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.close());
        ctx.waitTicks(10);
    }

    private void testClickBurst(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 2, Y - 1, 2, "stone");
        teleport(ctx, srv, 0, Y, 0);
        ctx.runOnClient(mc -> {
            mc.gui.setScreen(null);
            var b = com.OsamaClient.newbridge.Hacks.Combat.ClickBurst.INSTANCE;
            b.cps = 40.0; b.clicks = 20.0; b.rightClick = false; b.stopOnRelease = false;
            if (b.enabled) b.toggle();
            b.toggle();
            mc.options.keyAttack.setDown(true);
        });
        ctx.waitTicks(2);
        ctx.runOnClient(mc -> mc.options.keyAttack.setDown(false));
        int early = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Combat.ClickBurst.INSTANCE.clicksSent());
        ctx.waitTicks(20);
        int total = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Combat.ClickBurst.INSTANCE.clicksSent());
        log("clickburst early=" + early + " total=" + total);
        // stop on release: second burst gets cancelled
        ctx.runOnClient(mc -> {
            var b = com.OsamaClient.newbridge.Hacks.Combat.ClickBurst.INSTANCE;
            b.cps = 10.0; b.clicks = 100.0; b.stopOnRelease = true;
            mc.options.keyAttack.setDown(true);
        });
        ctx.waitTicks(3);
        ctx.runOnClient(mc -> mc.options.keyAttack.setDown(false));
        ctx.waitTicks(30);
        int total2 = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Combat.ClickBurst.INSTANCE.clicksSent());
        ctx.runOnClient(mc -> { var b = com.OsamaClient.newbridge.Hacks.Combat.ClickBurst.INSTANCE; b.stopOnRelease = false; if (b.enabled) b.toggle(); });
        assertTrue(total == 20, "burst should send exactly 20 clicks, sent " + total);
        assertTrue(early >= 4 && early < 20, "rate off (40 cps): " + early + " clicks after 2 ticks");
        assertTrue(total2 - total >= 1 && total2 - total < 10, "stop on release failed: " + (total2 - total));
    }

    private void testSpawnerFinder(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 8, Y - 1, 2, "stone");
        srv.runCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d spawner", ox + 6, Y, oz));
        teleport(ctx, srv, 0, Y, 0);
        ctx.runOnClient(mc -> {
            var f = com.OsamaClient.newbridge.Hacks.Donut.SpawnerFinder.INSTANCE;
            f.chat = true; f.waypoint = true; f.radiusChunks = 6;
            if (f.enabled) f.toggle();
            f.toggle();
        });
        ctx.waitTicks(60);
        var found = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Donut.SpawnerFinder.INSTANCE.found());
        ctx.runOnClient(mc -> { var f = com.OsamaClient.newbridge.Hacks.Donut.SpawnerFinder.INSTANCE; f.waypoint = false; if (f.enabled) f.toggle(); });
        srv.runCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d air", ox + 6, Y, oz));
        assertTrue(found.size() == 1 && found.get(0).equals(new BlockPos(ox + 6, Y, oz)), "spawners found: " + found);
    }

    private void testPlayerAlert(ClientGameTestContext ctx) {
        var P = com.OsamaClient.newbridge.Hacks.Donut.PlayerAlert.class;
        assertTrue(com.OsamaClient.newbridge.Hacks.Donut.PlayerAlert.within(63.9 * 63.9, 64), "inside");
        assertTrue(!com.OsamaClient.newbridge.Hacks.Donut.PlayerAlert.within(64.1 * 64.1, 64), "outside");
        ctx.runOnClient(mc -> { var a = com.OsamaClient.newbridge.Hacks.Donut.PlayerAlert.INSTANCE; if (a.enabled) a.toggle(); a.toggle(); });
        ctx.waitTicks(20);
        int alerts = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Donut.PlayerAlert.INSTANCE.alerts());
        ctx.runOnClient(mc -> { var a = com.OsamaClient.newbridge.Hacks.Donut.PlayerAlert.INSTANCE; if (a.enabled) a.toggle(); });
        assertTrue(alerts == 0, "alert although alone: " + alerts);
    }

    private void testAutoWeapon(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -3, Y - 1, -3, 6, Y - 1, 3, "stone");
        prepareInventory(srv);
        srv.runCommand("item replace entity @a hotbar.3 with minecraft:diamond_sword");
        srv.runCommand("item replace entity @a hotbar.5 with minecraft:wooden_sword");
        teleport(ctx, srv, 0, Y, 0);
        srv.runCommand(String.format(java.util.Locale.ROOT, "summon minecraft:armor_stand %.1f %d %.1f {NoGravity:1b}", ox + 2.5, Y, oz + 0.5));
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> {
            var a = com.OsamaClient.newbridge.Hacks.Combat.AutoWeapon.INSTANCE;
            a.preferAxe = false; a.switchBack = true;
            if (a.enabled) a.toggle();
            a.toggle();
            mc.player.getInventory().setSelectedSlot(0);
            mc.player.setXRot(10f);
            mc.options.keyAttack.setDown(true);
        });
        ctx.waitTicks(5);
        int during = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        ctx.runOnClient(mc -> mc.options.keyAttack.setDown(false));
        ctx.waitTicks(5);
        int after = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        ctx.runOnClient(mc -> { var a = com.OsamaClient.newbridge.Hacks.Combat.AutoWeapon.INSTANCE; if (a.enabled) a.toggle(); });
        srv.runCommand("kill @e[type=armor_stand]");
        assertTrue(during == 3, "should select the diamond sword (slot 3), was " + during);
        assertTrue(after == 0, "should switch back to slot 0, was " + after);
    }

    private void testAutoWalk(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -3, Y - 1, -3, 12, Y - 1, 3, "stone");
        teleport(ctx, srv, 0, Y, 0);
        double x0 = ctx.computeOnClient(mc -> mc.player.getX());
        ctx.runOnClient(mc -> { var w = com.OsamaClient.newbridge.Hacks.Movement.AutoWalk.INSTANCE; w.sprint = true; if (w.enabled) w.toggle(); w.toggle(); });
        ctx.waitTicks(30);
        double x1 = ctx.computeOnClient(mc -> mc.player.getX());
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Movement.AutoWalk.INSTANCE.toggle());
        ctx.waitTicks(15);
        double x2 = ctx.computeOnClient(mc -> mc.player.getX());
        ctx.waitTicks(10);
        double x3 = ctx.computeOnClient(mc -> mc.player.getX());
        assertTrue(x1 - x0 > 3.0, "did not walk: " + (x1 - x0));
        assertTrue(Math.abs(x3 - x2) < 0.05, "kept walking after disable: " + (x3 - x2));
    }

    private void testUiTabsAndHud(ClientGameTestContext ctx, TestServerContext srv) {
        try {
            uiTabsAndHud(ctx, srv);
        } finally {
            ctx.runOnClient(mc -> {
                mc.options.keyUp.setDown(false);
                mc.options.keyAttack.setDown(false);
                var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
                gui.setTab(0);
                if (gui.isOpen()) gui.close();
                var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
                if (h.enabled) h.toggle();
            });
            ctx.waitTicks(20);
        }
    }

    private void uiTabsAndHud(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 16, Y - 1, 2, "stone");
        teleport(ctx, srv, 0, Y, 0);
        // HUD with keystrokes (keys held)
        ctx.runOnClient(mc -> {
            var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
            if (!h.enabled) h.toggle();
            h.showKeys.enabled = true;
            h.showCps.enabled = true;
            mc.options.keyUp.setDown(true);
            mc.options.keyAttack.setDown(true);
        });
        ctx.waitTicks(20);
        Path hud = ctx.takeScreenshot("newbridge_hud_keystrokes");
        log("HUD screenshot: " + hud);
        ctx.runOnClient(mc -> { mc.options.keyUp.setDown(false); mc.options.keyAttack.setDown(false); });
        ctx.waitTicks(5);
        // Tabs: HUD tab and enchantment picker
        ctx.runOnClient(mc -> {
            var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
            gui.open();
            gui.setTab(1);
        });
        ctx.waitTicks(30);
        Path hudTab = ctx.takeScreenshot("newbridge_clickgui_hud_tab");
        log("HUD tab screenshot: " + hudTab);
        int tab = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.tab());
        assertTrue(tab == 1, "HUD tab not active");
        ctx.runOnClient(mc -> {
            var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
            gui.setTab(0);
            var trade = ModuleManager.getModuleByName("AutoLibrarian");
            try {
                Field f = trade.getClass().getDeclaredField("enchantPicker");
                f.setAccessible(true);
                var ep = (com.OsamaClient.newbridge.UI.components.EnchantmentPicker) f.get(trade);
                ep.selectedEnchantments.put("Mending", 12);
                ep.selectedEnchantments.put("Sharpness", 20);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            var panel = gui.panel(trade.category);
            for (var row : panel.rows()) {
                if (row.module() != trade) continue;
                row.setExpanded(true);
                panel.scrollToRow(row);
                for (var w : row.widgets()) if (w instanceof com.OsamaClient.newbridge.UI.gui.setting.ListPickerWidget lp) lp.setOpen(true);
            }
        });
        ctx.waitTicks(40);
        Path ench = ctx.takeScreenshot("newbridge_enchant_picker");
        log("Enchant screenshot: " + ench);
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.close());
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> { var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance; if (h.enabled) h.toggle(); });
    }

    // ------------------------------------------------------------------ helpers

    private void run(String name, Check check) {
        try {
            check.run();
            report.add("PASS  " + name);
            log("PASS " + name);
        } catch (Throwable t) {
            failures++;
            report.add("FAIL  " + name + "  ->  " + t);
            LOG.error("FAIL {}", name, t);
        } finally {
            try {
                // Navigation sicher stoppen, falls ein Test abgebrochen ist
                Minecraft.getInstance().execute(() -> Navigator.INSTANCE.stop(Minecraft.getInstance()));
            } catch (Throwable ignored) { }
        }
    }

    private boolean navigate(ClientGameTestContext ctx, Goal goal, int timeoutTicks) {
        return navigate(ctx, goal, timeoutTicks, mc -> {});
    }

    private boolean navigate(ClientGameTestContext ctx, Goal goal, int timeoutTicks,
                             java.util.function.Consumer<Minecraft> perTick) {
        AtomicReference<Boolean> result = new AtomicReference<>();
        ctx.runOnClient(mc -> Navigator.INSTANCE.goTo(mc, "gametest", 1000, goal, result::set));
        int waited = 0;
        while (result.get() == null && waited < timeoutTicks) {
            ctx.waitTick();
            ctx.runOnClient(perTick::accept);
            waited++;
        }
        if (result.get() == null) {
            ctx.runOnClient(mc -> Navigator.INSTANCE.cancel(mc, "gametest"));
            BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
            log("navigate timeout at " + p.toShortString());
            return false;
        }
        BlockPos p = ctx.computeOnClient(mc -> mc.player.blockPosition());
        log("navigate finished=" + result.get() + " after " + waited + " ticks at " + p.toShortString());
        return result.get();
    }

    private void clearArea(TestServerContext srv) {
        srv.runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d air", ox - 6, Y - 6, oz - 12, ox + 16, Y + 6, oz + 12));
        srv.runCommand("kill @e[type=item]");
    }

    private void fill(TestServerContext srv, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        srv.runCommand(String.format(java.util.Locale.ROOT, "fill %d %d %d %d %d %d %s", ox + x1, y1, oz + z1, ox + x2, y2, oz + z2, block));
    }

    private void teleport(ClientGameTestContext ctx, TestServerContext srv, int dx, int y, int dz) {
        srv.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.1f %d %.1f -90 0", ox + dx + 0.5, y, oz + dz + 0.5));
        srv.runCommand("effect clear @a");
        ctx.waitTicks(15);
    }

    private void prepareInventory(TestServerContext srv) {
        srv.runCommand("clear @a");
        srv.runCommand("item replace entity @a hotbar.0 with diamond_pickaxe");
        srv.runCommand("item replace entity @a hotbar.1 with cobblestone 64");
        srv.runCommand("item replace entity @a hotbar.2 with diamond_shovel");
    }

    private static int count(ClientGameTestContext ctx, Item item) {
        return ctx.computeOnClient(mc -> {
            int n = 0;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(item)) n += s.getCount();
            }
            return n;
        });
    }

    private Block block(TestServerContext srv, int dx, int y, int dz) {
        return srv.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(ox + dx, y, oz + dz)).getBlock());
    }

    private int countBlocks(TestServerContext srv, Block b) {
        return srv.computeOnServer((MinecraftServer s) -> {
            int n = 0;
            for (int x = ox - 6; x <= ox + 16; x++)
                for (int y = Y - 6; y <= Y + 6; y++)
                    for (int z = oz - 12; z <= oz + 12; z++) {
                        var st = s.overworld().getBlockState(new BlockPos(x, y, z));
                        if (st.is(b) || (b == Blocks.LAVA && st.getFluidState().getType() == Fluids.FLOWING_LAVA)) n++;
                    }
            return n;
        });
    }

    private float villagerHealth(TestServerContext srv) {
        return srv.computeOnServer(s -> {
            for (Entity e : s.overworld().getAllEntities()) {
                if (e.entityTags().contains("gt") && e instanceof LivingEntity le) return le.getHealth();
            }
            return -1f;
        });
    }

    private static int countWaypoints() {
        return Waypoints.INSTANCE.current(Minecraft.getInstance()).size();
    }

    private static boolean hasDeathWaypoint() {
        for (var w : Waypoints.INSTANCE.current(Minecraft.getInstance())) if (w.death) return true;
        return false;
    }

    private static int total(Map<?, java.util.NavigableMap<Float, float[]>> keys) {
        int n = 0;
        for (var m : keys.values()) n += m.size();
        return n;
    }

    private static void assertTrue(boolean cond, String message) {
        if (!cond) throw new AssertionError(message);
    }

    private void log(String s) {
        LOG.info(s);
    }

    private void writeReport(ClientGameTestContext ctx) {
        try {
            Path dir = ctx.computeOnClient(mc -> mc.gameDirectory.toPath());
            StringBuilder sb = new StringBuilder("NewBridge client gametest report\n\n");
            for (String line : report) sb.append(line).append('\n');
            sb.append('\n').append(failures == 0 ? "ALL PASSED" : failures + " FAILED").append('\n');
            Files.writeString(dir.resolve("newbridge-gametest-report.txt"), sb.toString(), StandardCharsets.UTF_8);
            LOG.info("\n{}", sb);
        } catch (Exception e) {
            LOG.error("Could not write report", e);
        }
    }
}
