package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatCurveTest {

    @Test
    void clampsOutsideTheKeys() {
        FloatCurve curve = FloatCurve.linear(new double[]{0.2, 0.8}, new double[]{1.0, 3.0});
        assertEquals(1.0, curve.evaluate(0.0), 0.0);
        assertEquals(3.0, curve.evaluate(1.0), 0.0);
        assertEquals(2.0, curve.evaluate(0.5), 1e-12);
    }

    @Test
    void hitsEveryKeyExactly() {
        double[] t = {0.0, 0.3, 0.55, 1.0};
        double[] v = {0.0, 2.0, -1.0, 4.0};
        for (FloatCurve.Mode mode : new FloatCurve.Mode[]{FloatCurve.Mode.MONOTONE, FloatCurve.Mode.CATMULL_ROM}) {
            FloatCurve curve = FloatCurve.cubic(t, v, mode);
            for (int i = 0; i < t.length; i++) {
                assertEquals(v[i], curve.evaluate(t[i]), 1e-12, mode + " key " + i);
            }
        }
    }

    @Test
    void stepHoldsUntilTheNextKey() {
        FloatCurve curve = FloatCurve.eased(new double[]{0.0, 0.5, 1.0}, new double[]{1.0, 5.0, 2.0},
                new Easing[]{Easings.STEP, Easings.STEP});
        assertEquals(1.0, curve.evaluate(0.49), 0.0);
        assertEquals(5.0, curve.evaluate(0.5), 0.0);
        assertEquals(5.0, curve.evaluate(0.99), 0.0);
        assertTrue(curve.isDiscontinuous());
    }

    @Test
    void catmullRomOvershootsWhereMonotoneDoesNot() {
        // Flat, then a sharp rise: Catmull–Rom's tangent at the corner makes it dip below zero first.
        double[] t = {0.0, 0.4, 0.5, 1.0};
        double[] v = {0.0, 0.0, 1.0, 1.0};
        FloatCurve catmull = FloatCurve.cubic(t, v, FloatCurve.Mode.CATMULL_ROM);
        FloatCurve monotone = FloatCurve.cubic(t, v, FloatCurve.Mode.MONOTONE);

        double catmullMin = Double.MAX_VALUE;
        double monotoneMin = Double.MAX_VALUE;
        double monotoneMax = -Double.MAX_VALUE;
        for (int i = 0; i <= 10_000; i++) {
            double x = i / 10_000.0;
            catmullMin = Math.min(catmullMin, catmull.evaluate(x));
            monotoneMin = Math.min(monotoneMin, monotone.evaluate(x));
            monotoneMax = Math.max(monotoneMax, monotone.evaluate(x));
        }
        assertTrue(catmullMin < -0.01, "expected Catmull–Rom overshoot, min was " + catmullMin);
        assertTrue(monotoneMin >= -1e-12, "monotone dipped to " + monotoneMin);
        assertTrue(monotoneMax <= 1.0 + 1e-12, "monotone rose to " + monotoneMax);
    }

    @Test
    void monotoneNeverLeavesTheRangeOfEachSegment() {
        Random random = new Random(42);
        for (int trial = 0; trial < 500; trial++) {
            int n = 2 + random.nextInt(7);
            double[] t = new double[n];
            double[] v = new double[n];
            double time = 0.0;
            for (int i = 0; i < n; i++) {
                time += 0.01 + random.nextDouble();
                t[i] = time;
                v[i] = random.nextGaussian() * 5.0;
            }
            FloatCurve curve = FloatCurve.cubic(t, v, FloatCurve.Mode.MONOTONE);
            for (int i = 0; i < n - 1; i++) {
                double lo = Math.min(v[i], v[i + 1]) - 1e-9;
                double hi = Math.max(v[i], v[i + 1]) + 1e-9;
                for (int j = 0; j <= 50; j++) {
                    double x = t[i] + (t[i + 1] - t[i]) * j / 50.0;
                    double y = curve.evaluate(x);
                    assertTrue(y >= lo && y <= hi, "trial " + trial + " segment " + i + ": " + y + " outside [" + lo + ", " + hi + "]");
                }
            }
        }
    }

    @Test
    void constantDetection() {
        assertTrue(FloatCurve.constant(3.0).isConstant());
        assertTrue(FloatCurve.cubic(new double[]{0, 1}, new double[]{2, 2}, FloatCurve.Mode.MONOTONE).isConstant());
        assertFalse(FloatCurve.linear(new double[]{0, 1}, new double[]{2, 3}).isConstant());
    }

    @Test
    void rejectsUnsortedKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> FloatCurve.linear(new double[]{0.5, 0.5}, new double[]{1, 2}));
        assertThrows(IllegalArgumentException.class,
                () -> FloatCurve.linear(new double[]{0.6, 0.2}, new double[]{1, 2}));
    }
}
