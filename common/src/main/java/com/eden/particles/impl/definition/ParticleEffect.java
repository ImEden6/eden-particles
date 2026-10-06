package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.mojang.serialization.Codec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A choreographed effect: emitters on a timeline of {@code duration} ticks. {@code range} is the send distance in blocks. */
public record ParticleEffect(int duration, boolean loop, float range, List<EmitterTrack> emitters) {

    public static final float DEFAULT_RANGE = 64f;

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
                emitters);
    }, (e, out) -> out
            .put("duration", Codecs.INT, e.duration)
            .put("loop", Codecs.BOOL, e.loop)
            .put("range", Codecs.FLOAT, e.range)
            .put("emitters", Codecs.list(EmitterTrack.CODEC), e.emitters));
}
