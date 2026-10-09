package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.EmitterShape;
import com.eden.particles.impl.definition.EmitterTrack;
import com.eden.particles.impl.definition.ScaledCurve;
import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.math.Rotations;
import com.eden.particles.impl.math.SpawnIntegrator;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.Optional;

/** {@code rateScale} multiplies the rate curve integrated by {@code rate}. */
public record BakedEmitter(
        int index,
        EmitterTrack track,
        BakedTemplate template,
        Optional<SpawnIntegrator> rate,
        Expr rateScale,
        BakedScaledCurve radius,
        BakedScaledCurve spin,
        Quaternionfc axisRotation) {

    public static BakedEmitter bake(int index, EmitterTrack track, BakedTemplate template) {
        ScaledCurve radius = switch (track.shape()) {
            case EmitterShape.Sphere s -> s.radius();
            case EmitterShape.Hemisphere h -> h.radius();
            case EmitterShape.Disc d -> d.radius();
            case EmitterShape.Ring r -> r.radius();
            default -> ScaledCurve.constant(0.0);
        };
        Quaternionf axis = Rotations.alignYTo(track.axis().x(), track.axis().y(), track.axis().z());
        return new BakedEmitter(index, track, template,
                track.spawn().rate().map(curve -> new SpawnIntegrator(curve.curve(), track.duration())),
                track.spawn().rate().map(ScaledCurve::scale).orElse(Expr.ONE),
                BakedScaledCurve.bake(radius),
                track.spin().map(BakedScaledCurve::bake).orElse(BakedScaledCurve.ZERO),
                axis);
    }
}
