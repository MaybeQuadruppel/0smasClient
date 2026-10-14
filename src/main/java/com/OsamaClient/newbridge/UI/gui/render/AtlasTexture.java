package com.OsamaClient.newbridge.UI.gui.render;

import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Minecraft-Atlas (Blocks, Items) als UiTexture; die Textur gehört Minecraft und wird nie von uns geschlossen. */
public final class AtlasTexture implements UiTexture {

    private static final Map<Identifier, AtlasTexture> CACHE = new HashMap<>();

    private final Identifier location;

    private AtlasTexture(Identifier location) { this.location = location; }

    public static AtlasTexture of(Identifier location) {
        return CACHE.computeIfAbsent(location, AtlasTexture::new);
    }

    @Override
    public GpuTextureView texture() {
        return Minecraft.getInstance().getTextureManager().getTexture(location).getTextureView();
    }
}
