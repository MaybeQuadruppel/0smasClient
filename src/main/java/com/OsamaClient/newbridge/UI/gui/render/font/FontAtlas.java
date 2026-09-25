package com.OsamaClient.newbridge.UI.gui.render.font;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FT_Size_Metrics;
import org.lwjgl.util.freetype.FT_Vector;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.util.freetype.FreeType.*;

/**
 * Rasterizes our bundled font with FreeType at one physical pixel size into our own atlas texture.
 * Glyphs are drawn 1:1 (nearest filtering), so text is as crisp as the pixel grid allows.
 * Not Minecraft's font system.
 */
public final class FontAtlas implements AutoCloseable {

    private static final String FONT_PATH = "/assets/newbridge/fonts/inter.ttf";
    private static final int LOAD_FLAGS = FT_LOAD_RENDER | (FT_RENDER_MODE_LIGHT << 16); // light (vertical) hinting
    private static final int PAD = 1;

    private static long library;
    private static ByteBuffer fontData;

    public static final class Glyph {
        public final float advance;
        public final int left, top, w, h;
        float u0, v0, u1, v1;
        final int index;

        Glyph(float advance, int left, int top, int w, int h, int index) {
            this.advance = advance;
            this.left = left;
            this.top = top;
            this.w = w;
            this.h = h;
            this.index = index;
        }
    }

    public final int pixelSize;
    private final FT_Face face;
    private final boolean kerning;
    public final float ascender, descender, lineHeight, capHeight;

    private final Glyph[] ascii = new Glyph[256];
    private final Map<Integer, Glyph> other = new HashMap<>();

    private int atlasSize = 256;
    private NativeImage image;
    private int penX = PAD, penY = PAD, rowH;
    private boolean dirty;
    private GpuTexture texture;
    private GpuTextureView view;

