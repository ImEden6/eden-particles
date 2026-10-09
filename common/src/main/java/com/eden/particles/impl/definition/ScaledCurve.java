package com.eden.particles.impl.definition;

import com.eden.particles.impl.expr.Expr;
import com.eden.particles.impl.math.FloatCurve;

import java.util.Set;

/** A curve over time multiplied by an expression over inputs: {@code curve(t) · scale}. */
public record ScaledCurve(FloatCurve curve, Expr scale) {

    public static ScaledCurve constant(double value) {
        return new ScaledCurve(FloatCurve.constant(value), Expr.ONE);
    }

    public static ScaledCurve of(FloatCurve curve) {
        return new ScaledCurve(curve, Expr.ONE);
    }

    public void names(Set<String> out) {
        scale.names(out);
    }
}
