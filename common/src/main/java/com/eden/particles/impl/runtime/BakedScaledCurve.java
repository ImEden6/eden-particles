package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.ScaledCurve;
import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.math.BakedCurve;

/** A baked curve and the expression scaling it; callers evaluate the scale once per tick or spawn. */
public record BakedScaledCurve(BakedCurve curve, Expr scale) {

    public static final BakedScaledCurve ZERO = bake(ScaledCurve.constant(0.0));
    public static final BakedScaledCurve ONE = bake(ScaledCurve.constant(1.0));

    public static BakedScaledCurve bake(ScaledCurve curve) {
        return new BakedScaledCurve(BakedCurve.bake(curve.curve()), curve.scale());
    }

    public float scale(float[] inputs) {
        return scale.eval(inputs);
    }
}
