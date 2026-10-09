package com.eden.particles.impl.runtime;

import com.eden.particles.impl.math.Rng;

/** Deterministic chance rolls and seeds for effects that particles play, from each particle's own random value. */
public final class Triggers {

    public static final long DEATH_SALT = 0x2545F4914F6CDD1DL;
    public static final long LAND_SALT = 0xBF58476D1CE4E5B9L;

    private Triggers() {
    }

    public static long seed(float variant, long salt) {
        return Rng.derive(Float.floatToIntBits(variant) ^ salt, 1);
    }

    public static boolean roll(float variant, long salt, float chance) {
        if (chance >= 1f) return true;
        if (!(chance > 0f)) return false;
        return Rng.toUnitFloat(seed(variant, salt)) < chance;
    }
}
