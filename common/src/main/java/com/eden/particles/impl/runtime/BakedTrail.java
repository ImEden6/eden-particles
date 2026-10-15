package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.definition.ScaledCurve;
import com.eden.particles.impl.definition.SpriteRef;
import com.eden.particles.impl.definition.TrailSpec;
import com.eden.particles.impl.math.BakedGradient;
import com.eden.particles.impl.math.Gradient;

import java.util.Optional;

/** Baked curves and resolved settings for a particle ribbon trail. */
public record BakedTrail(
        int lifetime,
        int maxPoints,
        float minDistance,
        BakedScaledCurve width,
        BakedScaledCurve alpha,
        BakedGradient color,
        Optional<SpriteRef> sprite,
        TrailSpec.Mode mode,
        float tileLength,
        Optional<RenderMode.Blend> blend,
        Optional<Boolean> emissive) {

    public static BakedTrail bake(TrailSpec spec, ScaledCurve fallbackWidth, ScaledCurve fallbackAlpha, Gradient fallbackColor) {
        return new BakedTrail(
                spec.lifetime(),
                spec.maxPoints(),
                spec.minDistance(),
                BakedScaledCurve.bake(spec.width().orElse(fallbackWidth)),
                BakedScaledCurve.bake(spec.alpha().orElse(fallbackAlpha)),
                spec.color().orElse(fallbackColor).bake(),
                spec.sprite(),
                spec.mode(),
                spec.tileLength(),
                spec.blend(),
                spec.emissive());
    }

    public float sampleWidth(float frac) {
        return width.curve().sample(frac);
    }

    public float sampleAlpha(float frac) {
        return alpha.curve().sample(frac);
    }
}
