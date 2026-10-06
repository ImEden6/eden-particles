package com.eden.particles.impl.math;

/** Counter-based SplitMix64 randomness. Seeds derive effect → emitter → particle. */
public final class Rng {

    public static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private static final long LOD_SALT = 0xD1B54A32D192ED03L;

    private Rng() {
    }

    public static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long derive(long parentSeed, long index) {
        return mix64(parentSeed + (index + 1) * GOLDEN_GAMMA);
    }

    public static float toUnitFloat(long bits) {
        return (bits >>> 40) * 0x1.0p-24f;
    }

    public static double toUnitDouble(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    /** LOD test. Survivors at a lower fraction are a subset of survivors at a higher one. */
    public static boolean keep(long particleSeed, float fraction) {
        if (fraction >= 1f) return true;
        if (fraction <= 0f) return false;
        return toUnitFloat(mix64(particleSeed ^ LOD_SALT)) < fraction;
    }
}
