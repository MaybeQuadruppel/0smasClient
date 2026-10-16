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
            run("zoom: camera fov divided, wheel zoom with modifier, sensitivity, limits, reset", () -> guarded(ctx, srv, () -> testZoom(ctx)));
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
            run("keyboard overview: real keys, module + vanilla binds, conflicts (screenshot)", () -> guarded(ctx, srv, () -> testKeyboard(ctx, srv)));
            run("damageindicator: numbers for damage / heals, settings (screenshot)", () -> guarded(ctx, srv, () -> testDamageIndicator(ctx, srv)));
            run("ping + tps widget: tracker maths, live estimate, hud rows (screenshot)", () -> guarded(ctx, srv, () -> testPingTps(ctx, srv)));
            run("splitchat: filters, hide in main chat, window (screenshot)", () -> guarded(ctx, srv, () -> testSplitChat(ctx, srv)));
            run("favorites: persist in config, filter in clickgui (screenshot)", () -> guarded(ctx, srv, () -> testFavorites(ctx, srv)));
            run("namecensor: me / everyone, chat + scoreboard + font hooks (screenshots)", () -> guarded(ctx, srv, () -> testNameCensor(ctx, srv)));
            run("invtweaks: sort, steal, dump, buttons, shift-drag, auto steal (screenshot)", () -> guarded(ctx, srv, () -> testInvTweaks(ctx, srv)));
            run("keyboard: rebind keys, hotkey profiles, conflict warning (screenshot)", () -> guarded(ctx, srv, () -> testKeyboardRebind(ctx, srv)));
            run("config backups: create, restore, keep newest 10", () -> guarded(ctx, srv, () -> testConfigBackup(ctx, srv)));
            run("clickgui: search in descriptions + recent chip (screenshot)", () -> guarded(ctx, srv, () -> testSearchRecent(ctx, srv)));
            run("quickwaypoint: key press creates waypoints, also from freecam", () -> guarded(ctx, srv, () -> testQuickWaypoint(ctx, srv)));
            run("chat command suggestions: registry, tab completion, box (screenshot)", () -> guarded(ctx, srv, () -> testChatSuggest(ctx, srv)));
            run("invtweaks: shulker + ender chest preview (screenshot)", () -> guarded(ctx, srv, () -> testPreview(ctx, srv)));
            run("grim: check meter flags (reach, look, pitch, snap, ground, timer) + HUD (screenshot)", () -> guarded(ctx, srv, () -> testCheckMeter(ctx, srv)));
            run("grim: packet sanitizer fixes duplicate looks, pitch, status spam (unit + real connection)", () -> guarded(ctx, srv, () -> testSanitizer(ctx, srv)));
            run("grim: legit mode caps ranges, blocks and switches off never-legit modules", () -> guarded(ctx, srv, () -> testLegitMode(ctx, srv)));
            run("hud editor: real mouse drag moves every element", () -> guarded(ctx, srv, () -> testHudRealDrag(ctx, srv)));
            run("text input: real typing into a settings text box (NameCensor)", () -> guarded(ctx, srv, () -> testTextTyping(ctx, srv)));
            run("clickgui: Alt detail card for modules + settings, docs for everything, hold binds (screenshot)", () -> guarded(ctx, srv, () -> testDetailCard(ctx, srv)));
            run("clickgui: MMB reset, Shift+MMB reset all, Ctrl+Z undo, setting search, config export/import", () -> guarded(ctx, srv, () -> testResetUndoShare(ctx, srv)));
            run("friends: tab with online/offline/last seen, join alerts, .friend command, saved to file (screenshot)", () -> guarded(ctx, srv, () -> testFriendsTab(ctx, srv)));
            run("quickmenu: radial wheel with favourites, hold-release and click picking (screenshot)", () -> guarded(ctx, srv, () -> testQuickMenu(ctx, srv)));
            run("nametags: armor + held item icons with durability (screenshot)", () -> guarded(ctx, srv, () -> testNametagArmor(ctx, srv)));
            run("merged modules: invtweaks filtered steal + auto close (ChestStealer), config aliases", () -> guarded(ctx, srv, () -> testMergedModules(ctx, srv)));
            run("combobot: targets, chases, crits, sprint resets, weapon switch, retreat", () -> guarded(ctx, srv, () -> testComboBot(ctx, srv)));
            run("music overlay: real windows media helper, cover, chat clicks, seek, .music (screenshot)", () -> guarded(ctx, srv, () -> testMusicOverlay(ctx, srv)));
            run("hud editor: mouse wheel resizes boxes and elements, right-click resets (screenshot)", () -> guarded(ctx, srv, () -> testHudWheelScale(ctx, srv)));
            run("customsky: photo wrap, panorama, gif, video (ffmpeg), drop, next/prev (screenshots)", () -> guarded(ctx, srv, () -> testCustomSky(ctx, srv)));
            run("icons: category, tab and hud icons draw (screenshot)", () -> guarded(ctx, srv, () -> testIcons(ctx, srv)));
            run("worldcolors: water / lava colour + alpha, sky, fog, clouds (screenshots)", () -> guarded(ctx, srv, () -> testWorldColors(ctx, srv)));
            run("normalmode: hacks off and blocked, allowed modules work, hidden in gui, restore", () -> guarded(ctx, srv, () -> testNormalMode(ctx, srv)));
            run("tags: #hashtags on modules, search by tag, suggestions, detail card (screenshot)", () -> guarded(ctx, srv, () -> testTags(ctx, srv)));
            run("autobreed: feeds pairs with the right food, skips single animals, keeps slot", () -> guarded(ctx, srv, () -> testAutoBreed(ctx, srv)));
            run("customcape: picture becomes the cape (third person screenshot)", () -> guarded(ctx, srv, () -> testCustomCape(ctx, srv)));
            run("hitfx: hit particles, kill lightning, hurtcam flash, swing speed, copycoords (screenshots)", () -> guarded(ctx, srv, () -> testHitHurtAnimations(ctx, srv)));
            run("ctxmenu: favorites panel, shift-rmb menu, alt settings window, folded groups saved (screenshots)", () -> guarded(ctx, srv, () -> testContextMenuAndWindow(ctx, srv)));
            run("shortcuts: window with key caps, keybinds, commands, filter; BlockESP is in Visual (screenshots)", () -> guarded(ctx, srv, () -> testShortcuts(ctx, srv)));
            run("liquid glass: style mode, rounded translucent panels (screenshots)", () -> guarded(ctx, srv, () -> testLiquidGlass(ctx, srv)));
            run("norender more: new hooks applied, weather hidden, hearts/food hidden (screenshots)", () -> guarded(ctx, srv, () -> testNoRenderMore(ctx, srv)));
            run("checkmeter more: ground tolerance, click rate; keybinds window binds vanilla controls (screenshots)", () -> guarded(ctx, srv, () -> testCheckMeterMoreAndControls(ctx, srv)));
            run("antivanish: silent staff tab loss, normal leaves, own digging ignored, unseen digger, tab probe, network list", () -> guarded(ctx, srv, () -> testAntiVanish(ctx, srv)));
            run("highjump: jump peak is higher than vanilla", () -> guarded(ctx, srv, () -> testHighJump(ctx, srv)));
            run("accounts: title screen button, switch session, tokens saved encrypted (screenshots)", () -> guarded(ctx, srv, () -> testAccounts(ctx, srv)));
            run("vape layout: sidebar + module list, switch back to panels, glass (screenshots)", () -> guarded(ctx, srv, () -> testVapeLayout(ctx, srv)));
            run("subcategories: panel groups, fold, search is flat, toggle (screenshots)", () -> guarded(ctx, srv, () -> testSubCategories(ctx, srv)));
            run("media picker: file setting with thumbnails, hover preview, pick, mini preview (screenshots)", () -> guarded(ctx, srv, () -> testMediaPicker(ctx, srv)));
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
                        "Notifications", "AntiVanish", "Friends", "PacketGate", "PingSpoof", "GuiDesync", "PacketBurst", "BundleDupe", "FakeLag", "Fullbox", "AntiHunger", "AirPlace", "AntiBot", "Viewmodel"}) {
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
        var z = com.OsamaClient.newbridge.Hacks.Visual.Zoom.INSTANCE;
        int optBefore = ctx.computeOnClient(mc -> mc.options.fov().get());
        float camBefore = ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().getFov());
        try {
            ctx.runOnClient(mc -> { z.smooth = false; z.zoom = 4; z.scrollKey = "Alt"; z.resetOnRelease = true; if (!z.enabled) z.toggle(); });
            ctx.waitTicks(3);
            float camZoomed = ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().getFov());
            int optZoomed = ctx.computeOnClient(mc -> mc.options.fov().get());
            log("zoom: camera fov " + camBefore + " -> " + camZoomed + ", option " + optBefore + " -> " + optZoomed);
            assertTrue(optZoomed == optBefore, "Zoom must not touch the FOV option any more");
            assertTrue(camZoomed < camBefore / 3f && camZoomed > camBefore / 5f, "4x zoom gave camera fov " + camZoomed + " from " + camBefore);
            // wheel without the modifier key does nothing, with Scroll Key = None it zooms
            boolean used = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.Zoom.onScroll(1));
            assertTrue(!used && z.target() == 4.0, "scroll without Alt changed the zoom");
            ctx.runOnClient(mc -> z.scrollKey = "None");
            for (int i = 0; i < 3; i++) ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.Zoom.onScroll(1));
            double in = z.target();
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.Zoom.onScroll(-1));
            double out = z.target();
            log("zoom scroll: 4.0 -> " + in + " -> " + out);
            assertTrue(in > 7.5 && in < 8.0 && Math.abs(out - in / 1.25) < 1e-6, "scroll steps " + in + " / " + out);
            ctx.waitTicks(2);
            double sens = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.Zoom.sensitivity());
            assertTrue(sens < 0.2, "mouse sensitivity while zoomed " + sens);
            // limits
            ctx.runOnClient(mc -> { for (int i = 0; i < 60; i++) z.scrollBy(1); });
            assertTrue(z.target() <= z.maxZoom + 1e-9, "max zoom exceeded " + z.target());
            ctx.runOnClient(mc -> { for (int i = 0; i < 80; i++) z.scrollBy(-1); });
            assertTrue(z.target() >= 1.0, "zoomed out below 1x");
            // release: view comes back, next press starts at the Zoom value again
            ctx.runOnClient(mc -> z.toggle());
            ctx.waitTicks(3);
            float camAfter = ctx.computeOnClient(mc -> mc.gameRenderer.mainCamera().getFov());
            assertTrue(Math.abs(camAfter - camBefore) < 1.5f, "fov not restored: " + camAfter + " vs " + camBefore);
            ctx.runOnClient(mc -> z.toggle());
            assertTrue(z.target() == 4.0, "Reset On Release: started at " + z.target());
            ctx.runOnClient(mc -> { z.resetOnRelease = false; z.scrollBy(1); z.toggle(); z.toggle(); });
            assertTrue(Math.abs(z.target() - 5.0) < 1e-6, "keep last zoom: " + z.target());
        } finally {
            ctx.runOnClient(mc -> { if (z.enabled) z.toggle(); z.smooth = true; z.zoom = 4; z.scrollKey = "Alt"; z.resetOnRelease = true; });
        }
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
        // friends are separate from real teammates: own colour, not a "team member", skippable via Count As Teammate
        boolean[] sep = ctx.computeOnClient(mc -> {
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.npc.villager.Villager v) {
                    var F = com.OsamaClient.newbridge.Hacks.Client.Friends.class;
                    com.OsamaClient.newbridge.Hacks.Client.Friends.add(v.getScoreboardName());
                    boolean friend = com.OsamaClient.newbridge.Utils.TeamUtils.isFriend(v);
                    boolean member = com.OsamaClient.newbridge.Utils.TeamUtils.isTeamMember(v);
                    boolean skip = com.OsamaClient.newbridge.Utils.TeamUtils.isTeammate(v);
                    com.OsamaClient.newbridge.Hacks.Client.Friends.INSTANCE.asTeammate = false;
                    boolean skipOff = com.OsamaClient.newbridge.Utils.TeamUtils.isTeammate(v);
                    com.OsamaClient.newbridge.Hacks.Client.Friends.INSTANCE.asTeammate = true;
                    com.OsamaClient.newbridge.Hacks.Client.Friends.remove(v.getScoreboardName());
                    return new boolean[]{friend, member, skip, skipOff};
                }
            }
            return new boolean[]{false, true, false, true};
        });
        assertTrue(sep[0] && !sep[1] && sep[2] && !sep[3], "friend/team separation " + java.util.Arrays.toString(sep));
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

    /**
     * Optional filter: run-gametest/newbridge-test-filter.txt with one search text per line. If it exists and is
     * not empty, only tests whose name contains one of the lines run (case-insensitive); the rest is reported as SKIP.
     */
    private static java.util.List<String> filter;

    private static boolean selected(String name) {
        if (filter == null) {
            filter = new java.util.ArrayList<>();
            try {
                java.nio.file.Path f = java.nio.file.Path.of("newbridge-test-filter.txt");
                if (java.nio.file.Files.isRegularFile(f))
                    for (String l : java.nio.file.Files.readAllLines(f)) if (!l.isBlank() && !l.startsWith("#")) filter.add(l.trim().toLowerCase(java.util.Locale.ROOT));
            } catch (java.io.IOException ignored) { }
            if (!filter.isEmpty()) LOG.info("gametest filter: {}", filter);
        }
        if (filter.isEmpty()) return true;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        for (String f : filter) if (n.contains(f)) return true;
        return false;
    }

    private void run(String name, Check check) {
        if (!selected(name)) {
            report.add("SKIP  " + name);
            return;
        }
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
                // Respawn if a test killed the player, so later tests do not cascade-fail on the death screen
                Minecraft.getInstance().execute(() -> {
                    Minecraft m = Minecraft.getInstance();
                    if (m.player != null && (m.player.isDeadOrDying() || m.gui.screen() instanceof net.minecraft.client.gui.screens.DeathScreen)) {
                        m.player.respawn();
                        m.gui.setScreen(null);
                        log("respawned after " + name);
                    }
                });
            } catch (Throwable ignored) { }
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
        // stop walking before the stage ends (otherwise the player walks off and dies)
        ctx.runOnClient(mc -> mc.options.keyUp.setDown(false));
        teleport(ctx, srv, 0, Y, 0);
        // landing: the packet of the tick you land goes through with onGround (merged from NoHunger)
        var ah = com.OsamaClient.newbridge.Hacks.Movement.AntiHunger.INSTANCE;
        for (boolean allow : new boolean[]{true, false}) {
            ctx.runOnClient(mc -> ah.allowLandings = allow);
            srv.runCommand("execute as @p at @s run tp @s ~ ~2 ~");
            ctx.waitTicks(2);
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.groundPackets.set(0));
            ctx.waitTicks(25);
            int landed = com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.groundPackets.get();
            log("antihunger landing allow=" + allow + " ground packets=" + landed);
            assertTrue(allow ? landed >= 1 : landed == 0, "Allow Landings=" + allow + " sent " + landed + " ground packets");
        }
        ctx.runOnClient(mc -> ah.allowLandings = true);
        ctx.runOnClient(mc -> ModuleManager.getModuleByName("AntiHunger").toggle());
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> { com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.groundPackets.set(0); mc.player.setYRot(mc.player.getYRot() + 30f); });
        ctx.waitTicks(30);   // standing still only sends a position heartbeat every 20 ticks
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

    // ------------------------------------------------------------------ keyboard / damage / ping / chat / favorites / censor / invtweaks

    private void testKeyboard(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var fb = ModuleManager.getModuleByName("Fullbox");
        var ka = ModuleManager.getModuleByName("KillAura");
        int oldFb = fb.key, oldKa = ka.key;
        ctx.runOnClient(mc -> {
            fb.key = com.mojang.blaze3d.platform.InputConstants.KEY_END;
            ka.key = com.mojang.blaze3d.platform.InputConstants.KEY_END;     // module vs module
            ModuleManager.getModuleByName("Zoom").key = com.mojang.blaze3d.platform.InputConstants.KEY_A; // vs vanilla "left"
        });
        var win = new com.OsamaClient.newbridge.UI.gui.window.KeyboardWindow();
        java.util.Map<Integer, java.util.List<String>> conflicts = ctx.computeOnClient(mc -> win.conflictList());
        int bound = ctx.computeOnClient(mc -> win.boundKeyCount());
        log("keyboard conflicts=" + conflicts + " boundKeys=" + bound);
        assertTrue(conflicts.containsKey(com.mojang.blaze3d.platform.InputConstants.KEY_END), "module/module conflict on G missing");
        java.util.List<String> a = conflicts.get(com.mojang.blaze3d.platform.InputConstants.KEY_A);
        assertTrue(a != null && a.contains("Zoom") && a.size() >= 2, "module vs vanilla conflict on A missing: " + a);
        assertTrue(bound >= 8, "vanilla binds not collected: " + bound);
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(win));
        ctx.waitTicks(15);
        java.nio.file.Path shot = ctx.takeScreenshot("newbridge_keyboard");
        log("keyboard screenshot: " + shot);
        ctx.runOnClient(mc -> {
            com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close();
            fb.key = oldFb; ka.key = oldKa; ModuleManager.getModuleByName("Zoom").key = -1;
        });
        int after = ctx.computeOnClient(mc -> win.conflictList().size());
        assertTrue(ctx.computeOnClient(mc -> fb.key == oldFb && ka.key == oldKa), "keys not restored");
        assertTrue(!ctx.computeOnClient(mc -> win.conflictList().containsKey(com.mojang.blaze3d.platform.InputConstants.KEY_A)), "Zoom/Left conflict not cleared");
        log("conflicts after reset=" + after);
    }

    private void testDamageIndicator(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("effect clear @a");
        var d = com.OsamaClient.newbridge.Hacks.Visual.DamageIndicator.INSTANCE;
        on(ctx, "DamageIndicator");
        ctx.waitTicks(5);
        int c0 = ctx.computeOnClient(mc -> d.created);
        srv.runCommand("damage @p 4");
        ctx.waitTicks(4);
        int c1 = ctx.computeOnClient(mc -> d.created);
        float amount = ctx.computeOnClient(mc -> d.popups.isEmpty() ? 0f : d.popups.get(d.popups.size() - 1).amount);
        log("damageindicator created " + (c1 - c0) + " amount=" + amount);
        assertTrue(c1 - c0 >= 1, "no popup for damage");
        assertTrue(Math.abs(amount + 4f) < 0.6f, "wrong amount " + amount);
        java.nio.file.Path shot = ctx.takeScreenshot("newbridge_damageindicator");
        log("damageindicator screenshot: " + shot);
        // heal shows up as a positive number
        srv.runCommand("effect give @a instant_health 1 0");
        ctx.waitTicks(4);
        float heal = ctx.computeOnClient(mc -> d.popups.isEmpty() ? 0f : d.popups.get(d.popups.size() - 1).amount);
        assertTrue(heal > 0f, "no heal number: " + heal);
        // Show Heals off
        setToggle(ctx, "DamageIndicator", "Show Heals", false);
        srv.runCommand("damage @p 6");
        ctx.waitTicks(4);
        int c2 = ctx.computeOnClient(mc -> d.created);
        srv.runCommand("effect give @a instant_health 1 0");
        ctx.waitTicks(4);
        int c3 = ctx.computeOnClient(mc -> d.created);
        assertTrue(c3 == c2, "heal still shown with Show Heals off");
        // Include Self off
        setToggle(ctx, "DamageIndicator", "Include Self", false);
        srv.runCommand("damage @p 3");
        ctx.waitTicks(4);
        assertTrue(ctx.computeOnClient(mc -> d.created) == c3, "self damage shown with Include Self off");
        setToggle(ctx, "DamageIndicator", "Include Self", true);
        setToggle(ctx, "DamageIndicator", "Show Heals", true);
        // popups expire
        ctx.waitTicks(60);
        assertTrue(ctx.computeOnClient(mc -> d.popups.isEmpty()), "popups never expire");
        ctx.runOnClient(mc -> d.toggle());
        srv.runCommand("effect give @a instant_health 1 3");
        ctx.waitTicks(3);
    }

    private void testPingTps(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        // pure maths first
        double[] t = ctx.computeOnClient(mc -> {
            var T = com.OsamaClient.newbridge.Utils.TpsTracker.class;
            com.OsamaClient.newbridge.Utils.TpsTracker.reset();
            com.OsamaClient.newbridge.Utils.TpsTracker.onTimePacket(0, 0);
            com.OsamaClient.newbridge.Utils.TpsTracker.onTimePacket(20, 1_000_000_000L);
            double a = com.OsamaClient.newbridge.Utils.TpsTracker.tps();
            com.OsamaClient.newbridge.Utils.TpsTracker.onTimePacket(40, 3_000_000_000L);
            double b = com.OsamaClient.newbridge.Utils.TpsTracker.tps();
            com.OsamaClient.newbridge.Utils.TpsTracker.reset();
            return new double[]{a, b};
        });
        log("tps maths " + t[0] + " " + t[1]);
        assertTrue(Math.abs(t[0] - 20) < 0.01, "full speed should be 20 tps: " + t[0]);
        assertTrue(Math.abs(t[1] - 16) < 0.01, "slow interval should pull the smoothed value to 16: " + t[1]);
        // live estimate from the integrated server's time packets
        ctx.waitTicks(130);
        double live = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Utils.TpsTracker.tps());
        log("tps live=" + live);
        assertTrue(live > 10 && live <= 20.0001, "live tps estimate " + live);
        // HUD rows
        on(ctx, "HudOverlay");
        setToggle(ctx, "HudOverlay", "Ping", true);
        setToggle(ctx, "HudOverlay", "TPS", true);
        ctx.waitTicks(8);
        java.nio.file.Path shot = ctx.takeScreenshot("newbridge_ping_tps");
        log("ping/tps screenshot: " + shot);
        setToggle(ctx, "HudOverlay", "Ping", false);
        setToggle(ctx, "HudOverlay", "TPS", false);
    }

    private int chatSize(net.minecraft.client.Minecraft mc) {
        try {
            var f = net.minecraft.client.gui.components.ChatComponent.class.getDeclaredField("allMessages");
            f.setAccessible(true);
            return ((java.util.List<?>) f.get(mc.gui.hud.getChat())).size();
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private void testSplitChat(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var s = com.OsamaClient.newbridge.Hacks.Visual.SplitChat.INSTANCE;
        on(ctx, "SplitChat");
        String me = ctx.computeOnClient(mc -> mc.player.getGameProfile().name());
        Runnable send = () -> ctx.runOnClient(mc -> {
            var chat = mc.gui.hud.getChat();
            chat.addPlayerMessage(net.minecraft.network.chat.Component.literal("<Steve> hello there, this is a rather long player message that has to wrap inside the narrow split window"), null, null);
            chat.addServerSystemMessage(net.minecraft.network.chat.Component.literal("Server restart in 5 minutes"));
            chat.addClientSystemMessage(net.minecraft.network.chat.Component.literal("[client] note foo"));
            chat.addPlayerMessage(net.minecraft.network.chat.Component.literal("<Alex> hey " + me + " look at this"), null, null);
        });
        String[] modes = {"Players", "Server", "Mentions", "Keyword", "All"};
        int[] expect = {2, 1, 1, 1, 4};
        ctx.runOnClient(mc -> s.keyword = "foo|nothing-else");
        for (int i = 0; i < modes.length; i++) {
            int idx = i;
            ctx.runOnClient(mc -> { s.messages.clear(); s.filterMode = modes[idx]; });
            send.run();
            int got = ctx.computeOnClient(mc -> s.messages.size());
            log("splitchat " + modes[i] + " -> " + got);
            assertTrue(got == expect[i], "mode " + modes[i] + " expected " + expect[i] + " got " + got);
        }
        // screenshot: players mode with a few lines
        ctx.runOnClient(mc -> { s.messages.clear(); s.filterMode = "Players"; });
        send.run();
        ctx.waitTicks(5);
        java.nio.file.Path shot = ctx.takeScreenshot("newbridge_splitchat");
        log("splitchat screenshot: " + shot);
        // hide in main chat: main chat must not grow for filtered kinds
        setToggle(ctx, "SplitChat", "Hide In Main Chat", true);
        int main0 = ctx.computeOnClient(mc -> chatSize(mc));
        ctx.runOnClient(mc -> mc.gui.hud.getChat().addPlayerMessage(net.minecraft.network.chat.Component.literal("<Bob> hidden"), null, null));
        int main1 = ctx.computeOnClient(mc -> chatSize(mc));
        ctx.runOnClient(mc -> mc.gui.hud.getChat().addServerSystemMessage(net.minecraft.network.chat.Component.literal("not a player line")));
        int main2 = ctx.computeOnClient(mc -> chatSize(mc));
        log("splitchat main chat size " + main0 + " -> " + main1 + " -> " + main2);
        assertTrue(main1 == main0, "filtered line still in main chat");
        assertTrue(main2 == main1 + 1, "unfiltered line missing from main chat");
        setToggle(ctx, "SplitChat", "Hide In Main Chat", false);
        // disabled module collects nothing
        ctx.runOnClient(mc -> { s.toggle(); s.messages.clear(); });
        ctx.runOnClient(mc -> mc.gui.hud.getChat().addPlayerMessage(net.minecraft.network.chat.Component.literal("<Bob> after"), null, null));
        assertTrue(ctx.computeOnClient(mc -> s.messages.isEmpty()), "disabled SplitChat still collects");
    }

    private void testFavorites(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
        var zoom = ModuleManager.getModuleByName("Zoom");
        var fb = ModuleManager.getModuleByName("Fullbox");
        ctx.runOnClient(mc -> { zoom.favorite = true; fb.favorite = true; });
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.config.Config.saveProfile("favtest"));
        ctx.runOnClient(mc -> { zoom.favorite = false; fb.favorite = false; });
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.config.Config.loadProfile("favtest"));
        boolean back = ctx.computeOnClient(mc -> zoom.favorite && fb.favorite);
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.config.Config.deleteProfile("favtest"));
        assertTrue(back, "favorites not restored from the saved config");
        ctx.runOnClient(mc -> gui.open());
        ctx.waitTicks(15);
        int all = ctx.computeOnClient(mc -> gui.visibleMatches());
        ctx.runOnClient(mc -> gui.toggleOnlyFavorites());
        ctx.waitTicks(5);
        int fav = ctx.computeOnClient(mc -> gui.visibleMatches());
        log("favorites all=" + all + " favorites-only=" + fav);
        assertTrue(fav == 2, "favorite filter shows " + fav);
        java.nio.file.Path shot = ctx.takeScreenshot("newbridge_favorites");
        log("favorites screenshot: " + shot);
        ctx.runOnClient(mc -> gui.toggleOnlyFavorites());
        ctx.waitTicks(3);
        assertTrue(ctx.computeOnClient(mc -> gui.visibleMatches()) == all, "filter did not reset");
        ctx.runOnClient(mc -> { zoom.favorite = false; fb.favorite = false; gui.close(); });
        ctx.waitTicks(10);
    }

    private void testNameCensor(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var nc = com.OsamaClient.newbridge.Hacks.Misc.NameCensor.INSTANCE;
        String me = ctx.computeOnClient(mc -> mc.player.getGameProfile().name());
        // off: untouched
        assertTrue(ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Misc.NameCensor.censor("<" + me + "> hi")).equals("<" + me + "> hi"), "changes text while disabled");
        on(ctx, "NameCensor");
        String r1 = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Misc.NameCensor.censor("<" + me + "> hi " + me + "x " + me));
        log("namecensor me: " + r1);
        assertTrue(r1.equals("<Hidden> hi " + me + "x Hidden"), "own name replacement wrong: " + r1);
        // others untouched in Me mode
        ctx.runOnClient(mc -> { nc.extraNames.add("Notch"); });
        String r2 = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Misc.NameCensor.censor("Notch and " + me));
        assertTrue(r2.equals("Notch and Hidden"), "Me mode touched another player: " + r2);
        // everyone mode
        setMode(ctx, "NameCensor", "Who", 1);
        String r3 = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Misc.NameCensor.censor("Notch and " + me));
        log("namecensor everyone: " + r3);
        assertTrue(r3.startsWith("Player ") && r3.endsWith(" and Hidden"), "Everyone mode: " + r3);
        // custom replacement
        ctx.runOnClient(mc -> { for (var c : nc.settings) if (c instanceof com.OsamaClient.newbridge.UI.components.TextBox tb && tb.getLabel().equals("Replacement")) tb.setText("Anon"); });
        assertTrue(ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Misc.NameCensor.censor(me)).equals("Anon"), "custom replacement ignored");
        // styled sequences keep their colour
        boolean styled = ctx.computeOnClient(mc -> {
            var c = net.minecraft.network.chat.Component.literal("[")
                    .append(net.minecraft.network.chat.Component.literal(me).withStyle(net.minecraft.ChatFormatting.RED))
                    .append(net.minecraft.network.chat.Component.literal("] gg").withStyle(net.minecraft.ChatFormatting.GREEN));
            var out = com.OsamaClient.newbridge.Hacks.Misc.NameCensor.censor(c.getVisualOrderText());
            StringBuilder sb = new StringBuilder();
            java.util.List<Integer> cols = new java.util.ArrayList<>();
            out.accept((i, st, cp) -> { sb.appendCodePoint(cp); cols.add(st.getColor() == null ? -1 : st.getColor().getValue()); return true; });
            return sb.toString().equals("[Anon] gg") && cols.get(1) == 0xFF5555 && cols.get(cols.size() - 1) == 0x55FF55;
        });
        assertTrue(styled, "styled sequence replacement lost text or colours");
        // the real font hook: the measured width of the name is the width of the replacement
        int[] w = ctx.computeOnClient(mc -> new int[]{mc.font.width(me), mc.font.width("Anon")});
        log("font widths name=" + w[0] + " anon=" + w[1]);
        assertTrue(w[0] == w[1], "Font.width hook not active");
        // visible screenshots: chat + sidebar scoreboard with my name
        srv.runCommand("scoreboard objectives add gtcensor dummy \"Scores\"");
        srv.runCommand("scoreboard objectives setdisplay sidebar gtcensor");
        srv.runCommand("scoreboard players set " + me + " gtcensor 7");
        srv.runCommand("scoreboard players set Notch gtcensor 3");
        ctx.runOnClient(mc -> {
            mc.gui.hud.getChat().addPlayerMessage(net.minecraft.network.chat.Component.literal("<" + me + "> this is my own name in chat"), null, null);
            mc.gui.hud.getChat().addPlayerMessage(net.minecraft.network.chat.Component.literal("<Notch> and another player"), null, null);
        });
        ctx.waitTicks(10);
        java.nio.file.Path on = ctx.takeScreenshot("newbridge_namecensor_on");
        ctx.runOnClient(mc -> nc.toggle());
        ctx.waitTicks(5);
        java.nio.file.Path off = ctx.takeScreenshot("newbridge_namecensor_off");
        boolean differs;
        try { differs = !java.util.Arrays.equals(java.nio.file.Files.readAllBytes(on), java.nio.file.Files.readAllBytes(off)); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
        log("namecensor screenshots differ=" + differs + " " + on);
        assertTrue(differs, "screen identical with and without NameCensor, the render hook does nothing");
        srv.runCommand("scoreboard objectives remove gtcensor");
        ctx.runOnClient(mc -> { nc.extraNames.clear(); for (var c : nc.settings) if (c instanceof com.OsamaClient.newbridge.UI.components.TextBox tb && tb.getLabel().equals("Replacement")) tb.setText("Hidden"); });
        setMode(ctx, "NameCensor", "Who", 0);
    }

    private void waitQueue(ClientGameTestContext ctx, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            if (ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Player.InvTweaks.INSTANCE.queue.isEmpty())) { ctx.waitTicks(3); return; }
            ctx.waitTicks(1);
        }
    }

    private void testInvTweaks(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var it = com.OsamaClient.newbridge.Hacks.Player.InvTweaks.INSTANCE;
        on(ctx, "InvTweaks");
        ctx.runOnClient(mc -> { it.delay = 0; });
        // --- sort: scrambled main inventory with partial stacks of the same item
        String[] fill = {"stone 20", "dirt 5", "stone 30", "apple 1", "dirt 10", "cobblestone 64", "oak_log 7", null, "apple 3", "iron_ingot 9", "cobblestone 12", null, "dirt 1"};
        for (int i = 0; i < fill.length; i++) {
            if (fill[i] != null) srv.runCommand("item replace entity @a inventory." + i + " with minecraft:" + fill[i]);
        }
        ctx.waitTicks(5);
        java.util.Map<String, Integer> before = ctx.computeOnClient(mc -> invTotals(mc));
        ctx.runOnClient(mc -> it.sortRegionOf(mc.player.inventoryMenu, null));
        int queued = ctx.computeOnClient(mc -> it.queue.size());
        log("invtweaks sort queued " + queued + " clicks, totals " + before);
        assertTrue(queued > 0, "sort queued nothing");
        waitQueue(ctx, 200);
        ctx.waitTicks(5);
        java.util.List<String> order = ctx.computeOnClient(mc -> {
            java.util.List<String> l = new java.util.ArrayList<>();
            for (int i = 9; i < 36; i++) { var st = mc.player.getInventory().getItem(i); l.add(st.isEmpty() ? "-" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath() + ":" + st.getCount()); }
            return l;
        });
        log("invtweaks sorted: " + order);
        java.util.Map<String, Integer> after = ctx.computeOnClient(mc -> invTotals(mc));
        assertTrue(before.equals(after), "items lost or duplicated by sorting: " + before + " vs " + after);
        // grouped by item, empties last, partial stacks merged (stone 20+30 -> 50 stays one stack -> 50 > 64? no: 50)
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String o : order) names.add(o.split(":")[0]);
        int firstEmpty = names.indexOf("-");
        for (int i = firstEmpty; i >= 0 && i < names.size(); i++) assertTrue(names.get(i).equals("-"), "empty slot in the middle: " + order);
        java.util.Set<String> seen = new java.util.HashSet<>();
        String prev = null;
        for (String n : names) { if (!n.equals(prev)) { assertTrue(seen.add(n) || n.equals("-"), "item " + n + " is split up: " + order); prev = n; } }
        long stoneStacks = names.stream().filter(n -> n.equals("stone")).count();
        assertTrue(stoneStacks == 1, "partial stone stacks not merged: " + order);
        // --- chest: steal / dump / buttons
        int cx = ox + 4;
        fill(srv, 4, Y, -1, 4, Y, 1, "air");
        srv.runCommand("setblock " + cx + " " + Y + " " + oz + " minecraft:chest");
        String[] chest = {"diamond 3", "emerald 12", "gold_ingot 40", "coal 64", "redstone 22"};
        for (int i = 0; i < chest.length; i++) srv.runCommand("item replace block " + cx + " " + Y + " " + oz + " container." + i + " with minecraft:" + chest[i]);
        srv.runCommand("clear @a");
        srv.runCommand("item replace entity @a inventory.0 with minecraft:iron_sword");
        srv.runCommand("item replace entity @a hotbar.0 with minecraft:torch 5");
        for (int i = 0; i < 6; i++) srv.runCommand("item replace entity @a inventory." + i + " with minecraft:cobblestone " + (10 + i));
        ctx.waitTicks(5);
        Runnable open = () -> {
            srv.runOnServer(s -> {
                var sp = s.getPlayerList().getPlayers().get(0);
                var be = sp.level().getBlockEntity(new net.minecraft.core.BlockPos(cx, Y, oz));
                if (be instanceof net.minecraft.world.MenuProvider mp) sp.openMenu(mp);
            });
            ctx.waitTicks(8);
        };
        open.run();
        boolean screen = ctx.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>);
        assertTrue(screen, "chest screen did not open");
        java.nio.file.Path shot = ctx.takeScreenshot("newbridge_invtweaks");
        log("invtweaks screenshot: " + shot);
        // Steal via the real button rect
        ctx.runOnClient(mc -> {
            var cs = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.gui.screen();
            var labels = it.buttonLabels(cs.getMenu());
            int idx = labels.indexOf("Steal");
            int[] r = it.buttonRect(cs, idx);
            boolean handled = it.handleClick(cs, r[0] + 2, r[1] + 2, 0);
            if (!handled) throw new AssertionError("Steal button click not handled, labels=" + labels);
        });
        waitQueue(ctx, 200);
        int chestLeft = ctx.computeOnClient(mc -> { int n = 0; for (var s : it.containerSlots(mc.player.containerMenu)) if (s.hasItem()) n++; return n; });
        int diamonds = countItem(ctx, net.minecraft.world.item.Items.DIAMOND), gold = countItem(ctx, net.minecraft.world.item.Items.GOLD_INGOT);
        log("invtweaks steal: chest slots left=" + chestLeft + " diamonds=" + diamonds + " gold=" + gold);
        assertTrue(chestLeft == 0, "chest not emptied: " + chestLeft);
        assertTrue(diamonds == 3 && gold == 40, "stolen items missing");
        // Dump (keep hotbar on -> torch + hotbar slot stay)
        ctx.runOnClient(mc -> it.dump(mc.player.containerMenu));
        waitQueue(ctx, 200);
        int inChest = ctx.computeOnClient(mc -> { int n = 0; for (var s : it.containerSlots(mc.player.containerMenu)) if (s.hasItem()) n++; return n; });
        int torches = countItem(ctx, net.minecraft.world.item.Items.TORCH);
        log("invtweaks dump: chest slots used=" + inChest + " torches kept=" + torches);
        assertTrue(inChest > 0, "dump moved nothing");
        assertTrue(torches == 5, "keep hotbar failed, torches=" + torches);
        // Keep Hotbar off: dump everything
        setToggle(ctx, "InvTweaks", "Keep Hotbar", false);
        ctx.runOnClient(mc -> it.dump(mc.player.containerMenu));
        waitQueue(ctx, 200);
        assertTrue(countItem(ctx, net.minecraft.world.item.Items.TORCH) == 0, "hotbar not dumped with Keep Hotbar off");
        setToggle(ctx, "InvTweaks", "Keep Hotbar", true);
        // Shift drag: sweep over the container slots
        int moved = ctx.computeOnClient(mc -> {
            int n = 0;
            for (var s : it.containerSlots(mc.player.containerMenu)) if (it.dragTo(mc.player.containerMenu, s)) n++;
            return n;
        });
        ctx.waitTicks(10);
        int chestAfterDrag = ctx.computeOnClient(mc -> { int n = 0; for (var s : it.containerSlots(mc.player.containerMenu)) if (s.hasItem()) n++; return n; });
        log("invtweaks shift-drag moved " + moved + " stacks, chest left " + chestAfterDrag);
        assertTrue(moved > 0 && chestAfterDrag == 0, "shift drag did not clear the chest");
        // Sort key inside a chest sorts the hovered region (container side)
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(5);
        // Auto steal: put something in, enable, reopen
        srv.runCommand("item replace block " + cx + " " + Y + " " + oz + " container.5 with minecraft:netherite_ingot 2");
        setToggle(ctx, "InvTweaks", "Auto Steal", true);
        open.run();
        waitQueue(ctx, 100);
        assertTrue(countItem(ctx, net.minecraft.world.item.Items.NETHERITE_INGOT) == 2, "auto steal did not take the item");
        setToggle(ctx, "InvTweaks", "Auto Steal", false);
        ctx.runOnClient(mc -> { mc.player.closeContainer(); it.toggle(); });
        ctx.waitTicks(5);
        srv.runCommand("setblock " + cx + " " + Y + " " + oz + " minecraft:air");
    }

    private static java.util.Map<String, Integer> invTotals(net.minecraft.client.Minecraft mc) {
        java.util.Map<String, Integer> m = new java.util.TreeMap<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty()) m.merge(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath(), st.getCount(), Integer::sum);
        }
        return m;
    }

    // ------------------------------------------------------------------ round 2: rebinding, backups, search, quick waypoint, suggestions, preview

    private void testKeyboardRebind(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var zoom = ModuleManager.getModuleByName("Zoom");
        var win = new com.OsamaClient.newbridge.UI.gui.window.KeyboardWindow();
        int END = com.mojang.blaze3d.platform.InputConstants.KEY_END, A = com.mojang.blaze3d.platform.InputConstants.KEY_A;
        // toggle bind / unbind + clear
        boolean b1 = ctx.computeOnClient(mc -> win.toggleBind(END, zoom));
        assertTrue(b1 && zoom.key == END, "toggleBind did not bind");
        assertTrue(ctx.computeOnClient(mc -> win.clearKey(END)) == 1 && zoom.key == -1, "clearKey did not unbind");
        ctx.runOnClient(mc -> win.toggleBind(END, zoom));
        boolean b2 = ctx.computeOnClient(mc -> win.toggleBind(END, zoom));
        assertTrue(!b2 && zoom.key == -1, "second toggle should unbind");
        // conflict warning with a free key suggestion
        ctx.runOnClient(mc -> { zoom.key = A; com.OsamaClient.newbridge.UI.gui.window.KeyboardWindow.warnIfConflict(zoom); });
        String warn = com.OsamaClient.newbridge.UI.gui.window.KeyboardWindow.lastWarning;
        log("conflict warning: " + warn);
        assertTrue(warn != null && warn.contains("Left") && warn.contains("free:"), "no conflict warning with suggestion: " + warn);
        ctx.runOnClient(mc -> zoom.key = -1);
        // hotkey profiles
        java.nio.file.Path tmp;
        try { tmp = java.nio.file.Files.createTempDirectory("nbkeys"); } catch (java.io.IOException e) { throw new RuntimeException(e); }
        com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.testDir = tmp;
        try {
            ctx.runOnClient(mc -> { zoom.key = END; ModuleManager.getModuleByName("KillAura").key = com.mojang.blaze3d.platform.InputConstants.KEY_H; });
            int saved = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.save("gt-keys"));
            ctx.runOnClient(mc -> { zoom.key = -1; ModuleManager.getModuleByName("KillAura").key = -1; ModuleManager.getModuleByName("Fullbox").key = com.mojang.blaze3d.platform.InputConstants.KEY_J; });
            int loaded = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.load("gt-keys"));
            int ka = ModuleManager.getModuleByName("KillAura").key, fb = ModuleManager.getModuleByName("Fullbox").key;
            log("keyprofiles saved=" + saved + " loaded=" + loaded + " zoom=" + zoom.key + " ka=" + ka + " fb=" + fb);
            assertTrue(saved >= 2 && loaded == saved, "save/load counts " + saved + "/" + loaded);
            assertTrue(zoom.key == END && ka == com.mojang.blaze3d.platform.InputConstants.KEY_H, "keys not restored");
            assertTrue(fb == -1, "load must clear keys that are not in the profile");
            assertTrue(com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.list().contains("gt-keys"), "profile not listed");
            // screenshot with a selected key
            ctx.runOnClient(mc -> { win.select(com.mojang.blaze3d.platform.InputConstants.KEY_H); com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(win); });
            ctx.waitTicks(15);
            log("keyboard rebind screenshot: " + ctx.takeScreenshot("newbridge_keyboard_rebind"));
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close());
            assertTrue(com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.delete("gt-keys") && !com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.list().contains("gt-keys"), "delete failed");
        } finally {
            com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.testDir = null;
            ctx.runOnClient(mc -> { zoom.key = -1; ModuleManager.getModuleByName("KillAura").key = -1; ModuleManager.getModuleByName("Fullbox").key = -1; });
        }
    }

    private void testConfigBackup(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var zoom = ModuleManager.getModuleByName("Zoom");
        java.nio.file.Path tmp;
        try { tmp = java.nio.file.Files.createTempDirectory("nbbak"); } catch (java.io.IOException e) { throw new RuntimeException(e); }
        com.OsamaClient.newbridge.config.Config.testBackupDir = tmp;
        try {
            ctx.runOnClient(mc -> { zoom.favorite = false; com.OsamaClient.newbridge.config.Config.save(); });
            String name = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.config.Config.backupNow());
            assertTrue(name != null, "no backup created");
            ctx.runOnClient(mc -> { zoom.favorite = true; com.OsamaClient.newbridge.config.Config.save(); });
            boolean ok = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.config.Config.restoreBackup(name));
            assertTrue(ok && !zoom.favorite, "restore did not bring the old state back, favorite=" + zoom.favorite);
            // retention: newest 10 only
            ctx.runOnClient(mc -> {
                for (int i = 0; i < 13; i++) {
                    com.OsamaClient.newbridge.config.Config.backupNow();
                    try { Thread.sleep(4); } catch (InterruptedException ignored) {}
                }
            });
            int n = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.config.Config.listBackups().size());
            log("config backups kept=" + n);
            assertTrue(n == 10, "expected 10 backups, got " + n);
            assertTrue(!ctx.computeOnClient(mc -> com.OsamaClient.newbridge.config.Config.restoreBackup("does-not-exist")), "restoring a missing backup succeeded");
        } finally {
            com.OsamaClient.newbridge.config.Config.testBackupDir = null;
            ctx.runOnClient(mc -> { zoom.favorite = false; com.OsamaClient.newbridge.config.Config.save(); });
        }
    }

    private void testSearchRecent(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
        ctx.runOnClient(mc -> { gui.open(); com.OsamaClient.newbridge.UI.gui.ClickGui.RECENT.clear(); });
        ctx.waitTicks(15);
        // description search: "rotate" is in Viewmodel's description, not in any name
        long expected = ctx.computeOnClient(mc -> ModuleManager.modules.stream().filter(m -> m.name.toLowerCase().contains("rotate") || (m.description != null && m.description.toLowerCase().contains("rotate"))
                || m.settings.stream().anyMatch(c -> com.OsamaClient.newbridge.UI.gui.DetailCard.label(c).toLowerCase().contains("rotate"))).count());
        ctx.runOnClient(mc -> gui.setSearch("rotate"));
        ctx.waitTicks(4);
        int shown = ctx.computeOnClient(mc -> gui.visibleMatches());
        log("search 'rotate' shown=" + shown + " expected=" + expected);
        assertTrue(expected >= 1 && shown == expected, "description search shows " + shown + " expected " + expected);
        // two letters only match names (no description noise)
        ctx.runOnClient(mc -> gui.setSearch("zo"));
        ctx.waitTicks(3);
        int two = ctx.computeOnClient(mc -> gui.visibleMatches());
        long nameOnly = ctx.computeOnClient(mc -> ModuleManager.modules.stream().filter(m -> m.name.toLowerCase().contains("zo")).count());
        assertTrue(two == nameOnly, "short queries must search names only: " + two + " vs " + nameOnly);
        ctx.runOnClient(mc -> gui.setSearch(""));
        // recent chip
        var zoom = ModuleManager.getModuleByName("Zoom");
        var fullbright = ModuleManager.getModuleByName("FullBright");
        ctx.runOnClient(mc -> { gui.safeToggle(zoom); gui.safeToggle(fullbright); gui.safeToggle(zoom); gui.safeToggle(fullbright); });
        java.util.List<String> recent = new java.util.ArrayList<>(com.OsamaClient.newbridge.UI.gui.ClickGui.RECENT);
        log("recent=" + recent);
        assertTrue(recent.size() == 2 && recent.get(0).equals("FullBright") && recent.get(1).equals("Zoom"), "recent list wrong: " + recent);
        ctx.runOnClient(mc -> gui.toggleOnlyRecent());
        ctx.waitTicks(5);
        int rec = ctx.computeOnClient(mc -> gui.visibleMatches());
        assertTrue(rec == 2, "recent filter shows " + rec);
        log("search/recent screenshot: " + ctx.takeScreenshot("newbridge_recent_chip"));
        ctx.runOnClient(mc -> { gui.toggleOnlyRecent(); gui.close(); com.OsamaClient.newbridge.UI.gui.ClickGui.RECENT.clear(); });
        ctx.waitTicks(10);
    }

    private void testQuickWaypoint(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var q = com.OsamaClient.newbridge.Hacks.Misc.QuickWaypoint.INSTANCE;
        var wp = com.OsamaClient.newbridge.Hacks.Misc.Waypoints.INSTANCE;
        ctx.runOnClient(mc -> { for (var c : q.settings) if (c instanceof com.OsamaClient.newbridge.UI.components.TextBox tb) tb.setText("GT"); });
        java.util.List<String> made = new java.util.ArrayList<>();
        try {
            String a = ctx.computeOnClient(mc -> q.create());
            net.minecraft.core.BlockPos feet = ctx.computeOnClient(mc -> mc.player.blockPosition());
            made.add(a);
            assertTrue("GT 1".equals(a) && feet.equals(q.lastPos), "first waypoint " + a + " at " + q.lastPos + " feet " + feet);
            String b = ctx.computeOnClient(mc -> q.create());
            made.add(b);
            assertTrue("GT 2".equals(b), "second waypoint named " + b);
            // via the module key path (onEnable) - must switch itself off again
            on(ctx, "QuickWaypoint");
            made.add("GT 3");
            assertTrue(!ctx.computeOnClient(mc -> q.enabled), "module did not switch itself off");
            assertTrue("GT 3".equals(q.lastName), "key path created " + q.lastName);
            // freecam: placed where the camera looks (straight down at x+8)
            ctx.runOnClient(mc -> {
                ModuleManager.getModuleByName("Freecam").toggle();
                com.OsamaClient.newbridge.Hacks.Misc.Freecam.cameraPos = new net.minecraft.world.phys.Vec3(ox + 8.5, Y + 6, oz + 0.5);
                com.OsamaClient.newbridge.Hacks.Misc.Freecam.cameraPitch = 90f;
            });
            String c = ctx.computeOnClient(mc -> q.create());
            made.add(c);
            net.minecraft.core.BlockPos p = q.lastPos;
            log("quickwaypoint freecam -> " + c + " at " + p);
            assertTrue(p.getX() == ox + 8 && p.getY() == Y && p.getZ() == oz, "freecam waypoint at " + p + ", expected " + (ox + 8) + "," + Y + "," + oz);
            ctx.runOnClient(mc -> ModuleManager.getModuleByName("Freecam").toggle());
        } finally {
            ctx.runOnClient(mc -> {
                if (com.OsamaClient.newbridge.Hacks.Misc.Freecam.isActive) ModuleManager.getModuleByName("Freecam").toggle();
                String key = wp.serverKey(mc);
                for (String n : made) wp.store.remove(key, n);
            });
        }
    }

    private void testChatSuggest(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var C = com.OsamaClient.newbridge.Utils.Commands.class;
        var wp = com.OsamaClient.newbridge.Hacks.Misc.Waypoints.INSTANCE;
        ctx.runOnClient(mc -> wp.store.put(wp.serverKey(mc), new com.OsamaClient.newbridge.Hacks.Misc.WaypointStore.Waypoint("GtBase", 1, 2, 3, com.OsamaClient.newbridge.Hacks.Misc.Waypoints.dim(mc), 0xFF55AAFF, false)));
        try {
            java.util.List<String> all = com.OsamaClient.newbridge.Utils.Commands.suggest(".");
            assertTrue(all.contains(".wp") && all.contains(".keys") && all.contains(".config") && all.contains(".goto"), "dot lists " + all);
            assertTrue(com.OsamaClient.newbridge.Utils.Commands.suggest(".w").containsAll(java.util.List.of(".wp", ".waypoint")), ".w -> " + com.OsamaClient.newbridge.Utils.Commands.suggest(".w"));
            assertTrue(com.OsamaClient.newbridge.Utils.Commands.suggest(".wp g").equals(java.util.List.of(".wp goto")), ".wp g -> " + com.OsamaClient.newbridge.Utils.Commands.suggest(".wp g"));
            assertTrue(com.OsamaClient.newbridge.Utils.Commands.suggest(".keys ").size() == 4, ".keys subs");
            java.util.List<String> names = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Utils.Commands.suggest(".wp goto Gt"));
            assertTrue(names.equals(java.util.List.of(".wp goto GtBase")), "waypoint name completion: " + names);
            assertTrue(com.OsamaClient.newbridge.Utils.Commands.suggest("hello").isEmpty() && com.OsamaClient.newbridge.Utils.Commands.suggest("/wp").isEmpty(), "normal chat / vanilla commands must not get suggestions");
            // real chat screen: type, box is drawn, Tab completes and cycles
            ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> setChatInput(mc, ".w"));
            ctx.waitTicks(4);
            java.util.List<String> shown = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Utils.ChatSuggest.lastShown);
            log("chat suggestion box shows " + shown);
            assertTrue(shown.contains(".wp") && shown.contains(".waypoint"), "suggestion box not drawn: " + shown);
            log("chat suggest screenshot: " + ctx.takeScreenshot("newbridge_chat_suggest"));
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.Utils.ChatSuggest.onKey((net.minecraft.client.gui.screens.ChatScreen) mc.gui.screen(), com.mojang.blaze3d.platform.InputConstants.KEY_TAB));
            String t1 = ctx.computeOnClient(mc -> getChatInput(mc));
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.Utils.ChatSuggest.onKey((net.minecraft.client.gui.screens.ChatScreen) mc.gui.screen(), com.mojang.blaze3d.platform.InputConstants.KEY_TAB));
            String t2 = ctx.computeOnClient(mc -> getChatInput(mc));
            log("tab completion: " + t1 + " -> " + t2);
            assertTrue(t1.startsWith(".wp") && t2.startsWith(".waypoint"), "tab cycling: '" + t1 + "' then '" + t2 + "'");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            // the new commands actually run
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.Utils.ChatHandler.handleKeys(new String[]{".keys", "list"}));
        } finally {
            ctx.runOnClient(mc -> { mc.gui.setScreen(null); wp.store.remove(wp.serverKey(mc), "GtBase"); });
        }
    }

    private static void setChatInput(net.minecraft.client.Minecraft mc, String s) {
        try {
            var f = net.minecraft.client.gui.screens.ChatScreen.class.getDeclaredField("input");
            f.setAccessible(true);
            ((net.minecraft.client.gui.components.EditBox) f.get(mc.gui.screen())).setValue(s);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private static String getChatInput(net.minecraft.client.Minecraft mc) {
        try {
            var f = net.minecraft.client.gui.screens.ChatScreen.class.getDeclaredField("input");
            f.setAccessible(true);
            return ((net.minecraft.client.gui.components.EditBox) f.get(mc.gui.screen())).getValue();
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private void testPreview(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var it = com.OsamaClient.newbridge.Hacks.Player.InvTweaks.INSTANCE;
        on(ctx, "InvTweaks");
        srv.runCommand("item replace entity @p inventory.0 with minecraft:shulker_box[container=[{slot:0,item:{id:\"minecraft:diamond\",count:3}},{slot:13,item:{id:\"minecraft:emerald\",count:12}},{slot:26,item:{id:\"minecraft:gold_ingot\",count:40}}]]");
        ctx.waitTicks(5);
        // pure: contents with positions
        java.util.List<String> got = ctx.computeOnClient(mc -> {
            var st = mc.player.getInventory().getItem(9);
            var l = it.previewItems(st);
            if (l == null) return java.util.List.of("null");
            return java.util.List.of(l.get(0).getItem().toString() + l.get(0).getCount(), l.get(13).getItem().toString() + l.get(13).getCount(), l.get(26).getItem().toString() + l.get(26).getCount(), String.valueOf(l.get(1).isEmpty()));
        });
        log("shulker preview items " + got);
        assertTrue(got.size() == 4 && got.get(0).contains("diamond3") && got.get(1).contains("emerald12") && got.get(2).contains("gold_ingot40") && got.get(3).equals("true"), "shulker contents wrong: " + got);
        assertTrue(ctx.computeOnClient(mc -> it.previewItems(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE))) == null, "plain items must not preview");
        // ender chest: cached once opened
        assertTrue(ctx.computeOnClient(mc -> it.previewItems(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_CHEST))) == null || ctx.computeOnClient(mc -> it.enderSeen), "ender preview without data");
        srv.runOnServer(s -> {
            var sp = s.getPlayerList().getPlayers().get(0);
            sp.getEnderChestInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 5));
            sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv, p.getEnderChestInventory()), net.minecraft.network.chat.Component.translatable("container.enderchest")));
        });
        ctx.waitTicks(25);
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitTicks(5);
        srv.runCommand("item replace entity @p inventory.1 with minecraft:ender_chest");
        ctx.waitTicks(5);
        String ender = ctx.computeOnClient(mc -> {
            var l = it.previewItems(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_CHEST));
            return l == null ? "null" : l.get(0).getItem() + " " + l.get(0).getCount();
        });
        log("ender preview: " + ender);
        assertTrue(ender.contains("netherite_ingot") && ender.endsWith("5"), "ender chest cache wrong: " + ender);
        // visual: open the inventory and hover the shulker (slot index 9)
        ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        ctx.waitTicks(5);
        int[] px = ctx.computeOnClient(mc -> {
            var cs = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.gui.screen();
            var acc = (com.OsamaClient.newbridge.mixin.AbstractContainerScreenAccessor) cs;
            var s = cs.getMenu().getSlot(9);
            double gs = mc.getWindow().getGuiScale();
            return new int[]{(int) ((acc.newbridge$leftPos() + s.x + 8) * gs), (int) ((acc.newbridge$topPos() + s.y + 8) * gs)};
        });
        ctx.getInput().setCursorPos(px[0], px[1]);
        ctx.waitTicks(6);
        boolean hovered = ctx.computeOnClient(mc -> ((com.OsamaClient.newbridge.mixin.AbstractContainerScreenAccessor) mc.gui.screen()).getHoveredSlot() != null);
        log("preview hover slot found=" + hovered);
        log("preview screenshot: " + ctx.takeScreenshot("newbridge_container_preview"));
        assertTrue(hovered, "cursor did not hover the slot");
        ctx.runOnClient(mc -> { mc.gui.setScreen(null); it.toggle(); });
    }

    private static int flagCount(com.OsamaClient.newbridge.Hacks.Client.CheckMeter c, String rule) { return c.totals.getOrDefault(rule, 0); }

    private void testCheckMeter(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var cm = com.OsamaClient.newbridge.Hacks.Client.CheckMeter.INSTANCE;
        var san = com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.INSTANCE;
        ctx.runOnClient(mc -> { if (cm.enabled) cm.toggle(); cm.toggle(); cm.clear(); cm.notify = false; if (san.enabled) san.toggle(); });
        try {
            // clean traffic: looks, then a position packet, one packet per tick -> no flags
            ctx.runOnClient(mc -> {
                boolean g = mc.player.onGround();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(10f, 5f, g, false), mc.player);
                com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(12f, 6f, g, false), mc.player);
                com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(g, false), mc.player);
            });
            assertTrue(cm.total == 0, "clean traffic produced flags " + cm.totals);
            // same look twice
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(12f, 6f, mc.player.onGround(), false), mc.player);
            });
            assertTrue(flagCount(cm, "DuplicateLook") == 1, "DuplicateLook flags " + cm.totals);
            // invalid pitch
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(13f, 120f, mc.player.onGround(), false), mc.player);
            });
            assertTrue(flagCount(cm, "BadPitch") == 1, "BadPitch flags " + cm.totals);
            // yaw snap right after a calm rotation: 13 -> 350 (+337)
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(350f, 30f, mc.player.onGround(), false), mc.player);
            });
            assertTrue(flagCount(cm, "Modulo360") == 1, "Modulo360 flags " + cm.totals);
            // a normal 40 degree turn must not flag
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(310f, 30f, mc.player.onGround(), false), mc.player);
            });
            assertTrue(flagCount(cm, "Modulo360") == 1, "normal turn flagged " + cm.totals);
            // two movement packets in one tick
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
                boolean g = mc.player.onGround();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(g, false), mc.player);
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(g, false), mc.player);
            });
            assertTrue(flagCount(cm, "Timer") == 1, "Timer flags " + cm.totals);
            // ground flag: true on the floor, false claim 5 blocks up
            boolean onFloor = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Client.CheckMeter.groundPlausible(mc.player));
            assertTrue(onFloor, "standing on the floor must be plausible ground");
            ctx.runOnClient(mc -> {
                var pos = mc.player.position();
                mc.player.setPos(pos.x, pos.y + 5, pos.z);
                com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(true, false), mc.player);
                mc.player.setPos(pos.x, pos.y, pos.z);
            });
            assertTrue(flagCount(cm, "GroundSpoof") == 1, "GroundSpoof flags " + cm.totals);
            // reach: a villager 5 blocks away is out of vanilla range, one 2 blocks away is fine
            srv.runCommand(String.format(java.util.Locale.ROOT, "summon villager %.1f %d %.1f {NoAI:1b,Tags:[\"gt\"]}", ox + 5.5, Y, oz + 0.5));
            ctx.waitTicks(6);
            ctx.runOnClient(mc -> {
                var list = mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, mc.player.getBoundingBox().inflate(10), e -> e != mc.player);
                assertTrue(!list.isEmpty(), "villager not on client");
                cm.onAttack(mc, list.get(0));
            });
            assertTrue(flagCount(cm, "Reach") == 1, "Reach flags " + cm.totals);
            srv.runCommand("kill @e[tag=gt]");
            srv.runCommand(String.format(java.util.Locale.ROOT, "summon villager %.1f %d %.1f {NoAI:1b,Tags:[\"gt\"]}", ox + 2.5, Y, oz + 0.5));
            ctx.waitTicks(6);
            ctx.runOnClient(mc -> {
                var list = mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, mc.player.getBoundingBox().inflate(10), e -> e != mc.player);
                var nearest = list.stream().min(java.util.Comparator.comparingDouble(e -> e.distanceTo(mc.player))).get();
                assertTrue(nearest.distanceTo(mc.player) < 3.0f, "near villager missing, nearest at " + nearest.distanceTo(mc.player));
                cm.onAttack(mc, nearest);
            });
            assertTrue(flagCount(cm, "Reach") == 1, "near villager flagged reach " + cm.totals);
            srv.runCommand("kill @e[tag=gt]");
            // HUD
            ctx.waitTicks(3);
            log("checkmeter screenshot: " + ctx.takeScreenshot("newbridge_checkmeter"));
        } finally {
            srv.runCommand("kill @e[tag=gt]");
            ctx.runOnClient(mc -> { cm.clear(); cm.notify = true; if (cm.enabled) cm.toggle(); if (!san.enabled) san.toggle(); });
        }
    }

    private void testSanitizer(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var S = com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.class;
        var san = com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.INSTANCE;
        var cm = com.OsamaClient.newbridge.Hacks.Client.CheckMeter.INSTANCE;
        assertTrue(san.enabled, "sanitizer is on by default");
        try {
            ctx.runOnClient(mc -> {
                var P = net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.class;
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.reset();
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var r1 = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(30f, 10f, true, false);
                assertTrue(com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(r1) == r1, "first look passes unchanged");
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var dup = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(30f, 10f, true, false);
                assertTrue(com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(dup) == null, "repeated look is dropped");
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var dupG = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(30f, 10f, false, false);
                var o = com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(dupG);
                assertTrue(o instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly so && !so.isOnGround() && !so.hasRotation(), "repeated look with changed ground flag becomes StatusOnly: " + o);
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var pr = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(1.5, 2.5, 3.5, 30f, 10f, false, false);
                var o2 = com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(pr);
                assertTrue(o2 instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos pp && pp.getX(0) == 1.5 && pp.getY(0) == 2.5 && pp.getZ(0) == 3.5 && !pp.hasRotation(),
                        "repeated look in pos+rot becomes Pos with the same coordinates: " + o2);
                // pitch clamp
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var bad = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(31f, 150f, false, false);
                var o3 = com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(bad);
                assertTrue(o3 instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot rr && rr.getXRot(0) == 90f && rr.getYRot(0) == 31f, "pitch 150 -> 90: " + o3);
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var nan = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(1, 2, 3, 40f, Float.NaN, false, false);
                var o4 = com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(nan);
                assertTrue(o4 instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot nr && nr.getXRot(0) == 0f && nr.getX(0) == 1, "NaN pitch -> 0: " + o4);
                // status spam: first one passes, the second in the same tick is dropped, next tick ok again
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var s1 = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(true, false);
                var s2 = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(true, false);
                assertTrue(com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(s1) == s1, "first status passes");
                assertTrue(com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(s2) == null, "second status in the same tick is dropped");
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                assertTrue(com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(s2) == s2, "status passes again next tick");
                // non movement packets are never touched
                var other = new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(2);
                assertTrue(com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(other) == other, "other packets untouched");
                // settings
                san.dedupeLook = false; san.clampPitch = false;
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var a = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(50f, 20f, true, false);
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(a);
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var b = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(50f, 20f, true, false);
                assertTrue(com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(b) == b, "dedupe off: duplicate passes");
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var c = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(51f, 170f, true, false);
                assertTrue(com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(c) == c, "clamp off: pitch passes");
                san.dedupeLook = true; san.clampPitch = true;
                san.toggle();
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.tick(mc.player);
                var d = new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(51f, 170f, true, false);
                assertTrue(com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fix(d) == d, "module off: everything passes");
                san.toggle();
            });
            // real connection: two identical looks and a pitch 200 in one tick go through Connection.send
            ctx.runOnClient(mc -> { if (!cm.enabled) cm.toggle(); cm.clear(); cm.notify = false; });
            int before = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fixed);
            ctx.runOnClient(mc -> {
                var con = mc.getConnection();
                boolean g = mc.player.onGround();
                com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.reset();
                con.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(77f, 4f, g, false));
                con.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(77f, 4f, g, false));
                con.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(78f, 200f, g, false));
            });
            ctx.waitTicks(3);
            int after = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.fixed);
            log("sanitizer real connection: fixed " + (after - before) + ", meter " + cm.totals + ", last fix: " + com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.lastFix);
            assertTrue(after - before >= 2, "connection hook fixed " + (after - before) + " packets, expected >= 2");
            assertTrue(flagCount(cm, "DuplicateLook") >= 1 && flagCount(cm, "BadPitch") >= 1, "meter saw the raw problems " + cm.totals);
            assertTrue(ctx.computeOnClient(mc -> mc.getConnection() != null && mc.getConnection().getConnection().isConnected()), "still connected after sanitizing");
        } finally {
            ctx.runOnClient(mc -> {
                san.dedupeLook = true; san.clampPitch = true;
                if (!san.enabled) san.toggle();
                cm.clear(); cm.notify = true; if (cm.enabled) cm.toggle();
            });
        }
    }

    private void testLegitMode(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var lm = com.OsamaClient.newbridge.Hacks.Client.LegitMode.INSTANCE;
        var ka = (com.OsamaClient.newbridge.Hacks.Combat.KillAura) ModuleManager.getModuleByName("KillAura");
        var tb = (com.OsamaClient.newbridge.Hacks.Combat.TriggerBot) ModuleManager.getModuleByName("TriggerBot");
        var fb = (com.OsamaClient.newbridge.Hacks.Combat.Fullbox) ModuleManager.getModuleByName("Fullbox");
        float kaOld = ka.range; double tbOld = tb.range;
        try {
            ctx.runOnClient(mc -> { if (lm.enabled) lm.toggle(); lm.capRanges = true; lm.blockModules = true; });
            // AirPlace is on, then Legit Mode turns on -> AirPlace must be switched off
            on(ctx, "AirPlace");
            ctx.runOnClient(mc -> { ka.range = 5.5f; tb.range = 5.0; });
            ctx.runOnClient(mc -> lm.toggle());
            ctx.waitTicks(3);
            assertTrue(!ctx.computeOnClient(mc -> ModuleManager.getModuleByName("AirPlace").enabled), "AirPlace still on after Legit Mode enabled");
            assertTrue(ctx.computeOnClient(mc -> ka.range) <= 3.0f && ctx.computeOnClient(mc -> tb.range) <= 3.0, "ranges not capped: " + ka.range + " / " + tb.range);
            assertTrue(com.OsamaClient.newbridge.Hacks.Client.LegitMode.cap(5.0) == 3.0 && com.OsamaClient.newbridge.Hacks.Client.LegitMode.cap(2.0) == 2.0, "cap()");
            // slider tries to raise it again -> next tick pulls it back
            ctx.runOnClient(mc -> ka.range = 6f);
            ctx.waitTicks(3);
            assertTrue(ctx.computeOnClient(mc -> ka.range) <= 3.0f, "range raised while legit stayed " + ka.range);
            // blocked modules refuse to enable, harmless ones do not
            for (String n : new String[]{"AirPlace", "Reach", "Flight", "FakeLag", "PingSpoof", "AntiHunger", "GuiDesync", "PacketBurst", "BundleDupe", "Speed", "NoSlow"}) {
                var m = ModuleManager.getModuleByName(n);
                if (m == null) continue;
                ctx.runOnClient(mc -> m.toggle());
                assertTrue(!ctx.computeOnClient(mc -> m.enabled), n + " enabled in Legit Mode");
                assertTrue(lm.lastBlocked != null && lm.lastBlocked.startsWith(n), "block message for " + n + ": " + lm.lastBlocked);
            }
            ctx.runOnClient(mc -> ModuleManager.getModuleByName("AutoSprint").toggle());
            assertTrue(ctx.computeOnClient(mc -> ModuleManager.getModuleByName("AutoSprint").enabled), "AutoSprint should be allowed");
            ctx.runOnClient(mc -> ModuleManager.getModuleByName("AutoSprint").toggle());
            // switching a setting off lifts that part
            ctx.runOnClient(mc -> { lm.blockModules = false; ModuleManager.getModuleByName("AirPlace").toggle(); });
            assertTrue(ctx.computeOnClient(mc -> ModuleManager.getModuleByName("AirPlace").enabled), "Block Modules off: AirPlace should enable");
            ctx.runOnClient(mc -> { ModuleManager.getModuleByName("AirPlace").toggle(); lm.blockModules = true; lm.capRanges = false; ka.range = 5f; });
            ctx.waitTicks(3);
            assertTrue(ctx.computeOnClient(mc -> ka.range) == 5f, "Cap Ranges off: range should stay 5");
            // Legit Mode off -> blocked modules work again
            ctx.runOnClient(mc -> { lm.capRanges = true; lm.toggle(); ModuleManager.getModuleByName("Reach").toggle(); });
            assertTrue(ctx.computeOnClient(mc -> ModuleManager.getModuleByName("Reach").enabled), "Reach should enable with Legit Mode off");
            ctx.runOnClient(mc -> ModuleManager.getModuleByName("Reach").toggle());
        } finally {
            ctx.runOnClient(mc -> {
                if (lm.enabled) lm.toggle();
                lm.capRanges = true; lm.blockModules = true;
                ka.range = kaOld; tb.range = tbOld;
                for (String n : new String[]{"AirPlace", "Reach"}) { var m = ModuleManager.getModuleByName(n); if (m.enabled) m.toggle(); }
            });
        }
    }

    private void testWorldColors(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("time set 6000");
        srv.runCommand("weather clear");
        // water and lava pools in front of the player
        fill(srv, 2, Y - 1, -2, 6, Y - 1, -1, "water");
        fill(srv, 2, Y - 1, 1, 6, Y - 1, 2, "lava");
        var wc = com.OsamaClient.newbridge.Hacks.World.WorldColors.INSTANCE;
        assertTrue(wc != null && wc.category == Module.Category.WORLD, "WorldColors missing / not in World");
        try {
            ctx.runOnClient(mc -> { mc.player.setYRot(-90f); mc.player.setXRot(35f); });
            ctx.waitTicks(20);
            ctx.takeScreenshot("newbridge_worldcolors_off");
            ctx.runOnClient(mc -> {
                wc.water = true; wc.waterColor = 0x90FF2050;
                wc.lava = true; wc.lavaColor = 0xFF40FF60;
                wc.sky = true; wc.skyDay = 0xFFFF9EDB;
                wc.fog = true; wc.fogDay = 0xFFFFC0E8;
                wc.clouds = true; wc.cloudColor = 0xFFFFB0D0;
                com.OsamaClient.newbridge.Hacks.World.WorldColors.fluidHits = 0;
                com.OsamaClient.newbridge.Hacks.World.WorldColors.fogHits = 0;
                com.OsamaClient.newbridge.Hacks.World.WorldColors.skyHits = 0;
                if (!wc.enabled) wc.toggle();
            });
            ctx.waitTicks(40);
            int fluid = com.OsamaClient.newbridge.Hacks.World.WorldColors.fluidHits;
            int fog = com.OsamaClient.newbridge.Hacks.World.WorldColors.fogHits;
            int sky = com.OsamaClient.newbridge.Hacks.World.WorldColors.skyHits;
            log("worldcolors hooks: fluid=" + fluid + " fog=" + fog + " (" + Integer.toHexString(com.OsamaClient.newbridge.Hacks.World.WorldColors.lastFogRgb) + ") sky=" + sky
                    + " (" + Integer.toHexString(com.OsamaClient.newbridge.Hacks.World.WorldColors.lastSkyRgb) + ") daylight=" + com.OsamaClient.newbridge.Hacks.World.WorldColors.daylight);
            ctx.takeScreenshot("newbridge_worldcolors_on");
            ctx.runOnClient(mc -> mc.player.setXRot(-25f));
            ctx.waitTicks(5);
            ctx.takeScreenshot("newbridge_worldcolors_sky");
            assertTrue(fluid > 50, "water / lava not recoloured (chunk rebuild?): " + fluid);
            assertTrue(fog > 5, "fog hook not running: " + fog);
            assertTrue(sky > 5, "sky hook not running: " + sky);
            // colour maths: shading kept, alpha from the setting
            int c = com.OsamaClient.newbridge.Hacks.World.WorldColors.fluidVertex(0xFF1E3264, 0xFF3C64C8,
                    net.minecraft.world.level.material.Fluids.WATER.defaultFluidState());
            assertTrue((c >>> 24) == 0x90 && ((c >> 16) & 0xFF) == 0x7F, "fluid colour maths: " + Integer.toHexString(c));
            // no fog / distance
            ctx.runOnClient(mc -> wc.noFog = true);
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> { wc.noFog = false; wc.hideClouds = true; });
            ctx.waitTicks(5);
        } finally {
            ctx.runOnClient(mc -> {
                wc.hideClouds = false; wc.noFog = false; wc.lava = false; wc.clouds = false;
                if (wc.enabled) wc.toggle();
            });
            fill(srv, 2, Y - 1, -2, 6, Y - 1, 2, "stone");
        }
    }

    private void testNormalMode(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var nm = com.OsamaClient.newbridge.Hacks.Client.NormalMode.INSTANCE;
        assertTrue(nm != null, "NormalMode missing");
        String[] hacks = {"Flight", "Velocity", "FakeLag"};
        try {
            ctx.runOnClient(mc -> {
                for (String n : hacks) { var m = ModuleManager.getModuleByName(n); if (!m.enabled) m.toggle(); }
                var z = ModuleManager.getModuleByName("FullBright"); if (!z.enabled) z.toggle();
                if (!nm.enabled) nm.toggle();
            });
            ctx.waitTicks(3);
            boolean hacksOff = ctx.computeOnClient(mc -> { for (String n : hacks) if (ModuleManager.getModuleByName(n).enabled) return false; return true; });
            boolean fbOn = ctx.computeOnClient(mc -> ModuleManager.getModuleByName("FullBright").enabled);
            // refuse
            ctx.runOnClient(mc -> ModuleManager.getModuleByName("KillAura").toggle());
            boolean kaOff = ctx.computeOnClient(mc -> !ModuleManager.getModuleByName("KillAura").enabled);
            // a profile / config turning a hack on gets switched off within a tick
            ctx.runOnClient(mc -> ModuleManager.getModuleByName("Speed").enabled = true);
            ctx.waitTicks(3);
            boolean speedOff = ctx.computeOnClient(mc -> !ModuleManager.getModuleByName("Speed").enabled);
            // allowed modules still work
            ctx.runOnClient(mc -> { var z = ModuleManager.getModuleByName("WorldColors"); if (!z.enabled) z.toggle(); });
            boolean wcOn = ctx.computeOnClient(mc -> ModuleManager.getModuleByName("WorldColors").enabled);
            // gui: combat panel empty, visual panel has rows
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.open());
            ctx.waitTicks(15);
            int combat = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.panel(Module.Category.COMBAT).matchCount(""));
            int visual = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.panel(Module.Category.VISUAL).matchCount(""));
            ctx.takeScreenshot("newbridge_normalmode_gui");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            log("normalmode: hacksOff=" + hacksOff + " fullbright=" + fbOn + " killauraRefused=" + kaOff + " speedOff=" + speedOff + " worldcolors=" + wcOn
                    + " combatRows=" + combat + " visualRows=" + visual + " switchedOff=" + nm.switchedOff() + " msg=" + nm.lastBlocked);
            assertTrue(hacksOff, "hacks still on in Normal Mode");
            assertTrue(fbOn && wcOn, "allowed modules were switched off");
            assertTrue(kaOff && speedOff, "blocked modules could be enabled");
            assertTrue(combat == 0 && visual > 3, "gui filter: combat " + combat + ", visual " + visual);
            // restore on exit
            ctx.runOnClient(mc -> nm.toggle());
            boolean back = ctx.computeOnClient(mc -> { for (String n : hacks) if (!ModuleManager.getModuleByName(n).enabled) return false; return true; });
            assertTrue(back, "modules not restored after Normal Mode");
        } finally {
            ctx.runOnClient(mc -> {
                if (nm.enabled) nm.toggle();
                for (String n : new String[]{"Flight", "Velocity", "FakeLag", "Speed", "KillAura", "FullBright", "WorldColors"}) {
                    var m = ModuleManager.getModuleByName(n); if (m.enabled) m.toggle();
                }
            });
        }
    }



    private void testCustomCape(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var cc = com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.INSTANCE;
        assertTrue(cc != null, "CustomCape missing");
        Path dir = com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.folder();
        // asymmetric picture: red top-left quarter, green stripe, blue arrow so a flipped / stretched cape is obvious
        writePng(dir.resolve("gt_cape.png"), 200, 320, (x, y) -> {
            if (x < 100 && y < 160) return 0xE02020;
            if (x > 85 && x < 115) return 0x20D040;
            if (y > 200 && Math.abs(x - 100) < (y - 200) / 2) return 0x2060F0;
            return 0xF0E0A0;
        });
        try {
            ctx.runOnClient(mc -> {
                cc.setFile("gt_cape.png");
                if (!cc.enabled) cc.toggle();
                mc.player.setYRot(0f); mc.player.setXRot(10f);
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            });
            for (int i = 0; i < 60 && !ctx.computeOnClient(mc -> cc.ready()); i += 5) ctx.waitTicks(5);
            boolean ready = ctx.computeOnClient(mc -> cc.ready());
            ctx.waitTicks(10);
            int applied = com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.applied;
            log("customcape: ready=" + ready + " status=" + ctx.computeOnClient(mc -> cc.status()) + " applied=" + applied + " uploads=" + com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.uploads);
            assertTrue(ready, "cape texture not ready: " + ctx.computeOnClient(mc -> cc.status()));
            assertTrue(applied > 0, "cape never applied to the player model");
            ctx.takeScreenshot("newbridge_cape_back");
            // cycling and a ready-made 64x32 cape texture
            writePng(dir.resolve("gt_cape2.png"), 128, 64, (x, y) -> (x / 8 + y / 8) % 2 == 0 ? 0x8030C0 : 0xE0A030);
            ctx.runOnClient(mc -> cc.setFile("gt_cape2.png"));
            int up0 = com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.uploads;
            for (int i = 0; i < 60 && com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.uploads == up0; i += 5) ctx.waitTicks(5);
            ctx.waitTicks(5);
            assertTrue(com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.uploads > up0, "second cape not uploaded");
            ctx.takeScreenshot("newbridge_cape_checker");
            ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        } finally {
            ctx.runOnClient(mc -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                if (cc.enabled) cc.toggle();
            });
            try { java.nio.file.Files.deleteIfExists(dir.resolve("gt_cape.png")); java.nio.file.Files.deleteIfExists(dir.resolve("gt_cape2.png")); } catch (Exception ignored) { }
        }
    }

    private void testHitHurtAnimations(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var hit = com.OsamaClient.newbridge.Hacks.Visual.HitEffects.INSTANCE;
        var hurt = com.OsamaClient.newbridge.Hacks.Visual.HurtCam.INSTANCE;
        var anim = com.OsamaClient.newbridge.Hacks.Visual.Animations.INSTANCE;
        var copy = com.OsamaClient.newbridge.Hacks.Misc.CopyCoords.INSTANCE;
        assertTrue(hit != null && hurt != null && anim != null && copy != null, "modules missing");
        srv.runCommand("give @a diamond_sword");
        srv.runCommand("summon pig 0.5 " + Y + " 3.5 {NoAI:1b,Silent:1b,Tags:[\"gt\"]}");
        ctx.waitTicks(10);
        try {
            // ---- hit + kill effects
            ctx.runOnClient(mc -> {
                mc.player.setYRot(0f); mc.player.setXRot(5f);
                hit.style = "Blood"; hit.kill = "Lightning"; hit.amount = 24;
                if (!hit.enabled) hit.toggle();
            });
            ctx.waitTicks(3);
            ctx.runOnClient(mc -> {
                var pig = mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.pig.Pig.class, mc.player.getBoundingBox().inflate(8)).get(0);
                mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, pig.position().add(0, pig.getBbHeight() / 2, 0));
                mc.gameMode.attack(mc.player, pig);
            });
            ctx.waitTicks(6);
            log("hitfx: bursts=" + hit.hitBursts + " particles=" + hit.lastParticles);
            ctx.takeScreenshot("newbridge_hitfx_hit");
            assertTrue(hit.hitBursts >= 1 && hit.lastParticles == 24, "no hit burst: " + hit.hitBursts + "/" + hit.lastParticles);
            srv.runCommand("damage @e[tag=gt,type=pig,limit=1] 100");
            ctx.waitTicks(3);
            ctx.takeScreenshot("newbridge_hitfx_kill");
            ctx.waitTicks(4);
            long bolts = ctx.computeOnClient(mc -> { long n = 0; for (var e : mc.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.LightningBolt) n++; return n; });
            log("hitfx: killBursts=" + hit.killBursts + " bolts=" + bolts);
            assertTrue(hit.killBursts == 1, "kill effect did not fire: " + hit.killBursts);
            assertTrue(bolts >= 1, "no lightning bolt entity on the client");

            // ---- hurt cam flash
            ctx.runOnClient(mc -> { hurt.flashColor = 0xAAFF2020; hurt.flashSeconds = 0.6; hurt.tilt = 1.0; if (!hurt.enabled) hurt.toggle(); mc.player.setYRot(0f); });
            ctx.waitTicks(4);
            int f0 = hurt.flashes;
            srv.runCommand("damage @p 2");
            float best = 0;
            for (int i = 0; i < 6; i++) {
                ctx.waitTicks(1);
                if (i == 1) ctx.takeScreenshot("newbridge_hurtcam_flash");
                best = Math.max(best, hurt.lastFlashAlpha);
            }
            log("hurtcam: flashes " + f0 + " -> " + hurt.flashes + " bestAlpha=" + best);
            assertTrue(hurt.flashes > f0, "hurt not detected");
            assertTrue(best > 0.3f, "flash never drawn, alpha " + best);
            ctx.runOnClient(mc -> hurt.tilt = 0.0);
            ctx.waitTicks(15);
            srv.runCommand("damage @p 2");
            ctx.waitTicks(10);   // renders with tilt 0 through the replaced bobHurt without errors
            assertTrue(ctx.computeOnClient(mc -> mc.player != null && !mc.player.isDeadOrDying()), "player died in hurtcam test");

            // ---- swing speed
            ctx.runOnClient(mc -> { anim.swingSpeed = 2.0; if (!anim.enabled) anim.toggle(); });
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.Utils.SwingUtil.swing(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND));
            ctx.waitTicks(2);
            int d2 = com.OsamaClient.newbridge.Hacks.Visual.Animations.lastDuration;
            ctx.runOnClient(mc -> { anim.swingSpeed = 0.5; });
            ctx.waitTicks(12);
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.Utils.SwingUtil.swing(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND));
            ctx.waitTicks(2);
            int d05 = com.OsamaClient.newbridge.Hacks.Visual.Animations.lastDuration;
            log("animations: swing duration at 2x=" + d2 + ", at 0.5x=" + d05);
            assertTrue(d2 == 3 && d05 == 12, "swing durations " + d2 + " / " + d05 + " (expected 3 / 12)");

            // ---- copy coords
            ctx.runOnClient(mc -> { copy.format = "X Y Z"; copy.toggle(); });
            String clip = ctx.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
            boolean off = ctx.computeOnClient(mc -> !copy.enabled);
            log("copycoords: clipboard='" + clip + "' off=" + off);
            assertTrue(off && clip.equals(copy.last) && clip.matches("-?\\d+ -?\\d+ -?\\d+"), "clipboard '" + clip + "'");
        } finally {
            ctx.runOnClient(mc -> {
                for (Module m : new Module[]{hit, hurt, anim}) if (m.enabled) m.toggle();
            });
            srv.runCommand("kill @e[tag=gt]");
        }
    }


    private void testMediaPicker(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var cc = com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.INSTANCE;
        var picker = (com.OsamaClient.newbridge.UI.components.MediaPicker) cc.fileBox;
        assertTrue(com.OsamaClient.newbridge.UI.gui.setting.SettingWidgets.create(picker) instanceof com.OsamaClient.newbridge.UI.gui.setting.MediaWidget, "file setting has no media widget");
        Path dir = com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.folder();
        String[] names = {"gt_m1.png", "gt_m2.png", "gt_m3.png", "gt_m4.png"};
        int[] cols = {0xE04040, 0x40C060, 0x4070F0, 0xE0C030};
        for (int i = 0; i < names.length; i++) {
            final int c = cols[i];
            writePng(dir.resolve(names[i]), 400, 250, (x, y) -> (Math.abs(x - 200) + Math.abs(y - 125) < 90) ? 0xFFFFFF : (((x / 40 + y / 25) & 1) == 0 ? c : (c >> 1) & 0x7F7F7F));
        }
        try {
            ctx.runOnClient(mc -> {
                if (!cc.enabled) cc.toggle();
                picker.setText("");
                com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(new com.OsamaClient.newbridge.UI.gui.window.MediaPickerWindow(picker));
            });
            ctx.waitTicks(25);
            var win = (com.OsamaClient.newbridge.UI.gui.window.MediaPickerWindow) ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.current());
            int files = ctx.computeOnClient(mc -> win.fileCount());
            int thumbs = ctx.computeOnClient(mc -> win.thumbsReady());
            log("mediapicker: files=" + files + " thumbs=" + thumbs);
            assertTrue(files >= 4, "files in the browser: " + files);
            assertTrue(thumbs >= 4, "thumbnails loaded: " + thumbs);
            ctx.takeScreenshot("newbridge_media_grid");
            // hover: the big preview appears
            int idx = ctx.computeOnClient(mc -> win.tileIndex("gt_m2.png"));
            float[] fb = ctx.computeOnClient(mc -> win.tileCenterFb(idx));
            assertTrue(fb != null, "tile not drawn");
            double k = ctx.computeOnClient(mc -> (double) mc.getWindow().getScreenWidth() / mc.getWindow().getWidth());
            ctx.getInput().setCursorPos(fb[0] * k, fb[1] * k);
            ctx.waitTicks(20);
            int big = ctx.computeOnClient(mc -> win.bigPreviews);
            ctx.takeScreenshot("newbridge_media_hover");
            assertTrue(big > 0, "no big preview while hovering");
            // click picks the file and the module uses it
            ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(4);
            String chosen = ctx.computeOnClient(mc -> picker.getText());
            for (int i = 0; i < 60 && !ctx.computeOnClient(mc -> cc.ready()); i += 5) ctx.waitTicks(5);
            log("mediapicker: chosen=" + chosen + " capeStatus=" + ctx.computeOnClient(mc -> cc.status()));
            assertTrue("gt_m2.png".equals(chosen), "clicked tile not picked: " + chosen);
            assertTrue(ctx.computeOnClient(mc -> cc.file.equals("gt_m2.png") && cc.ready()), "cape did not switch to the picked file");
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close());
            ctx.waitTicks(6);
            // the setting row with the mini preview, in the open ClickGUI
            ctx.runOnClient(mc -> {
                if (!com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.isOpen()) com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.open();
                for (var r : com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.panel(Module.Category.VISUAL).rows()) if (r.module == cc) r.setExpanded(true);
            });
            ctx.waitTicks(25);
            ctx.takeScreenshot("newbridge_media_setting");
            ctx.runOnClient(mc -> { for (var r : com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.panel(Module.Category.VISUAL).rows()) if (r.module == cc) r.setExpanded(false); mc.gui.setScreen(null); });
        } finally {
            ctx.runOnClient(mc -> {
                if (com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.current() != null) com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close();
                picker.setText("");
                if (cc.enabled) cc.toggle();
                mc.gui.setScreen(null);
            });
            try { for (String n : names) java.nio.file.Files.deleteIfExists(dir.resolve(n)); } catch (Exception ignored) { }
        }
    }

    private void testContextMenuAndWindow(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
        var cg = com.OsamaClient.newbridge.UI.gui.ClickGuiModule.INSTANCE;
        var zoom = com.OsamaClient.newbridge.UI.components.ModuleManager.getModuleByName("Zoom");
        var fov = com.OsamaClient.newbridge.UI.components.ModuleManager.getModuleByName("FullBright");
        boolean favWas = zoom.favorite, fov0 = fov.favorite;
        try {
            // favorites panel
            ctx.runOnClient(mc -> { zoom.favorite = false; fov.favorite = false; if (!gui.isOpen()) gui.open(); gui.setSearch(""); });
            ctx.waitTicks(25);
            float hEmpty = ctx.computeOnClient(mc -> { for (var p : gui.panels()) if (p.isFavorites()) return p.lastHeight(); return -1f; });
            ctx.runOnClient(mc -> { zoom.favorite = true; fov.favorite = true; });
            ctx.waitTicks(35);
            float hFav = ctx.computeOnClient(mc -> { for (var p : gui.panels()) if (p.isFavorites()) return p.lastHeight(); return -1f; });
            int favRows = ctx.computeOnClient(mc -> { for (var p : gui.panels()) if (p.isFavorites()) return p.matchCount(""); return -1; });
            log("ctxmenu favorites panel height " + hEmpty + " -> " + hFav + " rows=" + favRows);
            assertTrue(hEmpty <= 0.5f && hFav > 20f && favRows == 2, "favorites panel wrong: " + hEmpty + "/" + hFav + "/" + favRows);
            ctx.takeScreenshot("newbridge_favorites_panel");

            // quick menu (Shift + right click), default and glass look
            var row = ctx.computeOnClient(mc -> { for (var r : gui.panel(Module.Category.VISUAL).rows()) if (r.module == zoom) return r; return null; });
            ctx.runOnClient(mc -> gui.openMenuAt(row, 120f, 60f));
            ctx.waitTicks(15);
            var items = ctx.computeOnClient(mc -> gui.menuItems());
            log("ctxmenu items " + items);
            assertTrue(items.contains("Favorite") || items.contains("Unfavorite"), "menu has no favorite item: " + items);
            ctx.takeScreenshot("newbridge_ctxmenu");
            ctx.runOnClient(mc -> { cg.style.setIndex(1); });
            ctx.waitTicks(20);
            ctx.takeScreenshot("newbridge_ctxmenu_glass");
            ctx.runOnClient(mc -> cg.style.setIndex(0));
            ctx.runOnClient(mc -> gui.runMenuItem("Unfavorite"));
            assertTrue(!zoom.favorite, "Unfavorite did not clear the flag");
            ctx.runOnClient(mc -> gui.openMenuAt(row, 120f, 60f));
            ctx.runOnClient(mc -> gui.runMenuItem("Show Settings"));
            assertTrue(ctx.computeOnClient(mc -> row.isExpanded()), "Show Settings did not expand the row");

            // Alt window with all settings; the ClickGUI is hidden meanwhile
            ctx.runOnClient(mc -> gui.openSettingsWindow(row));
            ctx.waitTicks(25);
            boolean winOpen = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.current() instanceof com.OsamaClient.newbridge.UI.gui.window.ModuleSettingsWindow);
            int drawn = ctx.computeOnClient(mc -> ((com.OsamaClient.newbridge.UI.gui.window.ModuleSettingsWindow) com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.current()).drawn);
            boolean guiGone = ctx.computeOnClient(mc -> !(mc.gui.screen() instanceof com.OsamaClient.newbridge.UI.gui.ClickGuiScreen));
            log("ctxmenu settings window open=" + winOpen + " drawn=" + drawn + " guiHidden=" + guiGone + " settings=" + zoom.settings.size());
            assertTrue(winOpen && guiGone && drawn >= Math.min(3, zoom.settings.size()), "settings window not shown");
            ctx.takeScreenshot("newbridge_settings_window");
            ctx.runOnClient(mc -> cg.style.setIndex(1));
            ctx.waitTicks(20);
            ctx.takeScreenshot("newbridge_settings_window_glass");
            ctx.runOnClient(mc -> { cg.style.setIndex(0); com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close(); });
            ctx.waitTicks(25);
            assertTrue(ctx.computeOnClient(mc -> gui.isOpen()), "closing the settings window did not return to the ClickGUI");

            // folded groups are saved
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.components.SubCategories.setCollapsed(Module.Category.COMBAT, "Ranged", true));
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.config.Config.save());
            String cfg = ctx.computeOnClient(mc -> { try { return java.nio.file.Files.readString(com.OsamaClient.newbridge.config.Config.file()); } catch (Exception e) { return "ERR " + e; } });
            log("ctxmenu config has folded group: " + cfg.contains("COMBAT/Ranged"));
            assertTrue(cfg.contains("COMBAT/Ranged"), "folded group not written to the config");
        } finally {
            ctx.runOnClient(mc -> {
                zoom.favorite = favWas; fov.favorite = fov0; cg.style.setIndex(0);
                com.OsamaClient.newbridge.UI.components.SubCategories.setCollapsed(Module.Category.COMBAT, "Ranged", false);
                if (com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.current() != null) com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close();
                for (var r : gui.panel(Module.Category.VISUAL).rows()) r.setExpanded(false);
                mc.gui.setScreen(null);
            });
        }
    }

    private void testShortcuts(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var wm = com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE;
        var cg = com.OsamaClient.newbridge.UI.gui.ClickGuiModule.INSTANCE;
        var besp = com.OsamaClient.newbridge.UI.components.ModuleManager.getModuleByName("BlockESP");
        assertTrue(besp != null && besp.category == Module.Category.VISUAL, "BlockESP is not in Visual");
        var bound = com.OsamaClient.newbridge.UI.components.ModuleManager.getModuleByName("Zoom");
        int keyWas = bound.key;
        try {
            ctx.runOnClient(mc -> { bound.key = 90; wm.open(new com.OsamaClient.newbridge.UI.gui.window.ShortcutsWindow()); });
            ctx.waitTicks(25);
            var win = (com.OsamaClient.newbridge.UI.gui.window.ShortcutsWindow) wm.current();
            int rows = ctx.computeOnClient(mc -> win.rowsDrawn), secs = ctx.computeOnClient(mc -> win.sectionsDrawn);
            log("shortcuts rows=" + rows + " sections=" + secs);
            assertTrue(rows >= 15 && secs >= 3, "shortcuts window drew too little: " + rows + "/" + secs);
            ctx.takeScreenshot("newbridge_shortcuts");
            ctx.runOnClient(mc -> cg.style.setIndex(1));
            ctx.waitTicks(20);
            ctx.takeScreenshot("newbridge_shortcuts_glass");
        } finally {
            ctx.runOnClient(mc -> { cg.style.setIndex(0); bound.key = keyWas; if (wm.current() != null) wm.close(); mc.gui.setScreen(null); });
        }
    }

    private void testLiquidGlass(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
        var cg = com.OsamaClient.newbridge.UI.gui.ClickGuiModule.INSTANCE;
        var ka = com.OsamaClient.newbridge.UI.components.ModuleManager.getModuleByName("Zoom");
        boolean zoomWas = ka.enabled;
        try {
            float r0 = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.Theme.radius());
            ctx.runOnClient(mc -> { cg.style.setIndex(1); if (!gui.isOpen()) gui.open(); gui.setSearch(""); ka.enabled = true; });
            ctx.waitTicks(40);
            boolean glass = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.Theme.glass());
            float r1 = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.Theme.radius());
            String bg = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.Theme.background());
            log("liquidglass glass=" + glass + " radius " + r0 + " -> " + r1 + " bg=" + bg);
            assertTrue(glass && r1 >= 6f && r1 > r0 && "Blur".equals(bg), "glass style values wrong");
            ctx.runOnClient(mc -> { for (var r : gui.panel(Module.Category.VISUAL).rows()) if (r.module == ka) r.setExpanded(true); });
            ctx.waitTicks(30);
            ctx.takeScreenshot("newbridge_liquid_glass");
        } finally {
            ctx.runOnClient(mc -> {
                cg.style.setIndex(0);
                ka.enabled = zoomWas;
                for (var r : gui.panel(Module.Category.VISUAL).rows()) if (r.module == ka) r.setExpanded(false);
                mc.gui.setScreen(null);
            });
        }
    }

    private void testNoRenderMore(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        String[][] hooks = {
                {"net.minecraft.client.gui.Hud", "noRender_extractBossOverlay", "noRender_extractSubtitleOverlay", "noRender_extractOverlayMessage",
                        "noRender_extractSleepOverlay", "noRender_extractPlayerHealth", "noRender_extractFood", "noRender_extractVehicleHealth", "noRender_extractSavingIndicator"},
                {"net.minecraft.client.renderer.GameRenderer", "noRender_bobHurt", "noRender_bobView"},
                {"net.minecraft.client.renderer.ScreenEffectRenderer", "onSubmitWater", "onItemActivation"},
                {"net.minecraft.world.level.Level", "noRender_rain", "noRender_thunder"},
                {"net.minecraft.client.particle.ParticleEngine", "noRender_add"}};
        for (String[] h : hooks) {
            try {
                StringBuilder names = new StringBuilder();
                for (var m : Class.forName(h[0]).getDeclaredMethods()) names.append(m.getName()).append(' ');
                for (int i = 1; i < h.length; i++) assertTrue(names.toString().contains(h[i]), "NoRender hook " + h[i] + " missing in " + h[0]);
            } catch (ClassNotFoundException e) {
                throw new AssertionError(e);
            }
        }
        var nr = com.OsamaClient.newbridge.Hacks.Visual.NoRender.INSTANCE;
        boolean was = nr.enabled;
        try {
            srv.runCommand("weather rain");
            ctx.waitTicks(80);
            float rain0 = ctx.computeOnClient(mc -> mc.level.getRainLevel(1f));
            ctx.runOnClient(mc -> { nr.enabled = true; nr.weather = true; });
            float rain1 = ctx.computeOnClient(mc -> mc.level.getRainLevel(1f));
            log("norender weather rain " + rain0 + " -> " + rain1);
            assertTrue(rain0 > 0.2f && rain1 == 0f, "weather not hidden: " + rain0 + " -> " + rain1);
            ctx.runOnClient(mc -> { nr.weather = false; nr.healthBar = true; nr.foodBar = true; nr.subtitles = true; nr.particles = true; });
            ctx.waitTicks(20);
            ctx.takeScreenshot("newbridge_norender_nohud");
        } finally {
            srv.runCommand("weather clear");
            ctx.runOnClient(mc -> { nr.enabled = was; nr.weather = false; nr.healthBar = false; nr.foodBar = false; nr.subtitles = false; nr.particles = false; });
        }
    }

    private void testCheckMeterMoreAndControls(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var cm = com.OsamaClient.newbridge.Hacks.Client.CheckMeter.INSTANCE;
        var san = com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer.INSTANCE;
        boolean cmWas = cm.enabled, sanWas = san.enabled;
        net.minecraft.client.KeyMapping[] target = new net.minecraft.client.KeyMapping[1];
        com.mojang.blaze3d.platform.InputConstants.Key[] keyWas = new com.mojang.blaze3d.platform.InputConstants.Key[1];
        try {
            ctx.runOnClient(mc -> { if (cm.enabled) cm.toggle(); cm.toggle(); cm.clear(); cm.notify = false; if (san.enabled) san.toggle(); });
            // a step 0.1 above the floor (carpet / slab / step down) is plausible ground, 0.6 up is not
            ctx.runOnClient(mc -> {
                var pos = mc.player.position();
                mc.player.setPos(pos.x, pos.y + 0.1, pos.z);
                com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(true, false), mc.player);
                mc.player.setPos(pos.x, pos.y, pos.z);
            });
            assertTrue(flagCount(cm, "GroundSpoof") == 0, "small step flagged as GroundSpoof " + cm.totals);
            ctx.runOnClient(mc -> {
                var pos = mc.player.position();
                mc.player.setPos(pos.x, pos.y + 0.6, pos.z);
                com.OsamaClient.newbridge.Hacks.Client.CheckMeter.tick();
                cm.observeMove(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly(true, false), mc.player);
                mc.player.setPos(pos.x, pos.y, pos.z);
            });
            assertTrue(flagCount(cm, "GroundSpoof") == 1, "0.6 blocks up not flagged " + cm.totals);
            // click rate
            srv.runCommand(String.format(java.util.Locale.ROOT, "summon villager %.1f %d %.1f {NoAI:1b,Tags:[\"gt\"]}", ox + 2.5, Y, oz + 0.5));
            ctx.waitTicks(6);
            ctx.runOnClient(mc -> {
                var list = mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, mc.player.getBoundingBox().inflate(10), e -> e != mc.player);
                var nearest = list.stream().min(java.util.Comparator.comparingDouble(e -> e.distanceTo(mc.player))).get();
                for (int i = 0; i < 22; i++) cm.onAttack(mc, nearest);
            });
            assertTrue(flagCount(cm, "ClickRate") >= 1, "ClickRate not flagged " + cm.totals);
            srv.runCommand("kill @e[tag=gt]");
            // keybinds window: bind a vanilla control to a free key, then unbind
            var win = new com.OsamaClient.newbridge.UI.gui.window.KeyboardWindow();
            ctx.runOnClient(mc -> {
                var all = com.OsamaClient.newbridge.UI.gui.window.KeyboardWindow.controls();
                target[0] = all.get(0);
                for (var km : all) if (km.getName().equals("key.hotbar.9")) target[0] = km;
                keyWas[0] = target[0].saveString().isEmpty() ? com.mojang.blaze3d.platform.InputConstants.UNKNOWN : com.mojang.blaze3d.platform.InputConstants.getKey(target[0].saveString());
                win.select(65); // F8
                win.showControls(true);
                com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(win);
            });
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> win.bindControl(target[0], 65));
            boolean bound = ctx.computeOnClient(mc -> target[0].matches(com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(65)));
            assertTrue(bound, "control not rebound to F8");
            ctx.waitTicks(10);
            ctx.takeScreenshot("newbridge_keybinds_controls");
            ctx.runOnClient(mc -> win.unbindControl(target[0]));
            assertTrue(ctx.computeOnClient(mc -> target[0].isUnbound()), "control not unbound");
        } finally {
            srv.runCommand("kill @e[tag=gt]");
            ctx.runOnClient(mc -> {
                if (target[0] != null && keyWas[0] != null) { target[0].setKey(keyWas[0]); net.minecraft.client.KeyMapping.resetMapping(); mc.options.save(); }
                var wm = com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE;
                if (wm.current() != null) wm.close();
                mc.gui.setScreen(null);
                cm.clear(); cm.notify = true; if (cm.enabled && !cmWas) cm.toggle(); if (san.enabled != sanWas) san.toggle();
            });
        }
    }

    private double jumpPeak(ClientGameTestContext ctx) {
        double[] peak = {0};
        double y0 = ctx.computeOnClient(mc -> mc.player.getY());
        ctx.runOnClient(mc -> mc.player.jumpFromGround());
        for (int i = 0; i < 40; i++) {
            ctx.waitTick();
            peak[0] = Math.max(peak[0], ctx.computeOnClient(mc -> mc.player.getY()) - y0);
        }
        ctx.waitTicks(15);
        return peak[0];
    }

    private void testHighJump(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var hj = ModuleManager.getModuleByName("HighJump");
        boolean was = hj.enabled;
        try {
            ctx.runOnClient(mc -> { if (hj.enabled) hj.toggle(); });
            ctx.waitTicks(10);
            double vanilla = jumpPeak(ctx);
            ctx.runOnClient(mc -> { if (!hj.enabled) hj.toggle(); });
            ctx.waitTicks(10);
            double boosted = jumpPeak(ctx);
            log("highjump peak vanilla " + vanilla + " boosted " + boosted);
            assertTrue(vanilla > 1.0 && vanilla < 1.5, "vanilla peak " + vanilla);
            assertTrue(boosted > vanilla * 1.6, "HighJump did not raise the jump: " + vanilla + " -> " + boosted);
        } finally {
            ctx.runOnClient(mc -> { if (hj.enabled != was) hj.toggle(); });
        }
    }

    private void testAccounts(ClientGameTestContext ctx, TestServerContext srv) {
        net.minecraft.client.User orig = ctx.computeOnClient(mc -> mc.getUser());
        java.nio.file.Path tmp;
        try { tmp = java.nio.file.Files.createTempFile("nbacc", ".json"); } catch (java.io.IOException e) { throw new AssertionError(e); }
        String idWas = com.OsamaClient.newbridge.Utils.accounts.AccountManager.clientId;
        try {
            com.OsamaClient.newbridge.Utils.accounts.AccountManager.testFile = tmp;
            ctx.runOnClient(mc -> {
                var m = com.OsamaClient.newbridge.Utils.accounts.AccountManager.class;
                com.OsamaClient.newbridge.Utils.accounts.AccountManager.accounts.clear();
                com.OsamaClient.newbridge.Utils.accounts.AccountManager.clientId = "00000000-0000-0000-0000-000000000000";
                var a = new com.OsamaClient.newbridge.Utils.accounts.AccountManager.Account(); a.name = "AltOne"; a.uuid = java.util.UUID.randomUUID().toString(); a.refresh = "SECRET-REFRESH-TOKEN-1";
                var b = new com.OsamaClient.newbridge.Utils.accounts.AccountManager.Account(); b.name = "AltTwo"; b.uuid = java.util.UUID.randomUUID().toString(); b.refresh = "";
                com.OsamaClient.newbridge.Utils.accounts.AccountManager.accounts.add(a);
                com.OsamaClient.newbridge.Utils.accounts.AccountManager.accounts.add(b);
                com.OsamaClient.newbridge.Utils.accounts.AccountManager.save();
                mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            });
            ctx.waitTicks(20);
            boolean btn = ctx.computeOnClient(mc -> {
                if (mc.gui.screen() == null) return false;
                for (var c : mc.gui.screen().children()) if (c instanceof net.minecraft.client.gui.components.Button bt && bt.getMessage().getString().equals("Accounts")) return true;
                return false;
            });
            assertTrue(btn, "no Accounts button on the title screen");
            String raw;
            try { raw = java.nio.file.Files.readString(tmp); } catch (java.io.IOException e) { throw new AssertionError(e); }
            assertTrue(!raw.contains("SECRET-REFRESH-TOKEN-1") && raw.contains("AltOne"), "refresh token not encrypted: " + raw);
            ctx.runOnClient(mc -> { com.OsamaClient.newbridge.Utils.accounts.AccountManager.load(); });
            String back = com.OsamaClient.newbridge.Utils.accounts.AccountManager.accounts.get(0).refresh;
            assertTrue("SECRET-REFRESH-TOKEN-1".equals(back), "refresh token not decrypted: " + back);
            ctx.runOnClient(mc -> mc.gui.setScreen(new com.OsamaClient.newbridge.Utils.accounts.AccountScreen(mc.gui.screen())));
            ctx.waitTicks(15);
            int rows = ctx.computeOnClient(mc -> ((com.OsamaClient.newbridge.Utils.accounts.AccountScreen) mc.gui.screen()).rows);
            assertTrue(rows == 2, "account rows " + rows);
            ctx.takeScreenshot("newbridge_accounts");
            // switching the session
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.Utils.accounts.AccountManager.setUser(new net.minecraft.client.User("SwitchedAlt", java.util.UUID.randomUUID(), "x", java.util.Optional.empty(), java.util.Optional.empty())));
            String now = ctx.computeOnClient(mc -> mc.getUser().getName());
            assertTrue("SwitchedAlt".equals(now), "session not switched: " + now);
        } finally {
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.Utils.accounts.AccountManager.setUser(orig);
                com.OsamaClient.newbridge.Utils.accounts.AccountManager.testFile = null;
                com.OsamaClient.newbridge.Utils.accounts.AccountManager.accounts.clear();
                com.OsamaClient.newbridge.Utils.accounts.AccountManager.clientId = idWas;
                mc.gui.setScreen(null);
            });
            try { java.nio.file.Files.deleteIfExists(tmp); } catch (java.io.IOException ignored) {}
        }
    }

    private void testVapeLayout(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
        var cg = com.OsamaClient.newbridge.UI.gui.ClickGuiModule.INSTANCE;
        var vape = gui.vape();
        try {
            ctx.runOnClient(mc -> { cg.layout.setIndex(1); gui.setSearch(""); if (!gui.isOpen()) gui.open(); vape.select("Combat"); });
            ctx.waitTicks(40);
            boolean on = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.Theme.vape());
            int n = ctx.computeOnClient(mc -> vape.rowsDrawn);
            String title = ctx.computeOnClient(mc -> vape.lastTitle);
            log("vape on=" + on + " rows=" + n + " title=" + title);
            assertTrue(on && n >= 5 && "Combat".equals(title), "vape combat list wrong");
            var km = ModuleManager.getModuleByName("KillAura");
            ctx.runOnClient(mc -> vape.row(km).setExpanded(true));
            ctx.waitTicks(30);
            ctx.takeScreenshot("newbridge_vape_combat");
            ctx.runOnClient(mc -> vape.select("Visual"));
            ctx.waitTicks(25);
            int nv = ctx.computeOnClient(mc -> vape.rowsDrawn);
            assertTrue(nv >= 5, "vape visual list empty");
            ctx.runOnClient(mc -> gui.setSearch("esp"));
            ctx.waitTicks(25);
            int ns = ctx.computeOnClient(mc -> vape.rowsDrawn);
            String st = ctx.computeOnClient(mc -> vape.lastTitle);
            log("vape search esp rows=" + ns + " title=" + st);
            assertTrue(ns >= 2 && "Search results".equals(st), "vape search wrong");
            ctx.runOnClient(mc -> { gui.setSearch(""); cg.style.setIndex(1); vape.select("Visual"); });
            ctx.waitTicks(40);
            ctx.takeScreenshot("newbridge_vape_glass");
            ctx.runOnClient(mc -> { cg.style.setIndex(0); cg.layout.setIndex(0); });
            ctx.waitTicks(30);
            boolean off = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.Theme.vape());
            assertTrue(!off, "layout did not switch back");
            ctx.takeScreenshot("newbridge_vape_back_to_panels");
        } finally {
            ctx.runOnClient(mc -> { cg.layout.setIndex(0); cg.style.setIndex(0); gui.setSearch(""); vape.row(ModuleManager.getModuleByName("KillAura")).setExpanded(false); mc.gui.setScreen(null); });
        }
    }

    private void testSubCategories(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
        var sc = com.OsamaClient.newbridge.UI.components.SubCategories.class;
        java.util.Set<String> names = new java.util.HashSet<>();
        for (var m : com.OsamaClient.newbridge.UI.components.ModuleManager.modules) names.add(m.name);
        var unknown = com.OsamaClient.newbridge.UI.components.SubCategories.unknownNames(names);
        log("subcategories unknown names: " + unknown);
        // names of modules that only exist in some builds (e.g. Macros/Profiles/ArrayList registered elsewhere) are tolerated
        try {
            ctx.runOnClient(mc -> { gui.setSearch(""); if (!gui.isOpen()) gui.open(); gui.panel(Module.Category.COMBAT).setCollapsed(false); });
            ctx.waitTicks(30);
            var heads = ctx.computeOnClient(mc -> gui.panel(Module.Category.COMBAT).groupHeaders());
            log("subcategories combat headers: " + heads);
            assertTrue(heads.contains("Melee") && heads.contains("Defense"), "no group headers: " + heads);
            float h0 = ctx.computeOnClient(mc -> gui.panel(Module.Category.COMBAT).lastHeight());
            ctx.takeScreenshot("newbridge_subcategories");
            ctx.runOnClient(mc -> gui.panel(Module.Category.COMBAT).toggleGroup("Melee"));
            ctx.waitTicks(40);
            float h1 = ctx.computeOnClient(mc -> gui.panel(Module.Category.COMBAT).lastHeight());
            log("subcategories melee folded: " + h0 + " -> " + h1);
            assertTrue(h1 < h0 - 20f, "folding Melee did not shrink the panel");
            ctx.takeScreenshot("newbridge_subcategories_folded");
            ctx.runOnClient(mc -> { gui.panel(Module.Category.COMBAT).toggleGroup("Melee"); gui.setSearch("aura"); });
            ctx.waitTicks(30);
            var flat = ctx.computeOnClient(mc -> gui.panel(Module.Category.COMBAT).groupHeaders());
            assertTrue(flat.isEmpty(), "search still shows group headers: " + flat);
            ctx.runOnClient(mc -> { gui.setSearch(""); com.OsamaClient.newbridge.UI.gui.ClickGuiModule.INSTANCE.subcategories.enabled = false; });
            ctx.waitTicks(30);
            var off = ctx.computeOnClient(mc -> gui.panel(Module.Category.COMBAT).groupHeaders());
            assertTrue(off.isEmpty(), "headers shown although Subcategories is off: " + off);
        } finally {
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.UI.gui.ClickGuiModule.INSTANCE.subcategories.enabled = true;
                gui.setSearch("");
                com.OsamaClient.newbridge.UI.components.SubCategories.setCollapsed(Module.Category.COMBAT, "Melee", false);
                mc.gui.setScreen(null);
            });
        }
    }

    private void testTags(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var T = com.OsamaClient.newbridge.UI.components.Tags.class;
        // every tagged name is a real module
        var missing = new java.util.ArrayList<String>();
        for (String n : com.OsamaClient.newbridge.UI.components.Tags.taggedNames()) if (ModuleManager.getModuleByName(n) == null && java.util.Arrays.stream(new String[]{"noslow", "packetcontrol"}).noneMatch(n::equals)) missing.add(n);
        assertTrue(missing.isEmpty(), "tags for unknown modules: " + missing);
        // the examples from the request
        assertTrue(com.OsamaClient.newbridge.UI.components.Tags.has(ModuleManager.getModuleByName("AimAssist"), "undetectable"), "AimAssist not #undetectable");
        assertTrue(com.OsamaClient.newbridge.UI.components.Tags.has(ModuleManager.getModuleByName("Scaffold"), "undetectable"), "Scaffold not #undetectable");
        assertTrue(com.OsamaClient.newbridge.UI.components.Tags.has(ModuleManager.getModuleByName("AutoBreed"), "mobs"), "AutoBreed not #mobs");
        assertTrue(com.OsamaClient.newbridge.UI.components.Tags.has(ModuleManager.getModuleByName("Flight"), "risky"), "Flight not #risky");
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.open());
        ctx.waitTicks(15);
        var g = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
        String[] queries = {"#undetectable", "#sword", "#crystalpvp", "#ohc", "#mobs", "#cry", "#sword #undetectable", "#mobs kill", "#nonsense", "sword"};
        int[] counts = new int[queries.length];
        for (int i = 0; i < queries.length; i++) {
            String q = queries[i];
            ctx.runOnClient(mc -> g.setSearch(q));
            ctx.waitTicks(3);
            counts[i] = ctx.computeOnClient(mc -> g.visibleMatches());
        }
        // expected counts straight from the module list
        int undet = ctx.computeOnClient(mc -> { int n = 0; for (Module m : ModuleManager.modules) if (com.OsamaClient.newbridge.UI.components.Tags.has(m, "undetectable")) n++; return n; });
        int both = ctx.computeOnClient(mc -> { int n = 0; for (Module m : ModuleManager.modules) if (com.OsamaClient.newbridge.UI.components.Tags.has(m, "sword") && com.OsamaClient.newbridge.UI.components.Tags.has(m, "undetectable")) n++; return n; });
        int mobsKill = ctx.computeOnClient(mc -> { int n = 0; for (Module m : ModuleManager.modules) if (com.OsamaClient.newbridge.UI.components.Tags.has(m, "mobs") && m.name.toLowerCase().contains("kill")) n++; return n; });
        log("tags: counts " + java.util.Arrays.toString(counts) + " undetectableModules=" + undet + " swordAndUndetectable=" + both + " mobs+kill=" + mobsKill);
        assertTrue(counts[0] == undet && undet > 8, "#undetectable shows " + counts[0] + ", expected " + undet);
        assertTrue(counts[1] > 5 && counts[2] > 3 && counts[3] >= 3 && counts[4] > 3, "tag searches empty: " + java.util.Arrays.toString(counts));
        assertTrue(counts[5] == counts[2], "#cry (prefix) should equal #crystalpvp: " + counts[5] + " vs " + counts[2]);
        assertTrue(counts[6] == both && both > 0, "AND of two tags: " + counts[6] + " expected " + both);
        assertTrue(counts[7] == mobsKill && mobsKill > 0, "#mobs + word: " + counts[7] + " expected " + mobsKill);
        assertTrue(counts[8] == 0, "#nonsense matched " + counts[8]);
        assertTrue(counts[9] > 0, "plain 'sword' should also find #sword modules");
        // suggestions while typing a tag and clicking one completes the search
        ctx.runOnClient(mc -> g.setSearch("#cr"));
        ctx.waitTicks(4);
        int chips = ctx.computeOnClient(mc -> g.tagChipCount());
        String chip0 = ctx.computeOnClient(mc -> g.tagChipCount() > 0 ? g.tagChip(0) : "");
        log("tags: suggestions for '#cr' = " + chips + " first=" + chip0);
        assertTrue(chips >= 1 && chip0.startsWith("cr"), "no tag suggestion for #cr");
        ctx.runOnClient(mc -> g.setSearch("#"));
        ctx.waitTicks(4);
        ctx.takeScreenshot("newbridge_tags_suggestions");
        ctx.runOnClient(mc -> g.setSearch("#undetectable"));
        ctx.waitTicks(6);
        ctx.takeScreenshot("newbridge_tags_search");
        // the detail card lists the tags
        var card = com.OsamaClient.newbridge.UI.gui.DetailCard.module(ModuleManager.getModuleByName("AimAssist"));
        boolean cardTags = card.lines().stream().anyMatch(l -> l.text().startsWith("Tags:") && l.text().contains("#undetectable") && l.text().contains("#sword"));
        assertTrue(cardTags, "detail card has no tag line");
        ctx.runOnClient(mc -> { g.setSearch(""); mc.gui.setScreen(null); });
    }

    private void testAutoBreed(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("kill @e[type=cow]");
        srv.runCommand("kill @e[type=pig]");
        srv.runCommand("difficulty peaceful");
        srv.runCommand("give @a wheat 6");
        srv.runCommand("give @a carrot 4");
        srv.runCommand("summon cow 1.5 " + Y + " 1.5 {NoAI:1b,Tags:[\"gt\"]}");
        srv.runCommand("summon cow 2.0 " + Y + " -1.5 {NoAI:1b,Tags:[\"gt\"]}");
        srv.runCommand("summon pig -1.5 " + Y + " 1.0 {NoAI:1b,Tags:[\"gt\"]}");
        ctx.waitTicks(10);
        var ab = com.OsamaClient.newbridge.Hacks.Misc.AutoBreed.INSTANCE;
        assertTrue(ab != null, "AutoBreed missing");
        try {
            // put the carrots in slot 1, wheat in 0: the module must find the wheat by itself
            ctx.runOnClient(mc -> { mc.player.getInventory().setSelectedSlot(5); ab.range = 3.0; ab.delay = 2; });
            on(ctx, "AutoBreed");
            ctx.waitTicks(60);
            int cowsInLove = srv.computeOnServer(s -> {
                int n = 0;
                for (var e : s.overworld().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.animal.cow.Cow.class), c -> true)) if (e.isInLove()) n++;
                return n;
            });
            int pigsInLove = srv.computeOnServer(s -> {
                int n = 0;
                for (var e : s.overworld().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.animal.pig.Pig.class), c -> true)) if (e.isInLove()) n++;
                return n;
            });
            int wheat = countItem(ctx, net.minecraft.world.item.Items.WHEAT);
            int carrot = countItem(ctx, net.minecraft.world.item.Items.CARROT);
            int slot = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
            int fed = ctx.computeOnClient(mc -> ab.fedCount);
            log("autobreed: cowsInLove=" + cowsInLove + " pigsInLove=" + pigsInLove + " wheat=" + wheat + " carrot=" + carrot + " slot=" + slot + " fed=" + fed);
            assertTrue(cowsInLove == 2, "cows in love: " + cowsInLove);
            assertTrue(wheat == 4, "wheat left " + wheat + " (expected 4: one per cow)");
            assertTrue(pigsInLove == 0 && carrot == 4, "lone pig must not be fed: pigs in love " + pigsInLove + ", carrots " + carrot);
            assertTrue(fed == 2, "fed count " + fed);
            assertTrue(slot == 5, "slot not restored: " + slot);
            // a second pig makes a pair: now the carrots are used
            srv.runCommand("summon pig -1.0 " + Y + " -1.0 {NoAI:1b,Tags:[\"gt\"]}");
            ctx.waitTicks(60);
            int pigs2 = srv.computeOnServer(s -> {
                int n = 0;
                for (var e : s.overworld().getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(net.minecraft.world.entity.animal.pig.Pig.class), c -> true)) if (e.isInLove()) n++;
                return n;
            });
            log("autobreed: pigs in love after pairing = " + pigs2);
            assertTrue(pigs2 == 2, "pig pair not fed: " + pigs2);
            assertTrue(countItem(ctx, net.minecraft.world.item.Items.WHEAT) == 4, "cows were fed twice");
        } finally {
            ctx.runOnClient(mc -> { var m = ModuleManager.getModuleByName("AutoBreed"); if (m.enabled) m.toggle(); });
            srv.runCommand("kill @e[tag=gt]");
            srv.runCommand("difficulty normal");
        }
    }

    private void testAntiVanish(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var av = com.OsamaClient.newbridge.Hacks.Misc.AntiVanish.INSTANCE;
        assertTrue(av != null, "AntiVanish missing");
        java.util.UUID bob = java.util.UUID.randomUUID(), steve = java.util.UUID.randomUUID(), alex = java.util.UUID.randomUUID();
        try {
            ctx.runOnClient(mc -> { av.tabProbe = true; av.leaveMessages = "Yes"; av.watchOnly = false; av.critical = 70; av.sensors = true; });
            on(ctx, "AntiVanish");
            ctx.waitTicks(130); // quiet period after joining; the first real probe goes out and is answered by the server

            // 1) a staff player drops out of tab silently; Steve leaves normally (message first), Alex too (message a bit later)
            ctx.runOnClient(mc -> {
                av.testAddTab(bob, "Admin_Bob", "[Admin] Admin_Bob");
                av.testAddTab(steve, "Steve", "Steve");
                av.testAddTab(alex, "Alex", "Alex");
            });
            ctx.waitTicks(40);
            ctx.runOnClient(mc -> {
                AntiVanishFeed.leaveMessage("Steve");
                AntiVanishFeed.remove(bob);
                AntiVanishFeed.remove(steve);
                AntiVanishFeed.remove(alex);
            });
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> AntiVanishFeed.leaveMessage("Alex"));
            ctx.waitTicks(50);
            double bobConf = ctx.computeOnClient(mc -> av.confidenceOf("Admin_Bob"));
            double steveConf = ctx.computeOnClient(mc -> av.confidenceOf("Steve"));
            double alexConf = ctx.computeOnClient(mc -> av.confidenceOf("Alex"));
            String alert = ctx.computeOnClient(mc -> av.lastAlert);
            log("antivanish: bob=" + bobConf + " steve=" + steveConf + " alex=" + alexConf + " alert=" + alert);
            assertTrue(bobConf >= 80, "silent staff loss not detected: " + bobConf);
            assertTrue(alert.contains("Admin_Bob"), "no alert for Admin_Bob: " + alert);
            assertTrue(steveConf < 0, "normal leave (message first) reported: " + steveConf);
            assertTrue(alexConf < 0, "normal leave (message late) reported: " + alexConf);

            // 2) he shows up in tab again
            ctx.runOnClient(mc -> av.testAddTab(bob, "Admin_Bob", "[Admin] Admin_Bob"));
            ctx.waitTicks(5);
            assertTrue(ctx.computeOnClient(mc -> av.confidenceOf("Admin_Bob")) < 0, "reappearing player still reported");

            // 3) block-dig packets of an entity we cannot see: ignored when we broke that block ourselves ...
            ctx.runOnClient(mc -> {
                net.minecraft.core.BlockPos ownPos = mc.player.blockPosition().offset(4, 0, 0);
                AntiVanishFeed.ownStartBreak(ownPos);
                AntiVanishFeed.dig(7777777, ownPos);
            });
            ctx.waitTicks(4);
            double own = ctx.computeOnClient(mc -> av.activityScore());
            assertTrue(own < 1, "own block breaking counted as vanish activity: " + own);
            // ... and counted when somebody else digs
            ctx.runOnClient(mc -> AntiVanishFeed.dig(7777778, mc.player.blockPosition().offset(-6, 0, 0)));
            ctx.waitTicks(4);
            double foreign = ctx.computeOnClient(mc -> av.activityScore());
            log("antivanish: own dig=" + own + " foreign dig=" + foreign);
            assertTrue(foreign >= 55, "unseen digger not detected: " + foreign);

            // 4) tab-completion probe: a name the server completes but tab does not list
            String self = ctx.computeOnClient(mc -> mc.player.getName().getString());
            ctx.runOnClient(mc -> AntiVanishFeed.probeAnswer(av, 41500, java.util.List.of("Ghost_Joe", "@a", self)));
            ctx.waitTicks(4);
            double first = ctx.computeOnClient(mc -> av.confidenceOf("Ghost_Joe"));
            ctx.runOnClient(mc -> AntiVanishFeed.probeAnswer(av, 41501, java.util.List.of("Ghost_Joe", self)));
            ctx.waitTicks(4);
            double second = ctx.computeOnClient(mc -> av.confidenceOf("Ghost_Joe"));
            log("antivanish: probe first=" + first + " second=" + second);
            assertTrue(first >= 60 && first < 90, "first probe hit: " + first);
            assertTrue(second >= 95, "second probe hit not confirmed: " + second);
            assertTrue(ctx.computeOnClient(mc -> av.confidenceOf(self)) < 0, "own name reported");

            // 5) a network-wide name list switches the probe off
            ctx.runOnClient(mc -> AntiVanishFeed.probeAnswer(av, 41502, java.util.List.of("Net_A", "Net_B", "Net_C", "Net_D", self)));
            ctx.waitTicks(4);
            assertTrue(ctx.computeOnClient(mc -> av.probeSwitchedOff()), "network-wide list did not switch the probe off");
            assertTrue(ctx.computeOnClient(mc -> av.alertCount) >= 2, "too few alerts");
        } finally {
            ctx.runOnClient(mc -> { var m = ModuleManager.getModuleByName("AntiVanish"); if (m.enabled) m.toggle(); });
        }
    }

    private static void writePng(Path p, int w, int h, java.util.function.IntBinaryOperator px) {
        try {
            var img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, px.applyAsInt(x, y));
            java.nio.file.Files.createDirectories(p.getParent());
            javax.imageio.ImageIO.write(img, "png", p.toFile());
        } catch (java.io.IOException e) { throw new AssertionError("png " + p + ": " + e); }
    }

    private boolean waitSky(ClientGameTestContext ctx, int ticks) {
        var cs = com.OsamaClient.newbridge.Hacks.World.sky.CustomSky.INSTANCE;
        for (int i = 0; i < ticks; i += 5) {
            if (ctx.computeOnClient(mc -> cs.ready() && cs.draws > 0)) return true;
            ctx.waitTicks(5);
        }
        return false;
    }

    private void testCustomSky(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        srv.runCommand("time set 6000");
        srv.runCommand("weather clear");
        var cs = com.OsamaClient.newbridge.Hacks.World.sky.CustomSky.INSTANCE;
        assertTrue(cs != null && cs.category == Module.Category.WORLD, "CustomSky missing / not in World");
        Path dir = com.OsamaClient.newbridge.Hacks.World.sky.CustomSky.folder();
        Path photo = dir.resolve("gt_photo.png"), pano = dir.resolve("gt_pano.png");
        // a "photo": sky gradient, sun, mountains
        writePng(photo, 640, 360, (x, y) -> {
            double ridge = 230 + 40 * Math.sin(x / 47.0) + 25 * Math.sin(x / 13.0);
            if (y > ridge) return 0x2E4A2A + ((y - (int) ridge) / 6 << 8);
            double d = Math.hypot(x - 470, y - 110);
            if (d < 34) return 0xFFF2B0;
            int r = 255 - y / 3, g = 140 + y / 4, b = 90 + y / 3;
            return (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
        });
        writePng(pano, 1024, 512, (x, y) -> {
            int band = (x / 64) % 2 == 0 ? 0x3050C0 : 0xC05030;
            return y < 256 ? band : 0x205020 + (y - 256) / 4;
        });
        try {
            ctx.runOnClient(mc -> {
                mc.player.setYRot(-90f); mc.player.setXRot(-20f);
                cs.projection = "Auto"; cs.sunAndMoon = true; cs.followDaylight = true; cs.opacity = 1; cs.brightness = 1;
                cs.setFile("gt_photo.png");
                if (!cs.enabled) cs.toggle();
            });
            boolean ok = waitSky(ctx, 200);
            log("customsky photo: " + ctx.computeOnClient(mc -> cs.status()) + " draws=" + cs.draws + " uploads=" + cs.uploads);
            assertTrue(ok, "photo sky not drawn: " + ctx.computeOnClient(mc -> cs.status()));
            ctx.waitTicks(10);
            ctx.takeScreenshot("newbridge_sky_photo");
            ctx.runOnClient(mc -> mc.player.setXRot(-75f));
            ctx.waitTicks(5);
            ctx.takeScreenshot("newbridge_sky_photo_up");
            ctx.runOnClient(mc -> mc.player.setXRot(-20f));

            // panorama (2:1 picks Panorama automatically)
            int up0 = cs.uploads;
            ctx.runOnClient(mc -> cs.setFile("gt_pano.png"));
            ctx.waitTicks(40);
            boolean pOk = waitSky(ctx, 100) && cs.uploads > up0;
            String pst = ctx.computeOnClient(mc -> cs.status());
            log("customsky panorama: " + pst);
            assertTrue(pOk && pst.contains("1024x512"), "panorama not used as 2:1 texture: " + pst);
            ctx.takeScreenshot("newbridge_sky_panorama");

            // next / previous go through the folder
            String before = cs.file;
            ctx.runOnClient(mc -> ((com.OsamaClient.newbridge.UI.components.MediaPicker) cs.fileBox).step(1));
            String after = cs.file;
            log("customsky next: " + before + " -> " + after);
            assertTrue(!after.equals(before) && !after.isEmpty(), "step(1) did not change the file");

            // dropping a file onto the window copies it into the folder
            Path outside = Path.of(System.getProperty("java.io.tmpdir"), "gt_dropped_sky.png");
            writePng(outside, 320, 180, (x, y) -> 0x8040C0 + x / 2);
            boolean used = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.World.sky.CustomSky.dropped(java.util.List.of(outside)));
            assertTrue(used && java.nio.file.Files.exists(dir.resolve("gt_dropped_sky.png")) && cs.file.equals("gt_dropped_sky.png"), "drop failed");

            // animated GIF (works without ffmpeg)
            Path gif = dir.resolve("gt_anim.gif");
            writeGif(gif);
            ctx.runOnClient(mc -> cs.setFile("gt_anim.gif"));
            waitSky(ctx, 200);
            int g0 = cs.uploads;
            ctx.waitTicks(40);
            int g1 = cs.uploads;
            log("customsky gif: " + ctx.computeOnClient(mc -> cs.status()) + " uploads " + g0 + " -> " + g1);
            assertTrue(g1 > g0 + 3, "gif frames do not change: " + g0 + " -> " + g1);

            // video through ffmpeg, when installed
            boolean ff = com.OsamaClient.newbridge.Hacks.World.sky.SkyMedia.Ffmpeg.available();
            log("customsky ffmpeg available: " + ff);
            if (ff) {
                Path mp4 = dir.resolve("gt_video.mp4");
                Process p = new ProcessBuilder(com.OsamaClient.newbridge.Hacks.World.sky.SkyMedia.Ffmpeg.exe(), "-y", "-loglevel", "error", "-f", "lavfi",
                        "-i", "testsrc=size=480x270:rate=24", "-t", "3", "-pix_fmt", "yuv420p", mp4.toString()).redirectErrorStream(true).start();
                try { p.getInputStream().readAllBytes(); p.waitFor(); } catch (Exception e) { throw new AssertionError(e); }
                ctx.runOnClient(mc -> cs.setFile("gt_video.mp4"));
                boolean vOk = waitSky(ctx, 300);
                int v0 = cs.uploads;
                ctx.waitTicks(40);
                int v1 = cs.uploads;
                log("customsky video: " + ctx.computeOnClient(mc -> cs.status()) + " uploads " + v0 + " -> " + v1);
                assertTrue(vOk && v1 > v0 + 10, "video frames do not play: " + v0 + " -> " + v1);
                ctx.takeScreenshot("newbridge_sky_video");
            }
            // off: vanilla sky again
            ctx.runOnClient(mc -> cs.toggle());
            int d0 = cs.draws;
            ctx.waitTicks(10);
            assertTrue(cs.draws == d0, "still drawing after disable");
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        } finally {
            ctx.runOnClient(mc -> { if (cs.enabled) cs.toggle(); cs.setFile(""); });
            for (String f : new String[]{"gt_photo.png", "gt_pano.png", "gt_dropped_sky.png", "gt_anim.gif", "gt_video.mp4"}) {
                try { java.nio.file.Files.deleteIfExists(dir.resolve(f)); } catch (java.io.IOException ignored) { }
            }
        }
    }

    private static void writeGif(Path p) throws java.io.IOException {
        var writer = javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
        try (var out = javax.imageio.ImageIO.createImageOutputStream(p.toFile())) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            for (int f = 0; f < 6; f++) {
                var img = new java.awt.image.BufferedImage(160, 90, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 90; y++) for (int x = 0; x < 160; x++) img.setRGB(x, y, ((x + f * 25) % 160 < 80) ? 0xE0A020 : 0x2040A0);
                var type = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(img);
                var meta = writer.getDefaultImageMetadata(type, null);
                var root = (javax.imageio.metadata.IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0");
                var gce = new javax.imageio.metadata.IIOMetadataNode("GraphicControlExtension");
                gce.setAttribute("disposalMethod", "none");
                gce.setAttribute("userInputFlag", "FALSE");
                gce.setAttribute("transparentColorFlag", "FALSE");
                gce.setAttribute("delayTime", "8");
                gce.setAttribute("transparentColorIndex", "0");
                root.appendChild(gce);
                meta.setFromTree("javax_imageio_gif_image_1.0", root);
                writer.writeToSequence(new javax.imageio.IIOImage(img, null, meta), null);
            }
            writer.endWriteSequence();
        }
    }

    private void testIcons(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        // every icon name draws (returns true) and an unknown name falls back
        boolean all = ctx.computeOnClient(mc -> {
            var r = new com.OsamaClient.newbridge.UI.gui.render.UiRenderer();
            var u = new com.OsamaClient.newbridge.UI.gui.render.Ui(r);
            r.begin();
            boolean ok = true;
            for (String n : com.OsamaClient.newbridge.UI.gui.render.Icons.NAMES) ok &= com.OsamaClient.newbridge.UI.gui.render.Icons.draw(u, n, 10, 10, 8, 0xFFFFFFFF);
            ok &= !com.OsamaClient.newbridge.UI.gui.render.Icons.draw(u, "nope", 10, 10, 8, 0xFFFFFFFF);
            r.flush();
            return ok;
        });
        assertTrue(all, "an icon name does not draw");
        boolean worldPanel = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.panel(Module.Category.WORLD) != null
                || true);
        ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.open());
        ctx.waitTicks(20);
        boolean hasWorld = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.panel(Module.Category.WORLD) != null);
        ctx.takeScreenshot("newbridge_icons_clickgui");
        ctx.runOnClient(mc -> mc.gui.setScreen(null));
        ctx.waitTicks(5);
        ctx.runOnClient(mc -> { var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance; if (!h.enabled) h.toggle(); h.showSpeed.enabled = true; h.showFps.enabled = true; h.showPing.enabled = true; h.showBiome.enabled = true; });
        srv.runCommand("effect give @p speed 30 1");
        ctx.waitTicks(15);
        ctx.takeScreenshot("newbridge_icons_hud");
        ctx.runOnClient(mc -> { var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance; h.showSpeed.enabled = false; h.showFps.enabled = false; h.showPing.enabled = false; h.showBiome.enabled = false; });
        srv.runCommand("effect clear @p");
        assertTrue(hasWorld && worldPanel, "no World panel in the ClickGUI");
    }

    private float[] musicCenter(ClientGameTestContext ctx, int i) {
        return ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.music.MusicOverlay.INSTANCE.buttonCenter(i));
    }

    private void clickFb(ClientGameTestContext ctx, float[] fb) {
        double k = ctx.computeOnClient(mc -> (double) mc.getWindow().getScreenWidth() / mc.getWindow().getWidth());
        ctx.getInput().setCursorPos(fb[0] * k, fb[1] * k);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        ctx.waitTicks(3);
    }

    private void testMusicOverlay(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var MB = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.class;
        var mo = com.OsamaClient.newbridge.Hacks.Visual.music.MusicOverlay.INSTANCE;
        assertTrue(mo != null, "MusicOverlay missing");
        try {
            // 1) the real PowerShell helper starts and reports (Spotify may or may not be playing)
            if (com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.windows()) {
                ctx.runOnClient(mc -> { mo.source = "Any Player"; if (!mo.enabled) mo.toggle(); });
                int waited = 0;
                while (com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.sinceLastLine() > 5000 && waited < 400) { ctx.waitTicks(10); waited += 10; }
                var st = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.state();
                String err = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.error();
                log("music real helper: after " + waited + " ticks line=" + com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.sinceLastLine()
                        + "ms err='" + err + "' track='" + st.title() + "' artist='" + st.artist() + "' app=" + st.player() + " status=" + st.status()
                        + " pos=" + st.position() + "/" + st.dur() + " art=" + st.art());
                assertTrue(com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.sinceLastLine() < 5000, "media helper did not report (err '" + err + "')");
                assertTrue(err.isEmpty(), "media helper error: " + err);
                if (st.hasTrack()) {
                    ctx.waitTicks(20);
                    Path real = ctx.takeScreenshot("newbridge_music_real");
                    log("music real screenshot " + real + " cover=" + ctx.computeOnClient(mc -> mo.coverLoaded()));
                }
                ctx.runOnClient(mc -> { if (mo.enabled) mo.toggle(); });
            }
            // 2) fake player: cover, layout, buttons with the real mouse in the chat
            Path png = Path.of(System.getProperty("java.io.tmpdir"), "newbridge-test-cover.png");
            try (var img = new com.mojang.blaze3d.platform.NativeImage(128, 128, false)) {
                for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                    int r = 40 + x, g = 30 + y / 2, b = 200 - y;
                    boolean ring = Math.abs(Math.hypot(x - 64, y - 64) - 34) < 6;
                    img.setPixel(x, y, ring ? 0xFFF0F0F0 : 0xFF000000 | (r << 16) | (g << 8) | b);
                }
                img.writeToFile(png);
            } catch (java.io.IOException e) { throw new AssertionError("cover png: " + e); }
            var fakeState = new com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.State("Spotify.exe",
                    "A Really Long Song Title That Has To Slide Sideways", "Some Artist", "Some Album", "Playing",
                    30, 200, System.currentTimeMillis(), true, true, png.toString(), 7);
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.setFake(fakeState);
                mo.source = "Spotify First";
                if (!mo.enabled) mo.toggle();
            });
            ctx.waitTicks(25);
            boolean cover = ctx.computeOnClient(mc -> mo.coverLoaded());
            float[] rect = ctx.computeOnClient(mc -> mo.rect());
            log("music fake: cover=" + cover + " rect=" + java.util.Arrays.toString(rect));
            assertTrue(cover, "cover image not loaded");
            assertTrue(rect != null && rect[2] > 100, "widget not drawn: " + java.util.Arrays.toString(rect));
            ctx.takeScreenshot("newbridge_music_hud");

            ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false)));
            ctx.waitTicks(10);
            float[] toggle = musicCenter(ctx, 1);
            assertTrue(toggle != null, "no play button");
            clickFb(ctx, toggle);
            String st1 = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.state().status();
            clickFb(ctx, musicCenter(ctx, 2));
            String title2 = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.state().title();
            clickFb(ctx, musicCenter(ctx, 0));
            String title3 = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.state().title();
            float[] bar = ctx.computeOnClient(mc -> {
                float[] c = mo.buttonCenter(3);
                return c;
            });
            clickFb(ctx, bar);
            double pos = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.state().position();
            double dur = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.state().dur();
            // wheel over the widget: +5 s
            double k = ctx.computeOnClient(mc -> (double) mc.getWindow().getScreenWidth() / mc.getWindow().getWidth());
            ctx.getInput().setCursorPos((rect[0] + rect[2] - 20) * k, (rect[1] + 6) * k);
            ctx.waitTicks(2);
            ctx.getInput().scroll(1);
            ctx.waitTicks(3);
            double pos2 = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.state().position();
            boolean chatStillOpen = ctx.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen);
            // hover hint screenshot
            ctx.getInput().setCursorPos(bar[0] * k, bar[1] * k);
            ctx.waitTicks(5);
            ctx.takeScreenshot("newbridge_music_chat");
            log("music clicks: status " + st1 + " next '" + title2 + "' prev '" + title3 + "' seek " + pos + "/" + dur + " wheel " + pos2 + " clicks=" + mo.clicks);
            assertTrue("Paused".equals(st1), "play/pause click did not pause: " + st1);
            assertTrue("Next Song".equals(title2), "next click: " + title2);
            assertTrue("Previous Song".equals(title3), "previous click: " + title3);
            assertTrue(Math.abs(pos - dur / 2) < dur * 0.08, "bar click should jump to the middle: " + pos + " of " + dur);
            assertTrue(pos2 > pos + 3, "wheel did not skip 5 s: " + pos + " -> " + pos2);
            assertTrue(chatStillOpen, "chat closed by a widget click");
            ctx.runOnClient(mc -> mc.gui.setScreen(null));
            // 3) commands
            ctx.runOnClient(mc -> mo.handleCommand(new String[]{".music", "next"}));
            String c1 = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.lastCommand;
            ctx.runOnClient(mc -> mo.handleCommand(new String[]{".music", "seek", "1:05"}));
            double seekTo = com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.state().position();
            log("music commands: " + c1 + " seek->" + seekTo);
            assertTrue("next".equals(c1), "command next sent " + c1);
            assertTrue(Math.abs(seekTo - 65) < 2, ".music seek 1:05 -> " + seekTo);
            // 4) idle: hidden when nothing plays
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.setFake(com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.NONE));
            ctx.waitTicks(40);
            float[] gone = ctx.computeOnClient(mc -> mo.rect());
            assertTrue(gone == null, "widget still shown while idle");
        } finally {
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() != null) mc.gui.setScreen(null);
                if (mo.enabled) mo.toggle();
                com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.setFake(null);
            });
        }
    }

    private void testHudWheelScale(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var HL = com.OsamaClient.newbridge.Hacks.Visual.HudLayout.class;
        var mo = com.OsamaClient.newbridge.Hacks.Visual.music.MusicOverlay.INSTANCE;
        on(ctx, "CheckMeter");
        ctx.runOnClient(mc -> {
            com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.setFake(new com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.State("Spotify.exe",
                    "Song", "Artist", "", "Playing", 10, 100, System.currentTimeMillis(), true, true, "", 0));
            if (!mo.enabled) mo.toggle();
            var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
            if (!h.enabled) h.toggle();
            h.editLayout.enabled = true;
            h.openEditor();
        });
        ctx.waitTicks(15);
        try {
            double k = ctx.computeOnClient(mc -> (double) mc.getWindow().getScreenWidth() / mc.getWindow().getWidth());
            java.util.Map<String, String> res = new java.util.LinkedHashMap<>();
            for (String id : new String[]{"music", "checkmeter"}) {
                float[] r0 = ctx.computeOnClient(mc -> {
                    for (var e : com.OsamaClient.newbridge.Hacks.Visual.HudLayout.visible()) if (e.id.equals(id)) return new float[]{e.x, e.y, e.w, e.h};
                    return null;
                });
                assertTrue(r0 != null, id + " not in the editor");
                ctx.getInput().setCursorPos((r0[0] + 5) * k, (r0[1] + 5) * k);
                ctx.waitTicks(2);
                StringBuilder steps = new StringBuilder();
                for (int i = 0; i < 4; i++) {
                    ctx.getInput().scroll(1);
                    ctx.waitTicks(3);
                    float stepScale = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.HudLayout.scale(id)); steps.append(stepScale).append(' ');
                    String under = ctx.computeOnClient(mc -> {
                        var e = com.OsamaClient.newbridge.Hacks.Visual.HudLayout.elementAt(com.OsamaClient.newbridge.UI.gui.input.UiInput.mouseFbX(), com.OsamaClient.newbridge.UI.gui.input.UiInput.mouseFbY());
                        return e == null ? "-" : e.id;
                    });
                    steps.append('[').append(under).append("] ");
                }
                log("wheel " + id + " at " + java.util.Arrays.toString(r0) + ": " + steps);
                ctx.waitTicks(3);
                float sc = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.HudLayout.scale(id));
                float[] r1 = ctx.computeOnClient(mc -> {
                    for (var e : com.OsamaClient.newbridge.Hacks.Visual.HudLayout.visible()) if (e.id.equals(id)) return new float[]{e.x, e.y, e.w, e.h};
                    return null;
                });
                res.put(id, sc + " w " + r0[2] + "->" + r1[2]);
                assertTrue(Math.abs(sc - 1.2f) < 0.01f, id + " scale after 4 notches: " + sc);
                assertTrue(r1[2] > r0[2] * 1.12f, id + " did not get bigger: " + r0[2] + " -> " + r1[2]);
            }
            // a HudOverlay box (coordinates): wheel down makes it smaller
            float[] cr = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance.widgetRect("coords"));
            assertTrue(cr != null, "coordinates box not drawn");
            float[] cpos = {cr[0] + 6, cr[1] + 6};
            ctx.getInput().setCursorPos(cpos[0] * k, cpos[1] * k);
            ctx.waitTicks(2);
            for (int i = 0; i < 3; i++) { ctx.getInput().scroll(-1); ctx.waitTicks(2); }
            ctx.waitTicks(5);
            float cs = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.HudLayout.scale("coords"));
            res.put("coords", String.valueOf(cs));
            ctx.takeScreenshot("newbridge_hud_wheel_scale");
            log("hud wheel scale: " + res);
            assertTrue(Math.abs(cs - 0.85f) < 0.01f, "coordinates box scale after 3 notches down: " + cs);
            // saved in the config map
            boolean saved = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance.widgetPositions().containsKey("scale.music")
                    && com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance.widgetPositions().containsKey("scale.coords"));
            assertTrue(saved, "scales not in the saved layout");
            // right-click resets size (and place)
            ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
            ctx.waitTicks(4);
            float cs2 = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.Hacks.Visual.HudLayout.scale("coords"));
            assertTrue(cs2 == 1f, "right-click did not reset the scale: " + cs2);
        } finally {
            ctx.runOnClient(mc -> {
                var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
                h.editLayout.enabled = false;
                h.closeEditor();
                for (String id : new String[]{"music", "checkmeter", "coords"}) { com.OsamaClient.newbridge.Hacks.Visual.HudLayout.resetScale(id); com.OsamaClient.newbridge.Hacks.Visual.HudLayout.reset(id); }
                if (mo.enabled) mo.toggle();
                com.OsamaClient.newbridge.Hacks.Visual.music.MediaBridge.setFake(null);
                var m = ModuleManager.getModuleByName("CheckMeter"); if (m.enabled) m.toggle();
            });
        }
    }

    private void testHudRealDrag(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        on(ctx, "CheckMeter"); on(ctx, "SplitChat"); on(ctx, "DamageIndicator");
        ctx.runOnClient(mc -> {
            var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
            if (!h.enabled) h.toggle();
            h.editLayout.enabled = true;
            h.openEditor();
        });
        ctx.waitTicks(15);
        try {
            java.util.List<String> ids = ctx.computeOnClient(mc -> {
                java.util.List<String> l = new java.util.ArrayList<>();
                for (var e : com.OsamaClient.newbridge.Hacks.Visual.HudLayout.visible()) l.add(e.id);
                return l;
            });
            log("hud editor elements: " + ids);
            assertTrue(ids.contains("checkmeter") && ids.contains("splitchat"), "new widgets missing in editor: " + ids);
            java.util.List<String> failed = new java.util.ArrayList<>();
            for (String id : ids) {
                float[] before = ctx.computeOnClient(mc -> {
                    for (var e : com.OsamaClient.newbridge.Hacks.Visual.HudLayout.visible()) if (e.id.equals(id)) return new float[]{e.x, e.y, e.w, e.h};
                    return null;
                });
                if (before == null) continue;
                double[] win = ctx.computeOnClient(mc -> {
                    double k = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getWidth();
                    return new double[]{k};
                });
                double sx = (before[0] + 4) * win[0], sy = (before[1] + 4) * win[0];
                ctx.getInput().setCursorPos(sx, sy);
                ctx.waitTicks(2);
                ctx.getInput().holdMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
                ctx.waitTicks(3);
                double dir = before[0] > 300 ? -1 : 1; // move towards the middle (elements are clamped at the screen edge)
                ctx.getInput().setCursorPos(sx + 60 * dir, sy + 40);
                ctx.waitTicks(3);
                ctx.getInput().setCursorPos(sx + 120 * dir, sy + 80);
                ctx.waitTicks(3);
                ctx.getInput().releaseMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
                ctx.waitTicks(3);
                float[] after = ctx.computeOnClient(mc -> {
                    for (var e : com.OsamaClient.newbridge.Hacks.Visual.HudLayout.visible()) if (e.id.equals(id)) return new float[]{e.x, e.y};
                    return null;
                });
                log("drag " + id + ": " + java.util.Arrays.toString(before) + " -> " + java.util.Arrays.toString(after));
                if (after == null || Math.abs(after[0] - before[0]) < 20 || Math.abs(after[1] - before[1]) < 10) failed.add(id);
            }
            assertTrue(failed.isEmpty(), "elements that did not move with the real mouse: " + failed);
        } finally {
            ctx.runOnClient(mc -> {
                var h = com.OsamaClient.newbridge.Hacks.Visual.HudOverlay.instance;
                h.editLayout.enabled = false;
                h.closeEditor();
                for (String id : new String[]{"checkmeter", "splitchat", "teammates", "modulelist", "minimap"}) com.OsamaClient.newbridge.Hacks.Visual.HudLayout.reset(id);
            });
            for (String n : new String[]{"CheckMeter", "SplitChat", "DamageIndicator"}) ctx.runOnClient(mc -> { var m = ModuleManager.getModuleByName(n); if (m.enabled) m.toggle(); });
        }
    }

    private void testTextTyping(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var nc = (com.OsamaClient.newbridge.Hacks.Misc.NameCensor) ModuleManager.getModuleByName("NameCensor");
        com.OsamaClient.newbridge.UI.components.TextBox tb = null;
        for (var c : nc.settings) if (c instanceof com.OsamaClient.newbridge.UI.components.TextBox t && t.getLabel().equals("Replacement")) tb = t;
        final var box = tb;
        String old = box.getText();
        try {
            ctx.runOnClient(mc -> {
                var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
                gui.open();
                gui.setSearch("");
                for (var cat : Module.Category.values()) {
                    var panel = gui.panel(cat);
                    if (panel == null) continue;
                    for (var row : panel.rows()) if (row.module() == nc) { panel.setCollapsed(false); row.setExpanded(true); panel.scrollToRow(row); }
                }
            });
            ctx.waitTicks(40);
            double[] pos = ctx.computeOnClient(mc -> {
                try {
                    var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
                    for (var cat : Module.Category.values()) {
                        var panel = gui.panel(cat);
                        if (panel == null) continue;
                        for (var row : panel.rows()) {
                            if (row.module() != nc) continue;
                            for (var w : row.widgets()) {
                                if (!(w instanceof com.OsamaClient.newbridge.UI.gui.setting.TextWidget tw) || tw.setting() != box) continue;
                                var cls = com.OsamaClient.newbridge.UI.gui.setting.SettingWidget.class;
                                var fx = cls.getDeclaredField("x"); fx.setAccessible(true);
                                var fy = cls.getDeclaredField("y"); fy.setAccessible(true);
                                var fw = cls.getDeclaredField("w"); fw.setAccessible(true);
                                float sc = gui.currentScale();
                                double k = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getWidth();
                                double px = (fx.getFloat(tw) + fw.getFloat(tw) * 0.8) * sc * k;
                                double py = (fy.getFloat(tw) + com.OsamaClient.newbridge.UI.gui.Theme.SETTING_H * 0.5) * sc * k;
                                return new double[]{px, py};
                            }
                        }
                    }
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                return null;
            });
            assertTrue(pos != null, "text widget not found");
            log("text box click at " + pos[0] + "," + pos[1]);
            log("text before screenshot: " + ctx.takeScreenshot("newbridge_text_before"));
            // typing with nothing focused must NOT start a search (it used to catch the char of a key you just bound)
            ctx.getInput().typeChars("k");
            ctx.waitTicks(2);
            assertTrue(ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.searchQuery().isEmpty()), "a typed letter opened the search");
            ctx.getInput().setCursorPos(pos[0], pos[1]);
            ctx.waitTicks(3);
            log("text widget hovered before click: " + ctx.computeOnClient(mc -> { for (var cat : Module.Category.values()) { var pn = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.panel(cat); if (pn == null) continue; for (var row : pn.rows()) for (var w : row.widgets()) if (w.setting() == box) return w.isHovered(); } return false; }));
            ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(3);
            ctx.getInput().typeChars("Zq7");
            ctx.waitTicks(3);
            ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
            ctx.waitTicks(3);
            log("typing: text now '" + box.getText() + "'");
            log("text typing screenshot: " + ctx.takeScreenshot("newbridge_text_typing"));
            assertTrue(com.OsamaClient.newbridge.Utils.TextInputGate.active(), "SDL text input not started while the ClickGUI is open (real keyboard would not type)");
            assertTrue(box.getText().endsWith("Zq7"), "typed text did not arrive, box = '" + box.getText() + "'");
        } finally {
            ctx.runOnClient(mc -> { box.setText(old); com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.close(); });
        }
    }

    private void testDetailCard(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        // every module has a long description, every setting has some description
        java.util.List<String> noDoc = new java.util.ArrayList<>(), noSetDesc = new java.util.ArrayList<>();
        for (Module m : ModuleManager.modules) {
            if (com.OsamaClient.newbridge.UI.gui.Docs.module(m.name) == null) noDoc.add(m.name);
            for (var c : m.settings) {
                String lbl = com.OsamaClient.newbridge.UI.gui.DetailCard.label(c);
                String d = c.getDescription();
                if ((d == null || d.isEmpty()) && com.OsamaClient.newbridge.UI.gui.Docs.setting(m.name, lbl) == null) noSetDesc.add(m.name + "/" + lbl);
            }
        }
        log("detail docs: modules without long text " + noDoc + ", settings without any description " + noSetDesc);
        assertTrue(noDoc.isEmpty(), "modules without long description: " + noDoc);
        assertTrue(noSetDesc.size() <= 3, "settings without description: " + noSetDesc);
        var ka = ModuleManager.getModuleByName("KillAura");
        com.OsamaClient.newbridge.UI.components.Slider range = null;
        for (var c : ka.settings) if (c instanceof com.OsamaClient.newbridge.UI.components.Slider sl && sl.getLabel().equals("Range")) range = sl;
        final var rangeS = range;
        try {
            ctx.runOnClient(mc -> {
                var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
                gui.open();
                gui.setSearch("");
                var panel = gui.panel(Module.Category.COMBAT);
                panel.setCollapsed(false);
                for (var row : panel.rows()) if (row.module() == ka) { row.setExpanded(true); panel.scrollToRow(row); }
            });
            ctx.waitTicks(30);
            double[] pos = ctx.computeOnClient(mc -> {
                try {
                    var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
                    var panel = gui.panel(Module.Category.COMBAT);
                    for (var row : panel.rows()) {
                        if (row.module() != ka) continue;
                        for (var w : row.widgets()) {
                            if (w.setting() != rangeS) continue;
                            var cls = com.OsamaClient.newbridge.UI.gui.setting.SettingWidget.class;
                            var fx = cls.getDeclaredField("x"); fx.setAccessible(true);
                            var fy = cls.getDeclaredField("y"); fy.setAccessible(true);
                            var fw = cls.getDeclaredField("w"); fw.setAccessible(true);
                            var first = row.widgets().get(0);
                            float sc = gui.currentScale();
                            double k = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getWidth();
                            float rowY = fy.getFloat(first) - com.OsamaClient.newbridge.UI.gui.Theme.rowH() * 0.5f;
                            return new double[]{(fx.getFloat(w) + fw.getFloat(w) * 0.3) * sc * k, (fy.getFloat(w) + com.OsamaClient.newbridge.UI.gui.Theme.SETTING_H * 0.5) * sc * k,
                                    (fx.getFloat(w) + fw.getFloat(w) * 0.3) * sc * k, rowY * sc * k};
                        }
                    }
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                return null;
            });
            assertTrue(pos != null, "KillAura Range widget not found");
            // without Alt: no card
            com.OsamaClient.newbridge.UI.gui.ClickGui.lastDetailTitle = "";
            ctx.getInput().setCursorPos(pos[2], pos[3]);
            ctx.waitTicks(5);
            assertTrue(com.OsamaClient.newbridge.UI.gui.ClickGui.lastDetailTitle.isEmpty(), "detail card without Alt: " + com.OsamaClient.newbridge.UI.gui.ClickGui.lastDetailTitle);
            // real Alt key held over the module row
            ctx.getInput().holdKey(com.mojang.blaze3d.platform.InputConstants.KEY_LALT);
            ctx.waitTicks(5);
            String title = com.OsamaClient.newbridge.UI.gui.ClickGui.lastDetailTitle;
            String body = String.join(" ", com.OsamaClient.newbridge.UI.gui.ClickGui.lastDetailLines);
            log("detail card (module): " + title + " | " + body);
            log("detail module screenshot: " + ctx.takeScreenshot("newbridge_detail_module"));
            assertTrue("KillAura".equals(title), "module card title " + title);
            assertTrue(body.contains("cooldown") && body.contains("Key:"), "module card body " + body);
            // over the Range slider
            ctx.getInput().setCursorPos(pos[0], pos[1]);
            ctx.waitTicks(5);
            title = com.OsamaClient.newbridge.UI.gui.ClickGui.lastDetailTitle;
            body = String.join(" ", com.OsamaClient.newbridge.UI.gui.ClickGui.lastDetailLines);
            log("detail card (setting): " + title + " | " + body);
            log("detail setting screenshot: " + ctx.takeScreenshot("newbridge_detail_setting"));
            ctx.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LALT);
            assertTrue(title.endsWith("Range") && body.contains("Number") && body.contains("default"), "setting card " + title + " | " + body);
            // pure builders: a mode and a toggle
            var cardFake = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.DetailCard.module(ModuleManager.getModuleByName("AirPlace")));
            String fake = cardFake.lines().stream().map(com.OsamaClient.newbridge.UI.gui.DetailCard.Line::text).reduce("", (a, b) -> a + " " + b);
            assertTrue(fake.contains("Legit Mode"), "AirPlace card should mention Legit Mode: " + fake);
            // hold binds: right click on the bind widget switches Toggle/Hold
            var zoom = ModuleManager.getModuleByName("Zoom");
            boolean before = zoom.holdToActivate;
            ctx.runOnClient(mc -> {
                var bw = new com.OsamaClient.newbridge.UI.gui.setting.BindWidget(zoom, () -> false, () -> {});
                bw.mouseClicked(null, com.OsamaClient.newbridge.UI.gui.input.Keys.MOUSE_BUTTON_RIGHT, 0);
            });
            assertTrue(zoom.holdToActivate != before, "right click did not switch hold mode");
            ctx.runOnClient(mc -> zoom.holdToActivate = before);
        } finally {
            ctx.getInput().releaseKey(com.mojang.blaze3d.platform.InputConstants.KEY_LALT);
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.close());
            ctx.waitTicks(10);
        }
    }

    private void testResetUndoShare(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var H = com.OsamaClient.newbridge.UI.components.SettingHistory.class;
        var ka = ModuleManager.getModuleByName("KillAura");
        com.OsamaClient.newbridge.UI.components.Slider rangeS = null, cpsS = null;
        com.OsamaClient.newbridge.UI.components.ToggleButton rotS = null;
        for (var c : ka.settings) {
            if (c instanceof com.OsamaClient.newbridge.UI.components.Slider sl && sl.getLabel().equals("Range")) rangeS = sl;
            if (c instanceof com.OsamaClient.newbridge.UI.components.Slider sl && sl.getLabel().equals("CPS")) cpsS = sl;
            if (c instanceof com.OsamaClient.newbridge.UI.components.ToggleButton tb && tb.getLabel().equals("Rotations")) rotS = tb;
        }
        final var range = rangeS; final var cps = cpsS; final var rot = rotS;
        double r0 = range.getValue(), c0 = cps.getValue(); boolean t0 = rot.enabled;
        java.nio.file.Path oldBackup = com.OsamaClient.newbridge.config.Config.testBackupDir;
        try {
            com.OsamaClient.newbridge.config.Config.testBackupDir = java.nio.file.Files.createTempDirectory("nb-share");
            // --- API level: reset / reset all / undo
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.UI.components.SettingHistory.clear();
                cps.setValue(cps.getDefaultValue() + 3);
                assertTrue(com.OsamaClient.newbridge.UI.components.SettingHistory.reset(cps), "reset returned false");
                assertTrue(Math.abs(cps.getValue() - cps.getDefaultValue()) < 1e-9, "CPS not default after reset");
                String u = com.OsamaClient.newbridge.UI.components.SettingHistory.undo();
                assertTrue("CPS".equals(u) && Math.abs(cps.getValue() - (cps.getDefaultValue() + 3)) < 1e-9, "undo of reset: " + u + " cps=" + cps.getValue());
                rot.setValue(!rot.defaultValue);
                int n = com.OsamaClient.newbridge.UI.components.SettingHistory.resetAll(ka);
                assertTrue(n >= 2 && rot.enabled == rot.defaultValue && Math.abs(cps.getValue() - cps.getDefaultValue()) < 1e-9, "resetAll changed " + n);
                u = com.OsamaClient.newbridge.UI.components.SettingHistory.undo();
                assertTrue(u != null && rot.enabled != rot.defaultValue && Math.abs(cps.getValue() - (cps.getDefaultValue() + 3)) < 1e-9, "undo of resetAll: " + u);
                // search finds modules by setting name
                var panel = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.panel(Module.Category.COMBAT);
                assertTrue(panel.matchCount("cps") >= 2, "search 'cps' found " + panel.matchCount("cps"));
            });
            // --- real input: MMB on the Range slider resets it, Ctrl+Z brings it back
            ctx.runOnClient(mc -> {
                range.setValue(range.getDefaultValue() == 5.0 ? 4.0 : 5.0);
                var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
                gui.open();
                gui.setSearch("");
                var panel = gui.panel(Module.Category.COMBAT);
                panel.setCollapsed(false);
                for (var row : panel.rows()) if (row.module() == ka) { row.setExpanded(true); panel.scrollToRow(row); }
            });
            double changedRange = ctx.computeOnClient(mc -> range.getValue());
            ctx.waitTicks(30);
            double[] pos = ctx.computeOnClient(mc -> {
                try {
                    var gui = com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE;
                    for (var row : gui.panel(Module.Category.COMBAT).rows()) {
                        if (row.module() != ka) continue;
                        for (var w : row.widgets()) {
                            if (w.setting() != range) continue;
                            var cls = com.OsamaClient.newbridge.UI.gui.setting.SettingWidget.class;
                            var fx = cls.getDeclaredField("x"); fx.setAccessible(true);
                            var fy = cls.getDeclaredField("y"); fy.setAccessible(true);
                            var fw = cls.getDeclaredField("w"); fw.setAccessible(true);
                            float sc = gui.currentScale();
                            double k = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getWidth();
                            return new double[]{(fx.getFloat(w) + fw.getFloat(w) * 0.3) * sc * k, (fy.getFloat(w) + com.OsamaClient.newbridge.UI.gui.Theme.SETTING_H * 0.5) * sc * k};
                        }
                    }
                } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
                return null;
            });
            assertTrue(pos != null, "Range widget not found");
            ctx.getInput().setCursorPos(pos[0], pos[1]);
            ctx.waitTicks(4);
            ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE);
            ctx.waitTicks(4);
            double afterReset = ctx.computeOnClient(mc -> range.getValue());
            log("mmb reset: " + changedRange + " -> " + afterReset + " toast '" + com.OsamaClient.newbridge.UI.gui.ClickGui.lastToast + "'");
            log("reset toast screenshot: " + ctx.takeScreenshot("newbridge_reset_toast"));
            assertTrue(Math.abs(afterReset - range.getDefaultValue()) < 1e-9, "MMB did not reset Range: " + afterReset);
            ctx.getInput().setCursorPos(5, 5);
            ctx.waitTicks(2);
            ctx.getInput().holdControl();
            ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_Z);
            ctx.getInput().releaseControl();
            ctx.waitTicks(4);
            double afterUndo = ctx.computeOnClient(mc -> range.getValue());
            log("ctrl+z: " + afterUndo + " toast '" + com.OsamaClient.newbridge.UI.gui.ClickGui.lastToast + "'");
            assertTrue(Math.abs(afterUndo - changedRange) < 1e-9, "Ctrl+Z did not restore Range: " + afterUndo);
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.close());
            ctx.waitTicks(10);
            // --- share: export, change, import
            String exported = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.config.Config.exportString());
            assertTrue(exported != null && exported.startsWith(com.OsamaClient.newbridge.config.Config.SHARE_PREFIX), "export " + exported);
            ctx.runOnClient(mc -> { range.setValue(range.getMin()); cps.setValue(cps.getMin()); });
            boolean ok = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.config.Config.importString(exported));
            assertTrue(ok, "import failed");
            assertTrue(Math.abs(range.getValue() - changedRange) < 1e-6 && Math.abs(cps.getValue() - (cps.getDefaultValue() + 3)) < 1e-6,
                    "import did not restore values: range " + range.getValue() + " cps " + cps.getValue());
            assertTrue(!ctx.computeOnClient(mc -> com.OsamaClient.newbridge.config.Config.importString("hello")), "garbage import accepted");
            // chat command copies to the clipboard
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.Utils.ChatHandler.handleConfig(new String[]{".config", "export"}));
            String clip = ctx.computeOnClient(mc -> com.OsamaClient.newbridge.UI.gui.input.UiInput.clipboard());
            log("config export: " + exported.length() + " chars, clipboard ok=" + clip.startsWith(com.OsamaClient.newbridge.config.Config.SHARE_PREFIX));
            assertTrue(clip.startsWith(com.OsamaClient.newbridge.config.Config.SHARE_PREFIX), ".config export did not fill the clipboard");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            ctx.getInput().releaseControl();
            com.OsamaClient.newbridge.config.Config.testBackupDir = oldBackup;
            ctx.runOnClient(mc -> {
                range.setValue(r0); cps.setValue(c0); rot.setValue(t0);
                if (ka.enabled) ka.toggle();
                com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.close();
            });
        }
    }

    private void testFriendsTab(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var F = com.OsamaClient.newbridge.Hacks.Client.Friends.class;
        var fr = com.OsamaClient.newbridge.Hacks.Client.Friends.INSTANCE;
        java.nio.file.Path oldFile = com.OsamaClient.newbridge.Hacks.Client.Friends.testFile;
        try {
            com.OsamaClient.newbridge.Hacks.Client.Friends.testFile = java.nio.file.Files.createTempDirectory("nb-friends").resolve("friends.json");
            String me = ctx.computeOnClient(mc -> mc.player.getGameProfile().name());
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.Hacks.Client.Friends.load();
                if (!fr.enabled) fr.toggle();
                fr.joinAlerts = true;
                assertTrue(com.OsamaClient.newbridge.Hacks.Client.Friends.add("OfflineAlice"), "add Alice");
                assertTrue(!com.OsamaClient.newbridge.Hacks.Client.Friends.add("offlinealice"), "duplicate (case) accepted");
                com.OsamaClient.newbridge.Utils.ChatHandler.handleFriend(new String[]{".friend", "add", me});
                com.OsamaClient.newbridge.Utils.ChatHandler.handleFriend(new String[]{".friend", "add", "BobTemp"});
                com.OsamaClient.newbridge.Utils.ChatHandler.handleFriend(new String[]{".friend", "del", "BobTemp"});
                fr.setLastOnline(new java.util.HashSet<>()); // pretend nobody was online -> me "joins"
            });
            assertTrue(com.OsamaClient.newbridge.Hacks.Client.Friends.friends.size() == 2, "friends " + com.OsamaClient.newbridge.Hacks.Client.Friends.friends);
            ctx.waitTicks(25);
            log("friends alert: " + fr.lastAlert);
            assertTrue(fr.lastAlert != null && fr.lastAlert.contains(me) && fr.lastAlert.contains("joined"), "join alert " + fr.lastAlert);
            var info = com.OsamaClient.newbridge.Hacks.Client.Friends.infoOf(me);
            assertTrue(info.lastSeen > 0 && !info.lastServer.isEmpty(), "last seen not tracked: " + info.lastSeen + " " + info.lastServer);
            // saved and loaded again
            ctx.runOnClient(mc -> { com.OsamaClient.newbridge.Hacks.Client.Friends.save(); com.OsamaClient.newbridge.Hacks.Client.Friends.load(); });
            assertTrue(com.OsamaClient.newbridge.Hacks.Client.Friends.isFriendName("offlinealice") && com.OsamaClient.newbridge.Hacks.Client.Friends.isFriendName(me)
                    && com.OsamaClient.newbridge.Hacks.Client.Friends.infoOf(me).lastSeen > 0, "reload lost friends: " + com.OsamaClient.newbridge.Hacks.Client.Friends.friends);
            // the tab
            ctx.runOnClient(mc -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(new com.OsamaClient.newbridge.Hacks.Client.FriendsWindow()));
            ctx.waitTicks(20);
            var rows = com.OsamaClient.newbridge.Hacks.Client.FriendsWindow.lastRows;
            var states = com.OsamaClient.newbridge.Hacks.Client.FriendsWindow.lastStates;
            log("friends tab rows " + rows + " states " + states);
            log("friends screenshot: " + ctx.takeScreenshot("newbridge_friends_tab"));
            assertTrue(rows.size() == 2 && rows.get(0).equals(me) && states.get(0).equals("ONLINE") && states.get(1).equals("OFFLINE"), "rows " + rows + " " + states);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        } finally {
            ctx.runOnClient(mc -> {
                com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.close();
                com.OsamaClient.newbridge.Hacks.Client.Friends.testFile = oldFile;
                com.OsamaClient.newbridge.Hacks.Client.Friends.load();
            });
        }
    }

    private void testQuickMenu(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var qm = com.OsamaClient.newbridge.Hacks.Client.quick.QuickMenu.INSTANCE;
        var fb = ModuleManager.getModuleByName("FullBright");
        var zoom = ModuleManager.getModuleByName("Zoom");
        java.util.List<Module> oldFav = new java.util.ArrayList<>();
        for (Module m : ModuleManager.modules) if (m.favorite) oldFav.add(m);
        boolean oldHold = qm.holdToActivate;
        try {
            ctx.runOnClient(mc -> {
                for (Module m : ModuleManager.modules) m.favorite = false;
                fb.favorite = true;
                zoom.favorite = true;
                if (fb.enabled) fb.toggle();
                if (zoom.enabled) zoom.toggle();
                qm.source = "Favorites";
            });
            var entries = ctx.computeOnClient(mc -> qm.collect().stream().map(m -> m.name).toList());
            assertTrue(entries.size() == 2 && entries.contains("FullBright") && entries.contains("Zoom") && !entries.contains("QuickMenu"), "entries " + entries);
            String first = entries.get(0);
            Module firstM = ModuleManager.getModuleByName(first);
            // hold mode: key down opens the wheel, pointing up selects entry 0, key up switches it
            ctx.runOnClient(mc -> { qm.holdToActivate = true; qm.toggle(); });
            ctx.waitTicks(4);
            assertTrue(ctx.computeOnClient(mc -> mc.gui.screen() instanceof com.OsamaClient.newbridge.Hacks.Client.quick.QuickMenuScreen), "wheel screen not open");
            double[] c = ctx.computeOnClient(mc -> new double[]{mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0});
            ctx.getInput().setCursorPos(c[0], c[1] - 70);
            ctx.waitTicks(4);
            int hov = ctx.computeOnClient(mc -> qm.hovered());
            log("quickmenu screenshot: " + ctx.takeScreenshot("newbridge_quickmenu"));
            assertTrue(hov == 0, "pointing up should hover entry 0, got " + hov);
            ctx.runOnClient(mc -> qm.toggle()); // key released
            ctx.waitTicks(3);
            assertTrue(ctx.computeOnClient(mc -> firstM.enabled) && first.equals(qm.lastPicked), "release did not switch " + first + " (picked " + qm.lastPicked + ")");
            assertTrue(ctx.computeOnClient(mc -> mc.gui.screen() == null), "wheel still open after release");
            // releasing in the middle (dead zone) does nothing
            ctx.runOnClient(mc -> qm.toggle());
            ctx.waitTicks(3);
            ctx.getInput().setCursorPos(c[0], c[1]);
            ctx.waitTicks(3);
            qm.lastPicked = null;
            ctx.runOnClient(mc -> qm.toggle());
            ctx.waitTicks(3);
            assertTrue(qm.lastPicked == null && ctx.computeOnClient(mc -> firstM.enabled), "dead zone release picked " + qm.lastPicked);
            // toggle mode: real left click on the entry at the bottom (entry 1 of 2)
            ctx.runOnClient(mc -> { qm.holdToActivate = false; qm.toggle(); });
            ctx.waitTicks(3);
            ctx.getInput().setCursorPos(c[0], c[1] + 70);
            ctx.waitTicks(4);
            ctx.getInput().pressMouse(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
            ctx.waitTicks(4);
            Module second = ModuleManager.getModuleByName(entries.get(1));
            assertTrue(ctx.computeOnClient(mc -> second.enabled) && entries.get(1).equals(qm.lastPicked), "click did not switch " + entries.get(1));
            assertTrue(ctx.computeOnClient(mc -> mc.gui.screen() == null && !qm.enabled), "wheel did not close after the click");
            // opening it from inside another screen is refused
            ctx.runOnClient(mc -> { com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.open(); });
            ctx.waitTicks(3);
            ctx.runOnClient(mc -> qm.toggle());
            assertTrue(!ctx.computeOnClient(mc -> qm.enabled), "quick menu opened on top of the ClickGUI");
        } finally {
            ctx.runOnClient(mc -> {
                if (qm.enabled) qm.toggle();
                com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.close();
                qm.holdToActivate = oldHold;
                qm.source = "Favorites + Recent";
                for (Module m : ModuleManager.modules) m.favorite = oldFav.contains(m);
                if (fb.enabled) fb.toggle();
                if (zoom.enabled) zoom.toggle();
            });
            ctx.waitTicks(5);
        }
    }

    private void testNametagArmor(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var nt = com.OsamaClient.newbridge.Hacks.Visual.Nametags.instance;
        boolean wasOn = nt.enabled;
        Boolean oldHost = nt.targetPicker.filters.get("Hostiles");
        try {
            srv.runCommand("difficulty easy");
            ctx.waitTicks(10);
            double[] pp = ctx.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
            srv.runCommand(String.format(java.util.Locale.ROOT, "summon zombie %.2f %.2f %.2f {NoGravity:1b,NoAI:1b,", pp[0] + 4, pp[1], pp[2]) + (
                    "Silent:1b,PersistenceRequired:1b,Tags:[\"gt\"],equipment:{head:{id:\"minecraft:diamond_helmet\",components:{\"minecraft:damage\":150}},chest:{id:\"minecraft:iron_chestplate\"},feet:{id:\"minecraft:golden_boots\",components:{\"minecraft:damage\":80}},mainhand:{id:\"minecraft:diamond_sword\"}}}"));
            srv.runCommand("execute as @p at @s run tp @s ~ ~ ~ facing entity @e[tag=gt,limit=1] eyes");
            ctx.runOnClient(mc -> {
                nt.targetPicker.filters.put("Hostiles", true);
                if (!nt.enabled) nt.toggle();
                nt.armorIcons.setValue(true);
                nt.heldItem.setValue(true);
                nt.durabilityText.setValue(true);
            });
            ctx.waitTicks(20);
            var icons = new java.util.HashMap<>(com.OsamaClient.newbridge.Hacks.Visual.Nametags.lastIcons);
            log("nametag icons: " + icons);
            log("nametag armor screenshot: " + ctx.takeScreenshot("newbridge_nametag_armor"));
            var z = icons.get("Zombie");
            assertTrue(z != null, "no icons for the zombie: " + icons);
            String all = String.join(",", z);
            assertTrue(z.size() == 4 && z.get(0).startsWith("diamond_helmet@") && z.get(1).startsWith("iron_chestplate@100") && z.get(2).startsWith("golden_boots@") && z.get(3).startsWith("diamond_sword"),
                    "icon order / items: " + all);
            int helm = Integer.parseInt(z.get(0).substring(z.get(0).indexOf('@') + 1));
            assertTrue(helm > 30 && helm < 100, "helmet durability " + helm);
            // settings: no held item, then no icons at all
            ctx.runOnClient(mc -> nt.heldItem.setValue(false));
            ctx.waitTicks(4);
            assertTrue(com.OsamaClient.newbridge.Hacks.Visual.Nametags.lastIcons.getOrDefault("Zombie", java.util.List.of()).size() == 3, "held item still shown");
            ctx.runOnClient(mc -> nt.armorIcons.setValue(false));
            ctx.waitTicks(4);
            assertTrue(!com.OsamaClient.newbridge.Hacks.Visual.Nametags.lastIcons.containsKey("Zombie"), "icons still shown with Armor Icons off");
        } finally {
            srv.runCommand("kill @e[tag=gt]");
            ctx.runOnClient(mc -> {
                nt.armorIcons.setValue(true);
                nt.heldItem.setValue(true);
                nt.durabilityText.setValue(false);
                if (oldHost == null) nt.targetPicker.filters.remove("Hostiles"); else nt.targetPicker.filters.put("Hostiles", oldHost);
                if (nt.enabled != wasOn) nt.toggle();
            });
        }
    }

    private void testMergedModules(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        var it = com.OsamaClient.newbridge.Hacks.Player.InvTweaks.INSTANCE;
        assertTrue(ModuleManager.getModuleByName("ChestStealer") == null && ModuleManager.getModuleByName("NoHunger") == null, "merged modules still registered");
        on(ctx, "InvTweaks");
        int cx = ox + 4;
        fill(srv, 4, Y, -1, 4, Y, 1, "air");
        srv.runCommand("setblock " + cx + " " + Y + " " + oz + " minecraft:chest");
        String[] chest = {"diamond 3", "gold_ingot 40", "coal 64", "diamond 2"};
        for (int i = 0; i < chest.length; i++) srv.runCommand("item replace block " + cx + " " + Y + " " + oz + " container." + i + " with minecraft:" + chest[i]);
        srv.runCommand("clear @a");
        ctx.waitTicks(5);
        try {
            ctx.runOnClient(mc -> {
                it.delay = 0;
                it.autoSteal = true; it.autoClose = true; it.onlyFiltered = true; it.randomDelay = true;
                it.stealFilter.selectedItems.clear();
                it.stealFilter.selectedItems.put(net.minecraft.world.item.Items.DIAMOND, 0xFFFFFFFF);
            });
            srv.runOnServer(s -> {
                var sp = s.getPlayerList().getPlayers().get(0);
                var be = sp.level().getBlockEntity(new net.minecraft.core.BlockPos(cx, Y, oz));
                if (be instanceof net.minecraft.world.MenuProvider mp) sp.openMenu(mp);
            });
            ctx.waitTicks(40);
            int diamonds = countItem(ctx, net.minecraft.world.item.Items.DIAMOND), gold = countItem(ctx, net.minecraft.world.item.Items.GOLD_INGOT);
            boolean closed = ctx.computeOnClient(mc -> mc.gui.screen() == null);
            log("merged steal: diamonds=" + diamonds + " gold=" + gold + " closed=" + closed);
            assertTrue(diamonds == 5 && gold == 0, "filtered steal took diamonds=" + diamonds + " gold=" + gold);
            assertTrue(closed, "Auto Close did not close the chest");
            // config: an old config with NoHunger / ChestStealer enabled turns on the module it was merged into
            var ah = ModuleManager.getModuleByName("AntiHunger");
            ctx.runOnClient(mc -> {
                if (ah.enabled) ah.toggle();
                var root = com.google.gson.JsonParser.parseString("{\"modules\":{\"NoHunger\":{\"enabled\":true,\"key\":-1}}}").getAsJsonObject();
                com.OsamaClient.newbridge.config.ConfigCodec.decode(new com.OsamaClient.newbridge.config.ConfigCodec.Data(root), java.util.List.of(ah),
                        new java.util.HashMap<>(), new java.util.HashMap<>(), null, m -> m.toggle());
            });
            assertTrue(ctx.computeOnClient(mc -> ah.enabled), "NoHunger alias did not enable AntiHunger");
            ctx.runOnClient(mc -> ah.toggle());
        } finally {
            ctx.runOnClient(mc -> {
                it.autoSteal = false; it.autoClose = false; it.onlyFiltered = false; it.randomDelay = false;
                it.stealFilter.selectedItems.clear();
                if (mc.gui.screen() != null) mc.player.closeContainer();
            });
            srv.runCommand("clear @a");
        }
    }

    private void testComboBot(ClientGameTestContext ctx, TestServerContext srv) {
        flatStage(ctx, srv);
        fill(srv, -12, Y - 1, -12, 18, Y - 1, 12, "stone");   // wide arena: knockback must not push the zombie into the void
        var cb = com.OsamaClient.newbridge.Hacks.Combat.SwordBot.INSTANCE;
        srv.runCommand("difficulty easy");
        srv.runCommand("clear @a");
        srv.runCommand("item replace entity @a hotbar.0 with minecraft:dirt 8");
        srv.runCommand("item replace entity @a hotbar.3 with minecraft:wooden_sword");
        srv.runCommand("item replace entity @a hotbar.5 with minecraft:diamond_sword");
        ctx.waitTicks(5);
        double[] pp = ctx.computeOnClient(mc -> new double[]{mc.player.getX(), mc.player.getY(), mc.player.getZ()});
        srv.runCommand(String.format(java.util.Locale.ROOT, "summon zombie %.2f %.2f %.2f {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:[\"gt\"],Health:60f,attributes:[{id:\"minecraft:max_health\",base:60}]}", pp[0] + 5, pp[1], pp[2]));
        ctx.runOnClient(mc -> {
            mc.player.getInventory().setSelectedSlot(0);
            cb.targets.filters.put("Players", false);
            cb.targets.filters.put("Hostiles", true);
            cb.critMode = "Jump"; cb.resetMode = "W-Tap"; cb.strafeMode = "Circle"; cb.autoWeapon = true; cb.retreatHealth = 0;
            if (!cb.enabled) cb.toggle();
        });
        try {
            ctx.waitTicks(120);
            int hits = cb.hits, crits = cb.critHits, wtaps = cb.wtaps;
            int slot = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
            float zhp = ctx.computeOnClient(mc -> {
                for (var e : mc.level.entitiesForRendering()) if (e instanceof net.minecraft.world.entity.monster.zombie.Zombie z) return z.getHealth();
                return -1f;
            });
            log("combobot: target=" + cb.targetName + " hits=" + hits + " crits=" + crits + " wtaps=" + wtaps + " slot=" + slot + " zombieHp=" + zhp);
            assertTrue(hits >= 2, "ComboBot hit only " + hits + " times");
            assertTrue(crits >= 1, "no critical hit from a jump");
            assertTrue(wtaps >= 1, "no sprint reset after hits");
            assertTrue(slot == 5, "did not switch to the diamond sword, slot " + slot);
            assertTrue(zhp < 60, "zombie took no damage");
            // retreat: at or below 10 hearts (we have 10) the bot backs off and stops hitting
            ctx.runOnClient(mc -> cb.retreatHealth = 10);
            ctx.waitTicks(5);
            int before = cb.hits;
            boolean back = ctx.computeOnClient(mc -> mc.options.keyDown.isDown());
            ctx.waitTicks(40);
            assertTrue(cb.hits == before && back, "retreat: hits " + before + " -> " + cb.hits + ", back key " + back);
            // disabling releases all keys and gives the old slot back
            ctx.runOnClient(mc -> cb.toggle());
            boolean keys = ctx.computeOnClient(mc -> mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown() || mc.options.keyJump.isDown());
            int after = ctx.computeOnClient(mc -> mc.player.getInventory().getSelectedSlot());
            assertTrue(!keys && after == 0, "after disable: keys held=" + keys + " slot=" + after);
        } finally {
            ctx.runOnClient(mc -> {
                if (cb.enabled) cb.toggle();
                cb.targets.filters.put("Players", true);
                cb.targets.filters.put("Hostiles", false);
                cb.retreatHealth = 0;
            });
            srv.runCommand("kill @e[tag=gt]");
            srv.runCommand("clear @a");
        }
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
        assertTrue(typed.isEmpty(), "a typed letter must not start a search any more (only click / Ctrl+F), gave '" + typed + "'");
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
