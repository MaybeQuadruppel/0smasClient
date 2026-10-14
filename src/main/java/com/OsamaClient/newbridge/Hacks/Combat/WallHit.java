package com.OsamaClient.newbridge.Hacks.Combat;

import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Hitten durch Wände. Vanilla ersetzt das Ziel unter dem Fadenkreuz durch den Block, sobald
 * einer davor liegt. Dieses Modul setzt in dem Tick, in dem du Linksklick hältst, das Ziel
 * auf den Gegner hinter der Wand. Der eigentliche Schlag läuft danach ganz normal über
 * Vanilla (richtige Paketreihenfolge, Cooldown, Swing) - und die Wand wird nicht abgebaut.
 */
public class WallHit extends Module {

    private double reach = 3.0;

    private final EntityFilterPicker filter = new EntityFilterPicker("Targets");

    public WallHit() {
        super("WallHit", "Hit entities through walls with your normal attack click.", Category.COMBAT);

        filter.filters.put("Hostiles", false); // standardmäßig nur Spieler

        settings.add(new Slider("Reach", 1.0, 6.0, reach, 0.1, v -> reach = v)
                .withDescription("Max distance to the entity behind the wall (server default is 3.0)"));
        settings.add(filter.withDescription("Which entities can be hit through walls"));
    }

    /**
     * Aufgerufen aus MinecraftHandleKeybindsMixin (nach Vanillas pick(), vor handleKeybinds()).
     * Vorher lief das in onTick() (START_CLIENT_TICK) und wurde von pick() sofort wieder überschrieben.
     */
    public void redirect(Minecraft mc) {
        if (!enabled || mc.player == null || mc.level == null || mc.gameMode == null || mc.gui.screen() != null) return;
        if (mc.player.isUsingItem()) return;

        // Nur eingreifen, wenn gerade angegriffen wird (nicht beim Bauen/Rechtsklick)
        if (!mc.options.keyAttack.isDown() || mc.options.keyUse.isDown()) return;

        // Nur wenn Vanilla eine Wand/einen Block vor das Ziel gesetzt hat
        HitResult current = mc.hitResult;
        if (current == null || current.getType() != HitResult.Type.BLOCK) return;

        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        AABB search = mc.player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);

        Entity best = null;
        Vec3 bestHit = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : mc.level.getEntities(mc.player, search,
                ent -> ent.isAlive() && ent.isPickable() && isValidTarget(ent))) {
            AABB box = e.getBoundingBox().inflate(e.getPickRadius());
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isEmpty()) continue;
            double d = eye.distanceToSqr(hit.get());
            if (d < bestDist) {
                bestDist = d;
                best = e;
                bestHit = hit.get();
            }
        }

        if (best != null) {
            // Vanilla-startAttack() sieht jetzt einen Entity-Treffer und schlägt zu,
            // statt den Block anzufangen abzubauen.
            mc.hitResult = new EntityHitResult(best, bestHit);
        }
    }

    /** Wie in AimAssist: Gruppen-Filter des EntityFilterPickers direkt auswerten. */
    private boolean isValidTarget(Entity entity) {
        if (entity == Minecraft.getInstance().player || !entity.isAlive()) return false;

        if (filter.isFilterEnabled("Players") && entity instanceof Player) return true;
        if (filter.isFilterEnabled("Hostiles") && entity instanceof Enemy) return true;
        if (filter.isFilterEnabled("Animals") && entity instanceof Animal) return true;
        if (filter.isFilterEnabled("NPCs") && entity instanceof Npc) return true;
        if (filter.isFilterEnabled("ArmorStands") && entity instanceof ArmorStand) return true;

        return false;
    }
}