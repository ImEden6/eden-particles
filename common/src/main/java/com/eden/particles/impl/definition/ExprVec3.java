package com.eden.particles.impl.definition;

import com.eden.particles.impl.expr.Expr;
import org.joml.Vector3f;

import java.util.Set;

/** {@code [x, y, z]} where each component may be an expression. */
public record ExprVec3(Expr x, Expr y, Expr z) {

    public static final ExprVec3 ZERO = new ExprVec3(Expr.ZERO, Expr.ZERO, Expr.ZERO);

    public static ExprVec3 of(float x, float y, float z) {
        return new ExprVec3(Expr.constant(x), Expr.constant(y), Expr.constant(z));
    }

    public Vector3f eval(float[] inputs, Vector3f out) {
        return out.set(x.eval(inputs), y.eval(inputs), z.eval(inputs));
    }

    public boolean isConstant() {
        return x instanceof Expr.Const && y instanceof Expr.Const && z instanceof Expr.Const;
    }

    public void names(Set<String> out) {
        x.names(out);
        y.names(out);
        z.names(out);
    }
}
