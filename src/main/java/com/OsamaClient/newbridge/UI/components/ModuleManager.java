package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.Hacks.Combat.*;
import com.OsamaClient.newbridge.Hacks.Misc.*;
import com.OsamaClient.newbridge.Hacks.Movement.*;
import com.OsamaClient.newbridge.Hacks.Donut.*;
//import com.OsamaClient.newbridge.Hacks.Visual.ESP.BlockESP;
//import com.OsamaClient.newbridge.Hacks.Visual.ESP.PlayerESP;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteModule;
import com.OsamaClient.newbridge.Hacks.Troll.*;
import com.OsamaClient.newbridge.Hacks.Visual.ESP.BlockESP;
import com.OsamaClient.newbridge.Hacks.Visual.ESP.ItemESP;
import com.OsamaClient.newbridge.Hacks.Visual.ESP.PlayerESP;
import com.OsamaClient.newbridge.Hacks.Visual.HealthIndicator;
import com.OsamaClient.newbridge.Hacks.Visual.XRay;
import com.OsamaClient.newbridge.Hacks.Visual.*;
import com.OsamaClient.newbridge.UI.gui.ClickGuiModule;
//import com.OsamaClient.newbridge.Hacks.Visual.Trajectories;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModuleManager {
    public static List<Module> modules = new ArrayList<>();

    public static void init() {

        modules.add(new AimAssist());
        modules.add(new TriggerBot());
        modules.add(new SwordBot());
        modules.add(new Flight());
        modules.add(new NoFall());
        modules.add(new BlockESP());
        modules.add(new PlayerESP());
        modules.add(new Spider());
        modules.add(new AirJump());
        modules.add(new Velocity());
        modules.add(new AutoTotem());
        modules.add(new ModuleList());
        modules.add(new AutoSprint());
        modules.add(new FullBright());
        modules.add(new Trajectories());
        modules.add(new AutoCart());
        modules.add(new AutoDihhTap());
        modules.add(new ShieldDisable());
        modules.add(new HealthIndicator());
        modules.add(new Freelook());
        modules.add(new Scaffold());
        modules.add(new ONETAP());
        modules.add(new ElytraFly());
        modules.add(new AutoCrit());
        modules.add(new Reach());
        modules.add(new AttributeSwapping());
        modules.add(new Jesus());
        modules.add(new FakeLag());
        modules.add(new Freecam());
        modules.add(new XRay());
        modules.add(new GoTo());
        modules.add(new AutoMiner());
        modules.add(new Teleport());
        modules.add(new FastBreak());
        modules.add(new FastPlace());
        modules.add(new KillAura());
        modules.add(new NoRender());
        modules.add(new AutoSell());
        modules.add(new com.OsamaClient.newbridge.Hacks.Misc.AntiAfk());
        modules.add(new com.OsamaClient.newbridge.Hacks.Movement.FastLadder());
        modules.add(new com.OsamaClient.newbridge.Hacks.Movement.Glide());
        modules.add(new com.OsamaClient.newbridge.Hacks.Combat.ClickBurst());
        modules.add(new com.OsamaClient.newbridge.Hacks.Combat.AutoWeapon());
        modules.add(new com.OsamaClient.newbridge.Hacks.Movement.AutoWalk());
        modules.add(new com.OsamaClient.newbridge.Hacks.Donut.SpawnerFinder());
        modules.add(new com.OsamaClient.newbridge.Hacks.Donut.PlayerAlert());
        modules.add(new com.OsamaClient.newbridge.Hacks.Combat.AutoGap());
        modules.add(new com.OsamaClient.newbridge.Hacks.Combat.ShieldBlock());
        modules.add(new com.OsamaClient.newbridge.Hacks.Movement.SafeWalk());
        modules.add(new com.OsamaClient.newbridge.Hacks.Movement.BHop());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.Zoom());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.music.MusicOverlay());
        modules.add(new com.OsamaClient.newbridge.Hacks.World.sky.CustomSky());
        modules.add(new com.OsamaClient.newbridge.Hacks.World.WorldColors());
        modules.add(new com.OsamaClient.newbridge.Hacks.Client.NormalMode());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.ArmorWarning());
        modules.add(new com.OsamaClient.newbridge.Hacks.Misc.AutoBreed());
        modules.add(new com.OsamaClient.newbridge.Hacks.Misc.AntiVanish());
        modules.add(new com.OsamaClient.newbridge.Hacks.Misc.CopyCoords());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.HitEffects());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.HurtCam());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.Animations());
        modules.add(new com.OsamaClient.newbridge.Hacks.Misc.AutoDrop());
        modules.add(new com.OsamaClient.newbridge.Hacks.Misc.AutoReplenish());
        modules.add(new com.OsamaClient.newbridge.Hacks.Player.ToolSaver());
        modules.add(new com.OsamaClient.newbridge.Hacks.Player.AntiVoid());
        modules.add(new com.OsamaClient.newbridge.Hacks.Troll.Teabag());
        modules.add(new com.OsamaClient.newbridge.Hacks.Troll.HotbarDance());
        modules.add(new com.OsamaClient.newbridge.Hacks.Donut.TotemAlert());
        modules.add(new com.OsamaClient.newbridge.Hacks.Donut.InvFullAlert());
        modules.add(new com.OsamaClient.newbridge.Hacks.Client.Notifications());
        modules.add(new com.OsamaClient.newbridge.Hacks.Client.Friends());
        modules.add(new com.OsamaClient.newbridge.Hacks.Dupeing.PacketGate());
        modules.add(new com.OsamaClient.newbridge.Hacks.Dupeing.PingSpoof());
        modules.add(new com.OsamaClient.newbridge.Hacks.Dupeing.GuiDesync());
        modules.add(new com.OsamaClient.newbridge.Hacks.Movement.AntiHunger());
        modules.add(new com.OsamaClient.newbridge.Hacks.Player.AirPlace());
        modules.add(new com.OsamaClient.newbridge.Hacks.Combat.AntiBot());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.Viewmodel());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.DamageIndicator());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.SplitChat());
        modules.add(new com.OsamaClient.newbridge.Hacks.Client.CheckMeter());
        modules.add(new com.OsamaClient.newbridge.Hacks.Client.LegitMode());
        modules.add(new com.OsamaClient.newbridge.Hacks.Client.PacketSanitizer());
        modules.add(new com.OsamaClient.newbridge.Hacks.Client.quick.QuickMenu());
        modules.add(new com.OsamaClient.newbridge.Hacks.Misc.NameCensor());
        modules.add(new com.OsamaClient.newbridge.Hacks.Misc.QuickWaypoint());
        modules.add(new com.OsamaClient.newbridge.Hacks.Player.InvTweaks());
        modules.add(new com.OsamaClient.newbridge.Hacks.Dupeing.PacketBurst());
        modules.add(new com.OsamaClient.newbridge.Hacks.Dupeing.BundleDupe());
