package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ParticleTemplate;
import com.eden.particles.impl.definition.Physics;
import com.eden.particles.impl.definition.RandomInt;
import com.eden.particles.impl.definition.RenderMode;
import com.eden.particles.impl.definition.ScaledCurve;
import com.eden.particles.impl.definition.Trigger;
import com.eden.particles.impl.math.BakedGradient;
import com.eden.particles.impl.math.Gradient;

import java.util.Optional;

/** A template with curves baked for per-tick sampling; defaults filled in. */
public record BakedTemplate(
        RenderMode render,
        RandomInt lifetime,
        BakedScaledCurve size,
        BakedScaledCurve alpha,
        BakedScaledCurve speed,
        BakedGradient color,
        Physics physics,
        BakedForces forces,
        Optional<Trigger> onDeath,
        Optional<Trigger> onLand) {

    public static BakedTemplate bake(ParticleTemplate template) {
        return new BakedTemplate(
                template.render(),
                template.lifetime(),
                BakedScaledCurve.bake(template.size().orElse(ScaledCurve.constant(ParticleTemplate.DEFAULT_SIZE))),
                BakedScaledCurve.bake(template.alpha().orElse(ScaledCurve.constant(1.0))),
                BakedScaledCurve.bake(template.speed().orElse(ScaledCurve.constant(1.0))),
                template.color().orElse(Gradient.constant(0xFFFFFF)).bake(),
                template.physics(),
                BakedForces.bake(template.physics().forces()),
                template.onDeath(),
                template.onLand());
    }

    public void evaluate(float[] inputs, TemplateValues out) {
        out.size = size.scale(inputs);
        out.alpha = alpha.scale(inputs);
        out.speed = speed.scale(inputs);
        out.gravity = physics.gravity().eval(inputs);
        out.drag = Physics.clampDrag(physics.drag().eval(inputs));
        if (!forces.isEmpty()) out.forces = forces.evaluate(inputs);
        onDeath.ifPresent(t -> {
            out.deathChance = t.chance().eval(inputs);
            out.deathInputs = TemplateValues.evaluate(t, inputs);
        });
        onLand.ifPresent(t -> {
            out.landChance = t.chance().eval(inputs);
            out.landInputs = TemplateValues.evaluate(t, inputs);
        });
    }
}
