package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.mojang.serialization.Codec;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

/** Initial velocity of a spawned particle, relative to the emitter's local frame. */
public sealed interface VelocityMode {

    record None() implements VelocityMode {
    }

    record Fixed(Vector3fc direction, Speed speed) implements VelocityMode {
    }

    /** Away from the shape's centre. */
    record Radial(Speed speed) implements VelocityMode {
    }

    record TowardCenter(Speed speed) implements VelocityMode {
    }

    /** Around the emitter axis: {@code axis × radial}. */
    record Tangent(Speed speed) implements VelocityMode {
    }

    /** Uniform within {@code halfAngle} degrees of the emitter axis. */
    record Cone(float halfAngle, Speed speed) implements VelocityMode {
    }

    None NONE = new None();

    List<String> TYPES = List.of("none", "fixed", "radial", "toward_center", "tangent", "cone");

    Codec<VelocityMode> CODEC = ObjectCodec.of("velocity", f -> {
        String type = f.required("type", Codecs.STRING);
        return switch (type) {
            case "none" -> NONE;
            case "fixed" -> {
                Vector3fc direction = f.required("direction", Codecs.VEC3);
                if (direction.lengthSquared() < 1e-12f) throw f.failAt("direction", "must not be zero");
                yield new Fixed(new Vector3f(direction).normalize(), f.required("speed", Speed.CODEC));
            }
            case "radial" -> new Radial(f.required("speed", Speed.CODEC));
            case "toward_center" -> new TowardCenter(f.required("speed", Speed.CODEC));
            case "tangent" -> new Tangent(f.required("speed", Speed.CODEC));
            case "cone" -> {
                float halfAngle = f.required("half_angle", Codecs.floatIn(0f, 180f));
                if (halfAngle <= 0f) throw f.failAt("half_angle", "must be greater than 0");
                yield new Cone(halfAngle, f.required("speed", Speed.CODEC));
            }
            default -> throw f.failAt("type", Codecs.unknownChoice("velocity type", type, TYPES));
        };
    }, (velocity, out) -> {
        switch (velocity) {
            case None n -> out.put("type", Codecs.STRING, "none");
            case Fixed x -> out.put("type", Codecs.STRING, "fixed").put("direction", Codecs.VEC3, x.direction()).put("speed", Speed.CODEC, x.speed());
            case Radial r -> out.put("type", Codecs.STRING, "radial").put("speed", Speed.CODEC, r.speed());
            case TowardCenter t -> out.put("type", Codecs.STRING, "toward_center").put("speed", Speed.CODEC, t.speed());
            case Tangent t -> out.put("type", Codecs.STRING, "tangent").put("speed", Speed.CODEC, t.speed());
            case Cone c -> out.put("type", Codecs.STRING, "cone").put("half_angle", Codecs.FLOAT, c.halfAngle()).put("speed", Speed.CODEC, c.speed());
        }
    });
}
