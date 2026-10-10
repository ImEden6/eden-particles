package com.eden.particles.impl.images;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256, which identifies an image's content in offers and in the client cache. */
public final class ImageHash {

    private ImageHash() {
    }

    public static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM has SHA-256", e);
        }
    }
}
