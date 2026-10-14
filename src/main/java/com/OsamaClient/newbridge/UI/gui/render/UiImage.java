package com.OsamaClient.newbridge.UI.gui.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

/** CPU-Bild (ARGB) als Textur für den UiRenderer, z.B. die Minimap. Hochgeladen nur wenn geändert. */
public final class UiImage implements UiTexture {

    private final String name;
    private final int width, height;
    private NativeImage image;
    private GpuTexture texture;
    private GpuTextureView view;
    private boolean dirty = true;
    /** true = bilinear filtering when drawn (smooth scaling), false = pixel-exact. */
    public boolean smooth;

    public UiImage(String name, int width, int height) {
        this.name = name;
        this.width = width;
        this.height = height;
    }

    public int width() { return width; }
    public int height() { return height; }

    private NativeImage img() {
        if (image == null) image = new NativeImage(width, height, true);
        return image;
    }

    public void setPixel(int x, int y, int argb) {
        img().setPixel(x, y, argb);
    }

    public void markDirty() { dirty = true; }

    @Override
    public GpuTextureView texture() {
        if (texture == null) {
            texture = RenderSystem.getDevice().createTexture("newbridge image " + name,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, GpuFormat.RGBA8_UNORM,
                    width, height, 1, 1);
            view = RenderSystem.getDevice().createTextureView(texture);
            dirty = true;
        }
        if (dirty) {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, img());
            dirty = false;
        }
        return view;
    }

    public void close() {
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
        if (image != null) { image.close(); image = null; }
    }
}
