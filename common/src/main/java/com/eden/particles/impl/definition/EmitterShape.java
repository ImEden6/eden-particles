package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.eden.particles.impl.math.FloatCurve;
import com.mojang.serialization.Codec;
import org.joml.Vector3fc;

import java.util.List;

/** Where an emitter spawns, in its local frame. Radii are curves over emitter time. */
public sealed interface EmitterShape {

    record Point() implements EmitterShape {
    }

    record Sphere(FloatCurve radius, float innerRadius, boolean surface) implements EmitterShape {
    }

    record Hemisphere(FloatCurve radius, boolean surface) implements EmitterShape {
    }

    record Disc(FloatCurve radius, float innerRadius) implements EmitterShape {
    }

    record Ring(FloatCurve radius) implements EmitterShape {
    }

    /** {@code size} is the full extent on each axis. */
    record Box(Vector3fc size, boolean surface) implements EmitterShape {
    }

    record Line(Vector3fc from, Vector3fc to) implements EmitterShape {
    }

    Point POINT = new Point();

    List<String> TYPES = List.of("point", "sphere", "hemisphere", "disc", "ring", "box", "line");

    Codec<EmitterShape> CODEC = ObjectCodec.of("shape", f -> {
        String type = f.required("type", Codecs.STRING);
        Codec<Float> inner = Codecs.floatAtLeast(0f);
        return switch (type) {
            case "point" -> POINT;
            case "sphere" -> {
                float innerRadius = f.optional("inner_radius", inner, 0f);
                boolean surface = f.optional("surface", Codecs.BOOL, false);
                if (surface && innerRadius > 0f) throw f.failAt("inner_radius", "has no effect when 'surface' is true");
                yield new Sphere(f.required("radius", CurveCodecs.CURVE), innerRadius, surface);
            }
            case "hemisphere" -> new Hemisphere(f.required("radius", CurveCodecs.CURVE), f.optional("surface", Codecs.BOOL, false));
            case "disc" -> new Disc(f.required("radius", CurveCodecs.CURVE), f.optional("inner_radius", inner, 0f));
            case "ring" -> new Ring(f.required("radius", CurveCodecs.CURVE));
            case "box" -> new Box(f.required("size", Codecs.VEC3), f.optional("surface", Codecs.BOOL, false));
            case "line" -> new Line(f.required("from", Codecs.VEC3), f.required("to", Codecs.VEC3));
            default -> throw f.failAt("type", Codecs.unknownChoice("shape type", type, TYPES));
        };
    }, (shape, out) -> {
        switch (shape) {
            case Point p -> out.put("type", Codecs.STRING, "point");
            case Sphere s -> out.put("type", Codecs.STRING, "sphere").put("radius", CurveCodecs.CURVE, s.radius())
                    .put("inner_radius", Codecs.FLOAT, s.innerRadius()).put("surface", Codecs.BOOL, s.surface());
            case Hemisphere h -> out.put("type", Codecs.STRING, "hemisphere").put("radius", CurveCodecs.CURVE, h.radius())
                    .put("surface", Codecs.BOOL, h.surface());
            case Disc d -> out.put("type", Codecs.STRING, "disc").put("radius", CurveCodecs.CURVE, d.radius())
                    .put("inner_radius", Codecs.FLOAT, d.innerRadius());
            case Ring r -> out.put("type", Codecs.STRING, "ring").put("radius", CurveCodecs.CURVE, r.radius());
            case Box b -> out.put("type", Codecs.STRING, "box").put("size", Codecs.VEC3, b.size()).put("surface", Codecs.BOOL, b.surface());
            case Line l -> out.put("type", Codecs.STRING, "line").put("from", Codecs.VEC3, l.from()).put("to", Codecs.VEC3, l.to());
        }
    });
}