    public FontAtlas(int pixelSize) {
        this.pixelSize = Math.max(4, pixelSize);
        ensureLibrary();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pFace = stack.mallocPointer(1);
            check(FT_New_Memory_Face(library, fontData, 0, pFace), "FT_New_Memory_Face");
            face = FT_Face.create(pFace.get(0));
        }
        check(FT_Set_Pixel_Sizes(face, 0, this.pixelSize), "FT_Set_Pixel_Sizes");
        kerning = FT_HAS_KERNING(face);
        FT_Size_Metrics m = face.size().metrics();
        ascender = m.ascender() / 64f;
        descender = -m.descender() / 64f;
        lineHeight = ascender + descender;
        image = new NativeImage(atlasSize, atlasSize, true);
        for (int c = 32; c < 256; c++) glyph(c);
        Glyph h = glyph('H');
        capHeight = h != null && h.h > 0 ? h.top : Math.round(ascender * 0.73f);
    }

    private static synchronized void ensureLibrary() {
        if (library != 0) return;
        try (InputStream in = FontAtlas.class.getResourceAsStream(FONT_PATH)) {
            if (in == null) throw new IllegalStateException("missing font " + FONT_PATH);
            byte[] bytes = in.readAllBytes();
            fontData = MemoryUtil.memAlloc(bytes.length); // must stay alive while faces use it
            fontData.put(bytes).flip();
        } catch (IOException e) {
            throw new IllegalStateException("could not read font", e);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pLib = stack.mallocPointer(1);
            check(FT_Init_FreeType(pLib), "FT_Init_FreeType");
            library = pLib.get(0);
        }
    }

    private static void check(int err, String what) {
        if (err != FT_Err_Ok) throw new IllegalStateException(what + " failed: " + err);
    }

    /** Glyph for a code point (rasterized lazily), or null if the font has none and no fallback. */
    public Glyph glyph(int cp) {
        if (cp >= 0 && cp < 256) {
            Glyph g = ascii[cp];
            if (g == null) ascii[cp] = g = rasterize(cp);
            return g;
        }
        return other.computeIfAbsent(cp, this::rasterize);
    }

    private Glyph rasterize(int cp) {
        int index = FT_Get_Char_Index(face, cp);
        if (index == 0 && cp != '?') return glyph('?');
        if (FT_Load_Glyph(face, index, LOAD_FLAGS) != FT_Err_Ok) return null;
        FT_GlyphSlot slot = face.glyph();
        FT_Bitmap bmp = slot.bitmap();
        int w = bmp.width(), h = bmp.rows(), pitch = bmp.pitch();
        Glyph g = new Glyph(slot.advance().x() / 64f, slot.bitmap_left(), slot.bitmap_top(), w, h, index);
        if (w > 0 && h > 0) {
            place(g);
            ByteBuffer src = bmp.buffer(Math.abs(pitch) * h);
            int gx = Math.round(g.u0 * atlasSize), gy = Math.round(g.v0 * atlasSize);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int a = src.get(y * pitch + x) & 0xFF;
                    image.setPixel(gx + x, gy + y, (a << 24) | 0xFFFFFF);
                }
            }
            dirty = true;
        }
        return g;
    }

    /** Shelf packing; doubles the atlas (and re-packs all glyphs) when full. */
    private void place(Glyph g) {
        if (penX + g.w + PAD > atlasSize) {
            penX = PAD;
            penY += rowH + PAD;
            rowH = 0;
        }
        if (penY + g.h + PAD > atlasSize) {
            grow();
        }
        g.u0 = penX / (float) atlasSize;
        g.v0 = penY / (float) atlasSize;
        g.u1 = (penX + g.w) / (float) atlasSize;
        g.v1 = (penY + g.h) / (float) atlasSize;
        penX += g.w + PAD;
        rowH = Math.max(rowH, g.h);
    }

    private void grow() {
        NativeImage old = image;
        int oldSize = atlasSize;
        atlasSize *= 2;
        image = new NativeImage(atlasSize, atlasSize, true);
        // copy the old atlas into the top-left corner; UVs are rescaled below
        for (int y = 0; y < oldSize; y++)
            for (int x = 0; x < oldSize; x++)
                image.setPixel(x, y, old.getPixel(x, y));
        old.close();
        float f = oldSize / (float) atlasSize;
        for (Glyph g : ascii) rescale(g, f);
        for (Glyph g : other.values()) rescale(g, f);
        // continue packing below the old content
        penX = PAD;
        penY = oldSize + PAD;
        rowH = 0;
        closeTexture();
        dirty = true;
    }

    private static void rescale(Glyph g, float f) {
        if (g == null) return;
        g.u0 *= f; g.v0 *= f; g.u1 *= f; g.v1 *= f;
    }

    public float kerning(Glyph left, Glyph right) {
        if (!kerning || left == null || right == null) return 0f;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FT_Vector v = FT_Vector.malloc(stack);
            if (FT_Get_Kerning(face, left.index, right.index, FT_KERNING_DEFAULT, v) != FT_Err_Ok) return 0f;
            return v.x() / 64f;
        }
    }

    /** Uploads pending glyphs; must be called outside a render pass, before drawing. */
    public GpuTextureView texture() {
        if (texture == null) {
            texture = RenderSystem.getDevice().createTexture("newbridge font " + pixelSize,
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, GpuFormat.RGBA8_UNORM,
                    atlasSize, atlasSize, 1, 1);
            view = RenderSystem.getDevice().createTextureView(texture);
            dirty = true;
        }
        if (dirty) {
            RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, image);
            dirty = false;
        }
        return view;
    }

    public boolean isDirty() { return dirty || texture == null; }

    private void closeTexture() {
        if (view != null) { view.close(); view = null; }
        if (texture != null) { texture.close(); texture = null; }
    }

    @Override
    public void close() {
        closeTexture();
        if (image != null) { image.close(); image = null; }
        FT_Done_Face(face);
    }
}
