package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ParticleTemplate;
import com.eden.particles.impl.definition.Physics;
import com.eden.particles.impl.definition.RandomInt;
import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.math.BakedCurve;
import com.eden.particles.impl.math.BakedGradient;
import com.eden.particles.impl.math.FloatCurve;
import com.eden.particles.impl.math.Gradient;

/** A template with curves baked for per-tick sampling; defaults filled in. */
public record BakedTemplate(
        RenderMode render,
        RandomInt lifetime,
        BakedCurve size,
        BakedCurve alpha,
        BakedCurve speed,
        BakedGradient color,
        Physics physics) {

    public static BakedTemplate bake(ParticleTemplate template) {
        return new BakedTemplate(
                template.render(),
                template.lifetime(),
                BakedCurve.bake(template.size().orElse(FloatCurve.constant(ParticleTemplate.DEFAULT_SIZE))),
                BakedCurve.bake(template.alpha().orElse(FloatCurve.constant(1.0))),
                BakedCurve.bake(template.speed().orElse(FloatCurve.constant(1.0))),
                template.color().orElse(Gradient.constant(0xFFFFFF)).bake(),
                template.physics());
    }
}
