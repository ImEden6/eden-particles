package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.expr.InputNames;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A choreographed effect: emitters, sub-effects, sounds and lights on a timeline of {@code duration} ticks. {@code range} is the send
 * distance in blocks; {@code inputs} are the named numbers code can pass in, with their defaults. {@code tracks} keeps
 * the JSON order, so its indices match {@code emitters[i]} in error messages.
 */
public record ParticleEffect(int duration, boolean loop, float range, Map<String, Float> inputs, List<Track> tracks) {

    public static final float DEFAULT_RANGE = 64f;
    public static final int FORMAT = 1;

    static void readHeader(ObjectCodec.Fields f) {
        int format = f.optional("format", Codecs.intAtLeast(1), FORMAT);
        if (format > FORMAT) {
            throw f.fail("written for format " + format + ", but this version of Eden's Particles reads up to format "
                    + FORMAT + "; update the mod");
        }
        f.optional("$schema", Codecs.STRING);
    }

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

    private static final Codec<Track> TRACK = new Codec<>() {
        @Override
        public <T> DataResult<Pair<Track, T>> decode(DynamicOps<T> ops, T input) {
            var map = ops.getMap(input).result();
            if (map.map(m -> m.get("effect") != null).orElse(false)) {
                return SubEffect.CODEC.decode(ops, input).map(p -> p.mapFirst(Track.class::cast));
            }
            if (map.map(m -> m.get("sound") != null).orElse(false)) {
                return SoundCue.CODEC.decode(ops, input).map(p -> p.mapFirst(Track.class::cast));
            }
            if (map.map(m -> m.get("light") != null).orElse(false)) {
                return LightCue.CODEC.decode(ops, input).map(p -> p.mapFirst(Track.class::cast));
            }
            return EmitterTrack.CODEC.decode(ops, input).map(p -> p.mapFirst(Track.class::cast));
        }

        @Override
        public <T> DataResult<T> encode(Track input, DynamicOps<T> ops, T prefix) {
            return switch (input) {
                case EmitterTrack e -> EmitterTrack.CODEC.encode(e, ops, prefix);
                case SubEffect s -> SubEffect.CODEC.encode(s, ops, prefix);
                case SoundCue s -> SoundCue.CODEC.encode(s, ops, prefix);
                case LightCue l -> LightCue.CODEC.encode(l, ops, prefix);
            };
        }
    };

    public static final Codec<ParticleEffect> CODEC = ObjectCodec.of("effect", f -> {
        readHeader(f);
        int duration = f.required("duration", Codecs.intAtLeast(1));
        List<Track> emitters = new ArrayList<>(f.required("emitters", Codecs.nonEmptyList(TRACK)));
        Set<String> names = new HashSet<>();
        for (int i = 0; i < emitters.size(); i++) {
            if (emitters.get(i) instanceof SubEffect sub && sub.duration() == SubEffect.REST) {
                if (sub.start() >= duration) {
                    throw f.failAt("emitters", "at [" + i + "]: starts at tick " + sub.start() + ", past the effect's duration of " + duration);
                }
                emitters.set(i, sub.withDuration(duration - sub.start()));
            }
            if (emitters.get(i) instanceof SoundCue cue && cue.duration() == SoundCue.REST) {
                if (cue.start() >= duration) {
                    throw f.failAt("emitters", "at [" + i + "]: starts at tick " + cue.start() + ", past the effect's duration of " + duration);
                }
                emitters.set(i, cue.withDuration(duration - cue.start()));
            }
            if (emitters.get(i) instanceof LightCue light && light.duration() == LightCue.REST) {
                if (light.start() >= duration) {
                    throw f.failAt("emitters", "at [" + i + "]: starts at tick " + light.start() + ", past the effect's duration of " + duration);
                }
                emitters.set(i, light.withDuration(duration - light.start()));
            }
            Track track = emitters.get(i);
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
                List.copyOf(emitters));
    }, (e, out) -> out
            .put("duration", Codecs.INT, e.duration)
            .put("loop", Codecs.BOOL, e.loop)
            .put("range", Codecs.FLOAT, e.range)
            .put("inputs", INPUTS, e.inputs)
            .put("emitters", Codecs.list(TRACK), e.tracks));

    /** The particle emitters, in order. */
    public List<EmitterTrack> emitters() {
        return tracks.stream().filter(EmitterTrack.class::isInstance).map(EmitterTrack.class::cast).toList();
    }

    public List<SubEffect> subEffects() {
        return tracks.stream().filter(SubEffect.class::isInstance).map(SubEffect.class::cast).toList();
    }

    public List<SoundCue> sounds() {
        return tracks.stream().filter(SoundCue.class::isInstance).map(SoundCue.class::cast).toList();
    }

    public List<LightCue> lights() {
        return tracks.stream().filter(LightCue.class::isInstance).map(LightCue.class::cast).toList();
    }
}
