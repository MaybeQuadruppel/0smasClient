package com.OsamaClient.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public String name;
    public boolean enabled = false;
    public boolean keyAlreadyPressed = false;
    /** Key scancode (SDL3, see Keys) that toggles this module, -1 = unbound. */
    public int key = -1;
    /** true = the key does not toggle the module but keeps it enabled only while it is held down. */
    public boolean holdToActivate = false;
    public boolean favorite;
    public String description;
    public Category category;

    public List<Component> settings = new ArrayList<>();

    public enum Category {
        COMBAT, MOVEMENT, VISUAL, WORLD, MISC, DUPE, Donut, CLIENT, PLAYER, TROLL
    }

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public void toggle() {
        if (!enabled && (com.OsamaClient.newbridge.Hacks.Client.NormalMode.refuse(this) || com.OsamaClient.newbridge.Hacks.Client.LegitMode.refuse(this))) return;
        if (!enabled) Exclusive.beforeEnable(this);
        enabled = !enabled;
        if (enabled) onEnable();
        else onDisable();
        com.OsamaClient.newbridge.Hacks.Client.Notifications.moduleToggled(this);
    }
    public void onAttack(Minecraft mc, LivingEntity target) {}
    public void onEnable() {}
    public void onDisable() {}

    public void onTick(Minecraft client) {}

    // NEU: Wird jeden gerenderten Frame aufgerufen (für flüssige Bewegungen/Grafiken)
    public void onRender(Minecraft client) {}
}
