package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BakedCurveTest {

    @Test
    void constantCurvesSkipTheTable() {
        assertInstanceOf(BakedCurve.Constant.class, BakedCurve.bake(FloatCurve.constant(0.25)));
    }

    @Test
    void stepCurvesStayExact() {
        FloatCurve step = FloatCurve.eased(new double[]{0, 0.5}, new double[]{0, 1}, new Easing[]{Easings.STEP});
        BakedCurve baked = BakedCurve.bake(step);
        assertInstanceOf(BakedCurve.Exact.class, baked);
        assertEquals(0f, baked.sample(0.4999f), 0f);
        assertEquals(1f, baked.sample(0.5f), 0f);
    }

    @Test
    void everyEasingBakesWithinTolerance() {
        for (String name : Easings.names()) {
            FloatCurve curve = FloatCurve.eased(new double[]{0, 1}, new double[]{0, 1},
                    new Easing[]{Easings.byName(name).orElseThrow()});
            BakedCurve baked = BakedCurve.bake(curve);
            double worst = 0.0;
            for (int i = 0; i <= 20_000; i++) {
                float t = i / 20_000f;
                worst = Math.max(worst, Math.abs(baked.sample(t) - curve.evaluate(t)));
            }
            // Tolerance is 1e-3 × max(1, peak); overshooting easings peak at about 1.4.
            assertTrue(worst < 1.5e-3, name + " (" + baked.getClass().getSimpleName() + ") error " + worst);
        }
    }

    @Test
    void verticalTangentsFallBackToExactEvaluation() {
        // in_circ has an infinite slope at s = 1, so no table up to MAX_SIZE reaches 1e-3.
        for (String name : new String[]{"in_circ", "out_circ", "in_out_circ"}) {
            FloatCurve curve = FloatCurve.eased(new double[]{0, 1}, new double[]{0, 1},
                    new Easing[]{Easings.byName(name).orElseThrow()});
            assertInstanceOf(BakedCurve.Exact.class, BakedCurve.bake(curve), name);
        }
    }

    @Test
    void tableGrowsOnlyWhenTheCurveNeedsIt() {
        assertEquals(BakedCurve.MIN_SIZE, bakedSize("in_cubic"));
        assertTrue(bakedSize("out_elastic") > BakedCurve.MIN_SIZE, "elastic should need a larger table");
    }

    @Test
    void sampleMatchesTheExactCurve() {
        FloatCurve curve = FloatCurve.cubic(new double[]{0, 0.15, 1}, new double[]{0, 0.25, 0}, FloatCurve.Mode.MONOTONE);
        BakedCurve baked = BakedCurve.bake(curve);
        for (int i = 0; i <= 1000; i++) {
            float t = i / 1000f;
            assertEquals(curve.evaluate(t), baked.sample(t), 1e-3);
        }
    }

    @Test
    void outOfRangeAndNanClamp() {
        BakedCurve baked = BakedCurve.bake(FloatCurve.linear(new double[]{0, 1}, new double[]{2, 4}));
        assertEquals(2f, baked.sample(-1f), 0f);
        assertEquals(4f, baked.sample(2f), 0f);
        assertEquals(2f, baked.sample(Float.NaN), 0f);
    }

    private static int bakedSize(String easing) {
        FloatCurve curve = FloatCurve.eased(new double[]{0, 1}, new double[]{0, 1},
                new Easing[]{Easings.byName(easing).orElseThrow()});
        return ((BakedCurve.Table) BakedCurve.bake(curve)).values().length;
    }
}
