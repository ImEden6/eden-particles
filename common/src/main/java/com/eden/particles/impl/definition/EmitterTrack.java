package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One emitter on an effect's timeline. {@code start} and {@code duration} are in ticks; {@code spin} is degrees/tick
 * around {@code axis}, as a curve over emitter time.
 */
public record EmitterTrack(
        Optional<String> name,
        int start,
        int duration,
        ExprVec3 offset,
        Vector3fc axis,
        Optional<ScaledCurve> spin,
        TemplateRef template,
        EmitterShape shape,
        Distribution distribution,
        SpawnSpec spawn,
        VelocityMode velocity,
        boolean important) {

    /** {@code even} lays bursts out on a Fibonacci sphere, sunflower disc or evenly spaced ring. */
    public enum Distribution { RANDOM, EVEN }

    public static final Vector3fc UP = new Vector3f(0f, 1f, 0f);

    private static final Map<String, Vector3fc> NAMED_AXES = Map.of(
            "x", new Vector3f(1, 0, 0), "y", UP, "z", new Vector3f(0, 0, 1),
            "-x", new Vector3f(-1, 0, 0), "-y", new Vector3f(0, -1, 0), "-z", new Vector3f(0, 0, -1));

    private static final Codec<Vector3fc> AXIS = Codecs.byKind("an axis", Codecs.VEC3)
            .when(Codecs.Kind.STRING, Codecs.STRING.comapFlatMap(
                    s -> Optional.ofNullable(NAMED_AXES.get(s)).map(DataResult::success)
                            .orElseGet(() -> DataResult.error(() -> Codecs.unknownChoice("axis", s, NAMED_AXES.keySet()))),
                    Vector3fc::toString))
            .when(Codecs.Kind.LIST, Codecs.VEC3.comapFlatMap(
                    v -> v.lengthSquared() < 1e-12f ? DataResult.error(() -> "axis must not be zero") : DataResult.success(new Vector3f(v).normalize()),
                    v -> v))
            .build();

    private static final Codec<Distribution> DISTRIBUTION = Codecs.enumCodec(Distribution.class, "distribution");

    public static final Codec<EmitterTrack> CODEC = ObjectCodec.of("emitter", f -> {
        int duration = f.required("duration", Codecs.intAtLeast(1));
        EmitterShape shape = f.optional("shape", EmitterShape.CODEC, EmitterShape.POINT);
        Distribution distribution = f.optional("distribution", DISTRIBUTION, Distribution.RANDOM);
        if (distribution == Distribution.EVEN
                && !(shape instanceof EmitterShape.Sphere || shape instanceof EmitterShape.Disc || shape instanceof EmitterShape.Ring)) {
            throw f.failAt("distribution", "'even' needs a sphere, disc or ring shape");
        }
        SpawnSpec spawn = f.required("spawn", SpawnSpec.CODEC);
        for (int i = 0; i < spawn.bursts().size(); i++) {
            int at = spawn.bursts().get(i).at();
            if (at >= duration) {
                throw f.failAt("spawn", "at bursts[" + i + "]: fires at tick " + at + ", but the emitter only lasts " + duration + " ticks");
            }
        }
        return new EmitterTrack(
                f.optional("name", Codecs.STRING),
                f.optional("start", Codecs.intAtLeast(0), 0),
                duration,
                f.optional("offset", ExprCodecs.VEC3, ExprVec3.ZERO),
                f.optional("axis", AXIS, UP),
                f.optional("spin", CurveCodecs.SCALED),
                f.required("template", TemplateRef.CODEC),
                shape,
                distribution,
                spawn,
                f.optional("velocity", VelocityMode.CODEC, VelocityMode.NONE),
                f.optional("important", Codecs.BOOL, false));
    }, (t, out) -> out
            .put("name", Codecs.STRING, t.name)
            .put("start", Codecs.INT, t.start)
            .put("duration", Codecs.INT, t.duration)
            .put("offset", ExprCodecs.VEC3, t.offset)
            .put("axis", Codecs.VEC3, t.axis)
            .put("spin", CurveCodecs.SCALED, t.spin)
            .put("template", TemplateRef.CODEC, t.template)
            .put("shape", EmitterShape.CODEC, t.shape)
            .put("distribution", DISTRIBUTION, t.distribution)
            .put("spawn", SpawnSpec.CODEC, t.spawn)
            .put("velocity", VelocityMode.CODEC, t.velocity)
            .put("important", Codecs.BOOL, t.important));

    /** Input names used by this emitter, not counting its template. */
    public void names(Set<String> out) {
        offset.names(out);
        spin.ifPresent(s -> s.names(out));
        shape.names(out);
        spawn.names(out);
        velocity.names(out);
    }

    public int end() {
        return start + duration;
    }
}
