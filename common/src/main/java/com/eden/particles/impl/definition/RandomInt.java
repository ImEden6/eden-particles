package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.math.ParticleRandom;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;
import java.util.stream.Stream;

/** Inclusive range rolled once. JSON: {@code 20}, {@code [15, 25]} or {@code {"min","max"}}. */
public record RandomInt(int min, int max) {

    public static final Codec<RandomInt> CODEC = Codecs.byKind("a whole number or range", Codecs.<RandomInt>encoder((r, ops) ->
                    r.min == r.max ? ops.createInt(r.min) : ops.createList(Stream.of(ops.createInt(r.min), ops.createInt(r.max)))))
            .when(Codecs.Kind.NUMBER, Codecs.INT.xmap(v -> new RandomInt(v, v), RandomInt::min))
            .when(Codecs.Kind.LIST, Codecs.list(Codecs.INT).comapFlatMap(
                    l -> l.size() == 2 ? of(l.get(0), l.get(1)) : DataResult.error(() -> "expected [min, max], got " + l.size() + " values"),
                    r -> List.of(r.min, r.max)))
            .when(Codecs.Kind.MAP, ObjectCodec.decoder("range",
                    f -> f.orFail(of(f.required("min", Codecs.INT), f.required("max", Codecs.INT)))))
            .build();

    public static Codec<RandomInt> codecAtLeast(int lowest) {
        return CODEC.validate(r -> r.min >= lowest
                ? DataResult.success(r)
                : DataResult.error(() -> "must be at least " + lowest + ", got " + r.min));
    }

    public int sample(ParticleRandom random) {
        return min == max ? min : min + random.nextInt(max - min + 1);
    }

    private static DataResult<RandomInt> of(int min, int max) {
        return min <= max
                ? DataResult.success(new RandomInt(min, max))
                : DataResult.error(() -> "min (" + min + ") must not exceed max (" + max + ")");
    }
}
