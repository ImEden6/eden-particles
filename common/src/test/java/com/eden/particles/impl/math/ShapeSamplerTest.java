package com.eden.particles.impl.math;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeSamplerTest {

    private static final int N = 100_000;
    private static final float R = 2.0f;

    @Test
    void sphereSurfaceIsUnitLengthWithUniformAxialCoordinate() {
        ParticleRandom random = new ParticleRandom().reset(1L);
        Vector3f p = new Vector3f();
        double[] y01 = new double[N];
        for (int i = 0; i < N; i++) {
            ShapeSampler.sphereSurface(random.nextFloat(), random.nextFloat(), p);
            assertEquals(1.0, p.length(), 1e-5);
            y01[i] = (p.y + 1.0) / 2.0;
        }
        assertTrue(Stats.chiSquareUniform(y01, 20) < Stats.chiSquareCritical(19));
    }

    @Test
    void sphereSurfaceHasNoPreferredDirection() {
        ParticleRandom random = new ParticleRandom().reset(2L);
        Vector3f p = new Vector3f();
        Vector3f sum = new Vector3f();
        for (int i = 0; i < N; i++) {
            sum.add(ShapeSampler.sphereSurface(random.nextFloat(), random.nextFloat(), p));
        }
        // Mean of a uniform unit vector is 0; each component has variance 1/3, so the mean's sd is √(1/(3N)).
        double tolerance = 4.0 * Math.sqrt(1.0 / (3.0 * N));
        assertEquals(0.0, sum.x / N, tolerance);
        assertEquals(0.0, sum.y / N, tolerance);
        assertEquals(0.0, sum.z / N, tolerance);
    }

    @Test
    void ballRadiusFollowsTheCubicCdf() {
        ParticleRandom random = new ParticleRandom().reset(3L);
        Vector3f p = new Vector3f();
        double[] radii = new double[N];
        double sum = 0.0;
        for (int i = 0; i < N; i++) {
            ShapeSampler.sphereVolume(0f, R, random.nextFloat(), random.nextFloat(), random.nextFloat(), p);
            radii[i] = p.length();
            sum += radii[i];
        }
        double d = Stats.ksStatistic(radii, r -> Math.pow(r / R, 3));
        assertTrue(d < Stats.ksCritical(N), "KS D=" + d);
        assertEquals(0.75 * R, sum / N, 0.01); // E[r] = 3R/4
    }

    @Test
    void shellStaysBetweenItsRadii() {
        ParticleRandom random = new ParticleRandom().reset(4L);
        Vector3f p = new Vector3f();
        double[] radii = new double[N];
        float inner = 1.0f;
        for (int i = 0; i < N; i++) {
            ShapeSampler.sphereVolume(inner, R, random.nextFloat(), random.nextFloat(), random.nextFloat(), p);
            radii[i] = p.length();
            assertTrue(radii[i] >= inner - 1e-4 && radii[i] <= R + 1e-4);
        }
        double r0 = inner * inner * inner;
        double r1 = R * R * R;
        double d = Stats.ksStatistic(radii, r -> (r * r * r - r0) / (r1 - r0));
        assertTrue(d < Stats.ksCritical(N), "KS D=" + d);
    }

    @Test
    void discRadiusFollowsTheQuadraticCdf() {
        ParticleRandom random = new ParticleRandom().reset(5L);
        Vector3f p = new Vector3f();
        double[] radii = new double[N];
        double sum = 0.0;
        for (int i = 0; i < N; i++) {
            ShapeSampler.disc(0f, R, random.nextFloat(), random.nextFloat(), p);
            assertEquals(0f, p.y, 0f);
            radii[i] = p.length();
            sum += radii[i];
        }
        double d = Stats.ksStatistic(radii, r -> (r / R) * (r / R));
        assertTrue(d < Stats.ksCritical(N), "KS D=" + d);
        assertEquals(2.0 * R / 3.0, sum / N, 0.01); // E[r] = 2R/3
    }

    @Test
    void coneDirectionsAreUniformOverTheCap() {
        ParticleRandom random = new ParticleRandom().reset(6L);
        Vector3f p = new Vector3f();
        float halfAngle = (float) Math.toRadians(30);
        double cosAlpha = Math.cos(halfAngle);
        double[] cosTheta = new double[N];
        for (int i = 0; i < N; i++) {
            ShapeSampler.coneDirection(halfAngle, random.nextFloat(), random.nextFloat(), p);
            assertEquals(1.0, p.length(), 1e-5);
            assertTrue(p.y >= cosAlpha - 1e-5, "outside the cone");
            cosTheta[i] = p.y;
        }
        // Uniform over the cap ⇔ cosθ uniform on [cos α, 1].
        double d = Stats.ksStatistic(cosTheta, c -> (c - cosAlpha) / (1.0 - cosAlpha));
        assertTrue(d < Stats.ksCritical(N), "KS D=" + d);
    }

    @Test
    void boxSurfacePicksFacesByArea() {
        ParticleRandom random = new ParticleRandom().reset(7L);
        Vector3f p = new Vector3f();
        float hx = 3f, hy = 1f, hz = 0.5f;
        long[] faces = new long[3];
        for (int i = 0; i < N; i++) {
            ShapeSampler.boxSurface(hx, hy, hz, random.nextFloat(), random.nextFloat(), random.nextFloat(), p);
            if (Math.abs(Math.abs(p.x) - hx) < 1e-5) faces[0]++;
            else if (Math.abs(Math.abs(p.y) - hy) < 1e-5) faces[1]++;
            else if (Math.abs(Math.abs(p.z) - hz) < 1e-5) faces[2]++;
            else throw new AssertionError("not on the surface: " + p);
        }
        double ax = hy * hz, ay = hx * hz, az = hx * hy, total = ax + ay + az;
        double[] expected = {N * ax / total, N * ay / total, N * az / total};
        assertTrue(Stats.chiSquare(faces, expected) < Stats.chiSquareCritical(2));
    }

    @Test
    void fibonacciSphereIsEvenlySpread() {
        int n = 500;
        Vector3f[] points = new Vector3f[n];
        for (int k = 0; k < n; k++) {
            points[k] = ShapeSampler.fibonacciSphere(k, n, new Vector3f());
            assertEquals(1.0, points[k].length(), 1e-5);
        }
        // Nearest-neighbour distances on an even layout cluster tightly around √(4π/n)·~1.
        double minNearest = Double.MAX_VALUE;
        double maxNearest = 0.0;
        for (int i = 0; i < n; i++) {
            double nearest = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (i != j) nearest = Math.min(nearest, points[i].distance(points[j]));
            }
            minNearest = Math.min(minNearest, nearest);
            maxNearest = Math.max(maxNearest, nearest);
        }
        assertTrue(maxNearest / minNearest < 2.0, "uneven spacing: ratio " + maxNearest / minNearest);
    }

    @Test
    void sunflowerStaysInsideTheDisc() {
        Vector3f p = new Vector3f();
        for (int k = 0; k < 300; k++) {
            ShapeSampler.sunflowerDisc(k, 300, R, p);
            assertTrue(p.length() <= R + 1e-5);
        }
    }
}
