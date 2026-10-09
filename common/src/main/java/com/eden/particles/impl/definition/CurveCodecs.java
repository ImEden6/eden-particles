package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.math.CubicBezierEasing;
import com.eden.particles.impl.math.Easing;
import com.eden.particles.impl.math.Easings;
import com.eden.particles.impl.math.FloatCurve;
import com.eden.particles.impl.math.Gradient;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.RecordBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Curves over normalized time. An {@code ease} on a key shapes the segment arriving at that key.
 * Shorthands: a number is a constant, a list of {@code [t, v]} pairs is linear, a colour string is a flat gradient.
 */
public final class CurveCodecs {

    public static final Codec<Easing> EASING = Codecs.byKind("an easing", Codecs.<Easing>encoder((easing, ops) -> {
                if (easing instanceof CubicBezierEasing bezier) {
                    double[] p = bezier.controlPoints();
                    return ops.createMap(java.util.Map.of(ops.createString("cubic_bezier"),
                            ops.createList(IntStream.range(0, 4).mapToObj(i -> ops.createDouble(p[i])))));
                }
                return ops.createString(Easings.nameOf(easing).orElseThrow(() -> new IllegalStateException("unnamed easing")));
            }))
            .when(Codecs.Kind.STRING, Codecs.STRING.comapFlatMap(
                    name -> Easings.byName(name).map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> Codecs.unknownChoice("easing", name, Easings.names()))),
                    easing -> Easings.nameOf(easing).orElseThrow()))
            .when(Codecs.Kind.MAP, ObjectCodec.decoder("easing", f -> {
                List<Double> p = f.required("cubic_bezier", Codecs.list(Codecs.DOUBLE));
                if (p.size() != 4) throw f.failAt("cubic_bezier", "expected [x1, y1, x2, y2], got " + p.size() + " values");
                try {
                    return Easings.cubicBezier(p.get(0), p.get(1), p.get(2), p.get(3));
                } catch (IllegalArgumentException e) {
                    throw f.failAt("cubic_bezier", e.getMessage());
                }
            }))
            .build();

    private record Key(double t, double v, Optional<Easing> ease) {
    }

    private static DataResult<Double> checkTime(double t) {
        return t >= 0.0 && t <= 1.0 ? DataResult.success(t) : DataResult.error(() -> "key time must be between 0 and 1, got " + t);
    }

    private static final Codec<Double> TIME = Codecs.DOUBLE.validate(CurveCodecs::checkTime);

    private static final Codec<Key> KEY = Codecs.byKind("a key", Codecs.<Key>encoder((k, ops) -> null))
            .when(Codecs.Kind.LIST, Codecs.list(Codecs.DOUBLE).comapFlatMap(pair -> pair.size() != 2
                            ? DataResult.error(() -> "expected [t, value], got " + pair.size() + " values")
                            : checkTime(pair.get(0)).map(t -> new Key(t, pair.get(1), Optional.empty())),
                    k -> List.of(k.t, k.v)))
            .when(Codecs.Kind.MAP, ObjectCodec.decoder("key",
                    f -> new Key(f.required("t", TIME), f.required("v", Codecs.DOUBLE), f.optional("ease", EASING))))
            .build();

    private static final Codec<FloatCurve.Mode> MODE = Codecs.enumCodec(FloatCurve.Mode.class, "curve mode");

    public static final Codec<FloatCurve> CURVE = Codecs.byKind("a curve", Codecs.encoder(CurveCodecs::encodeCurve))
            .when(Codecs.Kind.NUMBER, Codecs.DOUBLE.xmap(FloatCurve::constant, c -> c.keyValue(0)))
            .when(Codecs.Kind.LIST, Codecs.nonEmptyList(KEY).comapFlatMap(keys -> build(keys, Optional.empty(), FloatCurve.Mode.EASED), c -> List.of()))
            .when(Codecs.Kind.MAP, ObjectCodec.decoder("curve", f -> {
                List<Key> keys = f.required("keys", Codecs.nonEmptyList(KEY));
                Optional<Easing> ease = f.optional("ease", EASING);
                FloatCurve.Mode mode = f.optional("mode", MODE, FloatCurve.Mode.EASED);
                return f.orFail(build(keys, ease, mode));
            }))
            .build();

    /**
     * A curve, or an expression over inputs (constant over time), or a curve object with a {@code scale} expression
     * multiplying it.
     */
    public static final Codec<ScaledCurve> SCALED = Codecs.byKind("a curve or expression", Codecs.encoder(CurveCodecs::encodeScaled))
            .when(Codecs.Kind.NUMBER, CURVE.xmap(ScaledCurve::of, ScaledCurve::curve))
            .when(Codecs.Kind.LIST, CURVE.xmap(ScaledCurve::of, ScaledCurve::curve))
            .when(Codecs.Kind.STRING, ExprCodecs.NUMBER.xmap(
                    e -> e instanceof Expr.Const c ? ScaledCurve.constant(c.value()) : new ScaledCurve(FloatCurve.constant(1.0), e),
                    ScaledCurve::scale))
            .when(Codecs.Kind.MAP, ObjectCodec.decoder("curve", f -> {
                List<Key> keys = f.required("keys", Codecs.nonEmptyList(KEY));
                Optional<Easing> ease = f.optional("ease", EASING);
                FloatCurve.Mode mode = f.optional("mode", MODE, FloatCurve.Mode.EASED);
                Expr scale = f.optional("scale", ExprCodecs.NUMBER, Expr.ONE);
                return new ScaledCurve(f.orFail(build(keys, ease, mode)), scale);
            }))
            .build();

    private record ColorKey(double t, int color, Optional<Easing> ease) {
    }

    private static final Codec<ColorKey> COLOR_KEY = ObjectCodec.decoder("colour key",
            f -> new ColorKey(f.required("t", TIME), f.required("color", Codecs.COLOR), f.optional("ease", EASING)));

    private static final Codec<Gradient.Space> SPACE = Codecs.enumCodec(Gradient.Space.class, "colour space");

    public static final Codec<Gradient> GRADIENT = Codecs.byKind("a colour or gradient", Codecs.encoder(CurveCodecs::encodeGradient))
            .when(Codecs.Kind.STRING, Codecs.COLOR.xmap(Gradient::constant, g -> g.keyColor(0)))
            .when(Codecs.Kind.LIST, Codecs.nonEmptyList(COLOR_KEY).comapFlatMap(keys -> buildGradient(keys, Easings.LINEAR, Gradient.Space.OKLAB), g -> List.of()))
            .when(Codecs.Kind.MAP, ObjectCodec.decoder("gradient", f -> {
                List<ColorKey> keys = f.required("keys", Codecs.nonEmptyList(COLOR_KEY));
                Easing ease = f.optional("ease", EASING, Easings.LINEAR);
                Gradient.Space space = f.optional("space", SPACE, Gradient.Space.OKLAB);
                return f.orFail(buildGradient(keys, ease, space));
            }))
            .build();

    private CurveCodecs() {
    }

    private static DataResult<FloatCurve> build(List<Key> keys, Optional<Easing> defaultEase, FloatCurve.Mode mode) {
        if (keys.get(0).ease.isPresent()) {
            return DataResult.error(() -> "the first key has no segment before it to ease");
        }
        boolean perKeyEase = keys.stream().anyMatch(k -> k.ease.isPresent());
        if (mode != FloatCurve.Mode.EASED && (perKeyEase || defaultEase.isPresent())) {
            return DataResult.error(() -> "easing only applies to mode 'eased', not '" + mode.name().toLowerCase(Locale.ROOT) + "'");
        }
        int n = keys.size();
        double[] t = new double[n];
        double[] v = new double[n];
        Easing[] easings = new Easing[n - 1];
        for (int i = 0; i < n; i++) {
            t[i] = keys.get(i).t;
            v[i] = keys.get(i).v;
            if (i > 0) easings[i - 1] = keys.get(i).ease.orElse(defaultEase.orElse(Easings.LINEAR));
        }
        try {
            return DataResult.success(mode == FloatCurve.Mode.EASED ? FloatCurve.eased(t, v, easings) : FloatCurve.cubic(t, v, mode));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    private static DataResult<Gradient> buildGradient(List<ColorKey> keys, Easing defaultEase, Gradient.Space space) {
        if (keys.get(0).ease.isPresent()) {
            return DataResult.error(() -> "the first key has no segment before it to ease");
        }
        int n = keys.size();
        double[] t = new double[n];
        int[] colors = new int[n];
        Easing[] easings = new Easing[n - 1];
        for (int i = 0; i < n; i++) {
            t[i] = keys.get(i).t;
            colors[i] = keys.get(i).color;
            if (i > 0) easings[i - 1] = keys.get(i).ease.orElse(defaultEase);
        }
        try {
            return DataResult.success(Gradient.eased(space, t, colors, easings));
        } catch (IllegalArgumentException e) {
            return DataResult.error(e::getMessage);
        }
    }

    private static Object encodeCurve(FloatCurve curve, DynamicOps<Object> ops) {
        RecordBuilder<Object> map = ops.mapBuilder();
        if (curve.mode() != FloatCurve.Mode.EASED) {
            map.add("mode", curve.mode(), MODE);
        }
        map.add("keys", ops.createList(IntStream.range(0, curve.keyCount()).mapToObj(i -> {
            RecordBuilder<Object> key = ops.mapBuilder()
                    .add("t", ops.createDouble(curve.keyTime(i)))
                    .add("v", ops.createDouble(curve.keyValue(i)));
            if (i > 0 && curve.mode() == FloatCurve.Mode.EASED && curve.easingInto(i) != Easings.LINEAR) {
                key.add("ease", curve.easingInto(i), EASING);
            }
            return key.build(ops.empty()).getOrThrow();
        })));
        return map.build(ops.empty()).getOrThrow();
    }

    private static Object encodeScaled(ScaledCurve scaled, DynamicOps<Object> ops) {
        if (scaled.scale() instanceof Expr.Const c && c.value() == 1f) return encodeCurve(scaled.curve(), ops);
        if (scaled.curve().isConstant() && scaled.curve().keyValue(0) == 1.0) return ops.createString(scaled.scale().print());
        return ops.mergeToMap(encodeCurve(scaled.curve(), ops), ops.createString("scale"),
                Codecs.encodeWith(ExprCodecs.NUMBER, scaled.scale(), ops)).getOrThrow();
    }

    private static Object encodeGradient(Gradient gradient, DynamicOps<Object> ops) {
        RecordBuilder<Object> map = ops.mapBuilder();
        if (gradient.space() != Gradient.Space.OKLAB) {
            map.add("space", gradient.space(), SPACE);
        }
        map.add("keys", ops.createList(IntStream.range(0, gradient.keyCount()).mapToObj(i -> {
            RecordBuilder<Object> key = ops.mapBuilder()
                    .add("t", ops.createDouble(gradient.keyTime(i)))
                    .add("color", gradient.keyColor(i), Codecs.COLOR);
            if (i > 0 && gradient.easingInto(i) != Easings.LINEAR) {
                key.add("ease", gradient.easingInto(i), EASING);
            }
            return key.build(ops.empty()).getOrThrow();
        })));
        return map.build(ops.empty()).getOrThrow();
    }
}
