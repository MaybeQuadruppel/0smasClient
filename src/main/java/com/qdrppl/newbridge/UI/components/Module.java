package com.qdrppl.newbridge.UI.components;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public String name;
    public boolean enabled = false;
    public boolean keyAlreadyPressed = false;
    public String description;
    public Category category;

    public List<Component> settings = new ArrayList<>();

    public enum Category {
        COMBAT, MOVEMENT, VISUAL, MISC
    }

    public Module(String name, String description, Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public void toggle() {
        enabled = !enabled;
        if (enabled) onEnable();
        else onDisable();
    }
    public void onAttack(Minecraft mc, LivingEntity target) {}
    public void onEnable() {}
    public void onDisable() {}

    public void onTick(Minecraft client) {}
}