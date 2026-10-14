package com.OsamaClient.newbridge.UI.gui.render;

import com.mojang.blaze3d.textures.GpuTextureView;

/** Etwas, das der UiRenderer als Textur binden kann (Schrift-Atlas, Bild). */
public interface UiTexture {
    /** Lädt ausstehende Änderungen hoch; nur außerhalb eines RenderPass aufrufen. */
    GpuTextureView texture();
}
