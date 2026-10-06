package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.EmitterShape;
import com.eden.particles.impl.definition.EmitterTrack;
import com.eden.particles.impl.math.BakedCurve;
import com.eden.particles.impl.math.FloatCurve;
import com.eden.particles.impl.math.Rotations;
import com.eden.particles.impl.math.SpawnIntegrator;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import java.util.Optional;

public record BakedEmitter(
        int index,
        EmitterTrack track,
        BakedTemplate template,
        Optional<SpawnIntegrator> rate,
        BakedCurve radius,
        BakedCurve spin,
        Quaternionfc axisRotation) {

    public static BakedEmitter bake(int index, EmitterTrack track, BakedTemplate template) {
        FloatCurve radius = switch (track.shape()) {
            case EmitterShape.Sphere s -> s.radius();
            case EmitterShape.Hemisphere h -> h.radius();
            case EmitterShape.Disc d -> d.radius();
            case EmitterShape.Ring r -> r.radius();
            default -> FloatCurve.constant(0.0);
        };
        Quaternionf axis = Rotations.alignYTo(track.axis().x(), track.axis().y(), track.axis().z());
        return new BakedEmitter(index, track, template,
                track.spawn().rate().map(curve -> new SpawnIntegrator(curve, track.duration())),
                BakedCurve.bake(radius),
                BakedCurve.bake(track.spin().orElse(FloatCurve.constant(0.0))),
                axis);
    }
}
