package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.LightCue;

/** A light cue with its level curve baked. */
public record BakedLight(LightCue definition, BakedScaledCurve level) {

    public static BakedLight bake(LightCue light) {
        return new BakedLight(light, BakedScaledCurve.bake(light.level()));
    }
}
