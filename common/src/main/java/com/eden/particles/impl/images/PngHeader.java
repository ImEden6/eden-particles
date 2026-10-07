package com.eden.particles.impl.images;

import java.nio.ByteBuffer;

/** Reads a PNG's dimensions from its IHDR chunk; the server can't decode images ({@code NativeImage} is client-only). */
public record PngHeader(int width, int height) {

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /** @throws IllegalArgumentException with a readable reason if this isn't a PNG */
    public static PngHeader read(byte[] bytes) {
        if (bytes.length < 24) throw new IllegalArgumentException("not a PNG (only " + bytes.length + " bytes)");
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (bytes[i] != SIGNATURE[i]) throw new IllegalArgumentException("not a PNG (wrong signature)");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        if (buffer.getInt(12) != 0x49484452) throw new IllegalArgumentException("not a PNG (first chunk isn't IHDR)");
        int width = buffer.getInt(16);
        int height = buffer.getInt(20);
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("invalid size " + width + "×" + height);
        return new PngHeader(width, height);
    }
}
