package com.eden.particles.impl.math;

/** Solves {@code X(s) = x}, then returns {@code Y(s)}. Newton first, bisection fallback, as in WebKit's UnitBezier. */
final class CubicBezierEasing implements Easing {

    private static final double EPSILON = 1e-9;
    private static final int NEWTON_ITERATIONS = 8;
    private static final int MAX_BISECTIONS = 64;

    private final double ax, bx, cx;
    private final double ay, by, cy;

    CubicBezierEasing(double x1, double y1, double x2, double y2) {
        if (!(x1 >= 0.0 && x1 <= 1.0 && x2 >= 0.0 && x2 <= 1.0)) {
            throw new IllegalArgumentException("cubic_bezier x values must be in [0,1], got x1=" + x1 + ", x2=" + x2);
        }
        if (!Double.isFinite(y1) || !Double.isFinite(y2)) {
            throw new IllegalArgumentException("cubic_bezier y values must be finite");
        }
        cx = 3.0 * x1;
        bx = 3.0 * (x2 - x1) - cx;
        ax = 1.0 - cx - bx;
        cy = 3.0 * y1;
        by = 3.0 * (y2 - y1) - cy;
        ay = 1.0 - cy - by;
    }

    @Override
    public double apply(double x) {
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;
        return sampleY(solveX(x));
    }

    private double sampleX(double s) {
        return ((ax * s + bx) * s + cx) * s;
    }

    private double sampleY(double s) {
        return ((ay * s + by) * s + cy) * s;
    }

    private double sampleDerivativeX(double s) {
        return (3.0 * ax * s + 2.0 * bx) * s + cx;
    }

    private double solveX(double x) {
        double s = x;
        for (int i = 0; i < NEWTON_ITERATIONS; i++) {
            double error = sampleX(s) - x;
            if (Math.abs(error) < EPSILON) {
                return s;
            }
            double derivative = sampleDerivativeX(s);
            if (Math.abs(derivative) < 1e-6) {
                break;
            }
            s -= error / derivative;
        }

        double lo = 0.0;
        double hi = 1.0;
        s = x;
        for (int i = 0; i < MAX_BISECTIONS && lo < hi; i++) {
            double xs = sampleX(s);
            if (Math.abs(xs - x) < EPSILON) {
                return s;
            }
            if (x > xs) {
                lo = s;
            } else {
                hi = s;
            }
            s = (lo + hi) * 0.5;
        }
        return s;
    }
}
