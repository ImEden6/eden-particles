package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.math.FloatCurve;
import com.mojang.serialization.Codec;

import java.util.List;
import java.util.Optional;

/**
 * {@code rate} is particles/tick over emitter time. Bursts fire {@code at} ticks after the emitter starts.
 * {@code jitter} makes rate spawns Poisson-distributed instead of evenly paced.
 */
public record SpawnSpec(Optional<FloatCurve> rate, List<Burst> bursts, boolean jitter) {

    public record Burst(int at, RandomInt count) {
        public static final Codec<Burst> CODEC = ObjectCodec.of("burst",
                f -> new Burst(f.optional("at", Codecs.intAtLeast(0), 0), f.required("count", RandomInt.codecAtLeast(0))),
                (b, out) -> out.put("at", Codecs.INT, b.at).put("count", RandomInt.CODEC, b.count));
    }

    public static final Codec<SpawnSpec> CODEC = ObjectCodec.of("spawn", f -> {
        Optional<FloatCurve> rate = f.optional("rate", CurveCodecs.CURVE);
        List<Burst> bursts = f.optional("bursts", Codecs.list(Burst.CODEC), List.of());
        boolean jitter = f.optional("jitter", Codecs.BOOL, false);
        if (rate.isEmpty() && bursts.isEmpty()) throw f.fail("needs a 'rate', 'bursts', or both");
        if (jitter && rate.isEmpty()) throw f.failAt("jitter", "only applies to 'rate'");
        return new SpawnSpec(rate, bursts, jitter);
    }, (s, out) -> out
            .put("rate", CurveCodecs.CURVE, s.rate)
            .put("bursts", Codecs.list(Burst.CODEC), s.bursts)
            .put("jitter", Codecs.BOOL, s.jitter));
}
