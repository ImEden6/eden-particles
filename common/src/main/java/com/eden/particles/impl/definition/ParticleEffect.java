package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.expr.InputNames;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A choreographed effect: emitters on a timeline of {@code duration} ticks. {@code range} is the send distance in
 * blocks; {@code inputs} are the named numbers code can pass in, with their defaults.
 */
public record ParticleEffect(int duration, boolean loop, float range, Map<String, Float> inputs, List<EmitterTrack> emitters) {

    public static final float DEFAULT_RANGE = 64f;

    private static final Codec<Map<String, Float>> INPUTS = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Map<String, Float>, T>> decode(DynamicOps<T> ops, T input) {
            if (Codecs.kindOf(ops, input) != Codecs.Kind.MAP) {
                return DataResult.error(() -> "expected an object of input names to default values");
            }
            Map<String, Float> inputs = new LinkedHashMap<>();
            for (var entry : ops.getMap(input).getOrThrow().entries().toList()) {
                String name = ops.getStringValue(entry.getFirst()).getOrThrow();
                if (!InputNames.isValid(name)) {
                    return DataResult.error(() -> "at " + name + ": input names use letters, digits and '_', and don't start with a digit");
                }
                if (name.equals(InputNames.DISTANCE)) {
                    return DataResult.error(() -> "at " + name + ": 'distance' is built in and can't be declared");
                }
                DataResult<Float> value = Codecs.FLOAT.parse(ops, entry.getSecond());
                if (value.error().isPresent()) return DataResult.error(() -> "at " + name + ": " + value.error().get().message());
                inputs.put(name, value.getOrThrow());
                InputNames.id(name);
            }
            return DataResult.success(Pair.of(Collections.unmodifiableMap(inputs), ops.empty()));
        }

        @Override
        public <T> DataResult<T> encode(Map<String, Float> input, DynamicOps<T> ops, T prefix) {
            return Codec.unboundedMap(Codec.STRING, Codec.FLOAT).encode(input, ops, prefix);
        }
    };

    public static final Codec<ParticleEffect> CODEC = ObjectCodec.of("effect", f -> {
        int duration = f.required("duration", Codecs.intAtLeast(1));
        List<EmitterTrack> emitters = f.required("emitters", Codecs.nonEmptyList(EmitterTrack.CODEC));
        Set<String> names = new HashSet<>();
        for (int i = 0; i < emitters.size(); i++) {
            EmitterTrack track = emitters.get(i);
            if (track.end() > duration) {
                throw f.failAt("emitters", "at [" + i + "]: runs from tick " + track.start() + " to " + track.end()
                        + ", past the effect's duration of " + duration);
            }
            if (track.name().isPresent() && !names.add(track.name().get())) {
                throw f.failAt("emitters", "at [" + i + "]: duplicate emitter name '" + track.name().get() + "'");
            }
        }
        return new ParticleEffect(duration,
                f.optional("loop", Codecs.BOOL, false),
                f.optional("range", Codecs.floatAtLeast(1f), DEFAULT_RANGE),
                f.optional("inputs", INPUTS, Map.of()),
                emitters);
    }, (e, out) -> out
            .put("duration", Codecs.INT, e.duration)
            .put("loop", Codecs.BOOL, e.loop)
            .put("range", Codecs.FLOAT, e.range)
            .put("inputs", INPUTS, e.inputs)
            .put("emitters", Codecs.list(EmitterTrack.CODEC), e.emitters));
}
