package com.OsamaClient.newbridge.Hacks.Visual.render.chams;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ESPRenderUtil {
    private static final Minecraft MC = Minecraft.getInstance();
    private static volatile boolean chamsWork;
    private static volatile EntityRenderSnapshot espSnapshot = EntityRenderSnapshot.inactive("PlayerESP");
    private static volatile ItemEspSnapshot itemEspSnapshot = ItemEspSnapshot.inactive();

    private static volatile boolean outlineWork;
    private static volatile boolean esp2dWork;

    private ESPRenderUtil() {
    }

    public static boolean shouldEsp(Entity entity) {
        if (entity instanceof ItemEntity) return false;
        EntityRenderSnapshot snapshot = espSnapshot();
        if (!snapshot.enabled()) return false;
        if (shouldSuppressEspForUi()) return false;
        return shouldRenderEntity(snapshot, entity) && espFadeAlpha(snapshot, entity) > 0.0;
    }

    public static int espColor(Entity entity) {
        EntityRenderSnapshot snapshot = espSnapshot();
        int color = entityColor(snapshot, entity, 0xCCFFFFFF);
        return withAlphaMultiplier(color, espFadeAlpha(snapshot, entity));
    }

    public static boolean chamsDrawArmor() {
        // Hier kannst du später eine Einstellung aus deinem Modul abfragen (z.B. eine Checkbox "Render Armor").
        // Für den Moment geben wir einfach "true" zurück, damit die Rüstung bei Chams standardmäßig sichtbar ist
        // und dein Code fehlerfrei kompiliert!
        return true;
    }

    public static int espOutlineColor(Entity entity) {
        return espColor(entity) | 0xFF000000;
    }

    public static boolean shouldItemEsp(Entity entity) {
        if (!(entity instanceof ItemEntity itemEntity)) return false;
        ItemEspSnapshot snapshot = itemEspSnapshot();
        if (!snapshot.enabled()) return false;
        if (shouldSuppressEspForUi()) return false;
        return shouldRenderItem(snapshot, itemEntity) && itemEspFadeAlpha(snapshot, itemEntity) > 0.0;
    }

    public static int itemEspColor(Entity entity) {
        ItemEspSnapshot snapshot = itemEspSnapshot();
        if (!(entity instanceof ItemEntity itemEntity)) return snapshot.color();
        int base = snapshot.dynamicColor()
                ? dynamicItemColor(snapshot, itemEntity.getItem(), snapshot.color())
                : snapshot.color();
        return withAlphaMultiplier(base, itemEspFadeAlpha(snapshot, itemEntity));
    }

    private static int dynamicItemColor(ItemEspSnapshot snapshot, ItemStack stack, int fallbackArgb) {
        int alpha = (fallbackArgb >>> 24) & 0xFF;

        int rgb = stack == null || stack.isEmpty()
                ? 0xFFD76A
                : snapshot.rgbCache().computeIfAbsent(stack.getItem(), ignored -> computeItemRgb(stack));
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    private static int computeItemRgb(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0xFFD76A;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id == null ? "" : id.getPath();

        if (path.contains("netherite")) return 0xB7AFA9;
        if (path.contains("diamond"))   return 0x4AE3D6;
        if (path.contains("emerald"))   return 0x2FE05A;
        if (path.contains("lapis"))     return 0x2A55E0;
        if (path.contains("redstone"))  return 0xFF3030;
        if (path.contains("amethyst"))  return 0xB48CF2;
        if (path.contains("copper"))    return 0xE08A5A;
        if (path.contains("gold") || path.contains("golden") || path.contains("raw_gold")) return 0xFCE24B;
        if (path.contains("iron"))      return 0xDADADA;
        if (path.contains("coal"))      return 0x2C2C2C;
        if (path.contains("quartz"))    return 0xF2EAE0;
        if (path.contains("melon"))     return 0x67C84B;
        if (path.contains("pumpkin"))   return 0xE08020;
        if (path.contains("ender"))     return 0x12B79A;
        if (path.contains("blaze"))     return 0xFFB52E;
        if (path.contains("slime"))     return 0x7FD45A;
        if (path.contains("bone"))      return 0xE9E4D0;
        if (path.contains("netherrack") || path.contains("nether_brick")) return 0x7A3B3B;

        if (stack.getItem() instanceof BlockItem blockItem) {
            try {
                int col = blockItem.getBlock().defaultMapColor().col;
                if (col != 0) return col & 0xFFFFFF;
            } catch (Throwable ignored) {  }
        }

        int hash = (id == null ? path.hashCode() : id.toString().hashCode());
        float hue = ((hash & 0x7FFFFFFF) % 360) / 360.0f;
        return java.awt.Color.HSBtoRGB(hue, 0.65f, 0.95f) & 0xFFFFFF;
    }

    public static int itemEspOutlineColor(Entity entity) {
        return itemEspColor(entity) | 0xFF000000;
    }

    public static int itemOutlineColorOrZero(Entity entity) {
        if (!(entity instanceof ItemEntity itemEntity)) return 0;
        ItemEspSnapshot snapshot = itemEspSnapshot();
        if (!snapshot.enabled() || !"Shader".equals(snapshot.mode()) || shouldSuppressEspForUi()) return 0;
        if (!shouldRenderItem(snapshot, itemEntity)) return 0;
        double fade = itemEspFadeAlpha(snapshot, itemEntity);
        if (fade <= 0.0) return 0;
        int base = snapshot.dynamicColor() ? dynamicItemColor(snapshot, itemEntity.getItem(), snapshot.color()) : snapshot.color();
        return withAlphaMultiplier(base, fade) | 0xFF000000;
    }

    public static int entityOutlineColorOrZero(Entity entity) {
        if (entity instanceof ItemEntity) return 0;
        EntityRenderSnapshot snapshot = espSnapshot();
        if (!snapshot.enabled() || !"Shader".equals(snapshot.mode()) || shouldSuppressEspForUi()) return 0;
        if (!shouldRenderEntity(snapshot, entity)) return 0;
        double fade = espFadeAlpha(snapshot, entity);
        if (fade <= 0.0) return 0;
        return withAlphaMultiplier(entityColor(snapshot, entity, 0xCCFFFFFF), fade) | 0xFF000000;
    }

    public static boolean shouldUseItemOutline() {
        ItemEspSnapshot snapshot = itemEspSnapshot();
        if (!snapshot.enabled() || !"Shader".equals(snapshot.mode())) return false;
        return !shouldSuppressEspForUi();
    }

    public static boolean shouldUseEntityOutline() {
        EntityRenderSnapshot snapshot = espSnapshot();
        if (!snapshot.enabled() || !"Shader".equals(snapshot.mode())) return false;
        return !shouldSuppressEspForUi();
    }

    public static boolean hasAnyOutlineWork() {
        return outlineWork;
    }

    public static boolean has2dEspWork() {
        return esp2dWork;
    }

    public static void refreshFastFlags() {
        synchronized (ESPRenderUtil.class) {
            espSnapshot = buildEntityRenderSnapshot("PlayerESP", false);
            itemEspSnapshot = buildItemEspSnapshot();

            outlineWork = (itemEspSnapshot.enabled() && "Shader".equals(itemEspSnapshot.mode()))
                    || (espSnapshot.enabled() && "Shader".equals(espSnapshot.mode()));
            esp2dWork = espSnapshot.enabled() && "2D".equals(espSnapshot.mode());

            chamsWork = espSnapshot.enabled() && !"2D".equals(espSnapshot.mode());
        }
    }

    public static boolean hasChamsWork() {
        return chamsWork;
    }

    public static boolean shouldSuppressEspForUi() {
        if (MC == null) return false;
        // Unterdrücke ESP, wenn ein normales UI offen ist (außer Chat)
        return MC.gui.screen() != null && !(MC.gui.screen() instanceof ChatScreen) && !(MC.gui.screen() instanceof InBedChatScreen);
    }

    private static boolean shouldRenderEntity(EntityRenderSnapshot snapshot, Entity entity) {
        if (snapshot == null || !snapshot.enabled()) return false;
        if (MC == null || MC.player == null || entity == null) return false;
        if (entity == MC.player) return false;

        if (entity == MC.getCameraEntity() && MC.options.getCameraType().isFirstPerson()) return false;
        Vec3 camera = MC.gameRenderer.mainCamera().position();
        if (!entity.shouldRender(camera.x, camera.y, camera.z)) return false;
        double maxDistance = snapshot.maxDistance();
        if (maxDistance > 0.0 && entity.distanceToSqr(MC.player) > maxDistance * maxDistance) return false;

        // Render standardmäßig alle Entitäten, wenn keine spezifischen Ids gesetzt sind
        if (snapshot.entityIds().isEmpty() && snapshot.entityPaths().isEmpty()) return true;

        return snapshot.matchCache().computeIfAbsent(entity.getType(), type -> {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            return snapshot.entityIds().contains(id) || snapshot.entityPaths().contains(id.getPath());
        });
    }

    private static boolean shouldRenderItem(ItemEspSnapshot snapshot, ItemEntity entity) {
        if (snapshot == null || !snapshot.enabled()) return false;
        if (MC == null || MC.player == null || entity == null) return false;
        if (entity.getItem() == null || entity.getItem().isEmpty()) return false;
        Vec3 camera = MC.gameRenderer.mainCamera().position();
        if (!entity.shouldRender(camera.x, camera.y, camera.z)) return false;
        double maxDistance = snapshot.maxDistance();
        if (maxDistance > 0.0 && entity.distanceToSqr(MC.player) > maxDistance * maxDistance) return false;
        if (!snapshot.someOnly()) return true;

        return snapshot.matchCache().computeIfAbsent(entity.getItem().getItem(), item -> {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            return snapshot.itemIds().contains(id) || snapshot.itemPaths().contains(id.getPath());
        });
    }

    private static EntityRenderSnapshot espSnapshot() {
        // Da du kein Revision-System hast, rufen wir refreshFastFlags() am besten im Tick-Event auf.
        // Die Utility greift hier einfach auf den aktuell gecacheten Snapshot zu.
        return espSnapshot;
    }

    private static ItemEspSnapshot itemEspSnapshot() {
        return itemEspSnapshot;
    }

    private static ItemEspSnapshot buildItemEspSnapshot() {
        // Falls du in Zukunft ein ItemESP Modul hinzufügst, den Namen hier anpassen
        Module module = ModuleManager.getModuleByName("ItemESP");
        if (module == null || !module.enabled) return ItemEspSnapshot.inactive();

        Set<Identifier> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();

        /*
         * Hier kannst du später deine Component-Settings auslesen[cite: 13, 14]
         * Beispiel: double maxDistance = ((SliderComponent) module.settings.get(0)).getValue();
         */

        return new ItemEspSnapshot(
                true,
                "Shader", // Mode default
                false,    // Some only default
                64.0,     // Max distance
                3.0,      // Fade distance
                Set.copyOf(ids),
                Set.copyOf(paths),
                0xCCFFD76A,
                true,
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>()
        );
    }

    private static EntityRenderSnapshot buildEntityRenderSnapshot(String moduleId, boolean useMaxDistance) {
        Module module = ModuleManager.getModuleByName(moduleId);
        if (module == null || !module.enabled) return EntityRenderSnapshot.inactive(moduleId);

        Set<Identifier> ids = new HashSet<>();
        Set<String> paths = new HashSet<>();

        /*
         * Hier kannst du später deine Component-Settings auslesen[cite: 13, 14]
         * Beispiel: String mode = ((ModeComponent) module.settings.get(0)).getValue();
         */

        return new EntityRenderSnapshot(
                moduleId,
                true,
                "Shader", // Mode default (z.B. "Shader" oder "2D")
                useMaxDistance ? 256.0 : 0.0,
                3.0, // Fade Distance
                Set.copyOf(ids),
                Set.copyOf(paths),
                0xCCFFFFFF, // Player Color
                0xCCFF4A4A, // Monster Color
                0xCC74FF8F, // Animal Color
                0xCC66D9FF, // Water Animal Color
                0xCCB78CFF, // Ambient Color
                0xCCCCCCCC, // Misc Color
                false,      // Distance Color
                40.0,       // Color Distance
                new ConcurrentHashMap<>()
        );
    }

    private static int entityColor(EntityRenderSnapshot snapshot, Entity entity, int fallback) {
        if (snapshot == null || entity == null) return fallback;

        if (snapshot.distanceColor()) return distanceColor(entity, snapshot.colorDistance());
        if (entity.getType() == EntityTypes.PLAYER) return snapshot.playersColor();
        MobCategory category = entity.getType().getCategory();
        return switch (category) {
            case MONSTER -> snapshot.monstersColor();
            case CREATURE -> snapshot.animalsColor();
            case WATER_CREATURE, WATER_AMBIENT, UNDERGROUND_WATER_CREATURE, AXOLOTLS -> snapshot.waterAnimalsColor();
            case AMBIENT -> snapshot.ambientColor();
            default -> snapshot.miscColor();
        };
    }

    private static int distanceColor(Entity entity, double span) {
        double distance = 0.0;
        if (MC != null && MC.gameRenderer != null && entity != null) {
            Vec3 camera = MC.gameRenderer.mainCamera().position();
            double dx = entity.getX() - camera.x;
            double dy = entity.getY() + entity.getBbHeight() * 0.5 - camera.y;
            double dz = entity.getZ() - camera.z;
            distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return distanceHueColor(distance, span);
    }

    public static int distanceHueColor(double distance, double span) {
        if (span <= 0.0) span = 40.0;
        float t = (float) Math.max(0.0, Math.min(1.0, distance / span));
        float hue = t * (120.0f / 360.0f);
        return 0xFF000000 | (hsbToRgb(hue, 1.0f, 1.0f) & 0xFFFFFF);
    }

    private static int hsbToRgb(float h, float s, float b) {
        h = (h % 1.0f + 1.0f) % 1.0f;
        int i = (int) (h * 6.0f);
        float f = h * 6.0f - i;
        float p = b * (1.0f - s);
        float q = b * (1.0f - s * f);
        float t = b * (1.0f - s * (1.0f - f));
        float r, g, bl;
        switch (i % 6) {
            case 0 -> { r = b; g = t; bl = p; }
            case 1 -> { r = q; g = b; bl = p; }
            case 2 -> { r = p; g = b; bl = t; }
            case 3 -> { r = p; g = q; bl = b; }
            case 4 -> { r = t; g = p; bl = b; }
            default -> { r = b; g = p; bl = q; }
        }
        int ri = Math.round(r * 255.0f);
        int gi = Math.round(g * 255.0f);
        int bi = Math.round(bl * 255.0f);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static double espFadeAlpha(EntityRenderSnapshot snapshot, Entity entity) {
        if (MC == null || MC.gameRenderer == null || entity == null) return 1.0;
        double fadeDistance = snapshot == null ? 3.0 : snapshot.fadeDistance();
        if (fadeDistance <= 0.0) return 1.0;
        Vec3 camera = MC.gameRenderer.mainCamera().position();
        double dx = entity.getX() - camera.x;
        double dy = entity.getY() + entity.getBbHeight() * 0.5 - camera.y;
        double dz = entity.getZ() - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double alpha = Math.min(1.0, distance / fadeDistance);
        return alpha <= 0.075 ? 0.0 : alpha;
    }

    private static double itemEspFadeAlpha(ItemEspSnapshot snapshot, ItemEntity entity) {
        if (MC == null || MC.gameRenderer == null || entity == null) return 1.0;
        double fadeDistance = snapshot == null ? 3.0 : snapshot.fadeDistance();
        if (fadeDistance <= 0.0) return 1.0;
        Vec3 camera = MC.gameRenderer.mainCamera().position();
        double dx = entity.getX() - camera.x;
        double dy = entity.getY() + entity.getBbHeight() * 0.5 - camera.y;
        double dz = entity.getZ() - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double alpha = Math.min(1.0, distance / fadeDistance);
        return alpha <= 0.075 ? 0.0 : alpha;
    }

    private static int withAlphaMultiplier(int color, double multiplier) {
        int alpha = Math.max(0, Math.min(255, (int) (((color >>> 24) & 0xFF) * multiplier)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private record EntityRenderSnapshot(
            String moduleId,
            boolean enabled,
            String mode,
            double maxDistance,
            double fadeDistance,
            Set<Identifier> entityIds,
            Set<String> entityPaths,
            int playersColor,
            int monstersColor,
            int animalsColor,
            int waterAnimalsColor,
            int ambientColor,
            int miscColor,
            boolean distanceColor,
            double colorDistance,
            ConcurrentHashMap<EntityType<?>, Boolean> matchCache
    ) {
        static EntityRenderSnapshot inactive(String moduleId) {
            return new EntityRenderSnapshot(
                    moduleId,
                    false,
                    "",
                    0.0,
                    0.0,
                    Set.of(),
                    Set.of(),
                    0xCCFFFFFF,
                    0xCCFF4A4A,
                    0xCC74FF8F,
                    0xCC66D9FF,
                    0xCCB78CFF,
                    0xCCCCCCCC,
                    false,
                    40.0,
                    new ConcurrentHashMap<>()
            );
        }
    }

    private record ItemEspSnapshot(
            boolean enabled,
            String mode,
            boolean someOnly,
            double maxDistance,
            double fadeDistance,
            Set<Identifier> itemIds,
            Set<String> itemPaths,
            int color,
            boolean dynamicColor,
            ConcurrentHashMap<Item, Integer> rgbCache,
            ConcurrentHashMap<Item, Boolean> matchCache
    ) {
        static ItemEspSnapshot inactive() {
            return new ItemEspSnapshot(
                    false,
                    "",
                    false,
                    0.0,
                    0.0,
                    Set.of(),
                    Set.of(),
                    0xCCFFD76A,
                    true,
                    new ConcurrentHashMap<>(),
                    new ConcurrentHashMap<>()
            );
        }
    }
}