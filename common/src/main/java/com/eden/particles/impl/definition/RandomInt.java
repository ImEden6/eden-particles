package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.math.ParticleRandom;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Inclusive range rolled once. JSON: {@code 20}, {@code [15, 25]} or {@code {"min","max"}}; bounds may be expressions,
 * rounded to whole numbers.
 */
public record RandomInt(Expr min, Expr max) {

    public static RandomInt of(int value) {
        Expr e = Expr.constant(value);
        return new RandomInt(e, e);
    }

    public static final Codec<RandomInt> CODEC = Codecs.byKind("a whole number, expression or range", Codecs.<RandomInt>encoder((r, ops) ->
                    r.min.equals(r.max) ? Codecs.encodeWith(ExprCodecs.WHOLE, r.min, ops)
                            : ops.createList(Stream.of(Codecs.encodeWith(ExprCodecs.WHOLE, r.min, ops), Codecs.encodeWith(ExprCodecs.WHOLE, r.max, ops)))))
            .when(Codecs.Kind.NUMBER, ExprCodecs.WHOLE.xmap(v -> new RandomInt(v, v), RandomInt::min))
            .when(Codecs.Kind.STRING, ExprCodecs.WHOLE.xmap(v -> new RandomInt(v, v), RandomInt::min))
            .when(Codecs.Kind.LIST, Codecs.list(ExprCodecs.WHOLE).comapFlatMap(
                    l -> l.size() == 2 ? of(l.get(0), l.get(1)) : DataResult.error(() -> "expected [min, max], got " + l.size() + " values"),
                    r -> List.of(r.min, r.max)))
            .when(Codecs.Kind.MAP, ObjectCodec.decoder("range",
                    f -> f.orFail(of(f.required("min", ExprCodecs.WHOLE), f.required("max", ExprCodecs.WHOLE)))))
            .build();

    /** Checks constant bounds; expression results are clamped to {@code lowest} by the caller. */
    public static Codec<RandomInt> codecAtLeast(int lowest) {
        return CODEC.validate(r -> !(r.min instanceof Expr.Const c) || c.value() >= lowest
                ? DataResult.success(r)
                : DataResult.error(() -> "must be at least " + lowest + ", got " + (int) ((Expr.Const) r.min).value()));
    }

    public int sample(ParticleRandom random, float[] inputs) {
        int lo = Math.round(min.eval(inputs));
        int hi = Math.round(max.eval(inputs));
        if (hi < lo) return lo;
        return lo == hi ? lo : lo + random.nextInt(hi - lo + 1);
    }

    public void names(Set<String> out) {
        min.names(out);
        max.names(out);
    }

    private static DataResult<RandomInt> of(Expr min, Expr max) {
        if (min instanceof Expr.Const lo && max instanceof Expr.Const hi && lo.value() > hi.value()) {
            return DataResult.error(() -> "min (" + (int) lo.value() + ") must not exceed max (" + (int) hi.value() + ")");
        }
        return DataResult.success(new RandomInt(min, max));
    }
}
