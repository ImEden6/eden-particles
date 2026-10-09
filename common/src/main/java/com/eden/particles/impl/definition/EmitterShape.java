package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.expr.Expr;
import com.mojang.serialization.Codec;

import java.util.List;
import java.util.Set;

/** Where an emitter spawns, in its local frame. Radii are curves over emitter time; other sizes are expressions. */
public sealed interface EmitterShape {

    default void names(Set<String> out) {
        switch (this) {
            case Point p -> {
            }
            case Sphere s -> {
                s.radius.names(out);
                s.innerRadius.names(out);
            }
            case Hemisphere h -> h.radius.names(out);
            case Disc d -> {
                d.radius.names(out);
                d.innerRadius.names(out);
            }
            case Ring r -> r.radius.names(out);
            case Box b -> b.size.names(out);
            case Line l -> {
                l.from.names(out);
                l.to.names(out);
            }
        }
    }

    record Point() implements EmitterShape {
    }

    record Sphere(ScaledCurve radius, Expr innerRadius, boolean surface) implements EmitterShape {
    }

    record Hemisphere(ScaledCurve radius, boolean surface) implements EmitterShape {
    }

    record Disc(ScaledCurve radius, Expr innerRadius) implements EmitterShape {
    }

    record Ring(ScaledCurve radius) implements EmitterShape {
    }

    /** {@code size} is the full extent on each axis. */
    record Box(ExprVec3 size, boolean surface) implements EmitterShape {
    }

    record Line(ExprVec3 from, ExprVec3 to) implements EmitterShape {
    }

    Point POINT = new Point();

    List<String> TYPES = List.of("point", "sphere", "hemisphere", "disc", "ring", "box", "line");

    Codec<EmitterShape> CODEC = ObjectCodec.of("shape", f -> {
        String type = f.required("type", Codecs.STRING);
        Codec<Expr> inner = ExprCodecs.atLeast(ExprCodecs.NUMBER, 0f);
        return switch (type) {
            case "point" -> POINT;
            case "sphere" -> {
                Expr innerRadius = f.optional("inner_radius", inner, Expr.ZERO);
                boolean surface = f.optional("surface", Codecs.BOOL, false);
                if (surface && !innerRadius.equals(Expr.ZERO)) throw f.failAt("inner_radius", "has no effect when 'surface' is true");
                yield new Sphere(f.required("radius", CurveCodecs.SCALED), innerRadius, surface);
            }
            case "hemisphere" -> new Hemisphere(f.required("radius", CurveCodecs.SCALED), f.optional("surface", Codecs.BOOL, false));
            case "disc" -> new Disc(f.required("radius", CurveCodecs.SCALED), f.optional("inner_radius", inner, Expr.ZERO));
            case "ring" -> new Ring(f.required("radius", CurveCodecs.SCALED));
            case "box" -> new Box(f.required("size", ExprCodecs.VEC3), f.optional("surface", Codecs.BOOL, false));
            case "line" -> new Line(f.required("from", ExprCodecs.VEC3), f.required("to", ExprCodecs.VEC3));
            default -> throw f.failAt("type", Codecs.unknownChoice("shape type", type, TYPES));
        };
    }, (shape, out) -> {
        switch (shape) {
            case Point p -> out.put("type", Codecs.STRING, "point");
            case Sphere s -> out.put("type", Codecs.STRING, "sphere").put("radius", CurveCodecs.SCALED, s.radius())
                    .put("inner_radius", ExprCodecs.NUMBER, s.innerRadius()).put("surface", Codecs.BOOL, s.surface());
            case Hemisphere h -> out.put("type", Codecs.STRING, "hemisphere").put("radius", CurveCodecs.SCALED, h.radius())
                    .put("surface", Codecs.BOOL, h.surface());
            case Disc d -> out.put("type", Codecs.STRING, "disc").put("radius", CurveCodecs.SCALED, d.radius())
                    .put("inner_radius", ExprCodecs.NUMBER, d.innerRadius());
            case Ring r -> out.put("type", Codecs.STRING, "ring").put("radius", CurveCodecs.SCALED, r.radius());
            case Box b -> out.put("type", Codecs.STRING, "box").put("size", ExprCodecs.VEC3, b.size()).put("surface", Codecs.BOOL, b.surface());
            case Line l -> out.put("type", Codecs.STRING, "line").put("from", ExprCodecs.VEC3, l.from()).put("to", ExprCodecs.VEC3, l.to());
        }
    });
}
