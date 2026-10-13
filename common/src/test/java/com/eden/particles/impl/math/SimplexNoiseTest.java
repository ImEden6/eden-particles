package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimplexNoiseTest {

    @Test
    void gradientMatchesFiniteDifferences() {
        SplittableRandom random = new SplittableRandom(1);
        double[] g = new double[3];
        double[] unused = new double[3];
        double h = 1e-6;
        for (int n = 0; n < 500; n++) {
            double x = random.nextDouble(-50, 50), y = random.nextDouble(-50, 50), z = random.nextDouble(-50, 50);
            SimplexNoise.noise(x, y, z, g, 0);
            assertEquals((SimplexNoise.noise(x + h, y, z, unused, 0) - SimplexNoise.noise(x - h, y, z, unused, 0)) / (2 * h), g[0], 1e-4);
            assertEquals((SimplexNoise.noise(x, y + h, z, unused, 0) - SimplexNoise.noise(x, y - h, z, unused, 0)) / (2 * h), g[1], 1e-4);
            assertEquals((SimplexNoise.noise(x, y, z + h, unused, 0) - SimplexNoise.noise(x, y, z - h, unused, 0)) / (2 * h), g[2], 1e-4);
        }
    }

    @Test
    void staysInRangeAndVaries() {
        SplittableRandom random = new SplittableRandom(2);
        double[] g = new double[3];
        double min = 0, max = 0;
        for (int n = 0; n < 100_000; n++) {
            double v = SimplexNoise.noise(random.nextDouble(-1000, 1000), random.nextDouble(-1000, 1000), random.nextDouble(-1000, 1000), g, 0);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        assertTrue(min > -1.1 && min < -0.6, "min " + min);
        assertTrue(max < 1.1 && max > 0.6, "max " + max);
    }

    @Test
    void worksFarFromTheOrigin() {
        double[] g = new double[3];
        double a = SimplexNoise.noise(2.0e7 + 0.25, 64.5, -2.0e7 + 0.75, g, 0);
        double b = SimplexNoise.noise(2.0e7 + 0.2501, 64.5, -2.0e7 + 0.75, g, 0);
        assertEquals(a, b, 1e-3);
        assertTrue(a != 0 || b != 0);
    }
}
