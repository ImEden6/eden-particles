package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.ObjectCodec;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

/**
 * Initial speed in blocks/tick, or with {@code {"distance": d}} the distance to cover over the particle's lifetime,
 * converted at spawn using its drag.
 */
public record Speed(RandomFloat value, boolean byDistance) {

    public void names(java.util.Set<String> out) {
        value.names(out);
    }

    public static final Speed ZERO = new Speed(new RandomFloat.Constant(0f), false);

    private static final Codec<Speed> DISTANCE = ObjectCodec.of("distance",
            f -> new Speed(f.required("distance", RandomFloat.CODEC), true),
            (s, out) -> out.put("distance", RandomFloat.CODEC, s.value()));

    public static final Codec<Speed> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Speed, T>> decode(DynamicOps<T> ops, T input) {
            boolean distance = ops.getMap(input).result().map(m -> m.get("distance") != null).orElse(false);
            return distance
                    ? DISTANCE.decode(ops, input)
                    : RandomFloat.CODEC.decode(ops, input).map(p -> p.mapFirst(v -> new Speed(v, false)));
        }

        @Override
        public <T> DataResult<T> encode(Speed input, DynamicOps<T> ops, T prefix) {
            return input.byDistance ? DISTANCE.encode(input, ops, prefix) : RandomFloat.CODEC.encode(input.value, ops, prefix);
        }
    };
}
