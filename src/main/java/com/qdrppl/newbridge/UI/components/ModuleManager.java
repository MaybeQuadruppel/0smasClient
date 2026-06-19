package com.qdrppl.newbridge.UI.components;

import com.qdrppl.newbridge.Hacks.Combat.*;
import com.qdrppl.newbridge.Hacks.Movement.*;
import com.qdrppl.newbridge.Hacks.Visual.ESP.*;
import com.qdrppl.newbridge.Hacks.Visual.*;
import com.qdrppl.newbridge.Hacks.Visual.Trajectories;
import com.qdrppl.newbridge.Hacks.Misc.*;
//import com.qdrppl.newbridge.Hacks.Dupeing.*;

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
//        modules.add(new PacketControl());
        modules.add(new AutoCrit());
        modules.add(new Reach());
        modules.add(new AttributeSwapping());
        modules.add(new Jesus());
        modules.add(new FakeLag());
        modules.add(new Freecam());
        modules.add(new XRay());
        modules.add(new GoTo());
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
