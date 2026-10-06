package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RngTest {

    private static final int N = 100_000;

    @Test
    void sameSeedReplaysTheSameSequence() {
        ParticleRandom a = new ParticleRandom().reset(1234L);
        ParticleRandom b = new ParticleRandom().reset(1234L);
        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
        a.reset(1234L);
        long first = a.nextLong();
        assertEquals(first, new ParticleRandom().reset(1234L).nextLong());
    }

    @Test
    void derivedSeedsDiffer() {
        long effect = 99L;
        assertNotEquals(Rng.derive(effect, 0), Rng.derive(effect, 1));
        assertNotEquals(effect, Rng.derive(effect, 0));
    }

    @Test
    void unitFloatsAreInRangeAndUniform() {
        ParticleRandom random = new ParticleRandom().reset(5L);
        double[] values = new double[N];
        for (int i = 0; i < N; i++) {
            float f = random.nextFloat();
            assertTrue(f >= 0f && f < 1f);
            values[i] = f;
        }
        assertTrue(Stats.chiSquareUniform(values, 50) < Stats.chiSquareCritical(49));
    }

    @Test
    void particleSeedsAcrossIndicesAreUniform() {
        // The first draw of consecutive particles must not be correlated with the index.
        long emitter = Rng.derive(42L, 3);
        double[] values = new double[N];
        for (int i = 0; i < N; i++) {
            values[i] = new ParticleRandom().reset(Rng.derive(emitter, i)).nextFloat();
        }
        assertTrue(Stats.chiSquareUniform(values, 50) < Stats.chiSquareCritical(49));
    }

    @Test
    void nextIntIsUnbiased() {
        ParticleRandom random = new ParticleRandom().reset(11L);
        long[] counts = new long[7];
        for (int i = 0; i < N; i++) {
            counts[random.nextInt(7)]++;
        }
        double[] expected = new double[7];
        java.util.Arrays.fill(expected, N / 7.0);
        assertTrue(Stats.chiSquare(counts, expected) < Stats.chiSquareCritical(6));
    }

    @Test
    void lodKeepsTheRightFractionAndNestedSubsets() {
        long emitter = Rng.derive(7L, 0);
        int keptHigh = 0;
        int keptLow = 0;
        for (int i = 0; i < N; i++) {
            long seed = Rng.derive(emitter, i);
            boolean high = Rng.keep(seed, 2f / 3f);
            boolean low = Rng.keep(seed, 1f / 3f);
            if (high) keptHigh++;
            if (low) {
                keptLow++;
                assertTrue(high, "particle kept at 1/3 must also be kept at 2/3");
            }
        }
        assertEquals(2.0 / 3.0, keptHigh / (double) N, 0.01);
        assertEquals(1.0 / 3.0, keptLow / (double) N, 0.01);
        assertTrue(Rng.keep(1L, 1f));
        assertTrue(!Rng.keep(1L, 0f));
    }
}
