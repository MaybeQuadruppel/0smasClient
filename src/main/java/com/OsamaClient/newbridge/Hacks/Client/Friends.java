package com.OsamaClient.newbridge.Hacks.Client;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.Utils.Notify;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Friends: look at a player and press the pick-block key (middle mouse) to add or remove them.
 * Friends count as teammates, so aura/aim/trigger modules skip them and TeammateList shows them.
 */
public class Friends extends Module {

    public static Friends INSTANCE;
    public static final Set<String> friends = new LinkedHashSet<>();
    private boolean wasDown;

    public Friends() {
        super("Friends", "Middle-click a player to make them a friend (treated as a teammate).", Category.CLIENT);
        INSTANCE = this;
    }

    public static boolean isFriend(Entity e) {
        Friends f = INSTANCE;
        return f != null && f.enabled && e != null && friends.contains(e.getScoreboardName());
    }

    public static boolean toggleFriend(String name) {
        if (!friends.remove(name)) { friends.add(name); return true; }
        return false;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null || mc.gui.screen() != null) { wasDown = false; return; }
        boolean down = mc.options.keyPickItem.isDown();
        if (down && !wasDown && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p) {
            boolean added = toggleFriend(p.getScoreboardName());
            Notify.bar(p.getName().getString() + (added ? " added to friends" : " removed from friends"));
        }
        wasDown = down;
    }
}
