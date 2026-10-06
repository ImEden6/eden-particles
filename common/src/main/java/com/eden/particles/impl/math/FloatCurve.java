package com.eden.particles.impl.math;

import java.util.Arrays;

/** Scalar keyframe curve, evaluated exactly at bake time. Holds the end values outside its keys. */
public final class FloatCurve {

    public enum Mode {
        /** Per-segment easing: {@code vᵢ + (vᵢ₊₁ − vᵢ)·E(s)}. */
        EASED,
        /** Cubic Hermite, Fritsch–Carlson (PCHIP) tangents; never overshoots its keys. */
        MONOTONE,
        /** Cubic Hermite, Catmull–Rom tangents; can overshoot. */
        CATMULL_ROM
    }

    private final Mode mode;
    private final double[] times;
    private final double[] values;
    private final Easing[] easings;   // EASED only, one per segment
    private final double[] tangents;  // cubic modes only, one per key

    private FloatCurve(Mode mode, double[] times, double[] values, Easing[] easings) {
        validate(times, values);
        this.mode = mode;
        this.times = times;
        this.values = values;
        this.easings = easings;
        this.tangents = switch (mode) {
            case EASED -> null;
            case MONOTONE -> monotoneTangents(times, values);
            case CATMULL_ROM -> catmullRomTangents(times, values);
        };
    }

    public static FloatCurve constant(double value) {
        return new FloatCurve(Mode.EASED, new double[]{0.0}, new double[]{value}, new Easing[0]);
    }

    public static FloatCurve linear(double[] times, double[] values) {
        Easing[] easings = new Easing[Math.max(0, times.length - 1)];
        Arrays.fill(easings, Easings.LINEAR);
        return eased(times, values, easings);
    }

    public static FloatCurve eased(double[] times, double[] values, Easing[] easings) {
        if (easings.length != Math.max(0, times.length - 1)) {
            throw new IllegalArgumentException("Expected " + (times.length - 1) + " segment easings, got " + easings.length);
        }
        return new FloatCurve(Mode.EASED, times.clone(), values.clone(), easings.clone());
    }

    public static FloatCurve cubic(double[] times, double[] values, Mode mode) {
        if (mode == Mode.EASED) {
            throw new IllegalArgumentException("Use eased() for EASED curves");
        }
        return new FloatCurve(mode, times.clone(), values.clone(), null);
    }

    public double evaluate(double t) {
        int n = times.length;
        if (n == 1 || t <= times[0]) return values[0];
        if (t >= times[n - 1]) return values[n - 1];

        int i = segmentIndex(t);
        double t0 = times[i];
        double h = times[i + 1] - t0;
        double s = (t - t0) / h;
        double v0 = values[i];
        double v1 = values[i + 1];

        if (mode == Mode.EASED) {
            return v0 + (v1 - v0) * easings[i].apply(s);
        }
        double s2 = s * s;
        double s3 = s2 * s;
        double h00 = 2 * s3 - 3 * s2 + 1;
        double h10 = s3 - 2 * s2 + s;
        double h01 = -2 * s3 + 3 * s2;
        double h11 = s3 - s2;
        return h00 * v0 + h10 * h * tangents[i] + h01 * v1 + h11 * h * tangents[i + 1];
    }

    public Mode mode() {
        return mode;
    }

    public int keyCount() {
        return times.length;
    }

    public double keyTime(int index) {
        return times[index];
    }

    public double keyValue(int index) {
        return values[index];
    }

    public boolean isConstant() {
        for (double v : values) {
            if (v != values[0]) return false;
        }
        return true;
    }

    public boolean isDiscontinuous() {
        if (easings == null) return false;
        for (Easing easing : easings) {
            if (easing.isDiscontinuous()) return true;
        }
        return false;
    }

    /** Largest {@code i ≤ n−2} with {@code times[i] ≤ t}; assumes {@code times[0] < t < times[n−1]}. */
    private int segmentIndex(double t) {
        int lo = 0;
        int hi = times.length - 2;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (times[mid] <= t) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static void validate(double[] times, double[] values) {
        if (times.length == 0) {
            throw new IllegalArgumentException("A curve needs at least one key");
        }
        if (times.length != values.length) {
            throw new IllegalArgumentException("Got " + times.length + " times but " + values.length + " values");
        }
        for (int i = 0; i < times.length; i++) {
            if (!Double.isFinite(times[i]) || !Double.isFinite(values[i])) {
                throw new IllegalArgumentException("Key " + i + " is not finite");
            }
            if (i > 0 && !(times[i] > times[i - 1])) {
                throw new IllegalArgumentException("Key times must be strictly increasing, but key " + i
                        + " (t=" + times[i] + ") does not come after t=" + times[i - 1]);
            }
        }
    }

    /** PCHIP: zero at extrema, else weighted harmonic mean of neighbouring slopes; three-point formula at the ends. */
    private static double[] monotoneTangents(double[] t, double[] v) {
        int n = t.length;
        double[] m = new double[n];
        if (n < 2) return m;

        double[] h = new double[n - 1];
        double[] delta = new double[n - 1];
        for (int k = 0; k < n - 1; k++) {
            h[k] = t[k + 1] - t[k];
            delta[k] = (v[k + 1] - v[k]) / h[k];
        }
        if (n == 2) {
            m[0] = m[1] = delta[0];
            return m;
        }
        for (int k = 1; k < n - 1; k++) {
            if (delta[k - 1] * delta[k] <= 0.0) {
                m[k] = 0.0;
            } else {
                double w1 = 2.0 * h[k] + h[k - 1];
                double w2 = h[k] + 2.0 * h[k - 1];
                m[k] = (w1 + w2) / (w1 / delta[k - 1] + w2 / delta[k]);
            }
        }
        m[0] = monotoneEndTangent(h[0], h[1], delta[0], delta[1]);
        m[n - 1] = monotoneEndTangent(h[n - 2], h[n - 3], delta[n - 2], delta[n - 3]);
        return m;
    }

    private static double monotoneEndTangent(double h0, double h1, double d0, double d1) {
        double m = ((2.0 * h0 + h1) * d0 - h0 * d1) / (h0 + h1);
        if (Math.signum(m) != Math.signum(d0)) {
            return 0.0;
        }
        if (Math.signum(d0) != Math.signum(d1) && Math.abs(m) > Math.abs(3.0 * d0)) {
            return 3.0 * d0;
        }
        return m;
    }

    private static double[] catmullRomTangents(double[] t, double[] v) {
        int n = t.length;
        double[] m = new double[n];
        if (n < 2) return m;
        m[0] = (v[1] - v[0]) / (t[1] - t[0]);
        m[n - 1] = (v[n - 1] - v[n - 2]) / (t[n - 1] - t[n - 2]);
        for (int k = 1; k < n - 1; k++) {
            m[k] = (v[k + 1] - v[k - 1]) / (t[k + 1] - t[k - 1]);
        }
        return m;
    }
}
