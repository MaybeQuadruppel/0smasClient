package com.OsamaClient.newbridge.Hacks.Visual;

import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects real item icons and player faces that HUD elements want to show. Our own UI pipeline only knows
 * SDF shapes and text, so these are drawn by {@link HudOverlayItemLayer} through vanilla's GuiGraphicsExtractor.
 * Positions are framebuffer pixels (our coordinate system). The layer consumes and clears the lists.
 */
public final class HudIcons {

    public record Item(ItemStack stack, float fbX, float fbY, float fbSize) {}

    public record Face(PlayerSkin skin, float fbX, float fbY, float fbSize) {}

    static final List<Item> items = new ArrayList<>();
    static final List<Face> faces = new ArrayList<>();

    private static final int MAX = 256;

    private HudIcons() {}

    public static void item(ItemStack stack, float fbX, float fbY, float fbSize) {
        if (stack == null || stack.isEmpty()) return;
        if (items.size() >= MAX) items.clear();
        items.add(new Item(stack, fbX, fbY, fbSize));
    }

    public static void face(PlayerSkin skin, float fbX, float fbY, float fbSize) {
        if (skin == null) return;
        if (faces.size() >= MAX) faces.clear();
        faces.add(new Face(skin, fbX, fbY, fbSize));
    }

    public static void clear() {
        items.clear();
        faces.clear();
    }
}
