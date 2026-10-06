package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorTest {

    @Test
    void whiteIsLightnessOneWithNoChroma() {
        double[] lab = new double[3];
        ColorMath.linearToOklab(1, 1, 1, lab);
        assertEquals(1.0, lab[0], 1e-4);
        assertEquals(0.0, lab[1], 1e-4);
        assertEquals(0.0, lab[2], 1e-4);
    }

    @Test
    void pureRedMatchesTheCssReference() {
        // CSS Color 4: #ff0000 is oklab(0.62796 0.22486 0.12585).
        double[] lab = new double[3];
        ColorMath.linearToOklab(1, 0, 0, lab);
        assertEquals(0.62796, lab[0], 1e-3);
        assertEquals(0.22486, lab[1], 1e-3);
        assertEquals(0.12585, lab[2], 1e-3);
    }

    @Test
    void oklabRoundTrips() {
        Random random = new Random(7);
        double[] lab = new double[3];
        double[] rgb = new double[3];
        for (int i = 0; i < 10_000; i++) {
            double r = random.nextDouble(), g = random.nextDouble(), b = random.nextDouble();
            ColorMath.linearToOklab(r, g, b, lab);
            ColorMath.oklabToLinear(lab[0], lab[1], lab[2], rgb);
            assertEquals(r, rgb[0], 1e-4);
            assertEquals(g, rgb[1], 1e-4);
            assertEquals(b, rgb[2], 1e-4);
        }
    }

    @Test
    void srgbTransferRoundTrips() {
        for (double c = 0.0; c <= 1.0; c += 0.01) {
            assertEquals(c, ColorMath.linearToSrgb(ColorMath.srgbToLinear(c)), 1e-9);
        }
    }

    @Test
    void gradientEndpointsAreExact() {
        Gradient gradient = Gradient.linear(Gradient.Space.OKLAB, new double[]{0, 1}, new int[]{0xD8C4FF, 0x5A3CFF});
        assertEquals(0xD8C4FF, gradient.evaluate(0));
        assertEquals(0x5A3CFF, gradient.evaluate(1));
        BakedGradient baked = gradient.bake();
        assertEquals(0xD8C4FF, baked.sample(0f));
        assertEquals(0x5A3CFF, baked.sample(1f));
    }

    @Test
    void oklabMidpointIsBrighterThanSrgbForRedToGreen() {
        // The classic case: naive sRGB blending of red and green gives a dark, muddy midpoint.
        int srgb = Gradient.linear(Gradient.Space.SRGB, new double[]{0, 1}, new int[]{0xFF0000, 0x00FF00}).evaluate(0.5);
        int oklab = Gradient.linear(Gradient.Space.OKLAB, new double[]{0, 1}, new int[]{0xFF0000, 0x00FF00}).evaluate(0.5);
        assertNotEquals(srgb, oklab);
        assertTrue(lightness(oklab) > lightness(srgb) + 0.05,
                "OKLab midpoint " + Integer.toHexString(oklab) + " should be clearly lighter than sRGB " + Integer.toHexString(srgb));
    }

    @Test
    void oklabLightnessChangesEvenlyAcrossAGradient() {
        // Halfway through black → white in OKLab is halfway in perceived lightness.
        int mid = Gradient.linear(Gradient.Space.OKLAB, new double[]{0, 1}, new int[]{0x000000, 0xFFFFFF}).evaluate(0.5);
        assertEquals(0.5, lightness(mid), 0.01);
    }

    private static double lightness(int rgb) {
        double[] lab = new double[3];
        ColorMath.linearToOklab(
                ColorMath.srgbToLinear(ColorMath.red(rgb)),
                ColorMath.srgbToLinear(ColorMath.green(rgb)),
                ColorMath.srgbToLinear(ColorMath.blue(rgb)), lab);
        return lab[0];
    }
}
