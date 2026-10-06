package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.math.ParticleRandom;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.RecordBuilder;

import java.util.List;
import java.util.stream.Stream;

/** Rolled once per particle. JSON: {@code 2}, {@code [1, 3]}, {@code {"min","max"}} or {@code {"mean","deviation"}}. */
public sealed interface RandomFloat {

    float sample(ParticleRandom random);

    float lowerBound();

    float upperBound();

    record Constant(float value) implements RandomFloat {
        @Override
        public float sample(ParticleRandom random) {
            return value;
        }

        @Override
        public float lowerBound() {
            return value;
        }

        @Override
        public float upperBound() {
            return value;
        }
    }

    record Uniform(float min, float max) implements RandomFloat {
        @Override
        public float sample(ParticleRandom random) {
            return min + (max - min) * random.nextFloat();
        }

        @Override
        public float lowerBound() {
            return min;
        }

        @Override
        public float upperBound() {
            return max;
        }
    }

    /** Box–Muller, clamped to {@code [min, max]}. */
    record Normal(float mean, float deviation, float min, float max) implements RandomFloat {
        @Override
        public float sample(ParticleRandom random) {
            double u1 = 1.0 - random.nextDouble();
            double z = Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * random.nextDouble());
            return (float) Math.min(max, Math.max(min, mean + deviation * z));
        }

        @Override
        public float lowerBound() {
            return min;
        }

        @Override
        public float upperBound() {
            return max;
        }
    }

    static DataResult<RandomFloat> uniform(float min, float max) {
        return min <= max
                ? DataResult.success(new Uniform(min, max))
                : DataResult.error(() -> "min (" + min + ") must not exceed max (" + max + ")");
    }

    Codec<RandomFloat> CODEC = Codecs.byKind("a number or random range", Codecs.<RandomFloat>encoder((r, ops) -> switch (r) {
                case Constant c -> ops.createFloat(c.value());
                case Uniform u -> ops.createList(Stream.of(ops.createFloat(u.min()), ops.createFloat(u.max())));
                case Normal n -> {
                    RecordBuilder<Object> map = ops.mapBuilder().add("mean", ops.createFloat(n.mean())).add("deviation", ops.createFloat(n.deviation()));
                    if (Float.isFinite(n.min())) map.add("min", ops.createFloat(n.min()));
                    if (Float.isFinite(n.max())) map.add("max", ops.createFloat(n.max()));
                    yield map.build(ops.empty()).getOrThrow();
                }
            }))
            .when(Codecs.Kind.NUMBER, Codecs.FLOAT.xmap(Constant::new, r -> ((Constant) r).value()))
            .when(Codecs.Kind.LIST, Codecs.list(Codecs.FLOAT).comapFlatMap(
                    l -> l.size() == 2 ? uniform(l.get(0), l.get(1)) : DataResult.error(() -> "expected [min, max], got " + l.size() + " values"),
                    r -> List.of(r.lowerBound(), r.upperBound())))
            .when(Codecs.Kind.MAP, ObjectCodec.decoder("random range", f -> {
                if (f.has("mean")) {
                    float mean = f.required("mean", Codecs.FLOAT);
                    float deviation = f.required("deviation", Codecs.floatAtLeast(0f));
                    float min = f.optional("min", Codecs.FLOAT, Float.NEGATIVE_INFINITY);
                    float max = f.optional("max", Codecs.FLOAT, Float.POSITIVE_INFINITY);
                    if (min > max) throw f.fail("min (" + min + ") must not exceed max (" + max + ")");
                    return new Normal(mean, deviation, min, max);
                }
                return f.orFail(uniform(f.required("min", Codecs.FLOAT), f.required("max", Codecs.FLOAT)));
            }))
            .build();
}
