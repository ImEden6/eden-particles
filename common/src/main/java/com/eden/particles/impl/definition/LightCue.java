package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.mojang.serialization.Codec;

import java.util.Optional;
import java.util.Set;

/**
 * Lights the blocks around the effect's anchor and {@code offset} from {@code start} for {@code duration} ticks
 * ({@link #REST} until the timeline ends). {@code level} is a block light curve, 0 to 15, over that window.
 */
public record LightCue(Optional<String> name, ScaledCurve level, int start, int duration, ExprVec3 offset) implements Track {

    public static final int REST = -1;

    public static final Codec<LightCue> CODEC = ObjectCodec.of("light", f -> new LightCue(
            f.optional("name", Codecs.STRING),
            f.required("light", CurveCodecs.SCALED),
            f.optional("start", Codecs.intAtLeast(0), 0),
            f.optional("duration", Codecs.intAtLeast(1), REST),
            f.optional("offset", ExprCodecs.VEC3, ExprVec3.ZERO)), (l, out) -> out
            .put("name", Codecs.STRING, l.name)
            .put("light", CurveCodecs.SCALED, l.level)
            .put("start", Codecs.INT, l.start)
            .put("duration", Codecs.INT, l.duration)
            .put("offset", ExprCodecs.VEC3, l.offset));

    LightCue withDuration(int duration) {
        return new LightCue(name, level, start, duration, offset);
    }

    @Override
    public void names(Set<String> out) {
        level.names(out);
        offset.names(out);
    }
}
