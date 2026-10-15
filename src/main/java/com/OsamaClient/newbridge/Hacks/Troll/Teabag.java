package com.OsamaClient.newbridge.Hacks.Troll;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Rapidly crouches while someone is next to you. */
public class Teabag extends Module {

    public static Teabag INSTANCE;

    public double range = 4;
    public double speed = 3;
    public boolean anyEntity = false;
    private int tick;
    private boolean forced;

    public Teabag() {
        super("Teabag", "Crouches repeatedly while another player is close.", Category.TROLL);
        INSTANCE = this;
        settings.add(new Slider("Range", 1, 8, range, 0.5, v -> range = v).withDescription("Start crouching when someone is this close."));
        settings.add(new Slider("Speed", 1, 10, speed, 1, v -> speed = v).withDescription("Ticks per crouch toggle."));
        settings.add(new ToggleButton("Any Entity", anyEntity, v -> anyEntity = v).withDescription("Also react to mobs and NPCs."));
    }

    @Override
    public void onDisable() { release(Minecraft.getInstance()); }

    private void release(Minecraft mc) {
        if (forced && mc.options != null) mc.options.keyShift.setDown(false);
        forced = false;
    }

    @Override
    public void onTick(Minecraft mc) {
        var p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) return;
        boolean near = false;
        double r2 = range * range;
        for (var e : mc.level.entitiesForRendering()) {
            if (e == p || !(e instanceof LivingEntity) || p.distanceToSqr(e) > r2) continue;
            if (e instanceof Player || anyEntity) { near = true; break; }
        }
        if (!near) { release(mc); tick = 0; return; }
        tick++;
        forced = true;
        mc.options.keyShift.setDown(((tick / (int) speed) & 1) == 0);
    }
}
