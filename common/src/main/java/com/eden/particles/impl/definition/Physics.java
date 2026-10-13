package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.eden.particles.impl.expr.Expr;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Vanilla particle physics: {@code gravity} scales 0.04 blocks/tick², {@code drag} is the per-tick friction. Gravity
 * and drag may be expressions, evaluated when a particle spawns. {@code forces} add further pushes each tick.
 */
public record Physics(Expr gravity, Expr drag, boolean collide, List<Force> forces) {

    public static final Physics DEFAULT = new Physics(Expr.ZERO, Expr.constant(0.98f), false, List.of());
    private static final float MIN_DRAG = 0.001f;

    private static final Codec<Expr> DRAG = ExprCodecs.NUMBER.validate(e -> !(e instanceof Expr.Const c) || (c.value() > 0f && c.value() <= 1f)
            ? DataResult.success(e)
            : DataResult.error(() -> "must be greater than 0 and at most 1, got " + ((Expr.Const) e).value()));

    /** Drag clamped to (0, 1]. */
    public static float clampDrag(float drag) {
        return Math.max(MIN_DRAG, Math.min(1f, drag));
    }

    public void names(Set<String> out) {
        gravity.names(out);
        drag.names(out);
        forces.forEach(force -> force.names(out));
    }

    public static final Codec<Physics> CODEC = ObjectCodec.of("physics",
            f -> new Physics(
                    f.optional("gravity", ExprCodecs.NUMBER, DEFAULT.gravity),
                    f.optional("drag", DRAG, DEFAULT.drag),
                    f.optional("collide", Codecs.BOOL, DEFAULT.collide),
                    f.optional("forces", Force.LIST, DEFAULT.forces)),
            (p, out) -> out.put("gravity", ExprCodecs.NUMBER, p.gravity).put("drag", ExprCodecs.NUMBER, p.drag).put("collide", Codecs.BOOL, p.collide)
                    .put("forces", Force.LIST, p.forces.isEmpty() ? Optional.empty() : Optional.of(p.forces)));
}
