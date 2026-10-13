package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.expr.Expr;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.joml.Vector3f;

import java.util.List;
import java.util.Set;

/**
 * A push on each particle every tick, in blocks/tick² scaled by the effect's scale like gravity. {@code strength} is a
 * curve over the particle's life; expressions are evaluated when the particle spawns. Points and lengths are in the
 * effect's space and follow its anchor.
 */
public sealed interface Force {

    int MAX = 8;

    /** Divergence-free noise flow; {@code scale} is the swirl size in blocks, {@code flow} how fast it drifts per tick. */
    record Turbulence(ScaledCurve strength, Expr scale, Expr flow, int octaves) implements Force {
    }

    /** Pulls toward {@code point} (negative strength pushes); particles closer than {@code absorb} die. */
    record Attractor(Point point, ScaledCurve strength, Falloff falloff, Expr radius, Expr absorb) implements Force {
    }

    /** Spins around {@code axis} through {@code point}, with optional pull toward the axis and lift along it. */
    record Vortex(Point point, ExprVec3 axis, ScaledCurve strength, Expr pull, Expr lift, Falloff falloff, Expr radius) implements Force {
    }

    /** How a force weakens with distance; {@code radius} 0 means it reaches everywhere. */
    enum Falloff { NONE, LINEAR, INVERSE_SQUARE }

    /** An offset from the effect's origin, or the far end of a two-point anchor. */
    record Point(boolean target, ExprVec3 offset) {
        public static final Point ORIGIN = new Point(false, ExprVec3.ZERO);
        public static final Point TARGET = new Point(true, ExprVec3.ZERO);
    }

    default void names(Set<String> out) {
        switch (this) {
            case Turbulence t -> {
                t.strength.names(out);
                t.scale.names(out);
                t.flow.names(out);
            }
            case Attractor a -> {
                a.point.offset().names(out);
                a.strength.names(out);
                a.radius.names(out);
                a.absorb.names(out);
            }
            case Vortex v -> {
                v.point.offset().names(out);
                v.axis.names(out);
                v.strength.names(out);
                v.pull.names(out);
                v.lift.names(out);
                v.radius.names(out);
            }
        }
    }

    List<String> TYPES = List.of("turbulence", "attractor", "vortex");

    Codec<Point> POINT = Codecs.byKind("a point: [x, y, z], \"origin\" or \"target\"", Codecs.<Point>encoder((p, ops) -> p.target()
                    ? ops.createString("target")
                    : Codecs.encodeWith(ExprCodecs.VEC3, p.offset(), ops)))
            .when(Codecs.Kind.LIST, ExprCodecs.VEC3.xmap(v -> new Point(false, v), Point::offset))
            .when(Codecs.Kind.STRING, Codecs.STRING.comapFlatMap(s -> switch (s) {
                case "origin" -> DataResult.success(Point.ORIGIN);
                case "target" -> DataResult.success(Point.TARGET);
                default -> DataResult.error(() -> Codecs.unknownChoice("point", s, List.of("origin", "target")));
            }, p -> p.target() ? "target" : "origin"))
            .build();

    Codec<Falloff> FALLOFF = Codecs.enumCodec(Falloff.class, "falloff");

    Codec<Force> CODEC = ObjectCodec.of("force", f -> {
        String type = f.required("type", Codecs.STRING);
        Codec<Expr> length = ExprCodecs.atLeast(ExprCodecs.NUMBER, 0f);
        return switch (type) {
            case "turbulence" -> {
                Expr scale = f.optional("scale", ExprCodecs.NUMBER, Expr.ONE);
                if (scale instanceof Expr.Const c && c.value() <= 0f) throw f.failAt("scale", "must be greater than 0, got " + c.value());
                int octaves = f.optional("octaves", Codecs.INT, 1);
                if (octaves < 1 || octaves > 2) throw f.failAt("octaves", "must be 1 or 2, got " + octaves);
                yield new Turbulence(f.required("strength", CurveCodecs.SCALED), scale, f.optional("flow", ExprCodecs.NUMBER, Expr.ZERO), octaves);
            }
            case "attractor" -> {
                Attractor a = new Attractor(f.optional("point", POINT, Point.ORIGIN), f.required("strength", CurveCodecs.SCALED),
                        f.optional("falloff", FALLOFF, Falloff.NONE), f.optional("radius", length, Expr.ZERO), f.optional("absorb", length, Expr.ZERO));
                checkRadius(f, a.falloff, a.radius);
                yield a;
            }
            case "vortex" -> {
                ExprVec3 axis = f.optional("axis", ExprCodecs.VEC3, ExprVec3.of(0, 1, 0));
                if (axis.isConstant() && axis.eval(new float[0], new Vector3f()).lengthSquared() < 1e-12f) {
                    throw f.failAt("axis", "must not be [0, 0, 0]");
                }
                Vortex v = new Vortex(f.optional("point", POINT, Point.ORIGIN), axis, f.required("strength", CurveCodecs.SCALED),
                        f.optional("pull", ExprCodecs.NUMBER, Expr.ZERO), f.optional("lift", ExprCodecs.NUMBER, Expr.ZERO),
                        f.optional("falloff", FALLOFF, Falloff.NONE), f.optional("radius", length, Expr.ZERO));
                checkRadius(f, v.falloff, v.radius);
                yield v;
            }
            default -> throw f.failAt("type", Codecs.unknownChoice("force type", type, TYPES));
        };
    }, (force, out) -> {
        switch (force) {
            case Turbulence t -> out.put("type", Codecs.STRING, "turbulence").put("strength", CurveCodecs.SCALED, t.strength())
                    .put("scale", ExprCodecs.NUMBER, t.scale()).put("flow", ExprCodecs.NUMBER, t.flow()).put("octaves", Codecs.INT, t.octaves());
            case Attractor a -> out.put("type", Codecs.STRING, "attractor").put("point", POINT, a.point())
                    .put("strength", CurveCodecs.SCALED, a.strength()).put("falloff", FALLOFF, a.falloff())
                    .put("radius", ExprCodecs.NUMBER, a.radius()).put("absorb", ExprCodecs.NUMBER, a.absorb());
            case Vortex v -> out.put("type", Codecs.STRING, "vortex").put("point", POINT, v.point()).put("axis", ExprCodecs.VEC3, v.axis())
                    .put("strength", CurveCodecs.SCALED, v.strength()).put("pull", ExprCodecs.NUMBER, v.pull())
                    .put("lift", ExprCodecs.NUMBER, v.lift()).put("falloff", FALLOFF, v.falloff()).put("radius", ExprCodecs.NUMBER, v.radius());
        }
    });

    Codec<List<Force>> LIST = Codecs.list(CODEC).validate(list -> list.size() <= MAX
            ? DataResult.success(list)
            : DataResult.error(() -> "at most " + MAX + " forces, got " + list.size()));

    private static void checkRadius(ObjectCodec.Fields f, Falloff falloff, Expr radius) {
        if (falloff == Falloff.LINEAR && radius.equals(Expr.ZERO)) {
            throw f.failAt("falloff", "'linear' fades to nothing at 'radius', so it needs one");
        }
    }
}