//        modules.add(new TestEnderChestESP());
        modules.add(new ClickTP());
        modules.add(new AutoTrade());
        modules.add(new BoatFly());
        modules.add(new ItemESP());
        modules.add(new SafeAnchor());
        modules.add(new Nametags());
        modules.add(new InvMove());
        modules.add(new TrueSight());
        modules.add(new AutoEat());
        modules.add(new TeammateList());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.CustomCrosshair());
        modules.add(new ClickGuiModule());
        modules.add(new HudOverlay());
        modules.add(new BaseFinder());
        modules.add(new StraightMine());
        modules.add(new RtpFinder());
        modules.add(new EmoteModule());
        modules.add(new Spam());
        modules.add(new Mimic());
        modules.add(new Derp());
        modules.add(new Shadow());
        modules.add(new BowAimbot());
        modules.add(new Speed());
        modules.add(new HighJump());
        modules.add(new Parkour());
        modules.add(new AutoRespawn());
        modules.add(new AutoTool());
        modules.add(new WallHit());
        modules.add(new com.OsamaClient.newbridge.Hacks.Client.Profiles());
        modules.add(new Waypoints());
        modules.add(new Breadcrumbs());
        modules.add(new Fullbox());
        modules.add(new com.OsamaClient.newbridge.Hacks.Visual.minimap.Minimap());
        modules.add(new com.OsamaClient.newbridge.Hacks.Player.AutoArmor());
        modules.add(new com.OsamaClient.newbridge.Hacks.Misc.stash.StashLogger());
        modules.add(new com.OsamaClient.newbridge.Hacks.Client.macro.Macros());
        modules.add(new com.OsamaClient.newbridge.Hacks.Player.AutoLog());
        modules.add(new com.OsamaClient.newbridge.Hacks.Client.Targets());
        Basics.apply(modules);
        Deps.apply(modules);



    }

    public static List<Module> getModulesByCategory(Module.Category c) {
        return modules.stream().filter(m -> m.category == c).collect(Collectors.toList());
    }

    public static Module getModuleByName(String name) {
        return modules.stream()
                .filter(m -> m.name.equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
