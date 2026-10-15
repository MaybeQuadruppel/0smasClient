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
            run("autogap: eats a golden apple at low health", () -> guarded(ctx, srv, () -> testAutoGap(ctx, srv)));
            run("shieldblock: raises the shield near a zombie", () -> guarded(ctx, srv, () -> testShieldBlock(ctx, srv)));
            run("safewalk: does not walk off the platform edge", () -> guarded(ctx, srv, () -> testSafeWalk(ctx, srv)));
            run("bhop: jumps while holding forward", () -> guarded(ctx, srv, () -> testBHop(ctx, srv)));
            run("zoom: changes and restores the fov", () -> guarded(ctx, srv, () -> testZoom(ctx)));
            run("armorwarning: warns for worn boots", () -> guarded(ctx, srv, () -> testArmorWarning(ctx, srv)));
            run("autodrop: drops the junk item only", () -> guarded(ctx, srv, () -> testAutoDrop(ctx, srv)));
            run("autoreplenish: refills the hotbar stack", () -> guarded(ctx, srv, () -> testAutoReplenish(ctx, srv)));
            run("toolsaver: swaps off a nearly broken tool", () -> guarded(ctx, srv, () -> testToolSaver(ctx, srv)));
            run("antivoid: bounces up near the world bottom", () -> guarded(ctx, srv, () -> testAntiVoid(ctx, srv)));
            run("teabag: toggles crouch near an entity", () -> guarded(ctx, srv, () -> testTeabag(ctx, srv)));
            run("hotbardance: cycles the hotbar slot", () -> guarded(ctx, srv, () -> testHotbarDance(ctx, srv)));
            run("totemalert + invfullalert: flags", () -> guarded(ctx, srv, () -> testAlerts(ctx, srv)));
            run("packetgate: holds movement packets until released, cancel mode drops", () -> guarded(ctx, srv, () -> testPacketGate(ctx, srv)));
            run("pingspoof: delays pong replies", () -> guarded(ctx, srv, () -> testPingSpoof(ctx, srv)));
            run("guidesync: sends close packet, screen stays open", () -> guarded(ctx, srv, () -> testGuiDesync(ctx, srv)));
            run("packetburst: sends N packets", () -> guarded(ctx, srv, () -> testPacketBurst(ctx, srv)));
            run("bundledupe: runs the packet loop and stops at max cycles", () -> guarded(ctx, srv, () -> testBundleDupe(ctx, srv)));
            run("clickgui: dupe category, auto scale, max panel height, search + filters (screenshot)", () -> guarded(ctx, srv, () -> testClickGuiUpgrade(ctx, srv)));
            run("fakelag: hold mode (blink) holds movement, releases on disable", () -> guarded(ctx, srv, () -> testBlink(ctx, srv)));
            run("fakelag: interval mode releases periodically", () -> guarded(ctx, srv, () -> testFakeLagInterval(ctx, srv)));
            run("antihunger: hides onGround from the server", () -> guarded(ctx, srv, () -> testAntiHunger(ctx, srv)));
            run("airplace: places a block in mid air", () -> guarded(ctx, srv, () -> testAirPlace(ctx, srv)));
            run("fullbox self mode: surrounds the own feet with obsidian", () -> guarded(ctx, srv, () -> testSurround(ctx, srv)));
            run("antibot: fake players are not valid targets", () -> guarded(ctx, srv, () -> testAntiBot(ctx, srv)));
            run("viewmodel: moves the first person hand (screenshots)", () -> guarded(ctx, srv, () -> testViewmodel(ctx, srv)));
            run("notifications + friends", () -> guarded(ctx, srv, () -> testNotificationsFriends(ctx, srv)));
            run("hud redesign: widgets, layout editor drag, teammates, nametags (screenshots)", () -> testHudRedesign(ctx, srv));
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
            run("fuzz: every module on/off with all settings at min/max/cycled, no exceptions", () -> guarded(ctx, srv, () -> testFuzzModules(ctx, srv)));
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

    private void guarded(ClientGameTestContext ctx, TestServerContext srv, Runnable body) {
        try {
            body.run();
        } finally {
            ctx.runOnClient(mc -> {
                mc.options.keyUp.setDown(false);
                mc.options.keyShift.setDown(false);
                mc.options.keyUse.setDown(false);
                for (String n : new String[]{"AutoGap", "ShieldBlock", "SafeWalk", "BHop", "Zoom", "ArmorWarning", "AutoDrop",
                        "AutoReplenish", "ToolSaver", "AntiVoid", "Teabag", "HotbarDance", "TotemAlert", "InvFullAlert",
                        "Notifications", "Friends", "PacketGate", "PingSpoof", "GuiDesync", "PacketBurst", "BundleDupe", "FakeLag", "Fullbox", "AntiHunger", "AirPlace", "AntiBot", "Viewmodel"}) {
                    var m = ModuleManager.getModuleByName(n);
                    if (m != null && m.enabled) m.toggle();
                }
                if (mc.player != null) mc.player.getInventory().setSelectedSlot(0);
            });
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.discard();
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.pingDelayMs = 0;
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.tracking = false;
            srv.runCommand("kill @e[tag=gt]");
            srv.runCommand("kill @e[type=item]");
            srv.runCommand("difficulty peaceful");
            srv.runCommand("clear @a");
            ctx.waitTicks(5);
        }
    }

    private void on(ClientGameTestContext ctx, String name) {
        ctx.runOnClient(mc -> { var m = ModuleManager.getModuleByName(name); if (!m.enabled) m.toggle(); });
    }

    private int countItem(ClientGameTestContext ctx, net.minecraft.world.item.Item item) {
        return ctx.computeOnClient(mc -> {
            int n = 0;
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
            return n;
        });
    }

    private void flatStage(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 16, Y - 1, 2, "stone");
        teleport(ctx, srv, 0, Y, 0);
        srv.runCommand("clear @a");
        ctx.runOnClient(mc -> { mc.gui.setScreen(null); mc.player.setYRot(-90f); mc.player.getInventory().setSelectedSlot(0); });
        ctx.waitTicks(5);
    }

    private void testAutoGap(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("give @a golden_apple 3");
        ctx.waitTicks(5);
        srv.runCommand("damage @p 10");
        ctx.waitTicks(3);
        on(ctx, "AutoGap");
        ctx.waitTicks(80);
        int left = countItem(ctx, net.minecraft.world.item.Items.GOLDEN_APPLE);
        log("autogap apples left=" + left);
        assertTrue(left < 3, "no golden apple eaten, left " + left);
    }

    private void testShieldBlock(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("item replace entity @a weapon.offhand with shield");
        srv.runCommand("difficulty normal");
        srv.runCommand(String.format(java.util.Locale.ROOT, "summon zombie %d %d %d {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"gt\"]}", ox + 2, Y, oz));
        ctx.waitTicks(5);
        on(ctx, "ShieldBlock");
        ctx.waitTicks(15);
        boolean blocking = ctx.computeOnClient(mc -> mc.player.isBlocking());
        assertTrue(blocking, "player is not blocking with the shield");
        srv.runCommand("kill @e[tag=gt]");
        ctx.waitTicks(10);
        boolean after = ctx.computeOnClient(mc -> mc.player.isBlocking());
        assertTrue(!after, "still blocking without an enemy");
    }

    private void testSafeWalk(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        on(ctx, "SafeWalk");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
        ctx.waitTicks(130);
        double[] pos = ctx.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getY()});
        log("safewalk x=" + (pos[0] - ox) + " y=" + pos[1]);
        assertTrue(pos[1] > Y - 0.5, "fell off the edge, y=" + pos[1]);
        assertTrue(pos[0] - ox > 12, "did not walk, x=" + (pos[0] - ox));
    }

    private void testBHop(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        on(ctx, "BHop");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
        double max = 0;
        for (int i = 0; i < 24; i++) {
            ctx.waitTicks(1);
            max = Math.max(max, ctx.computeOnClient(mc -> mc.player.getY()) - Y);
        }
        log("bhop max height=" + max);
        assertTrue(max > 0.3, "no hop, max height " + max);
    }

    private void testZoom(ClientGameTestContext ctx) {
        int before = ctx.computeOnClient(mc -> mc.options.fov().get());
        on(ctx, "Zoom");
        ctx.waitTicks(2);
        int zoomed = ctx.computeOnClient(mc -> mc.options.fov().get());
        ctx.runOnClient(mc -> ModuleManager.getModuleByName("Zoom").toggle());
        int after = ctx.computeOnClient(mc -> mc.options.fov().get());
        log("zoom fov " + before + " -> " + zoomed + " -> " + after);
        assertTrue(zoomed == 30, "zoom fov " + zoomed);
        assertTrue(after == before, "fov not restored " + after);
    }

    private void testArmorWarning(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("item replace entity @a armor.feet with diamond_boots[damage=400]");
        ctx.waitTicks(5);
        on(ctx, "ArmorWarning");
        ctx.waitTicks(5);
        String w = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.ArmorWarning.INSTANCE.lastWarning);
        log("armor warning: " + w);
        assertTrue(w != null && w.contains("Boots"), "warning " + w);
    }

    private void testAutoDrop(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("give @a cobblestone 64");
        srv.runCommand("give @a dirt 10");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Misc.AutoDrop.INSTANCE.junk.selectedItems.put(net.minecraft.world.item.Items.COBBLESTONE, 1));
        on(ctx, "AutoDrop");
        ctx.waitTicks(15);
        int cobble = countItem(ctx, net.minecraft.world.item.Items.COBBLESTONE);
        int dirt = countItem(ctx, net.minecraft.world.item.Items.DIRT);
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Misc.AutoDrop.INSTANCE.junk.selectedItems.clear());
        log("autodrop cobble=" + cobble + " dirt=" + dirt);
        assertTrue(cobble == 0, "cobblestone not dropped: " + cobble);
        assertTrue(dirt == 10, "dirt changed: " + dirt);
    }

    private void testAutoReplenish(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("item replace entity @a hotbar.0 with cobblestone 3");
        srv.runCommand("item replace entity @a inventory.0 with cobblestone 64");
        ctx.waitTicks(5);
        on(ctx, "AutoReplenish");
        ctx.waitTicks(15);
        int hot = ctx.computeOnClient(mc -> mc.player.getInventory().getItem(0).getCount());
        log("autoreplenish hotbar0=" + hot);
        assertTrue(hot > 3, "hotbar stack not refilled: " + hot);
    }

    private void testToolSaver(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("item replace entity @a hotbar.0 with diamond_pickaxe[damage=1556]");
        ctx.waitTicks(5);
        on(ctx, "ToolSaver");
        ctx.waitTicks(5);
        int slot = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        assertTrue(slot != 0, "still holding the almost broken tool");
    }

    private void testAntiVoid(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        int minY = ctx.computeOnClient(mc -> mc.level.getMinY());
        on(ctx, "AntiVoid");
        srv.runCommand("tp @a " + ox + " " + (minY + 2) + " " + oz);
        double max = minY;
        for (int i = 0; i < 20; i++) {
            ctx.waitTicks(1);
            max = Math.max(max, ctx.computeOnClient(mc -> mc.player.getY()));
        }
        log("antivoid minY=" + minY + " max=" + max);
        assertTrue(max > minY + 4, "no bounce, max y " + max);
    }

    private void testTeabag(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand(String.format(java.util.Locale.ROOT, "summon villager %d %d %d {NoAI:1b,Tags:[\"gt\"]}", ox + 2, Y, oz));
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Troll.Teabag.INSTANCE.anyEntity = true);
        on(ctx, "Teabag");
        boolean up = false, down = false;
        for (int i = 0; i < 16; i++) {
            ctx.waitTicks(1);
            if (ctx.computeOnClient(mc -> mc.options.keyShift.isDown())) down = true; else up = true;
        }
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Troll.Teabag.INSTANCE.anyEntity = false);
        assertTrue(up && down, "crouch did not toggle (up=" + up + " down=" + down + ")");
    }

    private void testHotbarDance(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        on(ctx, "HotbarDance");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < 20; i++) {
            ctx.waitTicks(1);
            seen.add(ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot()));
        }
        assertTrue(seen.size() >= 3, "slots seen " + seen);
        ctx.runOnClient(mc -> ModuleManager.getModuleByName("HotbarDance").toggle());
        ctx.waitTicks(2);
        int back = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        assertTrue(back == 0, "slot not restored: " + back);
    }

    private void testAlerts(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        on(ctx, "TotemAlert");
        on(ctx, "InvFullAlert");
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Donut.InvFullAlert.INSTANCE.freeSlots = 36);
        ctx.waitTicks(5);
        boolean missing = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Donut.TotemAlert.INSTANCE.missing);
        boolean full = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Donut.InvFullAlert.INSTANCE.full);
        assertTrue(missing, "totem alert should flag missing totem");
        assertTrue(full, "inv alert should flag at threshold 36");
        srv.runCommand("give @a totem_of_undying 1");
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Donut.InvFullAlert.INSTANCE.freeSlots = 0);
        ctx.waitTicks(5);
        boolean missing2 = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Donut.TotemAlert.INSTANCE.missing);
        boolean full2 = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Donut.InvFullAlert.INSTANCE.full);
        assertTrue(!missing2, "totem alert still flags with a totem");
        assertTrue(!full2, "inv alert flags with free slots");
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Donut.InvFullAlert.INSTANCE.freeSlots = 3);
    }

    private double serverX(TestServerContext srv) {
        return srv.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getX());
    }

    private void testPacketGate(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        double start = serverX(srv);
        ctx.runOnClient(mc -> {
            var g = com.OsamaClient.newbridge.Hacks.Dupeing.PacketGate.INSTANCE;
            g.preset = "Movement";
            g.mode = "Delay";
        });
        on(ctx, "PacketGate");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
        ctx.waitTicks(25);
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
        ctx.waitTicks(3);
        int held = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.heldCount());
        double clientX = ctx.computeOnClient(mc -> mc.player.getX());
        double serverHeld = serverX(srv);
        log("packetgate held=" + held + " clientX=" + (clientX - ox) + " serverX=" + (serverHeld - ox) + " start=" + (start - ox));
        assertTrue(held > 10, "movement packets not held: " + held);
        assertTrue(clientX - start > 1.5, "client did not move");
        assertTrue(serverHeld - start < 0.5, "server moved while gate was open: " + (serverHeld - start));
        ctx.runOnClient(mc -> ModuleManager.getModuleByName("PacketGate").toggle());
        ctx.waitTicks(45);
        int after = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.heldCount());
        int paced = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.pacedCount());
        boolean connected = ctx.computeOnClient(mc -> mc.getConnection() != null && mc.getConnection().getConnection().isConnected());
        log("packetgate after flush held=" + after + " paced=" + paced + " connected=" + connected);
        assertTrue(after == 0 && paced == 0, "still holding after flush: " + after + "/" + paced);
        assertTrue(connected, "server kicked us after the flush");
        // cancel mode: nothing is queued
        ctx.runOnClient(mc -> {
            var g = com.OsamaClient.newbridge.Hacks.Dupeing.PacketGate.INSTANCE;
            g.mode = "Cancel";
        });
        on(ctx, "PacketGate");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
        int heldCancel = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.heldCount());
        assertTrue(heldCancel == 0, "cancel mode queued packets: " + heldCancel);
        ctx.runOnClient(mc -> { ModuleManager.getModuleByName("PacketGate").toggle(); com.OsamaClient.newbridge.Hacks.Dupeing.PacketGate.INSTANCE.mode = "Delay"; });
    }

    private void testPingSpoof(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        ctx.runOnClient(mc -> {
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.resetSeen();
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.tracking = true;
            com.OsamaClient.newbridge.Hacks.Dupeing.PingSpoof.INSTANCE.delayMs = 300;
        });
        on(ctx, "PingSpoof");
        ctx.runOnClient(mc -> mc.getConnection().send(new net.minecraft.network.protocol.common.ServerboundPongPacket(4242)));
        int early = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.seen("ServerboundPongPacket"));
        int delayed = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.pingDelayed.get());
        ctx.waitTicks(14);
        int later = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.seen("ServerboundPongPacket"));
        log("pingspoof early=" + early + " later=" + later + " delayed=" + delayed);
        assertTrue(early == 1 && delayed >= 1, "pong not intercepted");
        assertTrue(later == 2, "delayed pong never released: " + later);
    }

    private void testGuiDesync(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        ctx.runOnClient(mc -> {
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.resetSeen();
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.tracking = true;
            mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        });
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> ModuleManager.getModuleByName("GuiDesync").toggle());
        ctx.waitTicks(2);
        int closes = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.seen("ServerboundContainerClosePacket"));
        boolean screenOpen = ctx.computeOnClient(mc -> mc.gui.screen() != null);
        boolean stillEnabled = ctx.computeOnClient(mc -> ModuleManager.getModuleByName("GuiDesync").enabled);
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        assertTrue(closes == 1, "close packets sent: " + closes);
        assertTrue(screenOpen, "screen was closed");
        assertTrue(!stillEnabled, "one-shot module stayed enabled");
    }

    private void testPacketBurst(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        ctx.runOnClient(mc -> {
            var b = com.OsamaClient.newbridge.Hacks.Dupeing.PacketBurst.INSTANCE;
            b.mode = "Swing";
            b.count = 7;
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.resetSeen();
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.tracking = true;
            b.toggle();
        });
        ctx.waitTicks(3);
        int sent = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketBurst.INSTANCE.lastSent);
        int seen = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.seen("ServerboundPunchPacket"));
        assertTrue(sent == 7 && seen == 7, "burst sent=" + sent + " seen=" + seen);
        assertTrue(!ctx.computeOnClient(mc -> ModuleManager.getModuleByName("PacketBurst").enabled), "burst stayed enabled");
    }

    private void testBundleDupe(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("item replace entity @p hotbar.0 with bundle[bundle_contents=[{id:\"minecraft:cobblestone\",count:5}]]");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> {
            var d = com.OsamaClient.newbridge.Hacks.Dupeing.BundleDupe.INSTANCE;
            d.maxCycles = 2; d.bundlePackets = 3; d.pickupDelayMs = 10; d.putBackDelayMs = 10; d.dropDelayMs = 10;
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.resetSeen();
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.tracking = true;
            d.toggle();
        });
        ctx.waitTicks(20);
        int cycles = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.BundleDupe.INSTANCE.cycles);
        boolean running = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.BundleDupe.INSTANCE.isRunning());
        int sel = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.seen("ServerboundSelectBundleItemPacket"));
        log("bundledupe cycles=" + cycles + " running=" + running + " select=" + sel);
        ctx.runOnClient(mc -> { var d = com.OsamaClient.newbridge.Hacks.Dupeing.BundleDupe.INSTANCE; d.maxCycles = 0; d.bundlePackets = 20; d.pickupDelayMs = 30; d.putBackDelayMs = 20; d.dropDelayMs = 50; });
        assertTrue(cycles == 2, "cycles " + cycles);
        assertTrue(!running, "loop still running");
        assertTrue(sel >= 6, "bundle select packets " + sel);
    }

    private void testNotificationsFriends(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        on(ctx, "Notifications");
        ctx.runOnClient(mc -> { var m = ModuleManager.getModuleByName("BHop"); m.toggle(); m.toggle(); });
        String msg = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Client.Notifications.INSTANCE.lastMessage);
        assertTrue("BHop disabled".equals(msg), "notification '" + msg + "'");
        srv.runCommand(String.format(java.util.Locale.ROOT, "summon villager %d %d %d {NoAI:1b,Tags:[\"gt\"]}", ox + 3, Y, oz));
        ctx.waitTicks(10);
        on(ctx, "Friends");
        boolean[] r = ctx.computeOnClient(mc -> {
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.npc.villager.Villager v) {
                    boolean before = com.OsamaClient.newbridge.Utils.TeamUtils.isTeammate(v);
                    com.OsamaClient.newbridge.Hacks.Client.Friends.toggleFriend(v.getScoreboardName());
                    boolean added = com.OsamaClient.newbridge.Utils.TeamUtils.isTeammate(v);
                    com.OsamaClient.newbridge.Hacks.Client.Friends.toggleFriend(v.getScoreboardName());
                    boolean removed = com.OsamaClient.newbridge.Utils.TeamUtils.isTeammate(v);
                    return new boolean[]{before, added, removed};
                }
            }
            return new boolean[]{true, false, true};
        });
        assertTrue(!r[0] && r[1] && !r[2], "friends teammate flags " + java.util.Arrays.toString(r));
    }

    private void testHudRedesign(ClientGameTestContext ctx, TestServerContext srv) {
        try {
            hudRedesign(ctx, srv);
        } finally {
            ctx.runOnClient(mc -> {
                var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
                if (h.editLayout.enabled) { h.editLayout.enabled = false; h.closeEditor(); }
                if (h.enabled) h.toggle();
                for (String n : new String[]{"TeammateList", "Nametags", "ArrayList", "Minimap"}) {
                    var m = ModuleManager.getModuleByName(n);
                    if (m != null && m.enabled) m.toggle();
                }
                com.OsamaClient.newbridge.Hacks.Visual.HudLayout.reset("teammates");
                com.OsamaClient.newbridge.Hacks.Visual.HudLayout.reset("modulelist");
                com.OsamaClient.newbridge.Hacks.Visual.HudLayout.reset("minimap");
            });
            srv.runCommand("kill @e[tag=gt]");
            ctx.waitTicks(10);
        }
    }

    private void hudRedesign(ClientGameTestContext ctx, TestServerContext srv) {
        clearArea(srv);
        fill(srv, -2, Y - 1, -2, 16, Y - 1, 2, "stone");
        teleport(ctx, srv, 0, Y, 0);
        srv.runCommand("clear @a");
        srv.runCommand("give @a totem_of_undying 2");
        srv.runCommand("give @a ender_pearl 5");
        srv.runCommand("give @a golden_apple 3");
        srv.runCommand("give @a diamond_sword 1");
        srv.runCommand("effect give @a speed 120 1 true");
        srv.runCommand("effect give @a poison 30 0 true");
        srv.runCommand(String.format(java.util.Locale.ROOT, "summon villager %d %d %d {NoAI:1b,Tags:[\"gt\"],CustomName:'\"Gegner\"'}", ox + 6, Y, oz));
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
            if (!h.enabled) h.toggle();
            for (String n : new String[]{"TeammateList", "Nametags", "ArrayList"}) {
                var m = ModuleManager.getModuleByName(n);
                if (m != null && !m.enabled) m.toggle();
            }
            ((com.OsamaClient.newbridge.Hacks.Visual.Nametags) ModuleManager.getModuleByName("Nametags")).targetPicker.filters.replaceAll((k, v) -> true);
        });
        ctx.waitTicks(30);
        Path normal = ctx.takeScreenshot("newbridge_hud_widgets");
        log("HUD widgets screenshot: " + normal + " layer calls=" + com.OsamaClient.newbridge.Hacks.Visual.HudOverlayItemLayer.calls + " drawn=" + com.OsamaClient.newbridge.Hacks.Visual.HudOverlayItemLayer.drawn);
        // rows exist for the new widgets
        int[] counts = ctx.computeOnClient(mc -> {
            var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
            int held = 0, items = 0, eff = 0, info = 0;
            try {
                var m = h.getClass().getDeclaredMethod("rowsFor", String.class, net.minecraft.client.Minecraft.class);
                m.setAccessible(true);
                held = ((java.util.List<?>) m.invoke(h, "held", mc)).size();
                items = ((java.util.List<?>) m.invoke(h, "items", mc)).size();
                eff = ((java.util.List<?>) m.invoke(h, "effects", mc)).size();
                info = ((java.util.List<?>) m.invoke(h, "info", mc)).size();
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            return new int[]{held, items, eff, info};
        });
        log("widget rows held/items/effects/info = " + java.util.Arrays.toString(counts));
        assertTrue(counts[0] == 1, "held widget rows " + counts[0]);
        assertTrue(counts[1] >= 3, "item widget rows " + counts[1]);
        assertTrue(counts[2] == 2, "effect widget rows " + counts[2]);
        assertTrue(counts[3] >= 2, "info widget rows " + counts[3]);
        // layout editor: open, drag the teammates card via the API, check position persists
        ctx.runOnClient(mc -> {
            var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
            h.editLayout.enabled = true;
            h.openEditor();
        });
        ctx.waitTicks(15);
        boolean editing = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.editing());
        assertTrue(editing, "layout editor did not open");
        int seen = ctx.computeOnClient(mc -> {
            int n = 0;
            for (var e : com.OsamaClient.newbridge.Hacks.Visual.HudLayout.visible()) n++;
            return n;
        });
        assertTrue(seen >= 2, "layout editor elements: " + seen);
        ctx.runOnClient(mc -> {
            int w = mc.getWindow().getWidth(), hh = mc.getWindow().getHeight();
            for (var e : com.OsamaClient.newbridge.Hacks.Visual.HudLayout.visible()) {
                if (!e.id.equals("teammates")) continue;
                com.OsamaClient.newbridge.Hacks.Visual.HudLayout.beginDrag(e.x + 3, e.y + 3);
                com.OsamaClient.newbridge.Hacks.Visual.HudLayout.dragTo(w * 0.35f, hh * 0.45f, w, hh);
                com.OsamaClient.newbridge.Hacks.Visual.HudLayout.stopDrag();
            }
        });
        ctx.waitTicks(10);
        Path edit = ctx.takeScreenshot("newbridge_hud_layout_editor");
        log("HUD editor screenshot: " + edit);
        boolean moved = ctx.computeOnClient(mc -> {
            var p = com.OsamaClient.newbridge.Hacks.Visual.HudLayout.get("teammates");
            return p != null && p.x() > 0.2f && p.y() > 0.3f
                    && com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance.widgetPositions().containsKey("layout.teammates");
        });
        assertTrue(moved, "teammates card was not moved/persisted");
        ctx.runOnClient(mc -> {
            var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
            h.editLayout.enabled = false;
            h.closeEditor();
        });
        ctx.waitTicks(10);
        // teammates: custom-nametag fallback (tab list colour)
        // nametag screenshot: look at the zombie
        ctx.runOnClient(mc -> mc.player.setYRot(-90f));
        ctx.waitTicks(10);
        Path tags = ctx.takeScreenshot("newbridge_nametags");
        log("Nametags screenshot: " + tags);
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

    private void testBlink(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        double start = serverX(srv);
        setMode(ctx, "FakeLag", "Mode", 1);
        on(ctx, "FakeLag");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
        double client = ctx.computeOnClient(mc -> mc.player.getX());
        int held = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.heldCount());
        double server = serverX(srv);
        log("blink held=" + held + " clientX=" + (client - start) + " serverX=" + (server - start));
        assertTrue(held > 5, "nothing held: " + held);
        assertTrue(Math.abs(server - start) < 0.01, "server saw movement: " + (server - start));
        assertTrue(client - start > 1.0, "client did not move");
        ctx.runOnClient(mc -> ModuleManager.getModuleByName("FakeLag").toggle());
        setMode(ctx, "FakeLag", "Mode", 0);
        ctx.waitTicks(20);
        boolean connected = ctx.computeOnClient(mc -> mc.getConnection() != null && mc.getConnection().getConnection().isConnected());
        double after = serverX(srv);
        double clientAfter = ctx.computeOnClient(mc -> mc.player.getX());
        log("blink after release serverX=" + (after - start) + " clientX=" + (clientAfter - start));
        assertTrue(connected, "kicked after release");
        assertTrue(Math.abs(after - clientAfter) < 1.0, "server did not catch up: " + (after - clientAfter));
    }

    private void setMode(ClientGameTestContext ctx, String module, String label, int idx) {
        ctx.runOnClient(mc -> {
            for (var c : ModuleManager.getModuleByName(module).settings)
                if (c instanceof com.OsamaClient.newbridge.UI.components.ModeButton mb && mb.getLabel().equals(label)) mb.setIndex(idx);
        });
    }

    private void setToggle(ClientGameTestContext ctx, String module, String label, boolean v) {
        ctx.runOnClient(mc -> {
            for (var c : ModuleManager.getModuleByName(module).settings)
                if (c instanceof com.OsamaClient.newbridge.UI.components.ToggleButton tb && tb.getLabel().equals(label)) tb.setValue(v);
        });
    }

    private void testFakeLagInterval(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        double start = serverX(srv);
        setMode(ctx, "FakeLag", "Mode", 0);
        on(ctx, "FakeLag");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
        ctx.waitTicks(35);
        double server = serverX(srv);
        double client = ctx.computeOnClient(mc -> mc.player.getX());
        boolean connected = ctx.computeOnClient(mc -> mc.getConnection() != null && mc.getConnection().getConnection().isConnected());
        log("fakelag interval serverX=" + (server - start) + " clientX=" + (client - start) + " connected=" + connected);
        assertTrue(connected, "kicked");
        assertTrue(server - start > 0.5, "server never received a release");
        assertTrue(client - server > 0.05, "server is not lagging behind: " + (client - server));
    }

    private void testAntiHunger(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        ctx.runOnClient(mc -> { com.OsamaClient.newbridge.Hacks.Movement.AntiHunger.rewritten = 0; com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.groundPackets.set(0); com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.tracking = true; });
        on(ctx, "AntiHunger");
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(true));
        ctx.waitTicks(30);
        boolean clientGround = ctx.computeOnClient(mc -> mc.player.onGround());
        int sentGround = com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.groundPackets.get();
        int n = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Movement.AntiHunger.rewritten);
        log("antihunger rewritten=" + n + " clientGround=" + clientGround + " groundPacketsSent=" + sentGround);
        assertTrue(clientGround, "client should be on the ground");
        assertTrue(sentGround == 0, "packets still claimed onGround: " + sentGround);
        assertTrue(n > 5, "no packets rewritten: " + n);
        ctx.runOnClient(mc -> ModuleManager.getModuleByName("AntiHunger").toggle());
        ctx.waitTicks(10);
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.groundPackets.set(0));
        ctx.waitTicks(10);
        int restored = com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.groundPackets.get();
        assertTrue(restored > 0, "onGround not reported again after disabling");
    }

    private void testAirPlace(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("item replace entity @a hotbar.0 with stone 64");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> { mc.player.setXRot(-25f); mc.player.getInventory().setSelectedSlot(0); });
        on(ctx, "AirPlace");
        net.minecraft.core.BlockPos target = ctx.computeOnClient(mc -> ((net.minecraft.world.phys.BlockHitResult) mc.player.pick(5, 0f, false)).getBlockPos());
        ctx.runOnClient(mc -> mc.options.keyUse.setDown(true));
        ctx.waitTicks(8);
        ctx.runOnClient(mc -> mc.options.keyUse.setDown(false));
        boolean solid = ctx.computeOnClient(mc -> !mc.level.getBlockState(target).isAir());
        int placed = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Player.AirPlace.INSTANCE.placed);
        log("airplace target=" + target + " solid=" + solid + " placed=" + placed);
        assertTrue(solid, "no block at " + target);
        assertTrue(placed >= 1, "placed=" + placed);
    }

    private void testSurround(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("item replace entity @a hotbar.0 with obsidian 64");
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        net.minecraft.core.BlockPos feet = ctx.computeOnClient(mc -> mc.player.blockPosition());
        setMode(ctx, "Fullbox", "Target", 1);
        setToggle(ctx, "Fullbox", "Head", false);
        setToggle(ctx, "Fullbox", "Roof", false);
        on(ctx, "Fullbox");
        ctx.waitTicks(30);
        int ok = 0;
        StringBuilder missing = new StringBuilder();
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            net.minecraft.core.BlockPos q = feet.relative(d);
            boolean obs = ctx.computeOnClient(mc -> mc.level.getBlockState(q).is(net.minecraft.world.level.block.Blocks.OBSIDIAN));
            if (obs) ok++; else missing.append(d).append(' ');
        }
        int slot = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
        log("surround ok=" + ok + " missing=" + missing + " slot=" + slot);
        assertTrue(ok == 4, "missing sides: " + missing);
        // Repairs a hole.
        net.minecraft.core.BlockPos hole = feet.east();
        srv.runCommand(String.format(java.util.Locale.ROOT, "setblock %d %d %d air", hole.getX(), hole.getY(), hole.getZ()));
        ctx.waitTicks(15);
        boolean fixed = ctx.computeOnClient(mc -> mc.level.getBlockState(hole).is(net.minecraft.world.level.block.Blocks.OBSIDIAN));
        setMode(ctx, "Fullbox", "Target", 0);
        setToggle(ctx, "Fullbox", "Head", true);
        setToggle(ctx, "Fullbox", "Roof", true);
        assertTrue(fixed, "hole was not repaired");
    }

    private void testAntiBot(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        boolean[] r = ctx.computeOnClient(mc -> {
            var bot = new net.minecraft.client.player.RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "RealName"));
            bot.setId(987654);
            bot.setPos(mc.player.getX() + 2, mc.player.getY(), mc.player.getZ());
            var ab = com.OsamaClient.newbridge.Hacks.Combat.AntiBot.INSTANCE;
            boolean offBot = com.OsamaClient.newbridge.Hacks.Combat.AntiBot.isBot(bot);
            if (!ab.enabled) ab.toggle();
            bot.tickCount = 0;
            boolean freshOk = !com.OsamaClient.newbridge.Hacks.Combat.AntiBot.isBot(bot);
            bot.tickCount = 45;
            boolean noTab = com.OsamaClient.newbridge.Hacks.Combat.AntiBot.isBot(bot);
            boolean ka = !com.OsamaClient.newbridge.Hacks.Combat.KillAura.INSTANCE.isValidTarget(bot, mc);
            boolean self = !com.OsamaClient.newbridge.Hacks.Combat.AntiBot.isBot(mc.player);
            ab.toggle();
            boolean offAgain = !com.OsamaClient.newbridge.Hacks.Combat.AntiBot.isBot(bot);
            return new boolean[]{!offBot, freshOk, noTab, offAgain, self, ka};
        });
        log("antibot " + java.util.Arrays.toString(r));
        String[] names = {"off => not bot", "fresh player ok", "no tab entry => bot", "disabled again => not bot", "self ok", "killaura ignores bot"};
        for (int i = 0; i < r.length; i++) assertTrue(r[i], "antibot: " + names[i]);
    }

    private void testViewmodel(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("item replace entity @a hotbar.0 with diamond_sword");
        ctx.waitTicks(10);
        java.nio.file.Path a = ctx.takeScreenshot("newbridge_viewmodel_off");
        ctx.runOnClient(mc -> {
            var v = com.OsamaClient.newbridge.Hacks.Visual.Viewmodel.INSTANCE;
            v.mainX = -0.4; v.mainY = 0.3; v.mainScale = 0.5; v.mainRotZ = 30;
            if (!v.enabled) v.toggle();
        });
        ctx.waitTicks(5);
        java.nio.file.Path b = ctx.takeScreenshot("newbridge_viewmodel_on");
        int applied = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.Viewmodel.INSTANCE.applied);
        boolean differs;
        try { differs = !java.util.Arrays.equals(java.nio.file.Files.readAllBytes(a), java.nio.file.Files.readAllBytes(b)); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.Viewmodel.INSTANCE.toggle());
        log("viewmodel applied=" + applied + " differs=" + differs);
        assertTrue(applied > 0, "mixin never applied");
        assertTrue(differs, "screenshot unchanged");
    }

    private static int aboveGround(net.minecraft.server.level.ServerPlayer sp) {
        try {
            var f = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundTickCount");
            f.setAccessible(true);
            return f.getInt(sp.connection);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void testFuzzModules(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        // the fuzz flips many settings; a profile snapshot restores them so neither later tests nor the saved config are polluted
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.config.Config.saveProfile("fuzzbackup"));
        try {
            fuzzBody(ctx, srv);
        } finally {
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.config.Config.loadProfile("fuzzbackup");
                com.OsamaClient.newbridge.config.Config.deleteProfile("fuzzbackup");
            });
        }
    }

    private void fuzzBody(ClientGameTestContext ctx, TestServerContext srv) {
        java.nio.file.Path logFile = java.nio.file.Path.of("logs", "latest.log");
        long before;
        try { before = java.nio.file.Files.size(logFile); } catch (java.io.IOException e) { before = 0; }
        java.util.Set<String> skip = java.util.Set.of("AutoLog", "Spam", "AutoSell", "ClickGui", "RtpFinder", "Freecam", "AutoMiner");
        java.util.List<String> names = ctx.computeOnClient(mc -> {
            java.util.List<String> l = new java.util.ArrayList<>();
            for (var m : ModuleManager.modules) l.add(m.name);
            return l;
        });
        int tested = 0;
        StringBuilder thrown = new StringBuilder();
        for (String n : names) {
            if (skip.contains(n)) continue;
            try {
            ctx.runOnClient(mc -> {
                var m = ModuleManager.getModuleByName(n);
                if (m != null && !m.enabled) m.toggle();
            });
            ctx.waitTicks(2);
            ctx.runOnClient(mc -> {
                var m = ModuleManager.getModuleByName(n);
                if (m == null) return;
                for (var c : m.settings) {
                    if (c instanceof com.OsamaClient.newbridge.UI.components.Slider sl) {
                        double old = sl.getValue();
                        sl.setValue(sl.getMin());
                        sl.setValue(sl.getMax());
                        sl.setValue(old);
                    } else if (c instanceof com.OsamaClient.newbridge.UI.components.ToggleButton tb) {
                        boolean old = tb.enabled;
                        tb.setValue(!old);
                        tb.setValue(old);
                    } else if (c instanceof com.OsamaClient.newbridge.UI.components.ModeButton mb) {
                        int old = mb.getIndex();
                        for (int i = 0; i < mb.getModes().size(); i++) mb.setIndex(i);
                        mb.setIndex(old);
                    }
                }
            });
            ctx.waitTicks(3);
            ctx.runOnClient(mc -> {
                var m = ModuleManager.getModuleByName(n);
                if (m != null && m.enabled) m.toggle();
                if (mc.player != null) mc.player.getInventory().setSelectedSlot(0);
                mc.options.keyUp.setDown(false);
                mc.options.keyShift.setDown(false);
                mc.options.keyUse.setDown(false);
            });
            } catch (RuntimeException ex) {
                thrown.append(n).append(": ").append(ex).append('\n');
                ctx.runOnClient(mc -> { var m = ModuleManager.getModuleByName(n); if (m != null) m.enabled = false; });
            }
            ctx.waitTicks(1);
            com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.discard();
            tested++;
        }
        StringBuilder bad = new StringBuilder(thrown);
        try {
            byte[] all = java.nio.file.Files.readAllBytes(logFile);
            String added = new String(all, (int) Math.min(before, all.length), (int) (all.length - Math.min(before, all.length)), java.nio.charset.StandardCharsets.UTF_8);
            for (String line : added.split("\n")) {
                if (line.contains("Exception") || line.contains("/ERROR]")) {
                    if (line.contains("FAIL ")) continue;
                    if (bad.length() < 1500) bad.append(line.strip(), 0, Math.min(line.strip().length(), 200)).append('\n');
                }
            }
        } catch (java.io.IOException e) {
            log("fuzz: log not readable: " + e);
        }
        log("fuzz tested " + tested + " modules");
        assertTrue(bad.length() == 0, "exceptions logged while fuzzing:\n" + bad);
        assertTrue(tested > 80, "only " + tested + " modules fuzzed");
    }

    private void testClickGuiUpgrade(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
        var cfg = com.OsamaClient.newbridge.UI.gui.ClickGuiModule.INSTANCE;
        ctx.runOnClient(mc -> gui.open());
        ctx.waitTicks(20);
        // dupe category
        int dupeRows = ctx.computeOnClient(mc -> gui.panel(Module.Category.DUPE) == null ? -1 : gui.panel(Module.Category.DUPE).rows().size());
        log("clickgui dupe rows=" + dupeRows);
        assertTrue(dupeRows == 5, "Dupe panel rows: " + dupeRows);
        for (String n : new String[]{"PacketGate", "PingSpoof", "GuiDesync", "PacketBurst", "BundleDupe"}) {
            Module m = ModuleManager.getModuleByName(n);
            assertTrue(m.category == Module.Category.DUPE, n + " is not in Dupe");
        }
        // auto scale: all columns fit the window width
        float[] sc = ctx.computeOnClient(mc -> new float[]{gui.currentScale(), (float) cfg.scale.getValue(), mc.getWindow().getWidth()});
        float panelsW = 8f + gui.panels().size() * (92f + 6f);
        log("clickgui scale=" + sc[0] + " base=" + sc[1] + " window=" + sc[2] + " neededUnits=" + panelsW);
        assertTrue(sc[0] * panelsW <= sc[2] + 2f, "panels do not fit: " + sc[0] * panelsW + " > " + sc[2]);
        assertTrue(sc[0] < sc[1], "scale was not reduced");
        ctx.runOnClient(mc -> cfg.autoScale.setValue(false));
        ctx.waitTicks(5);
        float off = ctx.computeOnClient(mc -> gui.currentScale());
        ctx.runOnClient(mc -> cfg.autoScale.setValue(true));
        ctx.waitTicks(5);
        assertTrue(Math.abs(off - sc[1]) < 0.01f, "auto scale off should use the configured scale: " + off);
        // max panel height + scrolling
        float full = ctx.computeOnClient(mc -> gui.panel(Module.Category.COMBAT).lastHeight());
        ctx.runOnClient(mc -> cfg.maxPanelHeight.setValue(40));
        ctx.waitTicks(15);
        float[] capped = ctx.computeOnClient(mc -> new float[]{gui.panel(Module.Category.COMBAT).lastHeight(), mc.getWindow().getHeight() / gui.currentScale()});
        log("clickgui combat height full=" + full + " capped=" + capped[0] + " screenUnits=" + capped[1]);
        assertTrue(capped[0] <= capped[1] * 0.4f + 0.6f, "panel not capped: " + capped[0] + " vs " + capped[1] * 0.4f);
        assertTrue(capped[0] < full, "capped panel is not smaller than the full one");
        ctx.runOnClient(mc -> cfg.maxPanelHeight.setValue(100));
        ctx.waitTicks(10);
        // search + type to search + enabled filter
        int total = ctx.computeOnClient(mc -> gui.visibleMatches());
        ctx.runOnClient(mc -> gui.setSearch("kill"));
        ctx.waitTicks(3);
        int some = ctx.computeOnClient(mc -> gui.visibleMatches());
        assertTrue(some >= 1 && some < total, "search matches " + some + " of " + total);
        ctx.runOnClient(mc -> gui.setSearch(""));
        ctx.runOnClient(mc -> gui.charTyped('z'));
        String typed = ctx.computeOnClient(mc -> gui.searchQuery());
        assertTrue("z".equals(typed), "type to search gave '" + typed + "'");
        ctx.runOnClient(mc -> gui.setSearch(""));
        on(ctx, "Zoom");
        ctx.runOnClient(mc -> gui.toggleOnlyEnabled());
        ctx.waitTicks(3);
        int enabledShown = ctx.computeOnClient(mc -> gui.visibleMatches());
        long enabledReal = ctx.computeOnClient(mc -> ModuleManager.modules.stream().filter(m -> m.enabled).count());
        log("clickgui enabled-only shown=" + enabledShown + " real=" + enabledReal);
        assertTrue(enabledShown == enabledReal && enabledShown >= 1, "enabled filter " + enabledShown + " vs " + enabledReal);
        ctx.runOnClient(mc -> gui.toggleOnlyEnabled());
        ctx.runOnClient(mc -> ModuleManager.getModuleByName("Zoom").toggle());
        // collapse / expand all
        ctx.runOnClient(mc -> gui.toggleCollapseAll());
        ctx.waitTicks(15);
        boolean all = ctx.computeOnClient(mc -> gui.allCollapsed());
        assertTrue(all, "collapse all failed");
        ctx.runOnClient(mc -> gui.toggleCollapseAll());
        ctx.waitTicks(20);
        assertTrue(!ctx.computeOnClient(mc -> gui.allCollapsed()), "expand all failed");
        Path shot = ctx.takeScreenshot("newbridge_clickgui_dupe");
        log("ClickGui screenshot: " + shot);
        ctx.runOnClient(mc -> gui.close());
        ctx.waitTicks(15);
    }
}
