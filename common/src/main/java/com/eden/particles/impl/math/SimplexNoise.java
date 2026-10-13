package com.eden.particles.impl.math;

/** 3D simplex noise with its analytic gradient (after Gustavson's sdnoise3). Output is roughly in [-1, 1]. */
public final class SimplexNoise {

    private static final double F3 = 1.0 / 3.0;
    private static final double G3 = 1.0 / 6.0;
    private static final int[] PERM = new int[512];
    private static final double[] GRADIENTS = {
            1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1, 0,
            1, 0, 1, -1, 0, 1, 1, 0, -1, -1, 0, -1,
            0, 1, 1, 0, -1, 1, 0, 1, -1, 0, -1, -1};

    static {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        long state = 0x5EED_0F_ED_E11L;
        for (int i = 255; i > 0; i--) {
            state += Rng.GOLDEN_GAMMA;
            int j = (int) Long.remainderUnsigned(Rng.mix64(state), i + 1);
            int swap = p[i];
            p[i] = p[j];
            p[j] = swap;
        }
        for (int i = 0; i < 512; i++) PERM[i] = p[i & 255];
    }

    private SimplexNoise() {
    }

    /** Returns the noise at (x, y, z) and writes its gradient into {@code gradient[offset..offset+2]}. */
    public static double noise(double x, double y, double z, double[] gradient, int offset) {
        double s = (x + y + z) * F3;
        int i = floor(x + s), j = floor(y + s), k = floor(z + s);
        double t = (i + j + k) * G3;
        double x0 = x - (i - t), y0 = y - (j - t), z0 = z - (k - t);

        int i1, j1, k1, i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
            else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
            else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
        } else {
            if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
            else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
            else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        }

        int ii = i & 255, jj = j & 255, kk = k & 255;
        gradient[offset] = 0;
        gradient[offset + 1] = 0;
        gradient[offset + 2] = 0;
        double n = corner(x0, y0, z0, PERM[ii + PERM[jj + PERM[kk]]], gradient, offset);
        n += corner(x0 - i1 + G3, y0 - j1 + G3, z0 - k1 + G3, PERM[ii + i1 + PERM[jj + j1 + PERM[kk + k1]]], gradient, offset);
        n += corner(x0 - i2 + 2 * G3, y0 - j2 + 2 * G3, z0 - k2 + 2 * G3, PERM[ii + i2 + PERM[jj + j2 + PERM[kk + k2]]], gradient, offset);
        n += corner(x0 - 1 + 3 * G3, y0 - 1 + 3 * G3, z0 - 1 + 3 * G3, PERM[ii + 1 + PERM[jj + 1 + PERM[kk + 1]]], gradient, offset);
        gradient[offset] *= 32;
        gradient[offset + 1] *= 32;
        gradient[offset + 2] *= 32;
        return 32 * n;
    }

    /** One corner's contribution {@code t⁴ (g·d)} with {@code t = 0.6 − |d|²}; adds its derivative to the gradient. */
    private static double corner(double x, double y, double z, int hash, double[] gradient, int offset) {
        double t = 0.6 - x * x - y * y - z * z;
        if (t <= 0) return 0;
        int g = (hash % 12) * 3;
        double gx = GRADIENTS[g], gy = GRADIENTS[g + 1], gz = GRADIENTS[g + 2];
        double dot = gx * x + gy * y + gz * z;
        double t2 = t * t, t4 = t2 * t2;
        double radial = -8 * t2 * t * dot;
        gradient[offset] += radial * x + t4 * gx;
        gradient[offset + 1] += radial * y + t4 * gy;
        gradient[offset + 2] += radial * z + t4 * gz;
        return t4 * dot;
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }
}
