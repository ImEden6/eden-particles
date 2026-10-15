package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.math.Gradient;
import com.mojang.serialization.Codec;

import java.util.Optional;
import java.util.Set;

/** Configuration for connected ribbon trails following particles. */
public record TrailSpec(
        int lifetime,
        int maxPoints,
        float minDistance,
        Optional<ScaledCurve> width,
        Optional<ScaledCurve> alpha,
        Optional<Gradient> color,
        Optional<SpriteRef> sprite,
        Mode mode,
        float tileLength,
        Optional<RenderMode.Blend> blend,
        Optional<Boolean> emissive) {

    public enum Mode { STRETCH, TILE }

    public static final int DEFAULT_MAX_POINTS = 16;
    public static final float DEFAULT_MIN_DISTANCE = 0.02f;
    public static final float DEFAULT_TILE_LENGTH = 1.0f;

    public static final Codec<Mode> MODE_CODEC = Codecs.enumCodec(Mode.class, "trail mode");

    public void names(Set<String> out) {
        width.ifPresent(w -> w.names(out));
        alpha.ifPresent(a -> a.names(out));
    }

    public static final Codec<TrailSpec> CODEC = ObjectCodec.of("trail", f -> new TrailSpec(
            f.required("lifetime", Codecs.intAtLeast(1)),
            f.optional("max_points", Codecs.intIn(3, 64), DEFAULT_MAX_POINTS),
            f.optional("min_distance", Codecs.floatAtLeast(1e-4f), DEFAULT_MIN_DISTANCE),
            f.optional("width", CurveCodecs.SCALED),
            f.optional("alpha", CurveCodecs.SCALED),
            f.optional("color", CurveCodecs.GRADIENT),
            f.optional("sprite", SpriteRef.CODEC),
            f.optional("mode", MODE_CODEC, Mode.STRETCH),
            f.optional("tile_length", Codecs.floatAtLeast(0.01f), DEFAULT_TILE_LENGTH),
            f.optional("blend", RenderMode.BLEND),
            f.optional("emissive", Codecs.BOOL)),
            (t, out) -> out
                    .put("lifetime", Codecs.INT, t.lifetime)
                    .put("max_points", Codecs.INT, t.maxPoints)
                    .put("min_distance", Codecs.FLOAT, t.minDistance)
                    .put("width", CurveCodecs.SCALED, t.width)
                    .put("alpha", CurveCodecs.SCALED, t.alpha)
                    .put("color", CurveCodecs.GRADIENT, t.color)
                    .put("sprite", SpriteRef.CODEC, t.sprite)
                    .put("mode", MODE_CODEC, t.mode)
                    .put("tile_length", Codecs.FLOAT, t.tileLength)
                    .put("blend", RenderMode.BLEND, t.blend)
                    .put("emissive", Codecs.BOOL, t.emissive));
}
