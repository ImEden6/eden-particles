package com.eden.particles.impl.images;

/** Caps on images a server may send; clients enforce the same limits on what they receive. */
public final class ImageLimits {

    public static final int MAX_SIZE = 256;
    public static final int MAX_FILE_BYTES = 256 * 1024;
    public static final int MAX_IMAGES = 512;
    public static final long MAX_TOTAL_BYTES = 8L * 1024 * 1024;

    private ImageLimits() {
    }

    /** @return why this image breaks a per-image limit, or null if it's fine */
    public static String check(byte[] bytes) {
        if (bytes.length > MAX_FILE_BYTES) {
            return "file is " + bytes.length / 1024 + " KiB; the limit is " + MAX_FILE_BYTES / 1024 + " KiB";
        }
        PngHeader header;
        try {
            header = PngHeader.read(bytes);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        if (header.width() > MAX_SIZE || header.height() > MAX_SIZE) {
            return "image is " + header.width() + "×" + header.height() + "; the limit is " + MAX_SIZE + "×" + MAX_SIZE;
        }
        return null;
    }
}
