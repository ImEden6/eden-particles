package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurlNoiseTest {

    @Test
    void isDivergenceFree() {
        SplittableRandom random = new SplittableRandom(3);
        double[] a = new double[3], b = new double[3];
        double h = 1e-5;
        for (int octaves = 1; octaves <= 2; octaves++) {
            for (int n = 0; n < 300; n++) {
                double x = random.nextDouble(-100, 100), y = random.nextDouble(-100, 100), z = random.nextDouble(-100, 100);
                double divergence = 0;
                CurlNoise.sample(x + h, y, z, octaves, a);
                CurlNoise.sample(x - h, y, z, octaves, b);
                divergence += (a[0] - b[0]) / (2 * h);
                CurlNoise.sample(x, y + h, z, octaves, a);
                CurlNoise.sample(x, y - h, z, octaves, b);
                divergence += (a[1] - b[1]) / (2 * h);
                CurlNoise.sample(x, y, z + h, octaves, a);
                CurlNoise.sample(x, y, z - h, octaves, b);
                divergence += (a[2] - b[2]) / (2 * h);
                assertEquals(0, divergence, 1e-3, "at " + x + ", " + y + ", " + z);
            }
        }
    }

    @Test
    void meanLengthIsAboutOne() {
        for (int octaves = 1; octaves <= 2; octaves++) {
            double mean = meanLength(octaves);
            assertEquals(1, mean, 0.03, octaves + " octave(s)");
        }
    }

    @Test
    void secondOctaveChangesTheField() {
        double[] one = new double[3], two = new double[3];
        CurlNoise.sample(1.3, 2.7, -0.4, 1, one);
        CurlNoise.sample(1.3, 2.7, -0.4, 2, two);
        assertTrue(Math.abs(one[0] - two[0]) + Math.abs(one[1] - two[1]) + Math.abs(one[2] - two[2]) > 1e-3);
    }

    static double meanLength(int octaves) {
        SplittableRandom random = new SplittableRandom(4);
        double[] v = new double[3];
        double sum = 0;
        int count = 200_000;
        for (int n = 0; n < count; n++) {
            CurlNoise.sample(random.nextDouble(-500, 500), random.nextDouble(-500, 500), random.nextDouble(-500, 500), octaves, v);
            sum += Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        }
        return sum / count;
    }
}
