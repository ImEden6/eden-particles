package com.eden.particles.impl.math;

/**
 * A curve prepared for per-tick sampling over {@code t ∈ [0,1]}. Tables grow from {@value #MIN_SIZE} to
 * {@value #MAX_SIZE} samples until within tolerance; curves no table can follow (e.g. circ's vertical tangent)
 * are evaluated exactly.
 */
public sealed interface BakedCurve permits BakedCurve.Constant, BakedCurve.Table, BakedCurve.Exact {

    int MIN_SIZE = 128;
    int MAX_SIZE = 1024;

    /** Scaled by {@code max(1, peak |value|)}. */
    double RELATIVE_TOLERANCE = 1e-3;

    float sample(float t);

    static BakedCurve bake(FloatCurve curve) {
        if (curve.isConstant()) {
            return new Constant((float) curve.keyValue(0));
        }
        if (curve.isDiscontinuous()) {
            return new Exact(curve);
        }

        int size = MIN_SIZE;
        float[] values = tabulate(curve, size);
        double error = measureError(curve, values);
        double tolerance = RELATIVE_TOLERANCE * Math.max(1.0, peakMagnitude(values));
        while (error > tolerance && size < MAX_SIZE) {
            size *= 2;
            values = tabulate(curve, size);
            error = measureError(curve, values);
        }
        return error <= tolerance ? new Table(values, error) : new Exact(curve);
    }

    private static float[] tabulate(FloatCurve curve, int size) {
        float[] values = new float[size];
        for (int i = 0; i < size; i++) {
            values[i] = (float) curve.evaluate(i / (double) (size - 1));
        }
        return values;
    }

    private static double measureError(FloatCurve curve, float[] values) {
        int checks = values.length * 4;
        double worst = 0.0;
        for (int i = 0; i <= checks; i++) {
            double t = i / (double) checks;
            worst = Math.max(worst, Math.abs(Table.lerp(values, (float) t) - curve.evaluate(t)));
        }
        return worst;
    }

    private static double peakMagnitude(float[] values) {
        double peak = 0.0;
        for (float v : values) {
            peak = Math.max(peak, Math.abs(v));
        }
        return peak;
    }

    record Constant(float value) implements BakedCurve {
        @Override
        public float sample(float t) {
            return value;
        }
    }

    record Table(float[] values, double maxError) implements BakedCurve {
        @Override
        public float sample(float t) {
            return lerp(values, t);
        }

        static float lerp(float[] values, float t) {
            int last = values.length - 1;
            if (!(t > 0f)) return values[0]; // also catches NaN
            if (t >= 1f) return values[last];
            float x = t * last;
            int i = (int) x;
            float f = x - i;
            return values[i] + (values[i + 1] - values[i]) * f;
        }
    }

    record Exact(FloatCurve curve) implements BakedCurve {
        @Override
        public float sample(float t) {
            return (float) curve.evaluate(t);
        }
    }
}
