package com.eden.particles.impl.expr;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Functions callable from expressions. {@code minArgs == maxArgs} for a fixed arity. */
public enum MathFunction {
    MIN(2, Integer.MAX_VALUE),
    MAX(2, Integer.MAX_VALUE),
    CLAMP(3, 3),
    LERP(3, 3),
    ABS(1, 1),
    FLOOR(1, 1),
    CEIL(1, 1),
    ROUND(1, 1),
    SQRT(1, 1),
    POW(2, 2);

    public static final List<String> NAMES = Arrays.stream(values()).map(MathFunction::id).toList();

    private final int minArgs;
    private final int maxArgs;

    MathFunction(int minArgs, int maxArgs) {
        this.minArgs = minArgs;
        this.maxArgs = maxArgs;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<MathFunction> byId(String id) {
        return Arrays.stream(values()).filter(f -> f.id().equals(id)).findFirst();
    }

    public boolean accepts(int count) {
        return count >= minArgs && count <= maxArgs;
    }

    public String arity() {
        if (maxArgs == Integer.MAX_VALUE) return "at least " + minArgs + " arguments";
        return minArgs + (minArgs == 1 ? " argument" : " arguments");
    }

    float apply(List<Expr> args, float[] in) {
        float a = args.get(0).eval(in);
        float r = switch (this) {
            case MIN -> {
                for (int i = 1; i < args.size(); i++) a = Math.min(a, args.get(i).eval(in));
                yield a;
            }
            case MAX -> {
                for (int i = 1; i < args.size(); i++) a = Math.max(a, args.get(i).eval(in));
                yield a;
            }
            case CLAMP -> Math.max(args.get(1).eval(in), Math.min(args.get(2).eval(in), a));
            case LERP -> {
                float b = args.get(1).eval(in);
                yield a + (b - a) * args.get(2).eval(in);
            }
            case ABS -> Math.abs(a);
            case FLOOR -> (float) Math.floor(a);
            case CEIL -> (float) Math.ceil(a);
            case ROUND -> Math.round(a);
            case SQRT -> (float) Math.sqrt(Math.max(0f, a));
            case POW -> (float) Math.pow(a, args.get(1).eval(in));
        };
        return Float.isFinite(r) ? r : 0f;
    }
}
