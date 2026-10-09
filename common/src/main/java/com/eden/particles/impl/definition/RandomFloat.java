package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.math.ParticleRandom;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.RecordBuilder;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rolled once per particle. JSON: {@code 2}, {@code [1, 3]}, {@code {"min","max"}} or {@code {"mean","deviation"}}.
 * Constants and uniform bounds may be expressions over inputs.
 */
public sealed interface RandomFloat {

    float sample(ParticleRandom random, float[] inputs);

    void names(Set<String> out);

    record Constant(Expr value) implements RandomFloat {
        public Constant(float value) {
            this(Expr.constant(value));
        }

        @Override
        public float sample(ParticleRandom random, float[] inputs) {
            return value.eval(inputs);
        }

        @Override
        public void names(Set<String> out) {
            value.names(out);
        }
    }

    record Uniform(Expr min, Expr max) implements RandomFloat {
        @Override
        public float sample(ParticleRandom random, float[] inputs) {
            float lo = min.eval(inputs);
            return lo + (max.eval(inputs) - lo) * random.nextFloat();
        }

        @Override
        public void names(Set<String> out) {
            min.names(out);
            max.names(out);
        }
    }

    /** Box–Muller, clamped to {@code [min, max]}. */
    record Normal(float mean, float deviation, float min, float max) implements RandomFloat {
        @Override
        public float sample(ParticleRandom random, float[] inputs) {
            double u1 = 1.0 - random.nextDouble();
            double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * random.nextDouble());
            return (float) Math.min(max, Math.max(min, mean + deviation * z));
        }

        @Override
        public void names(Set<String> out) {
        }
    }

    static DataResult<RandomFloat> uniform(Expr min, Expr max) {
        if (min instanceof Expr.Const lo && max instanceof Expr.Const hi && lo.value() > hi.value()) {
            return DataResult.error(() -> "min (" + lo.value() + ") must not exceed max (" + hi.value() + ")");
        }
        return DataResult.success(new Uniform(min, max));
    }

    Codec<RandomFloat> CODEC = Codecs.byKind("a number, expression or random range", Codecs.<RandomFloat>encoder((r, ops) -> switch (r) {
                case Constant c -> Codecs.encodeWith(ExprCodecs.NUMBER, c.value(), ops);
                case Uniform u -> ops.createList(Stream.of(Codecs.encodeWith(ExprCodecs.NUMBER, u.min(), ops),
                        Codecs.encodeWith(ExprCodecs.NUMBER, u.max(), ops)));
                case Normal n -> {
                    RecordBuilder<Object> map = ops.mapBuilder().add("mean", ops.createFloat(n.mean())).add("deviation", ops.createFloat(n.deviation()));
                    if (Float.isFinite(n.min())) map.add("min", ops.createFloat(n.min()));
                    if (Float.isFinite(n.max())) map.add("max", ops.createFloat(n.max()));
                    yield map.build(ops.empty()).getOrThrow();
                }
            }))
            .when(Codecs.Kind.NUMBER, ExprCodecs.NUMBER.xmap(Constant::new, r -> ((Constant) r).value()))
            .when(Codecs.Kind.STRING, ExprCodecs.NUMBER.xmap(Constant::new, r -> ((Constant) r).value()))
            .when(Codecs.Kind.LIST, Codecs.list(ExprCodecs.NUMBER).comapFlatMap(
                    l -> l.size() == 2 ? uniform(l.get(0), l.get(1)) : DataResult.error(() -> "expected [min, max], got " + l.size() + " values"),
                    r -> List.of(((Uniform) r).min(), ((Uniform) r).max())))
            .when(Codecs.Kind.MAP, ObjectCodec.decoder("random range", f -> {
                if (f.has("mean")) {
                    float mean = f.required("mean", Codecs.FLOAT);
                    float deviation = f.required("deviation", Codecs.floatAtLeast(0f));
                    float min = f.optional("min", Codecs.FLOAT, Float.NEGATIVE_INFINITY);
                    float max = f.optional("max", Codecs.FLOAT, Float.POSITIVE_INFINITY);
                    if (min > max) throw f.fail("min (" + min + ") must not exceed max (" + max + ")");
                    return new Normal(mean, deviation, min, max);
                }
                return f.orFail(uniform(f.required("min", ExprCodecs.NUMBER), f.required("max", ExprCodecs.NUMBER)));
            }))
            .build();
}
