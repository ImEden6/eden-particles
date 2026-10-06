package com.eden.particles.impl.runtime;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

/** Per-play adjustments. {@code scale} multiplies lengths, speeds, sizes and gravity; {@code tint} multiplies colour. */
public record PlayParams(float scale, Quaternionfc rotation, int tint, long seed) {

    public static PlayParams of(long seed) {
        return new PlayParams(1f, new Quaternionf(), 0xFFFFFF, seed);
    }
}
