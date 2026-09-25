package com.OsamaClient.newbridge.UI.gui.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Growable direct vertex buffer, reused every frame (no per-frame allocation once warmed up). */
final class QuadBuffer {

    final int stride;
    private ByteBuffer buf;
    private int vertices;

    QuadBuffer(int stride, int initialVertices) {
        this.stride = stride;
        this.buf = ByteBuffer.allocateDirect(stride * initialVertices).order(ByteOrder.nativeOrder());
    }

    void reset() {
        buf.clear();
        vertices = 0;
    }

    int vertexCount() { return vertices; }

    int byteSize() { return vertices * stride; }

    /** Makes room for {@code n} more vertices and returns the buffer positioned for writing. */
    ByteBuffer reserve(int n) {
        int need = (vertices + n) * stride;
        if (need > buf.capacity()) {
            int cap = buf.capacity();
            while (cap < need) cap *= 2;
            ByteBuffer bigger = ByteBuffer.allocateDirect(cap).order(ByteOrder.nativeOrder());
            buf.flip();
            bigger.put(buf);
            buf = bigger;
        }
        vertices += n;
        return buf;
    }

    /** Read-only view of the written bytes, for upload. */
    ByteBuffer view() {
        ByteBuffer v = buf.duplicate().order(ByteOrder.nativeOrder());
        v.position(0).limit(vertices * stride);
        return v;
    }

    static void putColor(ByteBuffer b, int argb) {
        b.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
    }
}
