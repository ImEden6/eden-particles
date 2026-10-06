package com.eden.particles.impl.math;

/**
 * Per-tick spawn counts from a rate curve (particles/tick over {@code u ∈ [0,1]}):
 * {@code N_k = D·(C(u_{k+1}) − C(u_k))} with {@code C(u) = ∫₀ᵘ r}. Ticks telescope, so the total is exactly
 * {@code D·C(1)}.
 */
public final class SpawnIntegrator {

    static final int GRID = 1024;

    private final int duration;
    private final double[] cumulative; // C at u = i / GRID

    public SpawnIntegrator(FloatCurve rate, int durationTicks) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("duration must be positive");
        }
        this.duration = durationTicks;
        this.cumulative = integrate(rate);
    }

    public double expectedInTick(int tick) {
        if (tick < 0 || tick >= duration) {
            return 0.0;
        }
        return duration * (cumulativeAt((tick + 1) / (double) duration) - cumulativeAt(tick / (double) duration));
    }

    public double expectedTotal() {
        return duration * cumulative[GRID];
    }

    public int duration() {
        return duration;
    }

    private double cumulativeAt(double u) {
        if (u <= 0.0) return 0.0;
        if (u >= 1.0) return cumulative[GRID];
        double x = u * GRID;
        int i = (int) x;
        return cumulative[i] + (cumulative[i + 1] - cumulative[i]) * (x - i);
    }

    /** Simpson per grid cell, split at key times so no panel straddles a step. */
    private static double[] integrate(FloatCurve rate) {
        double[] c = new double[GRID + 1];
        int key = 0;
        int keys = rate.keyCount();
        double total = 0.0;
        for (int i = 0; i < GRID; i++) {
            double a = i / (double) GRID;
            double b = (i + 1) / (double) GRID;
            while (key < keys && rate.keyTime(key) <= a) {
                key++;
            }
            double start = a;
            while (key < keys && rate.keyTime(key) < b) {
                double split = rate.keyTime(key);
                total += simpson(rate, start, split);
                start = split;
                key++;
            }
            total += simpson(rate, start, b);
            c[i + 1] = total;
        }
        return c;
    }

    private static double simpson(FloatCurve rate, double a, double b) {
        if (!(b > a)) return 0.0;
        double fa = Math.max(0.0, rate.evaluate(a));
        double fm = Math.max(0.0, rate.evaluate((a + b) * 0.5));
        // Left limit, so a step segment contributes its held value.
        double fb = Math.max(0.0, rate.evaluate(Math.nextDown(b)));
        return (b - a) / 6.0 * (fa + 4.0 * fm + fb);
    }

    public static final class Accumulator {

        private static final double ROUNDING_SLACK = 1e-9;

        private double carry;

        public int next(SpawnIntegrator integrator, int tick) {
            carry += integrator.expectedInTick(tick);
            int count = (int) Math.floor(carry + ROUNDING_SLACK);
            carry -= count;
            return count;
        }

        public void reset() {
            carry = 0.0;
        }
    }

    /** Knuth below λ = 30, rounded normal approximation above. */
    public static int poisson(double lambda, ParticleRandom random) {
        if (!(lambda > 0.0)) {
            return 0;
        }
        if (lambda < 30.0) {
            double limit = Math.exp(-lambda);
            double product = 1.0;
            int k = -1;
            do {
                k++;
                product *= random.nextDouble();
            } while (product > limit);
            return k;
        }
        double u1 = 1.0 - random.nextDouble();
        double u2 = random.nextDouble();
        double normal = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
        return (int) Math.max(0L, Math.round(lambda + Math.sqrt(lambda) * normal));
    }

    /** Stratified offset in (0,1); {@code jitter = 0.5} gives cell centres. */
    public static float subTickOffset(int j, int n, float jitter) {
        return (j + jitter) / n;
    }
}
