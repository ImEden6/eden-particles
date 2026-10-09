package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.SubEffect;
import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.math.Rotations;
import org.joml.Quaternionfc;

/** A sub-effect with its rotation and input expressions ready for per-tick use. */
public record BakedSubEffect(int index, SubEffect definition, Quaternionfc rotation, String[] inputNames, Expr[] inputValues) {

    public static BakedSubEffect bake(int index, SubEffect sub) {
        return new BakedSubEffect(index, sub,
                Rotations.fromYawPitchRoll(sub.rotation().x(), sub.rotation().y(), sub.rotation().z()),
                sub.inputs().keySet().toArray(String[]::new),
                sub.inputs().values().toArray(Expr[]::new));
    }
}
