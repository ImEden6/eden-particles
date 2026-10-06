package com.eden.particles.impl.math;

import java.util.Arrays;
import java.util.function.DoubleUnaryOperator;

/** Goodness-of-fit checks at α = 0.001. Callers use fixed seeds, so results are deterministic. */
final class Stats {

    private static final double Z_999 = 3.090232;

    private Stats() {
    }

    static double ksStatistic(double[] samples, DoubleUnaryOperator cdf) {
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        double d = 0.0;
        for (int i = 0; i < n; i++) {
            double f = cdf.applyAsDouble(sorted[i]);
            d = Math.max(d, Math.max((i + 1) / (double) n - f, f - i / (double) n));
        }
        return d;
    }

    /** {@code √(−ln(α/2)/2) / √n} */
    static double ksCritical(int n) {
        return Math.sqrt(-Math.log(0.0005) / 2.0) / Math.sqrt(n);
    }

    static double chiSquare(long[] observed, double[] expected) {
        double sum = 0.0;
        for (int i = 0; i < observed.length; i++) {
            double diff = observed[i] - expected[i];
            sum += diff * diff / expected[i];
        }
        return sum;
    }

    /** Wilson–Hilferty: {@code k(1 − 2/(9k) + z·√(2/(9k)))³}. */
    static double chiSquareCritical(int degreesOfFreedom) {
        double k = degreesOfFreedom;
        double c = 2.0 / (9.0 * k);
        double base = 1.0 - c + Z_999 * Math.sqrt(c);
        return k * base * base * base;
    }

    static double chiSquareUniform(double[] values, int bins) {
        long[] observed = new long[bins];
        for (double v : values) {
            observed[Math.min(bins - 1, (int) (v * bins))]++;
        }
        double[] expected = new double[bins];
        Arrays.fill(expected, values.length / (double) bins);
        return chiSquare(observed, expected);
    }
}
