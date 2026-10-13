package com.eden.particles.impl.math;

/**
 * Divergence-free flow for turbulence, so particles swirl without bunching up. Uses the cross product of two noise
 * gradients ({@code ∇n₁ × ∇n₂}, Sakai's bitangent noise), which is divergence-free like a curl but needs two noise
 * evaluations instead of three. Scaled so the mean length is about 1.
 */
public final class CurlNoise {

    private static final double SECOND_X = 31.416, SECOND_Y = -47.853, SECOND_Z = 12.793;
    /** Mean length of the raw field, measured over many random points (see CurlNoiseTest). */
    static final double MEAN_ONE_OCTAVE = 6.195;
    static final double MEAN_TWO_OCTAVES = 7.659;
    private static final ThreadLocal<double[]> WORK = ThreadLocal.withInitial(() -> new double[12]);

    private CurlNoise() {
    }

    /** Writes the flow at (x, y, z) into {@code out[0..2]}. {@code octaves} is 1 or 2; the second adds finer detail. */
    public static void sample(double x, double y, double z, int octaves, double[] out) {
        System.arraycopy(sample(x, y, z, octaves), 0, out, 0, 3);
    }

    /** Like {@link #sample(double, double, double, int, double[])}, into a per-thread array valid until the next call. */
    public static double[] sample(double x, double y, double z, int octaves) {
        double[] g = WORK.get();
        SimplexNoise.noise(x, y, z, g, 0);
        SimplexNoise.noise(x + SECOND_X, y + SECOND_Y, z + SECOND_Z, g, 3);
        double norm = MEAN_ONE_OCTAVE;
        if (octaves > 1) {
            SimplexNoise.noise(2 * x, 2 * y, 2 * z, g, 6);
            SimplexNoise.noise(2 * x + SECOND_X, 2 * y + SECOND_Y, 2 * z + SECOND_Z, g, 9);
            // The finer octave at quarter amplitude: ∇[¼n(2p)] = ½∇n(2p).
            for (int i = 0; i < 6; i++) g[i] += 0.5 * g[i + 6];
            norm = MEAN_TWO_OCTAVES;
        }
        double vx = (g[1] * g[5] - g[2] * g[4]) / norm;
        double vy = (g[2] * g[3] - g[0] * g[5]) / norm;
        double vz = (g[0] * g[4] - g[1] * g[3]) / norm;
        g[0] = vx;
        g[1] = vy;
        g[2] = vz;
        return g;
    }
}
