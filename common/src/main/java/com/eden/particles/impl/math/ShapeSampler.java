package com.eden.particles.impl.math;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Uniform shape sampling in a +Y-axis local frame. Inverse-CDF only (no rejection), so each sample uses a fixed
 * number of uniforms.
 */
public final class ShapeSampler {

    private static final float TWO_PI = (float) (2.0 * Math.PI);

    /** π(3 − √5) ≈ 137.508° */
    public static final float GOLDEN_ANGLE = (float) (Math.PI * (3.0 - Math.sqrt(5.0)));

    private ShapeSampler() {
    }

    /** Hat-box theorem: the axial coordinate is uniform. */
    public static Vector3f sphereSurface(float u, float v, Vector3f out) {
        float y = 2f * u - 1f;
        return onCircleAt(y, TWO_PI * v, out);
    }

    /** {@code y ≥ 0} */
    public static Vector3f hemisphereSurface(float u, float v, Vector3f out) {
        return onCircleAt(u, TWO_PI * v, out);
    }

    /** {@code r = ∛(w(R₁³ − R₀³) + R₀³)}; inner radius 0 gives a solid ball. */
    public static Vector3f sphereVolume(float innerRadius, float outerRadius, float u, float v, float w, Vector3f out) {
        sphereSurface(u, v, out);
        float r0 = innerRadius * innerRadius * innerRadius;
        float r1 = outerRadius * outerRadius * outerRadius;
        return out.mul((float) Math.cbrt(w * (r1 - r0) + r0));
    }

    /** XZ plane, {@code r = √(u(R₁² − R₀²) + R₀²)}. */
    public static Vector3f disc(float innerRadius, float outerRadius, float u, float v, Vector3f out) {
        float r0 = innerRadius * innerRadius;
        float r1 = outerRadius * outerRadius;
        float r = (float) Math.sqrt(u * (r1 - r0) + r0);
        float theta = TWO_PI * v;
        return out.set(r * (float) Math.cos(theta), 0f, r * (float) Math.sin(theta));
    }

    public static Vector3f ring(float radius, float u, Vector3f out) {
        float theta = TWO_PI * u;
        return out.set(radius * (float) Math.cos(theta), 0f, radius * (float) Math.sin(theta));
    }

    /** {@code cosθ = 1 − u(1 − cos α)}, uniform over the spherical cap. */
    public static Vector3f coneDirection(float halfAngle, float u, float v, Vector3f out) {
        float cosTheta = 1f - u * (1f - (float) Math.cos(halfAngle));
        return onCircleAt(cosTheta, TWO_PI * v, out);
    }

    public static Vector3f boxVolume(float hx, float hy, float hz, float u, float v, float w, Vector3f out) {
        return out.set((2f * u - 1f) * hx, (2f * v - 1f) * hy, (2f * w - 1f) * hz);
    }

    /** Faces weighted by area; {@code u} picks the face pair, then is rescaled to pick the side. */
    public static Vector3f boxSurface(float hx, float hy, float hz, float u, float v, float w, Vector3f out) {
        float areaX = hy * hz; // faces at x = ±hx
        float areaY = hx * hz; // faces at y = ±hy
        float areaZ = hx * hy; // faces at z = ±hz
        float total = areaX + areaY + areaZ;
        if (total <= 0f) {
            return out.set(0f);
        }
        float pick = u * total;
        float a = 2f * v - 1f;
        float b = 2f * w - 1f;
        if (pick < areaX) {
            return out.set(side(pick / areaX) * hx, a * hy, b * hz);
        }
        pick -= areaX;
        if (pick < areaY) {
            return out.set(a * hx, side(pick / areaY) * hy, b * hz);
        }
        pick -= areaY;
        return out.set(a * hx, b * hy, side(pick / areaZ) * hz);
    }

    public static Vector3f line(Vector3fc a, Vector3fc b, float u, Vector3f out) {
        return a.lerp(b, u, out);
    }

    public static Vector3f fibonacciSphere(int k, int n, Vector3f out) {
        float y = 1f - (2f * k + 1f) / n;
        return onCircleAt(y, k * GOLDEN_ANGLE, out);
    }

    /** Vogel's sunflower spiral. */
    public static Vector3f sunflowerDisc(int k, int n, float radius, Vector3f out) {
        float r = radius * (float) Math.sqrt((k + 0.5f) / n);
        float theta = k * GOLDEN_ANGLE;
        return out.set(r * (float) Math.cos(theta), 0f, r * (float) Math.sin(theta));
    }

    public static Vector3f evenRing(int k, int n, float radius, float phase, Vector3f out) {
        float theta = TWO_PI * k / n + phase;
        return out.set(radius * (float) Math.cos(theta), 0f, radius * (float) Math.sin(theta));
    }

    private static Vector3f onCircleAt(float y, float phi, Vector3f out) {
        float r = (float) Math.sqrt(Math.max(0f, 1f - y * y));
        return out.set(r * (float) Math.cos(phi), y, r * (float) Math.sin(phi));
    }

    private static float side(float u) {
        return u < 0.5f ? -1f : 1f;
    }
}
