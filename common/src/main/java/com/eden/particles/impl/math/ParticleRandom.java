package com.eden.particles.impl.math;

/** Reusable uniform stream for one seed; {@link #reset} per particle instead of allocating. */
public final class ParticleRandom {

    private long seed;
    private long counter;

    public ParticleRandom reset(long seed) {
        this.seed = seed;
        this.counter = 0;
        return this;
    }

    public long nextLong() {
        return Rng.mix64(seed + (++counter) * Rng.GOLDEN_GAMMA);
    }

    public float nextFloat() {
        return Rng.toUnitFloat(nextLong());
    }

    public double nextDouble() {
        return Rng.toUnitDouble(nextLong());
    }

    /** Lemire's multiply-shift; avoids the bias of {@code %}. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return (int) (((nextLong() >>> 32) * bound) >>> 32);
    }
}
