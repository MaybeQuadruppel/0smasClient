package com.qdrppl.newbridge.UI.components;

import com.qdrppl.newbridge.Hacks.Combat.*;
import com.qdrppl.newbridge.Hacks.Movement.*;
import com.qdrppl.newbridge.Hacks.Visual.ESP.*;
import com.qdrppl.newbridge.Hacks.Visual.Trajectories;
import com.qdrppl.newbridge.Hacks.Visual.ModuleList;
import com.qdrppl.newbridge.Hacks.Misc.*;

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
