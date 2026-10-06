package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EasingsTest {

    @Test
    void everyNamedEasingStartsAtZeroAndEndsAtOne() {
        for (String name : Easings.names()) {
            Easing easing = Easings.byName(name).orElseThrow();
            assertEquals(0.0, easing.apply(0.0), 1e-9, name + " at 0");
            assertEquals(1.0, easing.apply(1.0), 1e-9, name + " at 1");
        }
    }

    @Test
    void allPennerFamiliesAreRegistered() {
        for (String family : new String[]{"sine", "quad", "cubic", "quart", "quint", "expo", "circ", "back", "elastic", "bounce"}) {
            for (String prefix : new String[]{"in_", "out_", "in_out_"}) {
                assertTrue(Easings.byName(prefix + family).isPresent(), prefix + family);
            }
        }
    }

    @Test
    void knownValues() {
        assertEquals(0.25, ease("in_quad", 0.5), 1e-12);
        assertEquals(0.75, ease("out_quad", 0.5), 1e-12);
        assertEquals(0.5, ease("in_out_cubic", 0.5), 1e-12);
        assertEquals(0.5, ease("smooth", 0.5), 1e-12);
        assertEquals(0.0, ease("step", 0.999), 0.0);
    }

    @Test
    void outIsTheReflectionOfIn() {
        for (double s = 0.0; s <= 1.0; s += 0.0625) {
            assertEquals(1.0 - ease("in_expo", 1.0 - s), ease("out_expo", s), 1e-12);
        }
    }

    @Test
    void backOvershootsBelowZero() {
        double min = 0.0;
        for (double s = 0.0; s <= 1.0; s += 0.01) {
            min = Math.min(min, ease("in_back", s));
        }
        assertTrue(min < -0.09, "in_back should dip to about -0.1, got " + min);
    }

    @Test
    void cubicBezierIdentityIsLinear() {
        Easing linear = Easings.cubicBezier(0.0, 0.0, 1.0, 1.0);
        for (double s = 0.0; s <= 1.0; s += 0.05) {
            assertEquals(s, linear.apply(s), 1e-7);
        }
    }

    @Test
    void cubicBezierMatchesCssEase() {
        // CSS "ease" is cubic-bezier(0.25, 0.1, 0.25, 1.0); at x = 0.5 browsers give y ≈ 0.8024.
        Easing ease = Easings.cubicBezier(0.25, 0.1, 0.25, 1.0);
        assertEquals(0.8024, ease.apply(0.5), 1e-3);
    }

    @Test
    void cubicBezierIsMonotoneForMonotoneHandles() {
        Easing easing = Easings.cubicBezier(0.9, 0.0, 0.1, 1.0); // steep in the middle
        double previous = 0.0;
        for (double s = 0.0; s <= 1.0; s += 0.001) {
            double value = easing.apply(s);
            assertTrue(value >= previous - 1e-9, "not monotone at " + s);
            previous = value;
        }
    }

    @Test
    void cubicBezierRejectsNonMonotoneTime() {
        assertThrows(IllegalArgumentException.class, () -> Easings.cubicBezier(1.5, 0.0, 0.5, 1.0));
    }

    private static double ease(String name, double s) {
        return Easings.byName(name).orElseThrow().apply(s);
    }
}
