package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.math.Gradient;
import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** What a particle looks like and how it moves over its life. Curves sample particle age, {@code t ∈ [0,1]}. */
public record ParticleTemplate(
        RenderMode render,
        RandomInt lifetime,
        Optional<ScaledCurve> size,
        Optional<ScaledCurve> alpha,
        Optional<Gradient> color,
        Optional<ScaledCurve> speed,
        Physics physics,
        Optional<Trigger> onDeath,
        Optional<Trigger> onLand,
        Optional<TrailSpec> trail) {

    public static final float DEFAULT_SIZE = 0.1f;

    public void names(Set<String> out) {
        lifetime.names(out);
        size.ifPresent(c -> c.names(out));
        alpha.ifPresent(c -> c.names(out));
        speed.ifPresent(c -> c.names(out));
        physics.names(out);
        onDeath.ifPresent(t -> t.names(out));
        onLand.ifPresent(t -> t.names(out));
        trail.ifPresent(t -> t.names(out));
    }

    /** Effects this template plays when its particles die or land. */
    public Stream<Trigger> triggers() {
        return Stream.concat(onDeath.stream(), onLand.stream());
    }

    public static final Codec<ParticleTemplate> CODEC = ObjectCodec.of("template", f -> {
        RenderMode render = f.required("render", RenderMode.CODEC);
        ParticleTemplate template = new ParticleTemplate(
                render,
                f.required("lifetime", RandomInt.codecAtLeast(1)),
                f.optional("size", CurveCodecs.SCALED),
                f.optional("alpha", CurveCodecs.SCALED),
                f.optional("color", CurveCodecs.GRADIENT),
                f.optional("speed", CurveCodecs.SCALED),
                f.optional("physics", Physics.CODEC, Physics.DEFAULT),
                f.optional("on_death", Trigger.CODEC),
                f.optional("on_land", Trigger.CODEC),
                f.optional("trail", TrailSpec.CODEC));
        if (template.onLand.isPresent() && !template.physics.collide()) {
            throw f.failAt("on_land", "needs \"physics\": {\"collide\": true}, or particles never land");
        }
        if (render instanceof RenderMode.Ribbon && template.trail.isEmpty()) {
            throw f.failAt("render", "render type 'ribbon' needs a 'trail' block");
        }
        if (render instanceof RenderMode.Vanilla) {
            List<String> curves = new ArrayList<>();
            template.size.ifPresent(c -> curves.add("size"));
            template.alpha.ifPresent(c -> curves.add("alpha"));
            template.color.ifPresent(c -> curves.add("color"));
            template.speed.ifPresent(c -> curves.add("speed"));
            template.onDeath.ifPresent(c -> curves.add("on_death"));
            template.onLand.ifPresent(c -> curves.add("on_land"));
            template.trail.ifPresent(c -> curves.add("trail"));
            if (!template.physics.forces().isEmpty()) curves.add("physics.forces");
            if (!curves.isEmpty()) {
                throw f.fail("vanilla particles can't follow per-particle curves, triggers or forces; remove " + curves + " or use render type 'sprite'");
            }
        }
        return template;
    }, (t, out) -> out
            .put("render", RenderMode.CODEC, t.render)
            .put("lifetime", RandomInt.CODEC, t.lifetime)
            .put("size", CurveCodecs.SCALED, t.size)
            .put("alpha", CurveCodecs.SCALED, t.alpha)
            .put("color", CurveCodecs.GRADIENT, t.color)
            .put("speed", CurveCodecs.SCALED, t.speed)
            .put("physics", Physics.CODEC, t.physics)
            .put("on_death", Trigger.CODEC, t.onDeath)
            .put("on_land", Trigger.CODEC, t.onLand)
            .put("trail", TrailSpec.CODEC, t.trail));
}
