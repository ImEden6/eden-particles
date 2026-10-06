package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnIntegratorTest {

    private static int spawnAll(SpawnIntegrator integrator) {
        SpawnIntegrator.Accumulator accumulator = new SpawnIntegrator.Accumulator();
        int total = 0;
        for (int tick = 0; tick < integrator.duration(); tick++) {
            int n = accumulator.next(integrator, tick);
            assertTrue(n >= 0);
            total += n;
        }
        return total;
    }

    @Test
    void steepShortRampSpawnsItsIntegral() {
        // 0 → 20 per tick over 3 ticks: average 10, so 30 particles. Point-sampling would give 0 + 6.7 + 13.3.
        SpawnIntegrator integrator = new SpawnIntegrator(FloatCurve.linear(new double[]{0, 1}, new double[]{0, 20}), 3);
        assertEquals(30.0, integrator.expectedTotal(), 1e-6);
        assertEquals(30, spawnAll(integrator));
        // 3 · ∫₀^⅓ 20u du = 10/3. A single tick carries the table's interpolation error,
        // D · h²/8 · max|r'| = 3 · (1/1024)²/8 · 20 ≈ 7e-6; the total above telescopes and is exact.
        assertEquals(10.0 / 3.0, integrator.expectedInTick(0), 1e-5);
    }

    @Test
    void fractionalRatesAccumulate() {
        SpawnIntegrator integrator = new SpawnIntegrator(FloatCurve.constant(0.3), 10);
        assertEquals(3, spawnAll(integrator));
    }

    @Test
    void totalsMatchForEasedAndCubicCurves() {
        FloatCurve eased = FloatCurve.eased(new double[]{0, 0.3, 1}, new double[]{2, 12, 0},
                new Easing[]{Easings.byName("out_cubic").orElseThrow(), Easings.byName("in_out_sine").orElseThrow()});
        FloatCurve cubic = FloatCurve.cubic(new double[]{0, 0.2, 0.7, 1}, new double[]{0, 8, 3, 6}, FloatCurve.Mode.MONOTONE);
        for (FloatCurve curve : new FloatCurve[]{eased, cubic}) {
            for (int duration : new int[]{1, 7, 30, 200}) {
                SpawnIntegrator integrator = new SpawnIntegrator(curve, duration);
                double exact = duration * simpson(curve, 20_000);
                assertEquals(exact, integrator.expectedTotal(), 1e-3 * Math.max(1, exact));
                int spawned = spawnAll(integrator);
                assertTrue(Math.abs(spawned - integrator.expectedTotal()) <= 1.0,
                        "spawned " + spawned + " vs expected " + integrator.expectedTotal());
            }
        }
    }

    @Test
    void stepCurveIntegratesHeldValues() {
        // 10/tick for the first half, 0 after: 10 × 50 ticks = 500.
        FloatCurve step = FloatCurve.eased(new double[]{0, 0.5}, new double[]{10, 0}, new Easing[]{Easings.STEP});
        SpawnIntegrator integrator = new SpawnIntegrator(step, 100);
        assertEquals(500.0, integrator.expectedTotal(), 1e-6);
        assertEquals(500, spawnAll(integrator));
        assertEquals(0.0, integrator.expectedInTick(75), 1e-9);
    }

    @Test
    void negativeRatesCountAsZero() {
        FloatCurve dips = FloatCurve.linear(new double[]{0, 1}, new double[]{-5, 5});
        SpawnIntegrator integrator = new SpawnIntegrator(dips, 10);
        assertEquals(10 * 1.25, integrator.expectedTotal(), 1e-6); // ∫ max(0, 10u − 5) du = 1.25
    }

    @Test
    void poissonHasTheRightMean() {
        ParticleRandom random = new ParticleRandom().reset(9L);
        for (double lambda : new double[]{0.5, 4.0, 50.0}) {
            long sum = 0;
            int n = 100_000;
            for (int i = 0; i < n; i++) {
                sum += SpawnIntegrator.poisson(lambda, random);
            }
            // sd of the mean is √(λ/n); allow 5 sd.
            assertEquals(lambda, sum / (double) n, 5.0 * Math.sqrt(lambda / n), "lambda=" + lambda);
        }
    }

    @Test
    void subTickOffsetsAreStratified() {
        assertEquals(0.125f, SpawnIntegrator.subTickOffset(0, 4, 0.5f), 0f);
        assertEquals(0.875f, SpawnIntegrator.subTickOffset(3, 4, 0.5f), 0f);
    }

    /** Reference integral of max(0, f) over [0,1] by fine composite Simpson. */
    private static double simpson(FloatCurve curve, int panels) {
        double h = 1.0 / panels;
        double sum = 0.0;
        for (int i = 0; i < panels; i++) {
            double a = i * h;
            sum += h / 6.0 * (Math.max(0, curve.evaluate(a)) + 4 * Math.max(0, curve.evaluate(a + h / 2)) + Math.max(0, curve.evaluate(a + h)));
        }
        return sum;
    }
}
