package com.eden.particles.impl.runtime;

import com.eden.particles.impl.math.Rng;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * Where a playing effect is, for particle forces that follow it. It stops updating when the effect ends, so leftover
 * particles keep the last place.
 */
public final class EffectFrame {

    private static final long NOISE_SALT = 0x2545F4914F6CDD1DL;
    private static final double NOISE_SPREAD = 4096;

    public final Vector3d position = new Vector3d();
    /** The far end of a two-point anchor; the position otherwise. */
    public final Vector3d target = new Vector3d();
    public final Quaternionf facing = new Quaternionf();
    /** Ticks played, for turbulence drift. */
    public int ticks;
    /** Where this effect's turbulence sits in noise space, so each play swirls differently. */
    public final double noiseX, noiseY, noiseZ;

    public EffectFrame(long seed) {
        long noise = seed ^ NOISE_SALT;
        noiseX = Rng.toUnitDouble(Rng.derive(noise, 0)) * NOISE_SPREAD;
        noiseY = Rng.toUnitDouble(Rng.derive(noise, 1)) * NOISE_SPREAD;
        noiseZ = Rng.toUnitDouble(Rng.derive(noise, 2)) * NOISE_SPREAD;
    }
}
