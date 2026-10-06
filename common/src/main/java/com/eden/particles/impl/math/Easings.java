package com.eden.particles.impl.math;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Named easings. Each Penner family registers {@code in_}, {@code out_} and {@code in_out_} variants. */
public final class Easings {

    public static final Easing LINEAR = s -> s;

    public static final Easing STEP = new Easing() {
        @Override
        public double apply(double s) {
            return s >= 1.0 ? 1.0 : 0.0;
        }

        @Override
        public boolean isDiscontinuous() {
            return true;
        }
    };

    public static final Easing SMOOTH = s -> s * s * (3.0 - 2.0 * s);

    private static final double BACK_C1 = 1.70158;
    private static final double BACK_C3 = BACK_C1 + 1.0;
    private static final double ELASTIC_C4 = 2.0 * Math.PI / 3.0;

    private static final Map<String, Easing> BY_NAME = new LinkedHashMap<>();

    static {
        register("linear", LINEAR);
        register("step", STEP);
        register("smooth", SMOOTH);
        family("sine", s -> 1.0 - Math.cos(s * Math.PI / 2.0));
        family("quad", s -> s * s);
        family("cubic", s -> s * s * s);
        family("quart", s -> s * s * s * s);
        family("quint", s -> s * s * s * s * s);
        family("expo", s -> s <= 0.0 ? 0.0 : Math.pow(2.0, 10.0 * s - 10.0));
        family("circ", s -> 1.0 - Math.sqrt(Math.max(0.0, 1.0 - s * s)));
        family("back", s -> BACK_C3 * s * s * s - BACK_C1 * s * s);
        family("elastic", s -> {
            if (s <= 0.0) return 0.0;
            if (s >= 1.0) return 1.0;
            return -Math.pow(2.0, 10.0 * s - 10.0) * Math.sin((10.0 * s - 10.75) * ELASTIC_C4);
        });
        family("bounce", s -> 1.0 - outBounce(1.0 - s));
    }

    private Easings() {
    }

    public static Optional<Easing> byName(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    public static Set<String> names() {
        return Collections.unmodifiableSet(BY_NAME.keySet());
    }

    /** CSS {@code cubic-bezier()}; {@code x1} and {@code x2} must be in [0,1]. */
    public static Easing cubicBezier(double x1, double y1, double x2, double y2) {
        return new CubicBezierEasing(x1, y1, x2, y2);
    }

    private static void register(String name, Easing easing) {
        BY_NAME.put(name, easing);
    }

    private static void family(String name, Easing in) {
        register("in_" + name, in);
        register("out_" + name, s -> 1.0 - in.apply(1.0 - s));
        register("in_out_" + name, s -> s < 0.5 ? in.apply(2.0 * s) / 2.0 : 1.0 - in.apply(2.0 - 2.0 * s) / 2.0);
    }

    private static double outBounce(double s) {
        final double n1 = 7.5625;
        final double d1 = 2.75;
        if (s < 1.0 / d1) {
            return n1 * s * s;
        } else if (s < 2.0 / d1) {
            s -= 1.5 / d1;
            return n1 * s * s + 0.75;
        } else if (s < 2.5 / d1) {
            s -= 2.25 / d1;
            return n1 * s * s + 0.9375;
        }
        s -= 2.625 / d1;
        return n1 * s * s + 0.984375;
    }
}
